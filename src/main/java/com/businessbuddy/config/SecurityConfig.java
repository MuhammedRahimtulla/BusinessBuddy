package com.businessbuddy.config;

import com.businessbuddy.security.LoginHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Spring Security configuration (4.8.1 and 4.8.2).
 *
 * <p>URL rules follow the role matrix in Table 4.37. They are the first of three layers:
 * sensitive service methods also carry {@code @PreAuthorize}, and every record is checked
 * against the user's business by {@code BusinessContext}.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] BUSINESS_ROLES = {"OWNER", "MANAGER", "EMPLOYEE"};
    private static final String[] MANAGEMENT = {"OWNER", "MANAGER"};

    /** Content-Security-Policy: everything is served by the application itself (4.8.5). */
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; form-action 'self'; base-uri 'self'";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginHandlers handlers,
                                           SessionRegistry sessionRegistry) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**", "/webjars/**",
                        "/favicon.ico", "/error", "/error/**", "/actuator/health").permitAll()
                // Administration (Administrator only) and the audit log
                .requestMatchers("/admin/**").hasRole("ADMINISTRATOR")
                .requestMatchers("/audit/**").hasAnyRole("OWNER", "ADMINISTRATOR")
                // Settings
                .requestMatchers("/settings/business/**", "/settings/users/**").hasRole("OWNER")
                .requestMatchers("/settings/**").hasAnyRole(MANAGEMENT)
                // Owner and Manager modules
                .requestMatchers("/suppliers/**", "/purchases/**", "/recipes/**", "/pricing/**",
                        "/expenses/**", "/reports/**", "/forecasts/**", "/insights/**").hasAnyRole(MANAGEMENT)
                // Ingredients and products: everyone in the business may view, management may change
                .requestMatchers(HttpMethod.GET, "/ingredients/**", "/products/**").hasAnyRole(BUSINESS_ROLES)
                .requestMatchers("/ingredients/**", "/products/**").hasAnyRole(MANAGEMENT)
                // Inventory: waste for everyone, adjustments and returns for management
                .requestMatchers("/inventory/adjust/**").hasAnyRole(MANAGEMENT)
                .requestMatchers("/inventory/**").hasAnyRole(BUSINESS_ROLES)
                // Production and sales: voids and cancellations for management only
                .requestMatchers("/sales/*/void", "/production/*/cancel").hasAnyRole(MANAGEMENT)
                .requestMatchers("/production/**", "/sales/**", "/customers/**").hasAnyRole(BUSINESS_ROLES)
                .requestMatchers("/dashboard").hasAnyRole(BUSINESS_ROLES)
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(handlers)
                .failureHandler(handlers)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(ex -> ex.accessDeniedHandler(handlers))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredUrl("/login?expired"))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        // CSRF protection is on by default for every state-changing request.
        return http.build();
    }

    /** BCrypt with cost factor 12 (Table 4.50). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
