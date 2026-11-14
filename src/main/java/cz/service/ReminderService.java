package cz.service;

import cz.document.DocumentKind;
import cz.document.ReminderPolicy;
import cz.entity.Company;
import cz.entity.Invoice;
import cz.repository.CompanyRepository;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReminderService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private final InvoiceRepository invoiceRepository;
    private final CompanyRepository companyRepository;
    private final EmailService emailService;

    public ReminderService(InvoiceRepository invoiceRepository,
                           CompanyRepository companyRepository,
                           EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.companyRepository = companyRepository;
        this.emailService = emailService;
    }

    @Transactional
    public int sendDue(LocalDate today) {
        LocalDate date = today == null ? LocalDate.now(ZONE) : today;
        int sent = 0;
        for (Company company : companyRepository.findAll()) {
            List<Invoice> invoices = invoiceRepository.findByCompany(company);
            for (Invoice invoice : invoices) {
                if (invoice.getDocumentKind() == DocumentKind.QUOTE || invoice.getDocumentKind() == DocumentKind.ORDER) {
                    continue;
                }
                if (invoice.getStatus() == Invoice.InvoiceStatus.PAID || invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
                    continue;
                }
                if (invoice.getDueDate() != null && date.isAfter(invoice.getDueDate())
                        && invoice.getStatus() == Invoice.InvoiceStatus.ISSUED) {
                    invoice.setStatus(Invoice.InvoiceStatus.OVERDUE);
                }
                ReminderPolicy.Action action = ReminderPolicy.decide(
                        invoice.getDueDate(),
                        date,
                        company.getGentleReminderDays(),
                        company.getFormalReminderDays(),
                        invoice.getGentleReminderSentAt() != null,
                        invoice.getFormalReminderSentAt() != null,
                        company.isRemindersEnabled());
                if (action == ReminderPolicy.Action.NONE) {
                    invoiceRepository.save(invoice);
                    continue;
                }
                String email = invoice.getClient() == null ? null : invoice.getClient().getEmail();
                if (email == null || email.isBlank()) {
                    invoiceRepository.save(invoice);
                    continue;
                }
                String link = invoice.getPublicToken() == null ? "" : "\nNáhled: /p/" + invoice.getPublicToken();
                if (action == ReminderPolicy.Action.FORMAL) {
                    emailService.sendPlain(email,
                            "Upomínka k dokladu " + invoice.getInvoiceNumber(),
                            "Dobrý den,\n\ndoklad " + invoice.getInvoiceNumber() + " je po splatnosti. Prosíme o úhradu." + link);
                    invoice.setFormalReminderSentAt(date);
                } else {
                    emailService.sendPlain(email,
                            "Připomínka dokladu " + invoice.getInvoiceNumber(),
                            "Dobrý den,\n\ndoklad " + invoice.getInvoiceNumber() + " je několik dní po splatnosti. Pokud už je uhrazený, odpověz na tenhle e-mail." + link);
                    invoice.setGentleReminderSentAt(date);
                }
                invoiceRepository.save(invoice);
                sent++;
            }
        }
        return sent;
    }
}
