package cz.controller;

import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
public class AnalyticsController {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/analytics")
    public String analytics(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        List<Invoice> invoices = invoiceRepository.findByUser(currentUser);

        double grossTotalRevenue = invoices.stream()
                .mapToDouble(i -> i.getGrossTotal() != null ? i.getGrossTotal() : 0.0)
                .sum();

        double netAmountTotal = invoices.stream()
                .mapToDouble(i -> i.getNetAmount() != null ? i.getNetAmount() : 0.0)
                .sum();

        double vatAmountTotal = grossTotalRevenue - netAmountTotal;

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Double> monthlyRevenue = invoices.stream()
                .filter(inv -> inv.getIssueDate() != null)
                .collect(Collectors.groupingBy(
                        inv -> inv.getIssueDate().format(monthFormatter),
                        TreeMap::new,
                        Collectors.summingDouble(inv -> inv.getGrossTotal() != null ? inv.getGrossTotal() : 0.0)
                ));

        DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("yyyy");
        Map<String, Double> yearlyRevenue = invoices.stream()
                .filter(inv -> inv.getIssueDate() != null)
                .collect(Collectors.groupingBy(
                        inv -> inv.getIssueDate().format(yearFormatter),
                        TreeMap::new,
                        Collectors.summingDouble(inv -> inv.getGrossTotal() != null ? inv.getGrossTotal() : 0.0)
                ));

        model.addAttribute("grossTotalRevenue", grossTotalRevenue);
        model.addAttribute("netAmountTotal", netAmountTotal);
        model.addAttribute("vatAmountTotal", vatAmountTotal);
        model.addAttribute("monthlyRevenue", monthlyRevenue);
        model.addAttribute("yearlyRevenue", yearlyRevenue);
        model.addAttribute("activePage", "analytics");

        return "analytics";
    }
}
