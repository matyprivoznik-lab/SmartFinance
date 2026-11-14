package cz.controller;

import cz.entity.Company;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.CompanyService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class PaymentController {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final PaymentMatchService paymentMatchService;

    public PaymentController(UserRepository userRepository,
                             CompanyService companyService,
                             PaymentMatchService paymentMatchService) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.paymentMatchService = paymentMatchService;
    }

    @GetMapping("/payments")
    public String payments(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        Company company = companyService.current(user, request);
        model.addAttribute("company", company);
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
            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
            PaymentMatchService.Result result = paymentMatchService.importCsv(user, companyService.current(user, request), csv);
            redirectAttributes.addFlashAttribute("successMessage", result.message());
            if (!result.notes().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", String.join(" ", result.notes()));
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Výpis se nepodařilo přečíst.");
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

    private User user(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }
}
