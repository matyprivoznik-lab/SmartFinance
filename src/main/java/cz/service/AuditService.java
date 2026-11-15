package cz.service;

import cz.entity.AccessEvent;
import cz.entity.User;
import cz.repository.AccessEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AuditService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private final AccessEventRepository accessEventRepository;

    public AuditService(AccessEventRepository accessEventRepository) {
        this.accessEventRepository = accessEventRepository;
    }

    public void record(HttpServletRequest request, User user, String email, AccessEvent.Type type, String detail) {
        AccessEvent event = new AccessEvent();
        event.setCreatedAt(LocalDateTime.now(ZONE));
        event.setType(type);
        event.setUser(user);
        event.setEmail(trim(email != null ? email : (user != null ? user.getEmail() : null), 180));
        event.setDetail(trim(detail, 300));
        if (request != null) {
            event.setIpAddress(trim(request.getRemoteAddr(), 64));
            event.setForwardedFor(trim(request.getHeader("X-Forwarded-For"), 250));
            event.setUserAgent(trim(request.getHeader("User-Agent"), 300));
        }
        accessEventRepository.save(event);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
