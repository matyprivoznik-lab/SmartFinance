package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public MfaController(MfaService mfaService, UserRepository userRepository, UserDetailsService userDetailsService) {
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
    }

    // --- A. ZOBRAZENÍ A AKTIVACE V NASTAVENÍ ÚČTU ---

    @GetMapping("/settings/mfa")
    public String showMfaSettings(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) return "redirect:/login";

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        model.addAttribute("user", user);
        model.addAttribute("activePage", "settings");

        if (!user.isMfaEnabled()) {
            String secret = user.getMfaSecret();
            if (secret == null || secret.isEmpty()) {
                secret = mfaService.generateSecretKey();
                user.setMfaSecret(secret);
                userRepository.save(user);
            }
            String qrCodeBase64 = mfaService.generateQrCodeBase64(user.getEmail(), secret);
            model.addAttribute("qrCode", qrCodeBase64);
            model.addAttribute("secretKey", secret);
        }

        return "mfa-settings";
    }

    @PostMapping("/settings/mfa/enable")
    public String enableMfa(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam("code") String code) {
        if (userDetails == null) return "redirect:/login";

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        if (mfaService.verifyCode(user.getMfaSecret(), code)) {
            user.setMfaEnabled(true);
            userRepository.save(user);
            return "redirect:/settings/mfa?success=enabled";
        }

        return "redirect:/settings/mfa?error=invalidCode";
    }

    @PostMapping("/settings/mfa/disable")
    public String disableMfa(@AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam("code") String code) {
        if (userDetails == null) return "redirect:/login";

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        if (mfaService.verifyCode(user.getMfaSecret(), code)) {
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
            userRepository.save(user);
            return "redirect:/settings/mfa?success=disabled";
        }

        return "redirect:/settings/mfa?error=invalidCode";
    }

    // --- B. DVOUFÁZOVÉ PŘIHLÁŠENÍ (MFA CHALLENGE) ---

    @GetMapping("/login/mfa")
    public String showMfaChallenge(HttpSession session) {
        if (session.getAttribute("MFA_PRE_AUTH_EMAIL") == null) {
            return "redirect:/login";
        }
        return "mfa-challenge";
    }

    @PostMapping("/login/mfa")
    public String processMfaChallenge(@RequestParam("code") String code,
                                      HttpSession session,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        String email = (String) session.getAttribute("MFA_PRE_AUTH_EMAIL");
        if (email == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && mfaService.verifyCode(user.getMfaSecret(), code)) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(context, request, response);

            session.removeAttribute("MFA_PRE_AUTH_EMAIL");
            return "redirect:/dashboard";
        }

        return "redirect:/login/mfa?error=true";
    }
}