package cz.config;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.security.h2-console-enabled:false}")
    private boolean h2ConsoleEnabled;

    public SecurityConfig(CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler) {
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/", "/index", "/index.html",
                            "/login", "/login/mfa",
                            "/register/**",
                            "/css/**", "/js/**", "/images/**", "/favicon.ico",
                            "/billing/webhook",
                            "/error"
                    ).permitAll();
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").authenticated();
                    }
                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(customAuthenticationSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .csrf(csrf -> {
                    csrf.ignoringRequestMatchers("/billing/webhook");
                    if (h2ConsoleEnabled) {
                        csrf.ignoringRequestMatchers("/h2-console/**");
                    }
                })
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.xssProtection(Customizer.withDefaults());
                    headers.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.frameOptions(frame -> {
                        if (h2ConsoleEnabled) {
                            frame.sameOrigin();
                        } else {
                            frame.deny();
                        }
                    });
                });

        return http.build();
    }
}
