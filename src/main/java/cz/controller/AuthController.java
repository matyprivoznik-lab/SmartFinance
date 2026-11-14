package cz.controller;

import cz.entity.AccessEvent;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AresService;
import cz.service.AuditService;
import cz.service.EmailService;
import cz.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditService auditService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("step", 1);
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register/verify-ico")
    public String verifyIco(@RequestParam("ico") String ico, Model model) {
        String normalizedIco = AresService.normalizeIco(ico);
        if (normalizedIco == null) {
            model.addAttribute("error", "Zadejte platné IČO.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }

        if (userRepository.findByIco(normalizedIco).isPresent()) {
            model.addAttribute("error", "Tento subjekt je již v systému zaregistrován.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }

        AresService.AresResponse aresData = aresService.getCompanyDetails(normalizedIco);

        if (aresData != null && aresData.getName() != null) {
            User user = new User();
            user.setIco(aresData.getIco());
            user.setName(aresData.getName());
            user.setAddress(aresData.getAddress());

            model.addAttribute("user", user);
            model.addAttribute("step", 2);
            model.addAttribute("success", "Firma úspěšně ověřena v registru ARES. IČO, název a sídlo se berou z registru.");
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
                           HttpServletRequest request,
                           Model model) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || rawPassword == null || rawPassword.length() < 6) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Zadejte platný e-mail a heslo (minimálně 6 znaků).");
        }

        if (!acceptTerms || !acceptPrivacy) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Účet založíme až po potvrzení obchodních podmínek a informací o zpracování osobních údajů. Obojí si rozklikni a přečti. Marketingový souhlas není povinný.");
        }

        String normalizedIco = AresService.normalizeIco(user.getIco());
        AresService.AresResponse aresData = normalizedIco == null ? null : aresService.getCompanyDetails(normalizedIco);
        if (aresData == null || aresData.getName() == null) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Subjekt s tímto IČO nebyl v registru ARES nalezen. Údaje firmy se musí načíst z registru.");
        }

        String email = user.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Tento e-mail je již v systému zaregistrován.");
        }
        if (userRepository.findByIco(aresData.getIco()).isPresent()) {
            return registrationError(model, user, acceptTerms, acceptPrivacy, marketingConsent,
                    "Tento subjekt je již v systému zaregistrován.");
        }

        LocalDateTime acceptedAt = LocalDateTime.now(ZoneId.of("Europe/Prague"));
        user.setId(null);
        user.setEmail(email);
        user.setIco(aresData.getIco());
        user.setName(aresData.getName());
        user.setAddress(aresData.getAddress());
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setRegisteredAt(acceptedAt);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setTermsAccepted(true);
        user.setTermsAcceptedAt(acceptedAt);
        user.setPrivacyAccepted(true);
        user.setPrivacyAcceptedAt(acceptedAt);
        user.setMarketingConsent(marketingConsent);
        user.setMarketingConsentAt(marketingConsent ? acceptedAt : null);
        String code = emailVerificationService.issueCode(user);
        userRepository.save(user);
        emailService.sendVerificationCode(email, code);
        auditService.record(request, user, email, AccessEvent.Type.REGISTRATION, "IČO " + aresData.getIco());
        auditService.record(request, user, email, AccessEvent.Type.EMAIL_CODE_SENT, "První ověřovací kód");

        return "redirect:/register/verify-email?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }

    @GetMapping("/register/verify-email")
    public String verifyEmailPage(@RequestParam(value = "email", required = false) String email, Model model) {
        model.addAttribute("email", email == null ? "" : email);
        return "verify-email";
    }

    @PostMapping("/register/verify-email")
    public String verifyEmail(@RequestParam("email") String email,
                              @RequestParam("code") String code,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Účet s tímto e-mailem neevidujeme.");
            return verifyRedirect(normalizedEmail);
        }

        EmailVerificationService.Result result = emailVerificationService.confirm(user, code);
        if (result != EmailVerificationService.Result.WRONG && result != EmailVerificationService.Result.ALREADY) {
            userRepository.save(user);
        } else if (result == EmailVerificationService.Result.WRONG) {
            userRepository.save(user);
        }

        return switch (result) {
            case OK -> {
                auditService.record(request, user, normalizedEmail, AccessEvent.Type.EMAIL_VERIFIED, null);
                yield "redirect:/login?verified";
            }
            case ALREADY -> "redirect:/login?verified";
            case EXPIRED -> verificationError(redirectAttributes, normalizedEmail, "Kód vypršel. Pošli si nový.");
            case TOO_MANY -> verificationError(redirectAttributes, normalizedEmail, "Příliš mnoho pokusů. Pošli si nový kód.");
            case WRONG -> verificationError(redirectAttributes, normalizedEmail, "Kód nesedí.");
        };
    }

    @PostMapping("/register/verify-email/resend")
    public String resendEmailCode(@RequestParam("email") String email,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Účet s tímto e-mailem neevidujeme.");
            return verifyRedirect(normalizedEmail);
        }
        if (user.isEmailVerified() && user.getEmailVerificationCodeHash() == null) {
            return "redirect:/login?verified";
        }
        if (!emailVerificationService.canResend(user)) {
            redirectAttributes.addFlashAttribute("error", "Nový kód lze poslat nejdřív za minutu.");
            return verifyRedirect(normalizedEmail);
        }
        String code = emailVerificationService.issueCode(user);
        userRepository.save(user);
        emailService.sendVerificationCode(normalizedEmail, code);
        auditService.record(request, user, normalizedEmail, AccessEvent.Type.EMAIL_CODE_SENT, "Nový ověřovací kód");
        redirectAttributes.addFlashAttribute("success", "Poslali jsme nový kód. Platí 15 minut.");
        return verifyRedirect(normalizedEmail);
    }

    private String verificationError(RedirectAttributes redirectAttributes, String email, String message) {
        redirectAttributes.addFlashAttribute("error", message);
        return verifyRedirect(email);
    }

    private String verifyRedirect(String email) {
        return "redirect:/register/verify-email?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
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
