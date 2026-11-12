package cz.config;

import cz.entity.User;
import cz.repository.UserRepository;
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

    public CustomAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
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

            response.sendRedirect("/login/mfa");
        } else {
            response.sendRedirect("/dashboard");
        }
    }
}