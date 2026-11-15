package cz.controller;

import cz.document.DocumentNumbers;
import cz.entity.Company;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AresService;
import cz.service.CompanyService;
import cz.service.SetupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SetupController {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final SetupService setupService;
    private final AresService aresService;

    public SetupController(UserRepository userRepository,
                           CompanyService companyService,
                           SetupService setupService,
                           AresService aresService) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.setupService = setupService;
        this.aresService = aresService;
    }

    @GetMapping("/setup")
    public String show(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        if (!user.needsSetup()) {
            return "redirect:/dashboard";
        }
        Company company = companyService.current(user, request);
        model.addAttribute("currentUser", user);
        model.addAttribute("company", company);
        model.addAttribute("defaultPattern", user.getInvoiceNumberPattern() == null || user.getInvoiceNumberPattern().isBlank()
                ? DocumentNumbers.DEFAULT_PATTERN
                : user.getInvoiceNumberPattern());
        model.addAttribute("expenseChoice", expenseChoice(user.getExpenseRate()));
        return "setup";
    }

    @PostMapping("/setup")
    public String save(@RequestParam(required = false) String bankAccount,
                       @RequestParam(required = false) String iban,
                       @RequestParam(required = false) String swift,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String note,
                       @RequestParam(required = false) Boolean mainActivity,
                       @RequestParam(required = false) Double expenseRate,
                       @RequestParam(value = "vatPayer", required = false) Boolean vatPayer,
                       @RequestParam(required = false) String invoiceNumberPattern,
                       @RequestParam(value = "remindersEnabled", required = false) Boolean remindersEnabled,
                       @RequestParam(required = false) Integer gentleReminderDays,
                       @RequestParam(required = false) Integer formalReminderDays,
                       @RequestParam(required = false) String fioApiToken,
                       @RequestParam(required = false) String extraIco,
                       @AuthenticationPrincipal UserDetails userDetails,
                       HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            setupService.finish(user, request, bankAccount, iban, swift, phone, note,
                    mainActivity == null || mainActivity,
                    expenseRate,
                    Boolean.TRUE.equals(vatPayer),
                    invoiceNumberPattern,
                    Boolean.TRUE.equals(remindersEnabled),
                    gentleReminderDays,
                    formalReminderDays,
                    fioApiToken,
                    extraIco);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/setup";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Účet je nachystaný. Další změny najdeš v nastavení a v platbách.");
        return "redirect:/dashboard";
    }

    @PostMapping("/setup/skip")
    public String skip(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        setupService.skip(user);
        redirectAttributes.addFlashAttribute("successMessage", "Úvodní nastavení je odložené. Vrátíš se k němu v nastavení a v platbách.");
        return "redirect:/dashboard";
    }

    @PostMapping("/setup/ares")
    public String refresh(@AuthenticationPrincipal UserDetails userDetails,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        String ico = AresService.normalizeIco(user.getIco());
        AresService.AresResponse data = ico == null ? null : aresService.getCompanyDetails(ico);
        if (data == null || data.getName() == null || data.getName().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "ARES subjekt teď nenašel. Název a sídlo zůstávají beze změny.");
            return "redirect:/setup";
        }
        AresService.applyTo(user, data);
        userRepository.save(user);
        companyService.mirrorFromUser(user, request);
        redirectAttributes.addFlashAttribute("successMessage", "Údaje z ARES jsou znovu načtené.");
        return "redirect:/setup";
    }

    private static String expenseChoice(double rate) {
        double percent = rate > 0 && rate <= 1 ? rate * 100 : rate;
        if (percent >= 70) {
            return "80";
        }
        if (percent > 0 && percent < 50) {
            return "40";
        }
        return "60";
    }

    private User user(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }
}
