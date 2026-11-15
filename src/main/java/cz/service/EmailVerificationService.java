package cz.service;

import cz.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class EmailVerificationService {

    public static final int CODE_TTL_MINUTES = 15;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_WAIT_SECONDS = 60;

    public enum Result {
        OK, WRONG, EXPIRED, TOO_MANY, ALREADY
    }

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String issueCode(User user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now(AuditService.ZONE);
        user.setEmailVerified(false);
        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        user.setEmailVerificationSentAt(now);
        user.setEmailVerificationAttempts(0);
        return code;
    }

    public boolean canResend(User user) {
        LocalDateTime sentAt = user.getEmailVerificationSentAt();
        return sentAt == null || sentAt.isBefore(LocalDateTime.now(AuditService.ZONE).minusSeconds(RESEND_WAIT_SECONDS));
    }

    public Result confirm(User user, String code) {
        if (user.isEmailVerified() && user.getEmailVerificationCodeHash() == null) {
            return Result.ALREADY;
        }
        if (user.getEmailVerificationAttempts() >= MAX_ATTEMPTS) {
            return Result.TOO_MANY;
        }
        LocalDateTime expiresAt = user.getEmailVerificationExpiresAt();
        if (user.getEmailVerificationCodeHash() == null
                || expiresAt == null
                || expiresAt.isBefore(LocalDateTime.now(AuditService.ZONE))) {
            return Result.EXPIRED;
        }
        String submitted = code == null ? "" : code.trim();
        if (!passwordEncoder.matches(submitted, user.getEmailVerificationCodeHash())) {
            user.setEmailVerificationAttempts(user.getEmailVerificationAttempts() + 1);
            return Result.WRONG;
        }
        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationAttempts(0);
        return Result.OK;
    }
}
