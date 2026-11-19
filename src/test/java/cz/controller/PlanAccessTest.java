package cz.controller;

import cz.billing.SubscriptionPlan;
import cz.billing.SubscriptionStatus;
import cz.entity.Subscription;
import cz.entity.User;
import cz.repository.SubscriptionRepository;
import cz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanAccessTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void freemiumKeepsTheBasicInvoiceAndLocksTheExtraServices() throws Exception {
        String email = "free-" + UUID.randomUUID() + "@example.com";
        saveUser(email, false);
        MockHttpSession session = login(email);

        mockMvc.perform(get("/invoices/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Freemium vystaví běžnou fakturu")));

        mockMvc.perform(get("/invoices/new").param("kind", "QUOTE").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/billing"));

        mockMvc.perform(get("/taxes").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/billing?locked=1"));

        mockMvc.perform(get("/payments").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/billing?locked=1"));

        mockMvc.perform(get("/export/ucetni.csv").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/billing?locked=1"));

        mockMvc.perform(get("/billing").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Propojení banky, nejen token Fio")))
                .andExpect(content().string(containsString("5 faktur za kalendářní měsíc")));
    }

    @Test
    void premiumOpensTaxesAndUnlimitedInvoices() throws Exception {
        String email = "premium-" + UUID.randomUUID() + "@example.com";
        User user = saveUser(email, true);
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(SubscriptionPlan.MONTHLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);

        MockHttpSession session = login(email);
        mockMvc.perform(get("/taxes").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/payments").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fio")));
        mockMvc.perform(get("/invoices").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Premium · neomezené faktury")));
        mockMvc.perform(get("/invoices/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Zálohová faktura")))
                .andExpect(content().string(containsString("Dobropis, vrubopis, storno")));
    }

    private User saveUser(String email, boolean premium) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName(premium ? "Premium OSVČ" : "Freemium OSVČ");
        user.setIco("27074358");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setRole("ROLE_USER");
        return userRepository.save(user);
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        MvcResult login = mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", email)
                        .param("password", "heslo123")
                        .param("_csrf", csrf(loginPage.getResponse().getContentAsString())))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private static String csrf(String html) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("Stránka neobsahuje CSRF token.");
        }
        return matcher.group(1);
    }
}
