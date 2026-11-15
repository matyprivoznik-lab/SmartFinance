package cz.config;

import cz.entity.User;
import cz.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SetupInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public SetupInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/setup") || path.startsWith("/logout") || path.startsWith("/settings/mfa") || path.startsWith("/login")) {
            return true;
        }
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        if (user != null && user.needsSetup()) {
            response.sendRedirect("/setup");
            return false;
        }
        return true;
    }
}
