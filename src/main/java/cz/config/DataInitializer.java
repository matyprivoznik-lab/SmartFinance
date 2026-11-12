package cz.config;

import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.Product;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Inicializujeme pouze pokud v databázi zatiaľ žádný uživatel není
        if (userRepository.count() == 0) {

            // 1. Vytvoření testovacího uživatele (OSVČ)
            User testUser = new User();
            testUser.setEmail("jan.novak@example.com");
            testUser.setPassword(passwordEncoder.encode("heslo123"));
            testUser.setName("Jan Novák (Test)");
            testUser.setIco("12345678");
            testUser.setVatNumber("CZ12345678");
            testUser.setAddress("Masarykova 1, Hradec Králové");
            testUser.setPhone("+420 123 456 789");
            testUser.setBankAccount("123456789/0100");
            testUser.setVatPayer(true);         // Plátce DPH
            testUser.setExpenseRate(60.0);      // 60% paušální výdaje
            testUser.setMainActivity(true);     // Výchozí: Hlavní činnost

            userRepository.save(testUser);

            // 2. Vytvoření vzorových produktů / služeb
            Product prod1 = new Product();
            prod1.setName("Tvorba webu v Spring Boot");
            prod1.setUnitPriceGross(BigDecimal.valueOf(12100.0));
            prod1.setUnit("ks");
            prod1.setUser(testUser);
            productRepository.save(prod1);

            Product prod2 = new Product();
            prod2.setName("Konzultace a správcovství");
            prod2.setUnitPriceGross(BigDecimal.valueOf(1512.50));
            prod2.setUnit("hod");
            prod2.setUser(testUser);
            productRepository.save(prod2);

            // 3. Vytvoření vzorového klienta
            Client client = new Client();
            client.setName("ACME Corp s.r.o.");
            client.setIco("87654321");
            client.setVatNumber("CZ87654321");
            client.setAddress("Náměstí Míru 5, Pardubice");
            client.setEmail("info@acme.cz");
            client.setUser(testUser);
            clientRepository.save(client);

            // 4. Vytvoření vzorové faktury pro rok 2026
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber("2026001");
            invoice.setVariableSymbol("2026001");
            invoice.setIssueDate(LocalDate.of(2026, 2, 15));
            invoice.setDueDate(LocalDate.of(2026, 3, 1));
            invoice.setClient(client);
            invoice.setUser(testUser);

            // Vytvoření položky faktury (12 100 Kč včetně 21% DPH, základ 10 000 Kč)
            InvoiceItem item = new InvoiceItem();
            item.setDescription("Tvorba webové aplikace MiniFaktura");
            item.setQuantity(1.0);
            item.setUnitPriceGross(12100.0);
            item.setVatRate(21.0);
            item.setNetAmount(10000.0);
            item.setLineGrossTotal(12100.0);
            item.setInvoice(invoice);

            List<InvoiceItem> items = new ArrayList<>();
            items.add(item);
            invoice.setItems(items);

            invoice.setTaxableSupplyDate(LocalDate.of(2026, 2, 15));
            invoice.setNetAmount(10000.0);
            invoice.setVatAmount(2100.0);
            invoice.setGrossTotal(12100.0);

            invoiceRepository.save(invoice);

            System.out.println(">>> DataInitializer: Úspěšně vytvořen uživatel jan.novak@example.com (heslo: heslo123) s testovacími daty.");
        }
    }
}