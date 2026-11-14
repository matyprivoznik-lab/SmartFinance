package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AresService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AresService aresService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("step", 1); // Krok 1: Zmíněné zadání IČO
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register/verify-ico")
    public String verifyIco(@RequestParam("ico") String ico, Model model) {
        if (ico == null || ico.trim().isEmpty()) {
            model.addAttribute("error", "Zadejte platné IČO.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }

        if (userRepository.findByIco(ico.trim()).isPresent()) {
            model.addAttribute("error", "Tento subjekt je již v systému zaregistrován.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }

        AresService.AresResponse aresData = aresService.getCompanyDetails(ico);

        if (aresData != null && aresData.getName() != null) {
            User user = new User();
            user.setIco(aresData.getIco());
            user.setName(aresData.getName());
            user.setAddress(aresData.getAddress());

            model.addAttribute("user", user);
            model.addAttribute("step", 2); // Krok 2: Otevření formuláře pro e-mail, heslo a banku
            model.addAttribute("success", "Firma úspěšně ověřena v registru ARES.");
            return "register";
        } else {
            model.addAttribute("error", "Subjekt s tímto IČO nebyl v registru ARES nalezen.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }
    }

    @PostMapping("/register/save")
    public String saveUser(User user,
                           @RequestParam("rawPassword") String rawPassword,
                           @RequestParam(value = "acceptTerms", defaultValue = "false") boolean acceptTerms,
                           @RequestParam(value = "acceptPrivacy", defaultValue = "false") boolean acceptPrivacy,
                           @RequestParam(value = "marketingConsent", defaultValue = "false") boolean marketingConsent,
                           Model model) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || rawPassword == null || rawPassword.length() < 6) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Zadejte platný e-mail a heslo (minimálně 6 znaků).");
        }

        if (!acceptTerms || !acceptPrivacy) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Účet založíme až po potvrzení obchodních podmínek a informací o zpracování osobních údajů. Obojí si rozklikni a přečti. Marketingový souhlas není povinný.");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Tento e-mail je již v systému zaregistrován.");
        }

        LocalDateTime acceptedAt = LocalDateTime.now(ZoneId.of("Europe/Prague"));
        user.setId(null);
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(acceptedAt);
        user.setPrivacyAccepted(true);
        user.setPrivacyAcceptedAt(acceptedAt);
        user.setMarketingConsent(marketingConsent);
        user.setMarketingConsentAt(marketingConsent ? acceptedAt : null);
        userRepository.save(user);

        return "redirect:/login?registered";
    }

    private String registrationError(Model model, User user, boolean acceptTerms, boolean acceptPrivacy,
                                     boolean marketingConsent, String message) {
        model.addAttribute("error", message);
        model.addAttribute("step", 2);
        model.addAttribute("user", user);
        model.addAttribute("acceptTerms", acceptTerms);
        model.addAttribute("acceptPrivacy", acceptPrivacy);
        model.addAttribute("marketingConsent", marketingConsent);
        return "register";
    }
}