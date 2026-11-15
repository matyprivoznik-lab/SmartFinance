package cz.controller;

import cz.entity.Company;
import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.UserRepository;
import cz.service.CompanyService;
import cz.service.TurnoverService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private TurnoverService turnoverService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Company company = companyService.current(currentUser, request);
        List<Invoice> invoices = invoiceRepository.findByCompany(company);
        double grossTotalRevenue = invoices.stream()
                .mapToDouble(invoice -> invoice.getGrossTotal() != null ? invoice.getGrossTotal() : 0.0)
                .sum();
        double expenses = turnoverService.expenses(company);
        double estimatedProfit = grossTotalRevenue - expenses;
        double turnover = turnoverService.lastTwelveMonthsNet(company, java.time.LocalDate.now(java.time.ZoneId.of("Europe/Prague")));
        int invoicesCount = invoices.size();
        long productsCount = productRepository.countByCompany(company);

        model.addAttribute("username", currentUser != null && currentUser.getName() != null ? currentUser.getName() : email);
        model.addAttribute("grossTotalRevenue", grossTotalRevenue);
        model.addAttribute("estimatedProfit", estimatedProfit);
        model.addAttribute("vatTurnover", turnover);
        model.addAttribute("vatLimit", TurnoverService.VAT_LIMIT);
        model.addAttribute("vatShare", Math.min(100.0, turnover / TurnoverService.VAT_LIMIT * 100.0));
        model.addAttribute("invoicesCount", invoicesCount);
        model.addAttribute("productsCount", productsCount);
        model.addAttribute("activePage", "dashboard");

        return "dashboard";
    }
}
