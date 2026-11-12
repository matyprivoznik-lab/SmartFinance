package cz.controller;

import cz.entity.User;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import cz.service.TaxService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;

@Controller
@RequestMapping("/taxes")
public class TaxController {

    private final TaxService taxService;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;

    public TaxController(TaxService taxService, UserRepository userRepository, InvoiceRepository invoiceRepository) {
        this.taxService = taxService;
        this.userRepository = userRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Zobrazí přehled daní, příjmů a odvodů pro OSVČ za zvolený rok.
     */
    @GetMapping
    public String showTaxOverview(@RequestParam(name = "year", required = false) Integer year,
                                  @RequestParam(name = "isStudent", defaultValue = "false") boolean isStudent,
                                  @RequestParam(name = "isEmployed", defaultValue = "false") boolean isEmployed,
                                  Model model,
                                  Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel s e-mailem " + principal.getName() + " nebyl nalezen."));

        int selectedYear = (year != null) ? year : LocalDate.now().getYear();

        TaxService.TaxSummary taxSummary = taxService.calculateTaxSummary(user, selectedYear, isStudent, isEmployed);

        long invoicesCount = invoiceRepository.findByUser(user).stream()
                .filter(inv -> inv.getIssueDate() != null && inv.getIssueDate().getYear() == selectedYear)
                .count();

        model.addAttribute("user", user);
        model.addAttribute("summary", taxSummary);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("isStudent", isStudent);
        model.addAttribute("isEmployed", isEmployed);
        model.addAttribute("grossTotalRevenue", taxSummary.getTaxableRevenue());
        model.addAttribute("estimatedProfit", taxSummary.getIncomeTaxBase());
        model.addAttribute("invoicesCount", invoicesCount);

        return "taxes";
    }

    /**
     * Zobrazí detailní daňový report přizpůsobený pro tisk.
     */
    @GetMapping("/report")
    public String showTaxReport(@RequestParam(name = "year", required = false) Integer year,
                                @RequestParam(name = "isStudent", defaultValue = "false") boolean isStudent,
                                @RequestParam(name = "isEmployed", defaultValue = "false") boolean isEmployed,
                                Model model,
                                Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));

        int selectedYear = (year != null) ? year : LocalDate.now().getYear();
        TaxService.TaxSummary taxSummary = taxService.calculateTaxSummary(user, selectedYear, isStudent, isEmployed);

        model.addAttribute("user", user);
        model.addAttribute("summary", taxSummary);
        model.addAttribute("selectedYear", selectedYear);

        return "tax-report";
    }

    @GetMapping({"/pdf", "/download"})
    public String getTaxPdf(@RequestParam(name = "year", required = false) Integer year,
                            @RequestParam(name = "isStudent", defaultValue = "false") boolean isStudent,
                            @RequestParam(name = "isEmployed", defaultValue = "false") boolean isEmployed,
                            Model model,
                            Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));

        int selectedYear = (year != null) ? year : LocalDate.now().getYear();
        TaxService.TaxSummary taxSummary = taxService.calculateTaxSummary(user, selectedYear, isStudent, isEmployed);

        model.addAttribute("user", user);
        model.addAttribute("currentUser", user);
        model.addAttribute("summary", taxSummary);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("year", selectedYear);
        model.addAttribute("isMainActivity", user.isMainActivity());
        model.addAttribute("isVatPayer", user.isVatPayer());
        model.addAttribute("vatNumber", user.getVatNumber());
        model.addAttribute("expenseRate", user.getExpenseRate() > 1 ? user.getExpenseRate() : user.getExpenseRate() * 100);
        model.addAttribute("taxableRevenue", taxSummary.getTaxableRevenue());
        model.addAttribute("flatExpenses", taxSummary.getFlatExpenses());
        model.addAttribute("incomeTaxBase", taxSummary.getIncomeTaxBase());
        model.addAttribute("incomeTaxDue", taxSummary.getIncomeTaxDue());
        model.addAttribute("socialInsurance", taxSummary.getSocialInsurance());
        model.addAttribute("healthInsurance", taxSummary.getHealthInsurance());

        return "tax-pdf";
    }

    @GetMapping("/export")
    public String exportTaxReport(@RequestParam(name = "year", required = false) Integer year,
                                  @RequestParam(name = "isStudent", defaultValue = "false") boolean isStudent,
                                  @RequestParam(name = "isEmployed", defaultValue = "false") boolean isEmployed,
                                  Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        int selectedYear = (year != null) ? year : LocalDate.now().getYear();
        return "redirect:/taxes/download?year=" + selectedYear
                + "&isStudent=" + isStudent
                + "&isEmployed=" + isEmployed;
    }
}
