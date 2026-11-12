package cz.service;

import cz.entity.User;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class PlanQuotaService {

    public static final int FREE_MONTHLY_INVOICE_LIMIT = 5;

    private final InvoiceRepository invoiceRepository;
    private final BillingService billingService;

    public PlanQuotaService(InvoiceRepository invoiceRepository, BillingService billingService) {
        this.invoiceRepository = invoiceRepository;
        this.billingService = billingService;
    }

    public boolean isPro(User user) {
        return user != null && billingService.isActive(user);
    }

    public int monthlyLimit(User user) {
        return isPro(user) ? Integer.MAX_VALUE : FREE_MONTHLY_INVOICE_LIMIT;
    }

    public long invoicesThisMonth(User user) {
        if (user == null) {
            return 0;
        }
        YearMonth month = YearMonth.now();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return invoiceRepository.countByUserAndIssueDateBetween(user, from, to);
    }

    public boolean canCreateInvoice(User user) {
        if (isPro(user)) {
            return true;
        }
        return invoicesThisMonth(user) < FREE_MONTHLY_INVOICE_LIMIT;
    }

    public long remainingInvoices(User user) {
        if (isPro(user)) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, FREE_MONTHLY_INVOICE_LIMIT - invoicesThisMonth(user));
    }

    public String themeClass(User user) {
        return isPro(user) ? "theme-pro" : "theme-free";
    }
}
