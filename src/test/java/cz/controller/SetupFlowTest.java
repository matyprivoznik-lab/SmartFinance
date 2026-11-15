package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SetupFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void firstLoginOpensSetupAndStaysThereUntilItIsSaved() throws Exception {
        String email = "setup-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName("Nový OSVČ");
        user.setIco("27074358");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setSetupCompleted(false);
        user.setRole("ROLE_USER");
        userRepository.save(user);

        MvcResult loginPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();
        String loginCsrf = csrf(loginPage.getResponse().getContentAsString());

        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        MvcResult login = mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", email)
                        .param("password", "heslo123")
                        .param("_csrf", loginCsrf))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/setup"))
                .andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/setup"));

        MvcResult page = mockMvc.perform(get("/setup").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Jak se ke mně dostaneš")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("matejprivoznik.eu")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("velocode.eu")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nastavení → Údaje z ARES")))
                .andReturn();

        mockMvc.perform(post("/setup")
                        .session(session)
                        .param("_csrf", csrf(page.getResponse().getContentAsString()))
                        .param("bankAccount", "123456789/0100")
                        .param("phone", "+420 777 111 222")
                        .param("mainActivity", "true")
                        .param("expenseRate", "60")
                        .param("invoiceNumberPattern", "RRRR-CCC")
                        .param("remindersEnabled", "true")
                        .param("gentleReminderDays", "3")
                        .param("formalReminderDays", "14"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard"));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertFalse(saved.needsSetup());
        assertEquals("123456789/0100", saved.getBankAccount());

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void blankBankKeepsThePageAndAnExistingAccountIsNotForcedThroughIt() throws Exception {
        String fresh = "setup-blank-" + UUID.randomUUID() + "@example.com";
        saveUser(fresh, false);
        MockHttpSession session = login(fresh);

        MvcResult page = mockMvc.perform(get("/setup").session(session))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/setup")
                        .session(session)
                        .param("_csrf", csrf(page.getResponse().getContentAsString()))
                        .param("bankAccount", " ")
                        .param("phone", "+420 777 111 222"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/setup"));
        assertEquals(Boolean.FALSE, userRepository.findByEmail(fresh).orElseThrow().getSetupCompleted());

        String legacy = "setup-legacy-" + UUID.randomUUID() + "@example.com";
        saveUser(legacy, null);
        MockHttpSession legacySession = login(legacy);
        mockMvc.perform(get("/dashboard").session(legacySession))
                .andExpect(status().isOk());
    }

    private void saveUser(String email, Boolean setupCompleted) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("heslo123"));
        user.setName("Nový OSVČ");
        user.setIco("27074358");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setSetupCompleted(setupCompleted);
        user.setRole("ROLE_USER");
        userRepository.save(user);
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
