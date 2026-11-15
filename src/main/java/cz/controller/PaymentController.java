package cz.controller;

import cz.entity.Company;
import cz.entity.User;
import cz.repository.BankConnectionRepository;
import cz.repository.UserRepository;
import cz.service.BankCheckService;
import cz.service.BankStatementParser;
import cz.service.CompanyService;
import cz.service.OpenBankingService;
import cz.service.PaymentMatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class PaymentController {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final PaymentMatchService paymentMatchService;
    private final OpenBankingService openBankingService;
    private final BankConnectionRepository bankConnectionRepository;
    private final BankCheckService bankCheckService;

    public PaymentController(UserRepository userRepository,
                             CompanyService companyService,
                             PaymentMatchService paymentMatchService,
                             OpenBankingService openBankingService,
                             BankConnectionRepository bankConnectionRepository,
                             BankCheckService bankCheckService) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.paymentMatchService = paymentMatchService;
        this.openBankingService = openBankingService;
        this.bankConnectionRepository = bankConnectionRepository;
        this.bankCheckService = bankCheckService;
    }

    @GetMapping("/payments")
    public String payments(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        Company company = companyService.current(user, request);
        model.addAttribute("company", company);
        model.addAttribute("bankReady", openBankingService.configured());
        model.addAttribute("banks", openBankingService.institutions());
        model.addAttribute("connections", bankConnectionRepository.findByCompanyOrderByIdAsc(company));
        model.addAttribute("activePage", "payments");
        return "payments";
    }

    @PostMapping("/payments/import")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            @AuthenticationPrincipal UserDetails userDetails,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vyber CSV výpis. Sloupce jsou datum, částka a variabilní symbol.");
            return "redirect:/payments";
        }
        try {
            PaymentMatchService.Result result = paymentMatchService.apply(
                    user,
                    companyService.current(user, request),
                    BankStatementParser.parse(file.getBytes(), file.getOriginalFilename()));
            redirectAttributes.addFlashAttribute("successMessage", result.message());
            if (!result.notes().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", String.join(" ", result.notes()));
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Výpis se nepodařilo přečíst. Použij CSV, GPC nebo camt.053 XML.");
        }
        return "redirect:/payments";
    }

    @PostMapping("/payments/fio")
    public String importFio(@RequestParam LocalDate from,
                            @RequestParam LocalDate to,
                            @AuthenticationPrincipal UserDetails userDetails,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            PaymentMatchService.Result result = paymentMatchService.importFio(user, companyService.current(user, request), from, to);
            redirectAttributes.addFlashAttribute("successMessage", result.message());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/payments";
    }

    @PostMapping("/payments/fio-token")
    public String saveToken(@RequestParam(required = false) String fioApiToken,
                            @AuthenticationPrincipal UserDetails userDetails,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        companyService.updateFioToken(user, request, fioApiToken);
        redirectAttributes.addFlashAttribute("successMessage", fioApiToken == null || fioApiToken.isBlank() ? "Token Fio je smazaný." : "Token Fio je uložený u aktivní firmy.");
        return "redirect:/payments";
    }

    @PostMapping("/payments/bank")
    public String connectBank(@RequestParam String institutionId,
                              @RequestParam(required = false) String institutionName,
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            String link = openBankingService.begin(
                    companyService.current(user, request),
                    institutionId,
                    institutionName,
                    callback(request));
            return "redirect:" + link;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/payments";
        }
    }

    @GetMapping("/payments/bank/callback")
    public String bankCallback(@RequestParam(required = false) String ref,
                               @AuthenticationPrincipal UserDetails userDetails,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        if (ref == null || ref.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Banka se vrátila bez identifikace propojení.");
            return "redirect:/payments";
        }
        try {
            openBankingService.complete(companyService.current(user, request), ref);
            redirectAttributes.addFlashAttribute("successMessage", "Banka je propojená. Teď jde zkontrolovat, jestli platba přišla.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/payments";
    }

    @PostMapping("/payments/check")
    public String checkPayments(@RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        try {
            PaymentMatchService.Result result = bankCheckService.check(user, companyService.current(user, request), start, end);
            redirectAttributes.addFlashAttribute("successMessage", result.message());
            if (!result.notes().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", String.join(" ", result.notes()));
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/payments";
    }

    private static String callback(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String base = url.substring(0, url.length() - request.getRequestURI().length());
        return base + "/payments/bank/callback";
    }

    private User user(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }
}
