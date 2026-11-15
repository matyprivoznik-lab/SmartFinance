package cz.service;

import cz.document.DocumentKind;
import cz.entity.Company;
import cz.entity.Expense;
import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.CompanyRepository;
import cz.repository.ExpenseRepository;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CompanyOperationsTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private PaymentMatchService paymentMatchService;
    @Autowired
    private AccountingExportService accountingExportService;
    @Autowired
    private TurnoverService turnoverService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void companiesStaySeparateAndAPaymentMarksTheMatchingInvoice() {
        User user = new User();
        user.setEmail("firmy-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName("Dvě firmy");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        Company first = company("První s.r.o.", "11111111", user);
        Company second = company("Druhá s.r.o.", "22222222", user);

        Invoice open = invoice(user, first, "2026-001", "2026001", 1500.0, LocalDate.of(2026, 11, 1));
        Invoice other = invoice(user, second, "2026-009", "2026009", 1500.0, LocalDate.of(2026, 11, 1));

        assertEquals(1, invoiceRepository.findByCompany(first).stream().filter(item -> item.getId().equals(open.getId())).count());
        assertEquals(0, invoiceRepository.findByCompany(first).stream().filter(item -> item.getId().equals(other.getId())).count());

        PaymentMatchService.Result result = paymentMatchService.importCsv(user, first, "2026-11-14;1500;2026001\n2026-11-14;1500;2026009\n");
        assertEquals(1, result.matched());
        assertEquals(Invoice.InvoiceStatus.PAID, invoiceRepository.findById(open.getId()).orElseThrow().getStatus());
        assertEquals(Invoice.InvoiceStatus.ISSUED, invoiceRepository.findById(other.getId()).orElseThrow().getStatus());

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setCompany(first);
        expense.setIssueDate(LocalDate.of(2026, 10, 1));
        expense.setSupplierName("Pronájem");
        expense.setGrossTotal(400.0);
        expense.setNetAmount(400.0);
        expense.setVatAmount(0.0);
        expenseRepository.save(expense);

        String csv = new String(accountingExportService.csv(first), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("2026-001"));
        assertTrue(csv.contains("Pronájem"));
        assertTrue(!csv.contains("2026-009"));
        String xml = new String(accountingExportService.pohoda(first), StandardCharsets.UTF_8);
        assertTrue(xml.contains("2026-001"));
        assertTrue(!xml.contains("2026-009"));
        assertEquals(1500.0, turnoverService.lastTwelveMonthsNet(first, LocalDate.of(2026, 11, 14)));
        assertEquals(400.0, turnoverService.expenses(first));
    }

    private Company company(String name, String ico, User owner) {
        Company company = new Company();
        company.setOwner(owner);
        company.setName(name);
        company.setIco(ico);
        return companyRepository.save(company);
    }

    private Invoice invoice(User user, Company company, String number, String symbol, double net, LocalDate issued) {
        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setCompany(company);
        invoice.setDocumentKind(DocumentKind.INVOICE);
        invoice.setInvoiceNumber(number);
        invoice.setVariableSymbol(symbol);
        invoice.setIssueDate(issued);
        invoice.setDueDate(issued.plusDays(14));
        invoice.setStatus(Invoice.InvoiceStatus.ISSUED);
        invoice.setCurrency("CZK");
        invoice.setNetAmount(net);
        invoice.setVatAmount(0.0);
        invoice.setGrossTotal(net);
        invoice.setSupplierName(company.getName());
        invoice.setCustomerName("Odběratel");
        return invoiceRepository.save(invoice);
    }
}
