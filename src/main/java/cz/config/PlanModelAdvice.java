package cz.config;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.PlanQuotaService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PlanModelAdvice {

    private final UserRepository userRepository;
    private final PlanQuotaService planQuotaService;

    public PlanModelAdvice(UserRepository userRepository, PlanQuotaService planQuotaService) {
        this.userRepository = userRepository;
        this.planQuotaService = planQuotaService;
    }

    @ModelAttribute("planTheme")
    public String planTheme() {
        User user = currentUser();
        return user == null ? "theme-free" : planQuotaService.themeClass(user);
    }

    @ModelAttribute("isPro")
    public boolean isPro() {
        User user = currentUser();
        return user != null && planQuotaService.isPro(user);
    }

    @ModelAttribute("invoiceQuotaUsed")
    public long invoiceQuotaUsed() {
        User user = currentUser();
        return user == null ? 0 : planQuotaService.invoicesThisMonth(user);
    }

    @ModelAttribute("invoiceQuotaLimit")
    public int invoiceQuotaLimit() {
        return PlanQuotaService.FREE_MONTHLY_INVOICE_LIMIT;
    }

    @ModelAttribute("invoiceQuotaRemaining")
    public long invoiceQuotaRemaining() {
        User user = currentUser();
        return user == null ? PlanQuotaService.FREE_MONTHLY_INVOICE_LIMIT : planQuotaService.remainingInvoices(user);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
