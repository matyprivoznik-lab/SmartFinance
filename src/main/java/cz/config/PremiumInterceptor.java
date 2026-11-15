package cz.config;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.PlanQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PremiumInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final PlanQuotaService planQuotaService;

    public PremiumInterceptor(UserRepository userRepository, PlanQuotaService planQuotaService) {
        this.userRepository = userRepository;
        this.planQuotaService = planQuotaService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!premiumOnly(request)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return true;
        }
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        if (user != null && planQuotaService.isPro(user)) {
            return true;
        }
        response.sendRedirect("/billing?locked=1");
        return false;
    }

    private static boolean premiumOnly(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/payments")
                || path.startsWith("/expenses")
                || path.startsWith("/taxes")
                || path.startsWith("/analytics")
                || path.startsWith("/invoices/recurring")) {
            return true;
        }
        if ("/export/ucetni.csv".equals(path) || "/export/pohoda.xml".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/companies".equals(path)) {
            return true;
        }
        return "POST".equalsIgnoreCase(request.getMethod())
                && path.startsWith("/clients/")
                && path.endsWith("/risk");
    }
}
