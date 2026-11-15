package cz.config;

import cz.entity.AccessEvent;
import cz.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuditLogoutSuccessHandler implements LogoutSuccessHandler {

    private final AuditService auditService;

    public AuditLogoutSuccessHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException {
        if (authentication != null) {
            auditService.record(request, null, authentication.getName(), AccessEvent.Type.LOGOUT, null);
        }
        response.sendRedirect("/login?logout");
    }
}
