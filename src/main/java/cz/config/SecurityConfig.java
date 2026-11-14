package cz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final AuditAuthenticationFailureHandler auditAuthenticationFailureHandler;
    private final AuditLogoutSuccessHandler auditLogoutSuccessHandler;

    public SecurityConfig(CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          AuditAuthenticationFailureHandler auditAuthenticationFailureHandler,
                          AuditLogoutSuccessHandler auditLogoutSuccessHandler) {
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.auditAuthenticationFailureHandler = auditAuthenticationFailureHandler;
        this.auditLogoutSuccessHandler = auditLogoutSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/admin/**").hasRole("ADMIN");
                    auth.requestMatchers(
                            "/", "/index", "/index.html",
                            "/login", "/login/mfa",
                            "/register/**",
                            "/vop", "/privacy",
                            "/css/**", "/js/**", "/images/**", "/favicon.ico",
                            "/billing/webhook",
                            "/error"
                    ).permitAll();
                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(auditAuthenticationFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(auditLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> response.sendRedirect("/dashboard")))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/billing/webhook"))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.xssProtection(Customizer.withDefaults());
                    headers.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.frameOptions(frame -> frame.deny());
                });

        return http.build();
    }
}
