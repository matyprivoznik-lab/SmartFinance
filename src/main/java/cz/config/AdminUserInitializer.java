package cz.config;

import cz.entity.User;
import cz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@Order(20)
public class AdminUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserInitializer(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:}") String adminEmail,
                                @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isEmpty()) {
            return;
        }
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        String password = adminPassword.isBlank() ? randomPassword() : adminPassword;
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setName("Správce MiniFaktury");
        admin.setRole("ROLE_ADMIN");
        admin.setEnabled(true);
        admin.setEmailVerified(true);
        userRepository.save(admin);

        if (adminPassword.isBlank()) {
            System.out.println(">>> Admin účet " + adminEmail + " vytvořen. Heslo (zobrazí se jen teď): " + password);
        } else {
            System.out.println(">>> Admin účet " + adminEmail + " vytvořen z APP_ADMIN_PASSWORD.");
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
