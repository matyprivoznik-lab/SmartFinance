package cz.config;

import cz.entity.AccessEvent;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuditAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuditAuthenticationFailureHandler(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String email = request.getParameter("username");
        String normalized = email == null ? "" : email.trim().toLowerCase();
        User user = normalized.isEmpty() ? null : userRepository.findByEmail(normalized).orElse(null);
        auditService.record(request, user, normalized, AccessEvent.Type.LOGIN_FAILURE, exception.getClass().getSimpleName());

        if (user != null && !user.isEnabled()) {
            response.sendRedirect("/login?blocked");
            return;
        }
        if (user != null && !user.isEmailVerified()) {
            response.sendRedirect("/login?unverified");
            return;
        }
        response.sendRedirect("/login?error");
    }
}
