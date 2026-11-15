package cz.service;

import cz.document.DocumentKind;
import cz.document.DocumentPricing;
import cz.document.PublicLinks;
import cz.document.VatRegime;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

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
        if (user == null || source == null || source.getId() == null) {
            throw new IllegalArgumentException("Doklad nebyl nalezen.");
        }
        source = invoiceRepository.findById(source.getId())
                .orElseThrow(() -> new IllegalArgumentException("Doklad nebyl nalezen."));
        if (source.getUser() == null || !source.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Doklad nebyl nalezen.");
        }
        DocumentKind target = targetKind(source.getDocumentKind());
        if (invoiceRepository.existsBySourceDocumentAndDocumentKind(source, target)) {
            throw new IllegalArgumentException("Z tohoto dokladu už navazující doklad vznikl.");
        }

        LocalDate today = LocalDate.now(ZONE);
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
        created.setIssueDate(today);
        created.setTaxableSupplyDate(today);
        created.setDueDate(today.plusDays(14));
        created.setStatus(Invoice.InvoiceStatus.ISSUED);
        created.setInvoiceNumber(documentNumberService.next(user, source.getCompany(), target, today));
        created.setVariableSymbol(digits(created.getInvoiceNumber()));

        if (source.getItems() != null) {
            double sign = target == DocumentKind.CREDIT_NOTE ? -1.0 : 1.0;
            for (InvoiceItem item : source.getItems()) {
                InvoiceItem copy = new InvoiceItem();
                copy.setDescription(item.getDescription());
                copy.setQuantity((item.getQuantity() == null ? 1.0 : item.getQuantity()) * sign);
                copy.setUnitPriceGross(item.getUnitPriceGross());
                copy.setVatRate(item.getVatRate());
                created.addItem(copy);
            }
        }
        DocumentPricing.apply(created);
        if (!"CZK".equalsIgnoreCase(created.getCurrency()) && source.getExchangeRate() != null) {
            DocumentPricing.applyRate(created, source.getExchangeRate(), source.getExchangeRateDate());
        }
        if (source.getDocumentKind() == DocumentKind.PROFORMA) {
            source.setStatus(Invoice.InvoiceStatus.PAID);
        }
        return invoiceRepository.save(created);
    }

    private static DocumentKind targetKind(DocumentKind source) {
        if (source == DocumentKind.INVOICE) {
            return DocumentKind.CREDIT_NOTE;
        }
        if (source != null && source.canBecomeInvoice()) {
            return DocumentKind.INVOICE;
        }
        throw new IllegalArgumentException("Z tohoto typu dokladu navazující doklad nevystavíme.");
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
