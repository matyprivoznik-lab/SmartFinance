package cz.service;

import cz.document.DocumentKind;
import cz.document.DocumentPricing;
import cz.document.PublicLinks;
import cz.document.VatRegime;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentWorkflowService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private final InvoiceRepository invoiceRepository;
    private final DocumentNumberService documentNumberService;

    public DocumentWorkflowService(InvoiceRepository invoiceRepository, DocumentNumberService documentNumberService) {
        this.invoiceRepository = invoiceRepository;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public Invoice followUp(User user, Invoice source) {
        return followUp(user, source, null, null);
    }

    @Transactional
    public Invoice followUp(User user, Invoice source, DocumentKind requested, Double amount) {
        source = lockedSource(user, source);
        DocumentKind target = requested == null ? defaultTarget(source.getDocumentKind()) : requested;
        if (target == DocumentKind.CREDIT_NOTE || target == DocumentKind.DEBIT_NOTE) {
            if (amount == null) {
                throw new IllegalArgumentException("U položky zadej rozdíl částky a důvod opravy.");
            }
            Long itemId = firstItemId(source);
            String reason = target == DocumentKind.CREDIT_NOTE ? "Snížení ceny" : "Zvýšení ceny";
            return correct(user, source, target, null, List.of(new LineChange(itemId, amount, reason)));
        }
        if (target == DocumentKind.STORNO) {
            return storno(user, source);
        }
        if (target == DocumentKind.ADVANCE_TAX) {
            return advanceTax(user, source);
        }
        if (target == DocumentKind.SETTLEMENT) {
            return settlement(user, source);
        }
        if (target == DocumentKind.INVOICE && source.getDocumentKind().canBecomeInvoice()) {
            return copy(user, source, DocumentKind.INVOICE, 1.0, LocalDate.now(ZONE));
        }
        throw new IllegalArgumentException("Z tohoto typu dokladu takový navazující doklad nevystavíme.");
    }

    @Transactional
    public Invoice summarize(User user, Client client, YearMonth month) {
        if (user == null || client == null || client.getId() == null || month == null) {
            throw new IllegalArgumentException("Vyber odběratele a měsíc.");
        }
        if (client.getUser() == null || !client.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Odběratel nebyl nalezen.");
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<Invoice> included = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByClient(client)) {
            if (invoice.getUser() == null || !invoice.getUser().getId().equals(user.getId())) {
                continue;
            }
            if (invoice.getDocumentKind() != DocumentKind.INVOICE) {
                continue;
            }
            if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED || invoice.getSummaryDocument() != null) {
                continue;
            }
            if (invoice.getIssueDate() == null || invoice.getIssueDate().isBefore(from) || invoice.getIssueDate().isAfter(to)) {
                continue;
            }
            included.add(invoice);
        }
        if (included.isEmpty()) {
            throw new IllegalArgumentException("V tom měsíci nemá ten odběratel žádnou běžnou fakturu, která by ještě nebyla v souhrnu.");
        }

        LocalDate latest = to.plusDays(15);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate issueDate = today.isAfter(latest) ? latest : today;
        Invoice summary = shell(user, included.get(0), DocumentKind.SUMMARY, issueDate);
        summary.setSourceDocument(null);
        summary.setTaxableSupplyDate(to);
        summary.setDueDate(issueDate.plusDays(14));
        summary.setClient(client);
        summary.captureCustomer(client);
        for (Invoice invoice : included) {
            if (invoice.getItems() == null) {
                continue;
            }
            for (InvoiceItem item : invoice.getItems()) {
                InvoiceItem copy = copyItem(item, 1.0);
                copy.setDescription(invoice.getInvoiceNumber() + " — " + (item.getDescription() == null ? "Položka" : item.getDescription()));
                summary.addItem(copy);
            }
        }
        DocumentPricing.apply(summary);
        Invoice saved = invoiceRepository.save(summary);
        for (Invoice invoice : included) {
            invoice.setSummaryDocument(saved);
            invoiceRepository.save(invoice);
        }
        return saved;
    }

    @Transactional
    public Invoice correct(User user, Invoice source, DocumentKind target, String note, List<LineChange> lines) {
        source = lockedSource(user, source);
        if (target != DocumentKind.CREDIT_NOTE && target != DocumentKind.DEBIT_NOTE) {
            throw new IllegalArgumentException("Tak se vystavuje jen dobropis nebo vrubopis.");
        }
        requireOpenTaxInvoice(source);
        rejectWhenStornoExists(source);
        String header = cleanNote(note);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("U položky zadej rozdíl částky a důvod opravy.");
        }
        Invoice created = shell(user, source, target, LocalDate.now(ZONE));
        created.setDocumentNote(header);
        boolean any = false;
        for (LineChange change : lines) {
            if (change == null || change.amount() == null || change.amount() <= 0.0) {
                continue;
            }
            InvoiceItem sourceItem = itemOf(source, change.itemId());
            String reason = change.reason() == null ? "" : change.reason().trim();
            if (reason.isEmpty()) {
                reason = header == null ? "" : header;
            }
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("U položky „" + labelOf(sourceItem) + "“ chybí důvod opravy.");
            }
            if (reason.length() > 300) {
                reason = reason.substring(0, 300);
            }
            double amount = change.amount();
            if (target == DocumentKind.CREDIT_NOTE) {
                double room = Math.abs(lineGross(sourceItem)) - creditedOnItem(source, sourceItem.getId());
                if (amount > room + 0.01) {
                    throw new IllegalArgumentException("Dobropis u položky „" + labelOf(sourceItem) + "“ může snížit ještě " + money(room) + " Kč.");
                }
            }
            InvoiceItem line = new InvoiceItem();
            line.setDescription(reason + " – " + labelOf(sourceItem) + " (k faktuře č. " + source.getInvoiceNumber() + ")");
            line.setQuantity(target == DocumentKind.CREDIT_NOTE ? -1.0 : 1.0);
            line.setUnitPriceGross(amount);
            line.setVatRate(sourceItem.getVatRate() == null ? firstRate(source) : sourceItem.getVatRate());
            line.setSourceItemId(sourceItem.getId());
            created.addItem(line);
            any = true;
        }
        if (!any) {
            throw new IllegalArgumentException("U položky zadej rozdíl částky a důvod opravy. Novou cenu nepiš, jen o kolik se mění.");
        }
        DocumentPricing.apply(created);
        return invoiceRepository.save(created);
    }

    private Invoice storno(User user, Invoice source) {
        requireOpenTaxInvoice(source);
        once(source, DocumentKind.STORNO);
        Map<Long, Double> byItem = new HashMap<>();
        double loose = 0.0;
        boolean hasCorrections = false;
        for (Invoice child : invoiceRepository.findBySourceDocument(source)) {
            if (child.getDocumentKind() != DocumentKind.CREDIT_NOTE && child.getDocumentKind() != DocumentKind.DEBIT_NOTE) {
                continue;
            }
            hasCorrections = true;
            if (child.getItems() == null) {
                continue;
            }
            for (InvoiceItem item : child.getItems()) {
                if (item.getSourceItemId() != null) {
                    byItem.merge(item.getSourceItemId(), lineGross(item), Double::sum);
                } else {
                    loose += lineGross(item);
                }
            }
        }
        Invoice created;
        if (!hasCorrections) {
            created = copy(user, source, DocumentKind.STORNO, -1.0, LocalDate.now(ZONE));
            if (created.getItems() != null) {
                for (InvoiceItem item : created.getItems()) {
                    item.setDescription("Úplné zrušení – " + labelOf(item) + " (k faktuře č. " + source.getInvoiceNumber() + ")");
                }
            }
        } else {
            created = shell(user, source, DocumentKind.STORNO, LocalDate.now(ZONE));
            if (source.getItems() != null) {
                for (InvoiceItem item : source.getItems()) {
                    double remaining = lineGross(item) + byItem.getOrDefault(item.getId(), 0.0);
                    if (Math.abs(remaining) < 0.01) {
                        continue;
                    }
                    created.addItem(reversal(item, remaining, source.getInvoiceNumber()));
                }
            }
            if (Math.abs(loose) >= 0.01) {
                InvoiceItem line = new InvoiceItem();
                line.setDescription("Úplné zrušení zbývající opravy (k faktuře č. " + source.getInvoiceNumber() + ")");
                line.setQuantity(loose > 0 ? -1.0 : 1.0);
                line.setUnitPriceGross(Math.abs(loose));
                line.setVatRate(firstRate(source));
                created.addItem(line);
            }
            if (created.getItems() == null || created.getItems().isEmpty()) {
                InvoiceItem line = new InvoiceItem();
                line.setDescription("Úplné zrušení faktury č. " + source.getInvoiceNumber() + ". Opravy už její hodnotu vynulovaly.");
                line.setQuantity(-1.0);
                line.setUnitPriceGross(0.0);
                line.setVatRate(0.0);
                created.addItem(line);
            }
            DocumentPricing.apply(created);
            created = invoiceRepository.save(created);
        }
        created.setDocumentNote("Úplné zrušení faktury č. " + source.getInvoiceNumber() + " od samého počátku.");
        invoiceRepository.save(created);
        source.setStatus(Invoice.InvoiceStatus.CANCELLED);
        invoiceRepository.save(source);
        return created;
    }

    private Invoice advanceTax(User user, Invoice source) {
        if (source.getDocumentKind() != DocumentKind.PROFORMA) {
            throw new IllegalArgumentException("Daňový doklad k platbě navazuje jen na zálohovou fakturu.");
        }
        boolean payer = user.isVatPayer()
                || source.getVatRegime() == VatRegime.PAYER
                || source.getVatRegime() == VatRegime.REVERSE_CHARGE;
        if (!payer) {
            throw new IllegalArgumentException("Daňový doklad k přijaté platbě vystavuje plátce DPH.");
        }
        once(source, DocumentKind.ADVANCE_TAX);
        if (source.getPaidAt() == null) {
            source.setPaidAt(LocalDate.now(ZONE));
            source.setStatus(Invoice.InvoiceStatus.PAID);
            invoiceRepository.save(source);
        }
        LocalDate paidOn = source.getPaidAt();
        Invoice created = copy(user, source, DocumentKind.ADVANCE_TAX, 1.0, paidOn);
        created.setTaxableSupplyDate(paidOn);
        created.setDueDate(paidOn);
        DocumentPricing.apply(created);
        return invoiceRepository.save(created);
    }

    private Invoice settlement(User user, Invoice source) {
        if (source.getDocumentKind() != DocumentKind.PROFORMA) {
            throw new IllegalArgumentException("Vyúčtovací faktura navazuje na zálohovou fakturu.");
        }
        once(source, DocumentKind.SETTLEMENT);
        LocalDate today = LocalDate.now(ZONE);
        Invoice created = shell(user, source, DocumentKind.SETTLEMENT, today);
        if (source.getItems() != null) {
            for (InvoiceItem item : source.getItems()) {
                created.addItem(copyItem(item, 1.0));
            }
        }
        double deducted = 0.0;
        for (Invoice advance : invoiceRepository.findBySourceDocument(source)) {
            if (advance.getDocumentKind() != DocumentKind.ADVANCE_TAX) {
                continue;
            }
            double gross = advance.getGrossTotal() == null ? 0.0 : advance.getGrossTotal();
            InvoiceItem deduction = new InvoiceItem();
            deduction.setDescription("Odpočet zálohy " + advance.getInvoiceNumber());
            deduction.setQuantity(-1.0);
            deduction.setUnitPriceGross(Math.abs(gross));
            deduction.setVatRate(firstRate(advance));
            created.addItem(deduction);
            deducted += Math.abs(gross);
        }
        if (deducted == 0.0 && source.getStatus() == Invoice.InvoiceStatus.PAID) {
            InvoiceItem deduction = new InvoiceItem();
            deduction.setDescription("Odpočet uhrazené zálohy " + source.getInvoiceNumber());
            deduction.setQuantity(-1.0);
            deduction.setUnitPriceGross(Math.abs(source.getGrossTotal() == null ? 0.0 : source.getGrossTotal()));
            deduction.setVatRate(firstRate(source));
            created.addItem(deduction);
        }
        DocumentPricing.apply(created);
        copyRate(source, created);
        return invoiceRepository.save(created);
    }

    private Invoice copy(User user, Invoice source, DocumentKind target, double sign, LocalDate issueDate) {
        Invoice created = shell(user, source, target, issueDate);
        if (source.getItems() != null) {
            for (InvoiceItem item : source.getItems()) {
                created.addItem(copyItem(item, sign));
            }
        }
        DocumentPricing.apply(created);
        copyRate(source, created);
        return invoiceRepository.save(created);
    }

    private Invoice shell(User user, Invoice source, DocumentKind target, LocalDate issueDate) {
        Invoice created = new Invoice();
        created.setUser(user);
        created.setCompany(source.getCompany());
        PublicLinks.ensure(created);
        created.setDocumentKind(target);
        created.setSourceDocument(source);
        created.setClient(source.getClient());
        created.captureSupplier(user);
        created.copyCustomerFrom(source);
        if (created.getCustomerName() == null) {
            created.captureCustomer(source.getClient());
        }
        created.setVatRegime(source.getVatRegime() == null
                ? (user.isVatPayer() ? VatRegime.PAYER : VatRegime.NON_PAYER)
                : source.getVatRegime());
        created.setCurrency(source.getCurrency());
        created.setPaymentMethod(source.getPaymentMethod());
        created.setIssueDate(issueDate);
        created.setTaxableSupplyDate(issueDate);
        created.setDueDate(issueDate.plusDays(14));
        created.setStatus(Invoice.InvoiceStatus.ISSUED);
        created.setInvoiceNumber(documentNumberService.next(user, source.getCompany(), target, issueDate));
        created.setVariableSymbol(digits(created.getInvoiceNumber()));
        return created;
    }

    private static InvoiceItem copyItem(InvoiceItem item, double sign) {
        InvoiceItem copy = new InvoiceItem();
        copy.setDescription(item.getDescription());
        copy.setQuantity((item.getQuantity() == null ? 1.0 : item.getQuantity()) * sign);
        copy.setUnitPriceGross(item.getUnitPriceGross());
        copy.setVatRate(item.getVatRate());
        return copy;
    }

    private static void copyRate(Invoice source, Invoice created) {
        if (!"CZK".equalsIgnoreCase(created.getCurrency()) && source.getExchangeRate() != null) {
            DocumentPricing.applyRate(created, source.getExchangeRate(), source.getExchangeRateDate());
        }
    }

    private Invoice lockedSource(User user, Invoice source) {
        if (user == null || source == null || source.getId() == null) {
            throw new IllegalArgumentException("Doklad nebyl nalezen.");
        }
        source = invoiceRepository.findById(source.getId())
                .orElseThrow(() -> new IllegalArgumentException("Doklad nebyl nalezen."));
        if (source.getUser() == null || !source.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Doklad nebyl nalezen.");
        }
        if (source.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Stornovaná faktura už se dál neopravuje.");
        }
        return source;
    }

    private static DocumentKind defaultTarget(DocumentKind source) {
        if (source != null && source.canBecomeInvoice()) {
            return DocumentKind.INVOICE;
        }
        throw new IllegalArgumentException("Vyber, jaký navazující doklad se má vystavit.");
    }

    private void requireOpenTaxInvoice(Invoice source) {
        DocumentKind kind = source.getDocumentKind();
        if (kind != DocumentKind.INVOICE && kind != DocumentKind.SETTLEMENT && kind != DocumentKind.SUMMARY) {
            throw new IllegalArgumentException("Dobropis, vrubopis a storno navazují na běžnou, vyúčtovací nebo souhrnnou fakturu.");
        }
    }

    private void rejectWhenStornoExists(Invoice source) {
        if (invoiceRepository.existsBySourceDocumentAndDocumentKind(source, DocumentKind.STORNO)) {
            throw new IllegalArgumentException("Tahle faktura je už stornovaná.");
        }
    }

    private void once(Invoice source, DocumentKind target) {
        if (target != DocumentKind.CREDIT_NOTE && target != DocumentKind.DEBIT_NOTE
                && invoiceRepository.existsBySourceDocumentAndDocumentKind(source, target)) {
            throw new IllegalArgumentException("Z tohoto dokladu už " + target.getLabel().toLowerCase() + " vznikl.");
        }
    }

    private double creditedOnItem(Invoice source, Long itemId) {
        double sum = 0.0;
        for (Invoice child : invoiceRepository.findBySourceDocument(source)) {
            if (child.getDocumentKind() != DocumentKind.CREDIT_NOTE || child.getItems() == null) {
                continue;
            }
            for (InvoiceItem item : child.getItems()) {
                if (itemId != null && itemId.equals(item.getSourceItemId())) {
                    sum += Math.abs(lineGross(item));
                }
            }
        }
        return sum;
    }

    private static InvoiceItem reversal(InvoiceItem sourceItem, double remaining, String invoiceNumber) {
        InvoiceItem line = new InvoiceItem();
        line.setDescription("Úplné zrušení – " + labelOf(sourceItem) + " (k faktuře č. " + invoiceNumber + ")");
        line.setQuantity(remaining > 0 ? -1.0 : 1.0);
        line.setUnitPriceGross(Math.abs(remaining));
        line.setVatRate(sourceItem.getVatRate() == null ? 21.0 : sourceItem.getVatRate());
        line.setSourceItemId(sourceItem.getId());
        return line;
    }

    private static Long firstItemId(Invoice source) {
        if (source.getItems() == null || source.getItems().isEmpty() || source.getItems().get(0).getId() == null) {
            throw new IllegalArgumentException("Faktura nemá položku, kterou by šlo opravit.");
        }
        return source.getItems().get(0).getId();
    }

    private static InvoiceItem itemOf(Invoice source, Long itemId) {
        if (source.getItems() != null && itemId != null) {
            for (InvoiceItem item : source.getItems()) {
                if (itemId.equals(item.getId())) {
                    return item;
                }
            }
        }
        throw new IllegalArgumentException("Položka na původní faktuře nebyla nalezena.");
    }

    private static String labelOf(InvoiceItem item) {
        if (item == null || item.getDescription() == null || item.getDescription().isBlank()) {
            return "Položka";
        }
        return item.getDescription().trim();
    }

    private static double lineGross(InvoiceItem item) {
        if (item.getLineGrossTotal() != null && item.getLineGrossTotal() != 0.0) {
            return item.getLineGrossTotal();
        }
        double quantity = item.getQuantity() == null ? 1.0 : item.getQuantity();
        double price = item.getUnitPriceGross() == null ? 0.0 : item.getUnitPriceGross();
        return Math.round(quantity * price * 100.0) / 100.0;
    }

    private static String cleanNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    public record LineChange(Long itemId, Double amount, String reason) {
    }

    private static double firstRate(Invoice invoice) {
        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                if (item.getVatRate() != null) {
                    return item.getVatRate();
                }
            }
        }
        return invoice.getVatRate() == null ? 21.0 : invoice.getVatRate();
    }

    private static String money(double value) {
        return String.format(java.util.Locale.US, "%.2f", Math.max(0.0, value));
    }

    private static String digits(String number) {
        if (number == null) {
            return null;
        }
        String only = number.replaceAll("\\D", "");
        if (only.isEmpty()) {
            return null;
        }
        return only.length() <= 10 ? only : only.substring(only.length() - 10);
    }
}
