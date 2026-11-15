package cz.controller;

import cz.entity.Company;
import cz.entity.Expense;
import cz.entity.User;
import cz.repository.ExpenseRepository;
import cz.repository.UserRepository;
import cz.service.CompanyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class ExpenseController {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final CompanyService companyService;

    public ExpenseController(UserRepository userRepository,
                             ExpenseRepository expenseRepository,
                             CompanyService companyService) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.companyService = companyService;
    }

    @GetMapping("/expenses")
    public String expenses(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        Company company = companyService.current(user, request);
        model.addAttribute("expenses", expenseRepository.findByCompanyOrderByIssueDateDesc(company));
        model.addAttribute("activePage", "expenses");
        return "expenses";
    }

    @PostMapping("/expenses")
    public String save(@RequestParam(required = false) LocalDate issueDate,
                       @RequestParam String supplierName,
                       @RequestParam(required = false) String supplierIco,
                       @RequestParam(required = false) String documentNumber,
                       @RequestParam(defaultValue = "0") Double netAmount,
                       @RequestParam(defaultValue = "0") Double vatAmount,
                       @RequestParam(required = false) Double grossTotal,
                       @RequestParam(required = false) String note,
                       @AuthenticationPrincipal UserDetails userDetails,
                       HttpServletRequest request) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        Expense expense = new Expense();
        expense.setUser(user);
        expense.setCompany(companyService.current(user, request));
        expense.setIssueDate(issueDate == null ? LocalDate.now() : issueDate);
        expense.setSupplierName(supplierName);
        expense.setSupplierIco(supplierIco);
        expense.setDocumentNumber(documentNumber);
        expense.setNetAmount(netAmount);
        expense.setVatAmount(vatAmount);
        double gross = grossTotal == null ? netAmount + vatAmount : grossTotal;
        expense.setGrossTotal(gross);
        expense.setNote(note);
        expenseRepository.save(expense);
        return "redirect:/expenses";
    }

    private User user(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }
}
