package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.DataRightsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UserRepository userRepository;
    private final DataRightsService dataRightsService;
    private final PasswordEncoder passwordEncoder;

    public SettingsController(UserRepository userRepository,
                              DataRightsService dataRightsService,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.dataRightsService = dataRightsService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String showSettings(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel s e-mailem " + principal.getName() + " nebyl nalezen."));

        model.addAttribute("currentUser", user);
        model.addAttribute("user", user);
        return "settings";
    }

    @PostMapping
    public String updateSettings(@RequestParam(required = false) String name,
                                 @RequestParam(required = false) String ico,
                                 @RequestParam(required = false) String vatNumber,
                                 @RequestParam(required = false) String address,
                                 @RequestParam(required = false) Boolean mainActivity,
                                 @RequestParam(required = false) Double expenseRate,
                                 @RequestParam(value = "vatPayer", required = false) Boolean vatPayer,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));

        if (name != null && !name.isBlank()) {
            currentUser.setName(name.trim());
        }
        currentUser.setIco(ico);
        currentUser.setVatNumber(vatNumber);
        currentUser.setAddress(address);
        currentUser.setMainActivity(mainActivity == null || mainActivity);
        if (expenseRate != null) {
            currentUser.setExpenseRate(expenseRate);
        }
        currentUser.setVatPayer(Boolean.TRUE.equals(vatPayer));

        userRepository.save(currentUser);

        redirectAttributes.addFlashAttribute("successMessage", "Profil a platební údaje byly úspěšně uloženy.");
        return "redirect:/settings?success";
    }

    @PostMapping("/consents")
    public String updateConsents(@RequestParam(value = "acceptTerms", defaultValue = "false") boolean acceptTerms,
                                 @RequestParam(value = "acceptPrivacy", defaultValue = "false") boolean acceptPrivacy,
                                 @RequestParam(value = "marketingConsent", defaultValue = "false") boolean marketingConsent,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));

        if (!acceptTerms || !acceptPrivacy) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Obchodní podmínky a informace o osobních údajích jsou nutné pro používání služby. Když je chceš odvolat, smaž účet. Marketingový souhlas odškrtni a ulož.");
            return "redirect:/settings";
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Prague"));
        currentUser.setTermsAccepted(true);
        if (currentUser.getTermsAcceptedAt() == null) {
            currentUser.setTermsAcceptedAt(now);
        }
        currentUser.setPrivacyAccepted(true);
        if (currentUser.getPrivacyAcceptedAt() == null) {
            currentUser.setPrivacyAcceptedAt(now);
        }
        if (marketingConsent && !currentUser.isMarketingConsent()) {
            currentUser.setMarketingConsent(true);
            currentUser.setMarketingConsentAt(now);
        } else if (!marketingConsent && currentUser.isMarketingConsent()) {
            currentUser.setMarketingConsent(false);
            currentUser.setMarketingConsentAt(null);
        }
        userRepository.save(currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Nastavení souhlasů je uložené.");
        return "redirect:/settings";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportPersonalData(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));
        byte[] body = dataRightsService.exportPersonalData(currentUser);
        return jsonAttachment(body, "minifaktura-osobni-udaje.json");
    }

    @PostMapping("/delete")
    public String deleteAccount(@RequestParam String password,
                                Principal principal,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) throws ServletException {
        if (principal == null) {
            return "redirect:/login";
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));
        if (password == null || !passwordEncoder.matches(password, currentUser.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Heslo nesouhlasí. Účet nebyl smazán.");
            return "redirect:/settings";
        }
        dataRightsService.deleteAccount(currentUser);
        request.logout();
        return "redirect:/login?deleted";
    }

    private static ResponseEntity<byte[]> jsonAttachment(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
