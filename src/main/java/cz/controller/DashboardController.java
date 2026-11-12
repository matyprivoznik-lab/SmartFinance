package cz.controller;

import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.UserRepository;
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

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        List<Invoice> invoices = invoiceRepository.findByUser(currentUser);
        double grossTotalRevenue = invoices.stream()
                .mapToDouble(invoice -> invoice.getGrossTotal() != null ? invoice.getGrossTotal() : 0.0)
                .sum();

        double estimatedProfit = grossTotalRevenue * 0.6;
        int invoicesCount = invoices.size();
        long productsCount = productRepository.countByUser(currentUser);

        model.addAttribute("username", currentUser != null && currentUser.getName() != null ? currentUser.getName() : email);
        model.addAttribute("grossTotalRevenue", grossTotalRevenue);
        model.addAttribute("estimatedProfit", estimatedProfit);
        model.addAttribute("invoicesCount", invoicesCount);
        model.addAttribute("productsCount", productsCount);
        model.addAttribute("activePage", "dashboard");

        return "dashboard";
    }
}
