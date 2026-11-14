package cz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DataRightsServiceTest {

    @Autowired
    private DataRightsService dataRightsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationRequiresBothConsentsAndStoresThem() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/register/save")
                        .param("email", email)
                        .param("rawPassword", "heslo123")
                        .param("name", "Test OSVČ")
                        .param("ico", "12345678")
                        .param("address", "Testovací 1")
                        .param("bankAccount", "123456789/0100")
                        .param("phone", "+420777000111")
                        .param("acceptTerms", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Účet založíme")));

        assertTrue(userRepository.findByEmail(email).isEmpty());

        mockMvc.perform(post("/register/save")
                        .param("email", email)
                        .param("rawPassword", "heslo123")
                        .param("name", "Test OSVČ")
                        .param("ico", "12345678")
                        .param("address", "Testovací 1")
                        .param("bankAccount", "123456789/0100")
                        .param("phone", "+420777000111")
                        .param("acceptTerms", "true")
                        .param("acceptPrivacy", "true")
                        .param("marketingConsent", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertTrue(saved.isTermsAccepted());
        assertTrue(saved.isPrivacyAccepted());
        assertTrue(saved.isMarketingConsent());
        assertTrue(saved.getTermsAcceptedAt() != null);
        assertTrue(saved.getPrivacyAcceptedAt() != null);
    }

    @Test
    void invoiceExportImportsBackWithoutSecrets() throws Exception {
        User user = new User();
        user.setEmail("port-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName("Přenos Test");
        user.setMfaSecret("TOTP-SECRET-SHOULD-NOT-LEAK");
        user.setRole("ROLE_USER");
        user = userRepository.save(user);

        Client client = new Client();
        client.setUser(user);
        client.setName("Odběratel Export s.r.o.");
        client.setIco("27074358");
        client = clientRepository.save(client);

        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setInvoiceNumber("EXP-2024-1");
        invoice.setVariableSymbol("2024001");
        invoice.setIssueDate(LocalDate.of(2024, 5, 2));
        invoice.setDueDate(LocalDate.of(2024, 5, 16));
        invoice.setTaxableSupplyDate(LocalDate.of(2024, 5, 2));
        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Konzultace");
        item.setQuantity(2.0);
        item.setUnitPriceGross(1210.0);
        item.setVatRate(21.0);
        invoice.addItem(item);
        invoice.setNetAmount(2000.0);
        invoice.setVatAmount(420.0);
        invoice.setGrossTotal(2420.0);
        invoiceRepository.save(invoice);

        byte[] exported = dataRightsService.exportPersonalData(user);
        String json = new String(exported);
        assertFalse(json.contains("TOTP-SECRET-SHOULD-NOT-LEAK"));
        assertFalse(json.contains(user.getPassword()));
        JsonNode root = objectMapper.readTree(exported);
        assertEquals("minifaktura-invoices", root.get("format").asText());
        assertEquals("Odběratel Export s.r.o.", root.get("invoices").get(0).get("client").get("name").asText());

        invoiceRepository.deleteAll(invoiceRepository.findByUser(user));
        clientRepository.deleteAll(clientRepository.findByUser(user));

        DataRightsService.ImportResult result = dataRightsService.importInvoices(user, exported);
        assertEquals(1, result.imported());

        Invoice imported = invoiceRepository.findByUserAndInvoiceNumber(user, "EXP-2024-1").orElseThrow();
        assertEquals(Invoice.InvoiceStatus.PAID, imported.getStatus());
        assertEquals("Odběratel Export s.r.o.", imported.getClient().getName());
        assertEquals("27074358", imported.getClient().getIco());
        assertEquals(1, imported.getItems().size());
        assertEquals("Konzultace", imported.getItems().get(0).getDescription());
        assertEquals(2420.0, imported.getGrossTotal(), 0.001);

        DataRightsService.ImportResult again = dataRightsService.importInvoices(user, exported);
        assertEquals(0, again.imported());
        assertEquals(1, again.skippedDuplicate());

        dataRightsService.deleteAccount(user);
        assertTrue(userRepository.findByEmail(user.getEmail()).isEmpty());
        assertTrue(invoiceRepository.findByUserEmail(user.getEmail()).isEmpty());
    }
}
