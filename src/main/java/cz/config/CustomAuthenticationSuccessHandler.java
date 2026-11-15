package cz.config;

import cz.entity.AccessEvent;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && user.isMfaEnabled()) {
            HttpSession session = request.getSession();
            session.setAttribute("MFA_PRE_AUTH_EMAIL", email);
            SecurityContextHolder.getContext().setAuthentication(null);
            auditService.record(request, user, email, AccessEvent.Type.LOGIN_SUCCESS, "Heslo přijato, čeká se na kód 2FA");
            response.sendRedirect("/login/mfa");
            return;
        }

        auditService.record(request, user, email, AccessEvent.Type.LOGIN_SUCCESS, null);
        response.sendRedirect("/dashboard");
    }
}
