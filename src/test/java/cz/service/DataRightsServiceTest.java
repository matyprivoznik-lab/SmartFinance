package cz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.controller.SettingsController;
import cz.entity.AccessEvent;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.User;
import cz.repository.AccessEventRepository;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import cz.service.AccountModerationService;
import cz.service.AresService;
import cz.service.CustomUserDetailsService;
import cz.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Autowired
    private SettingsController settingsController;

    @Autowired
    private AccountModerationService accountModerationService;

    @Autowired
    private AccessEventRepository accessEventRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private AresService aresService;

    @MockBean
    private EmailService emailService;

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

        AresService.AresResponse ares = new AresService.AresResponse();
        ares.setIco("88000001");
        ares.setName("Asseco Central Europe, a.s.");
        ares.setAddress("Budějovická 778/3a, Michle, 14000 Praha 4");
        when(aresService.getCompanyDetails("88000001")).thenReturn(ares);

        mockMvc.perform(post("/register/save")
                        .param("email", email)
                        .param("rawPassword", "heslo123")
                        .param("name", "Podvodník")
                        .param("ico", "88000001")
                        .param("address", "Falešná 1")
                        .param("bankAccount", "123456789/0100")
                        .param("phone", "+420777000111")
                        .param("acceptTerms", "true")
                        .param("acceptPrivacy", "true")
                        .param("marketingConsent", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register/verify-email?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertEquals("Asseco Central Europe, a.s.", saved.getName());
        assertEquals("88000001", saved.getIco());
        assertEquals("Budějovická 778/3a, Michle, 14000 Praha 4", saved.getAddress());
        assertTrue(saved.isTermsAccepted());
        assertTrue(saved.isPrivacyAccepted());
        assertTrue(saved.isMarketingConsent());
        assertTrue(saved.isEnabled());
        assertFalse(saved.isEmailVerified());
        assertTrue(accessEventRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .anyMatch(event -> email.equals(event.getEmail()) && event.getType() == AccessEvent.Type.REGISTRATION));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq(email), code.capture());

        mockMvc.perform(post("/register/verify-email")
                        .param("email", email)
                        .param("code", code.getValue()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verified"));

        assertTrue(userRepository.findByEmail(email).orElseThrow().isEmailVerified());
    }

    @Test
    void icoStaysLockedAndAdminCanBlockLogin() {
        User user = new User();
        user.setEmail("lock-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName("Původní jméno");
        user.setIco("27074358");
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        RedirectAttributesModelMap redirects = new RedirectAttributesModelMap();
        String email = user.getEmail();
        settingsController.updateSettings("Nové jméno", "00000001", null, "Jiná 2", true, 60.0, false, null,
                null, null, null, () -> email, null, redirects);

        User reloaded = userRepository.findByEmail(email).orElseThrow();
        assertEquals("27074358", reloaded.getIco());
        assertEquals("Původní jméno", reloaded.getName());
        assertEquals("", reloaded.getAddress());
        assertEquals("Název, sídlo, IČO a DIČ jdou změnit jen novým načtením z ARES.", redirects.getFlashAttributes().get("errorMessage"));

        User admin = new User();
        admin.setEmail("admin-" + UUID.randomUUID() + "@example.com");
        admin.setPassword(passwordEncoder.encode("heslo123"));
        admin.setRole("ROLE_ADMIN");
        admin.setEnabled(true);
        admin.setEmailVerified(true);
        admin = userRepository.save(admin);

        assertTrue(accountModerationService.setBlocked(admin, reloaded.getId(), true));
        assertFalse(accountModerationService.setBlocked(admin, admin.getId(), true));
        assertFalse(userRepository.findById(reloaded.getId()).orElseThrow().isEnabled());
        assertFalse(customUserDetailsService.loadUserByUsername(email).isEnabled());
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
        invoice.captureSupplier(user);
        invoiceRepository.save(invoice);
        user.setName("Po exportu");
        user.setAddress("Nová adresa 9");
        userRepository.save(user);

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
        assertEquals("Přenos Test", imported.getSupplierName());

        DataRightsService.ImportResult again = dataRightsService.importInvoices(user, exported);
        assertEquals(0, again.imported());
        assertEquals(1, again.skippedDuplicate());

        dataRightsService.deleteAccount(user);
        assertTrue(userRepository.findByEmail(user.getEmail()).isEmpty());
        assertTrue(invoiceRepository.findByUserEmail(user.getEmail()).isEmpty());
    }

    @Test
    void aresRefreshUpdatesAccountAndLeavesIssuedInvoices() {
        User user = new User();
        user.setEmail("ares-" + UUID.randomUUID() + "@example.com");
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName("Staré jméno");
        user.setAddress("Stará 1, Praha");
        user.setIco("27074358");
        user.setVatNumber("CZ27074358");
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        Invoice issued = new Invoice();
        issued.setUser(user);
        issued.setInvoiceNumber("SNAP-" + UUID.randomUUID());
        issued.setIssueDate(LocalDate.of(2024, 1, 10));
        issued.captureSupplier(user);
        issued = invoiceRepository.save(issued);

        String email = user.getEmail();
        RedirectAttributesModelMap redirects = new RedirectAttributesModelMap();
        when(aresService.getCompanyDetails("27074358")).thenReturn(null);
        settingsController.refreshFromAres(() -> email, null, redirects);
        assertEquals("Staré jméno", userRepository.findByEmail(email).orElseThrow().getName());
        assertEquals("ARES subjekt teď nenašel. Název a sídlo zůstávají beze změny.", redirects.getFlashAttributes().get("errorMessage"));

        AresService.AresResponse fresh = new AresService.AresResponse();
        fresh.setIco("27074358");
        fresh.setName("Nový název s.r.o.");
        fresh.setAddress("Nová 9, Brno");
        fresh.setVatNumber("CZ99999999");
        when(aresService.getCompanyDetails("27074358")).thenReturn(fresh);
        settingsController.refreshFromAres(() -> email, null, redirects);

        User after = userRepository.findByEmail(email).orElseThrow();
        assertEquals("Nový název s.r.o.", after.getName());
        assertEquals("Nová 9, Brno", after.getAddress());
        assertEquals("27074358", after.getIco());
        assertEquals("CZ99999999", after.getVatNumber());

        Invoice kept = invoiceRepository.findById(issued.getId()).orElseThrow();
        assertEquals("Staré jméno", kept.getSupplierName());
        assertEquals("Stará 1, Praha", kept.getSupplierAddress());
        assertEquals("27074358", kept.getSupplierIco());
        assertEquals("CZ27074358", kept.getSupplierVatNumber());

        Invoice newer = new Invoice();
        newer.setUser(after);
        newer.setInvoiceNumber("SNAP-NEW-" + UUID.randomUUID());
        newer.captureSupplier(after);
        newer = invoiceRepository.save(newer);
        assertEquals("Nový název s.r.o.", newer.getSupplierName());
        assertEquals("Nová 9, Brno", newer.getSupplierAddress());
        assertEquals("CZ99999999", newer.getSupplierVatNumber());

        settingsController.updateSettings("Ruční", "00000001", "CZ1", "Falešná 1", true, 60.0, false, "RRRR-CCC", null, null, null, () -> email, null, redirects);
        User locked = userRepository.findByEmail(email).orElseThrow();
        assertEquals("Nový název s.r.o.", locked.getName());
        assertEquals("Nová 9, Brno", locked.getAddress());
        assertEquals("27074358", locked.getIco());
        assertEquals("CZ99999999", locked.getVatNumber());
        assertEquals("Staré jméno", invoiceRepository.findById(issued.getId()).orElseThrow().getSupplierName());
    }
}
