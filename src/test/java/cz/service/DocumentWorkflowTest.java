package cz.service;

import cz.billing.SubscriptionPlan;
import cz.billing.SubscriptionStatus;
import cz.document.DocumentKind;
import cz.document.VatRegime;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.RecurringTemplate;
import cz.entity.Subscription;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.RecurringTemplateRepository;
import cz.repository.SubscriptionRepository;
import cz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class DocumentWorkflowTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private RecurringTemplateRepository recurringTemplateRepository;
    @Autowired
    private DocumentWorkflowService documentWorkflowService;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private RecurringInvoiceService recurringInvoiceService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void snapshotsStayAndFollowUpUsesTheSupplierValidAtIssue() {
        User user = user();
        user.setName("Staré jméno");
        user.setAddress("Stará 1");
        user.setIco("27074358");
        user.setVatNumber("CZ27074358");
        user = userRepository.save(user);

        Client client = new Client();
        client.setUser(user);
        client.setName("Odběratel s.r.o.");
        client.setAddress("Zákaznická 2");
        client.setIco("87654321");
        client.setVatNumber("CZ87654321");
        client = clientRepository.save(client);

        Invoice quote = document(user, client, DocumentKind.QUOTE, "N202611-001");
        quote = invoiceRepository.save(quote);

        user.setName("Nové jméno z ARES");
        user.setAddress("Nová 9");
        userRepository.save(user);
        client.setName("Přejmenovaný odběratel");
        clientRepository.save(client);

        Invoice kept = invoiceRepository.findById(quote.getId()).orElseThrow();
        assertEquals("Staré jméno", kept.getSupplierName());
        assertEquals("Odběratel s.r.o.", kept.getCustomerName());

        Invoice invoice = documentWorkflowService.followUp(user, kept);
        assertEquals(DocumentKind.INVOICE, invoice.getDocumentKind());
        assertEquals("Nové jméno z ARES", invoice.getSupplierName());
        assertEquals("Nová 9", invoice.getSupplierAddress());
        assertEquals("Odběratel s.r.o.", invoice.getCustomerName());
        assertEquals("Zákaznická 2", invoice.getCustomerAddress());
        assertEquals(12100.0, invoice.getGrossTotal(), 0.001);
        assertEquals("Staré jméno", invoiceRepository.findById(quote.getId()).orElseThrow().getSupplierName());

        User actor = user;
        Invoice issued = invoice;
        Invoice storno = documentWorkflowService.followUp(actor, issued, DocumentKind.STORNO, null);
        assertEquals(DocumentKind.STORNO, storno.getDocumentKind());
        assertEquals(-12100.0, storno.getGrossTotal(), 0.001);
        assertEquals(-2100.0, storno.getVatAmount(), 0.001);
        assertEquals(issued.getId(), storno.getSourceDocument().getId());
        assertEquals(Invoice.InvoiceStatus.CANCELLED, invoiceRepository.findById(issued.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class, () -> documentWorkflowService.followUp(actor, issued, DocumentKind.STORNO, null));
    }

    @Test
    void creditNoteLowersOnlyTheEnteredAmount() {
        User user = userRepository.save(user());
        Client client = client(user);
        Invoice invoice = invoiceRepository.save(document(user, client, DocumentKind.INVOICE, "202611-010"));

        Long itemId = invoice.getItems().get(0).getId();
        Invoice credit = documentWorkflowService.correct(user, invoice, DocumentKind.CREDIT_NOTE, null,
                java.util.List.of(new DocumentWorkflowService.LineChange(itemId, 1000.0, "Vrácení zboží")));
        assertEquals(DocumentKind.CREDIT_NOTE, credit.getDocumentKind());
        assertEquals(-1000.0, credit.getGrossTotal(), 0.001);
        assertTrue(credit.getItems().get(0).getDescription().contains("Vrácení zboží"));
        assertTrue(credit.getItems().get(0).getDescription().contains("Služba"));
        assertTrue(credit.getItems().get(0).getDescription().contains("202611-010"));
        assertEquals(Invoice.InvoiceStatus.ISSUED, invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus());

        Invoice debit = documentWorkflowService.correct(user, invoice, DocumentKind.DEBIT_NOTE, "Doúčtování za celou zakázku",
                java.util.List.of(new DocumentWorkflowService.LineChange(itemId, 500.0, "")));
        assertEquals(DocumentKind.DEBIT_NOTE, debit.getDocumentKind());
        assertEquals(500.0, debit.getGrossTotal(), 0.001);
        assertTrue(debit.getItems().get(0).getDescription().contains("Doúčtování za celou zakázku"));
        assertEquals("Doúčtování za celou zakázku", debit.getDocumentNote());
        assertThrows(IllegalArgumentException.class,
                () -> documentWorkflowService.correct(user, invoice, DocumentKind.CREDIT_NOTE, null,
                        java.util.List.of(new DocumentWorkflowService.LineChange(itemId, 20000.0, "Moc velká sleva"))));

        Invoice storno = documentWorkflowService.followUp(user, invoice, DocumentKind.STORNO, null);
        assertEquals(DocumentKind.STORNO, storno.getDocumentKind());
        assertEquals(-11600.0, storno.getGrossTotal(), 0.001);
        assertEquals(Invoice.InvoiceStatus.CANCELLED, invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> documentWorkflowService.followUp(user, invoice, DocumentKind.STORNO, null));
    }

    @Test
    void settlementDeductsTheTaxDocumentForTheAdvance() {
        User user = userRepository.save(user());
        Client client = client(user);
        Invoice proforma = invoiceRepository.save(document(user, client, DocumentKind.PROFORMA, "Z202611-010"));

        Invoice tax = documentWorkflowService.followUp(user, proforma, DocumentKind.ADVANCE_TAX, null);
        assertEquals(DocumentKind.ADVANCE_TAX, tax.getDocumentKind());
        assertEquals(12100.0, tax.getGrossTotal(), 0.001);
        assertEquals(Invoice.InvoiceStatus.PAID, invoiceRepository.findById(proforma.getId()).orElseThrow().getStatus());

        Invoice settlement = documentWorkflowService.followUp(user, proforma, DocumentKind.SETTLEMENT, null);
        assertEquals(DocumentKind.SETTLEMENT, settlement.getDocumentKind());
        assertEquals(0.0, settlement.getGrossTotal(), 0.001);
        assertEquals(0.0, settlement.getVatAmount(), 0.001);
    }

    @Test
    void summaryJoinsOneCustomersInvoicesForTheMonth() {
        User user = userRepository.save(user());
        Client client = client(user);
        Invoice first = invoiceRepository.save(document(user, client, DocumentKind.INVOICE, "202611-021"));
        invoiceRepository.save(document(user, client, DocumentKind.INVOICE, "202611-022"));

        Invoice summary = documentWorkflowService.summarize(user, client, YearMonth.of(2026, 11));
        assertEquals(DocumentKind.SUMMARY, summary.getDocumentKind());
        assertEquals(24200.0, summary.getGrossTotal(), 0.001);
        assertEquals(summary.getId(), invoiceRepository.findById(first.getId()).orElseThrow().getSummaryDocument().getId());
        assertThrows(IllegalArgumentException.class,
                () -> documentWorkflowService.summarize(user, client, YearMonth.of(2026, 11)));
    }

    @Test
    void recurringTemplateIssuesOneInvoiceAndMovesTheNextDate() {
        User user = user();
        user.setName("Paušál s.r.o.");
        user.setVatPayer(true);
        user = userRepository.save(user);

        Client client = new Client();
        client.setUser(user);
        client.setName("Web klient");
        client.setEmail("klient@example.com");
        client = clientRepository.save(client);

        RecurringTemplate template = new RecurringTemplate();
        template.setUser(user);
        template.setClient(client);
        template.setTitle("Správa webu");
        template.setDescription("Měsíční správa");
        template.setQuantity(1.0);
        template.setUnitPriceGross(1210.0);
        template.setVatRate(21.0);
        template.setVatRegime(VatRegime.PAYER);
        template.setInterval(RecurringTemplate.Interval.MONTHLY);
        template.setNextIssueDate(LocalDate.of(2026, 11, 1));
        template.setActive(true);
        template = recurringTemplateRepository.save(template);

        assertEquals(0, recurringInvoiceService.issueDue(LocalDate.of(2026, 11, 14)));
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(SubscriptionPlan.YEARLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);

        int issued = recurringInvoiceService.issueDue(LocalDate.of(2026, 11, 14));
        assertEquals(1, issued);
        Invoice invoice = invoiceRepository.findByUser(user).stream()
                .filter(item -> item.getDocumentKind() == DocumentKind.INVOICE)
                .findFirst()
                .orElseThrow();
        assertEquals("Web klient", invoice.getCustomerName());
        assertEquals("Paušál s.r.o.", invoice.getSupplierName());
        assertEquals(1000.0, invoice.getNetAmount(), 0.001);
        assertEquals(LocalDate.of(2026, 12, 1), recurringTemplateRepository.findById(template.getId()).orElseThrow().getNextIssueDate());
        assertTrue(invoice.getInvoiceNumber().startsWith("202611-"));
    }

    private User user() {
        User user = new User();
        user.setEmail("doc-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setVatPayer(true);
        return user;
    }

    private Client client(User user) {
        Client client = new Client();
        client.setUser(user);
        client.setName("Odběratel s.r.o.");
        client.setAddress("Zákaznická 2");
        client.setIco("87654321");
        return clientRepository.save(client);
    }

    private Invoice document(User user, Client client, DocumentKind kind, String number) {
        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setDocumentKind(kind);
        invoice.setVatRegime(VatRegime.PAYER);
        invoice.setInvoiceNumber(number);
        invoice.setIssueDate(LocalDate.of(2026, 11, 1));
        invoice.setTaxableSupplyDate(LocalDate.of(2026, 11, 1));
        invoice.captureSupplier(user);
        invoice.captureCustomer(client);
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Služba");
        item.setQuantity(1.0);
        item.setUnitPriceGross(12100.0);
        item.setVatRate(21.0);
        item.setNetAmount(10000.0);
        item.setLineGrossTotal(12100.0);
        invoice.addItem(item);
        invoice.setNetAmount(10000.0);
        invoice.setVatAmount(2100.0);
        invoice.setGrossTotal(12100.0);
        return invoice;
    }
}
