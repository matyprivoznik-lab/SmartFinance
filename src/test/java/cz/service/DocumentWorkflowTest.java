package cz.service;

import cz.document.DocumentKind;
import cz.document.VatRegime;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.RecurringTemplate;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.RecurringTemplateRepository;
import cz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        Invoice credit = documentWorkflowService.followUp(actor, issued);
        assertEquals(DocumentKind.CREDIT_NOTE, credit.getDocumentKind());
        assertEquals(-12100.0, credit.getGrossTotal(), 0.001);
        assertEquals(-2100.0, credit.getVatAmount(), 0.001);
        assertEquals(issued.getId(), credit.getSourceDocument().getId());
        assertThrows(IllegalArgumentException.class, () -> documentWorkflowService.followUp(actor, issued));
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
