package cz.controller;

import cz.entity.AccessEvent;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.document.DocumentNumbers;
import cz.service.AresService;
import cz.service.AuditService;
import cz.service.CompanyService;
import cz.service.DataRightsService;
import cz.service.PlanQuotaService;
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
    private final AresService aresService;
    private final AuditService auditService;
    private final CompanyService companyService;
    private final PlanQuotaService planQuotaService;

    public SettingsController(UserRepository userRepository,
                              DataRightsService dataRightsService,
                              PasswordEncoder passwordEncoder,
                              AresService aresService,
                              AuditService auditService,
                              CompanyService companyService,
                              PlanQuotaService planQuotaService) {
        this.userRepository = userRepository;
        this.dataRightsService = dataRightsService;
        this.passwordEncoder = passwordEncoder;
        this.aresService = aresService;
        this.auditService = auditService;
        this.companyService = companyService;
        this.planQuotaService = planQuotaService;
    }

    @GetMapping
    public String showSettings(Model model, Principal principal, HttpServletRequest request) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel s e-mailem " + principal.getName() + " nebyl nalezen."));

        model.addAttribute("currentUser", user);
        model.addAttribute("user", user);
        model.addAttribute("companyList", companyService.list(user));
        model.addAttribute("activeCompany", companyService.current(user, request));
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
                                 @RequestParam(required = false) String invoiceNumberPattern,
                                 @RequestParam(value = "remindersEnabled", required = false) Boolean remindersEnabled,
                                 @RequestParam(required = false) Integer gentleReminderDays,
                                 @RequestParam(required = false) Integer formalReminderDays,
                                 Principal principal,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));

        if (differs(name, currentUser.getName())
                || differs(ico, currentUser.getIco())
                || differs(vatNumber, currentUser.getVatNumber())
                || differs(address, currentUser.getAddress())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Název, sídlo, IČO a DIČ jdou změnit jen novým načtením z ARES.");
        }
        currentUser.setMainActivity(mainActivity == null || mainActivity);
        if (expenseRate != null) {
            currentUser.setExpenseRate(expenseRate);
        }
        currentUser.setVatPayer(Boolean.TRUE.equals(vatPayer));
        if (invoiceNumberPattern == null || invoiceNumberPattern.isBlank()) {
            currentUser.setInvoiceNumberPattern(null);
        } else if (DocumentNumbers.isValid(invoiceNumberPattern.trim())) {
            currentUser.setInvoiceNumberPattern(invoiceNumberPattern.trim());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Formát čísla dokladu použij jako RRRR-CCC, FVRRRRCCCC nebo DRUHRRRRMM-CCC.");
        }

        userRepository.save(currentUser);
        companyService.mirrorFromUser(currentUser, request);
        if (planQuotaService.isPro(currentUser)) {
            companyService.updateReminders(currentUser, request, Boolean.TRUE.equals(remindersEnabled), gentleReminderDays, formalReminderDays);
        } else if (Boolean.TRUE.equals(remindersEnabled)) {
            redirectAttributes.addFlashAttribute("errorMessage", PlanQuotaService.PREMIUM_LOCKED);
        }

        redirectAttributes.addFlashAttribute("successMessage", "Daňové volby byly uloženy. Údaje z ARES zůstávají beze změny.");
        return "redirect:/settings?success";
    }

    @PostMapping("/contact")
    public String updateContact(@RequestParam(required = false) String bankAccount,
                                @RequestParam(required = false) String iban,
                                @RequestParam(required = false) String swift,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String note,
                                Principal principal,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));
        if (bankAccount == null || bankAccount.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Číslo účtu nech ve tvaru 123456789/0100, ať jde vytvořit QR platba.");
            return "redirect:/settings";
        }
        currentUser.setBankAccount(bankAccount.trim());
        currentUser.setIban(blank(iban));
        currentUser.setSwift(blank(swift));
        currentUser.setPhone(blank(phone));
        currentUser.setNote(blank(note));
        userRepository.save(currentUser);
        companyService.mirrorFromUser(currentUser, request);
        redirectAttributes.addFlashAttribute("successMessage", "Účet, telefon a poznámka na faktuře jsou uložené.");
        return "redirect:/settings";
    }

    @PostMapping("/ares")
    public String refreshFromAres(Principal principal,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));
        String ico = AresService.normalizeIco(currentUser.getIco());
        if (ico == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Účet nemá platné IČO, z ARES není co načíst.");
            return "redirect:/settings";
        }

        AresService.AresResponse data = aresService.getCompanyDetails(ico);
        if (data == null || data.getName() == null || data.getName().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "ARES subjekt teď nenašel. Název a sídlo zůstávají beze změny.");
            return "redirect:/settings";
        }

        AresService.applyTo(currentUser, data);
        userRepository.save(currentUser);
        companyService.mirrorFromUser(currentUser, request);
        auditService.record(request, currentUser, currentUser.getEmail(), AccessEvent.Type.ARES_REFRESH,
                "IČO " + data.getIco());
        redirectAttributes.addFlashAttribute("successMessage",
                "Údaje z ARES jsou aktualizované. Už vystavené faktury si nechávají původní název, sídlo, IČO a DIČ.");
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
        return jsonAttachment(body, "mujdokladomat-osobni-udaje.json");
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

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean differs(String submitted, String stored) {
        String left = submitted == null ? "" : submitted.trim();
        String right = stored == null ? "" : stored.trim();
        return !left.equals(right);
    }

    private static ResponseEntity<byte[]> jsonAttachment(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
