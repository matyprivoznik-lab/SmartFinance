package cz.service;

import cz.document.DocumentKind;
import cz.document.DocumentPricing;
import cz.document.VatRegime;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.RecurringTemplate;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import cz.repository.RecurringTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class RecurringInvoiceService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private final RecurringTemplateRepository recurringTemplateRepository;
    private final InvoiceRepository invoiceRepository;
    private final DocumentNumberService documentNumberService;
    private final PlanQuotaService planQuotaService;
    private final EmailService emailService;

    public RecurringInvoiceService(RecurringTemplateRepository recurringTemplateRepository,
                                   InvoiceRepository invoiceRepository,
                                   DocumentNumberService documentNumberService,
                                   PlanQuotaService planQuotaService,
                                   EmailService emailService) {
        this.recurringTemplateRepository = recurringTemplateRepository;
        this.invoiceRepository = invoiceRepository;
        this.documentNumberService = documentNumberService;
        this.planQuotaService = planQuotaService;
        this.emailService = emailService;
    }

    @Transactional
    public int issueDue(LocalDate today) {
        LocalDate date = today == null ? LocalDate.now(ZONE) : today;
        List<RecurringTemplate> due = recurringTemplateRepository.findByActiveTrueAndNextIssueDateLessThanEqual(date);
        int issued = 0;
        for (RecurringTemplate template : due) {
            User user = template.getUser();
            if (user == null || !planQuotaService.canCreateInvoice(user)) {
                continue;
            }
            LocalDate issueDate = template.getNextIssueDate() == null ? date : template.getNextIssueDate();
            Invoice invoice = new Invoice();
            invoice.setUser(user);
            invoice.setDocumentKind(DocumentKind.INVOICE);
            invoice.setClient(template.getClient());
            invoice.captureSupplier(user);
            invoice.captureCustomer(template.getClient());
            invoice.setVatRegime(template.getVatRegime() == null
                    ? (user.isVatPayer() ? VatRegime.PAYER : VatRegime.NON_PAYER)
                    : template.getVatRegime());
            invoice.setCurrency("CZK");
            invoice.setIssueDate(issueDate);
            invoice.setTaxableSupplyDate(issueDate);
            invoice.setDueDate(issueDate.plusDays(14));
            invoice.setStatus(Invoice.InvoiceStatus.ISSUED);
            invoice.setInvoiceNumber(documentNumberService.next(user, DocumentKind.INVOICE, issueDate));
            invoice.setVariableSymbol(invoice.getInvoiceNumber() == null ? null : invoice.getInvoiceNumber().replaceAll("\\D", ""));
            InvoiceItem item = new InvoiceItem();
            item.setDescription(template.getDescription() == null ? template.getTitle() : template.getDescription());
            item.setQuantity(template.getQuantity());
            item.setUnitPriceGross(template.getUnitPriceGross());
            item.setVatRate(template.getVatRate());
            invoice.addItem(item);
            DocumentPricing.apply(invoice);
            invoiceRepository.save(invoice);
            if (template.getClient() != null && template.getClient().getEmail() != null && !template.getClient().getEmail().isBlank()) {
                emailService.sendInvoiceNotification(
                        template.getClient().getEmail(),
                        invoice.getInvoiceNumber(),
                        invoice.getGrossTotal() == null ? 0.0 : invoice.getGrossTotal(),
                        user.getName() == null ? "MiniFaktura" : user.getName());
            }
            template.setNextIssueDate(issueDate.plusMonths(template.getInterval() == RecurringTemplate.Interval.QUARTERLY ? 3 : 1));
            recurringTemplateRepository.save(template);
            issued++;
        }
        return issued;
    }
}
