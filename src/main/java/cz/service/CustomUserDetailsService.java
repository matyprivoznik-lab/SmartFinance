package cz.service;

import cz.entity.User;
import cz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String raw = email == null ? "" : email.trim();
        User user = userRepository.findByEmail(raw)
                .or(() -> userRepository.findByEmail(raw.toLowerCase()))
                .orElseThrow(() -> new UsernameNotFoundException("Uživatel s tímto emailem nebyl nalezen."));

        String role = user.getRole() == null || user.getRole().isBlank() ? "ROLE_USER" : user.getRole();
        boolean canLogin = user.isEnabled() && user.isEmailVerified();
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(role)
                .disabled(!canLogin)
                .build();
    }
}