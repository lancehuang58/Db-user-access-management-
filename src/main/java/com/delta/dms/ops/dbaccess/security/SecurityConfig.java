package com.delta.dms.ops.dbaccess.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Comprehensive Spring Security configuration for web application security
 *
 * Features:
 * - Form-based authentication with custom login page
 * - Role-based access control (ADMIN, USER, VIEWER)
 * - Remember-me functionality
 * - Session management and fixation protection
 * - CSRF protection
 * - Security headers (XSS, clickjacking protection)
 * - Logout functionality
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Password encoder using BCrypt for secure password hashing
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 for better security
    }

    /**
     * Authentication manager for handling authentication requests
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Remember-me service configuration
     */
    @Bean
    public TokenBasedRememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices rememberMeServices =
            new TokenBasedRememberMeServices("db-access-management-remember-me-key", userDetailsService);
        rememberMeServices.setTokenValiditySeconds(86400 * 7); // 7 days
        rememberMeServices.setParameter("remember-me");
        return rememberMeServices;
    }

    /**
     * Main security filter chain configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Authorization rules
            .authorizeHttpRequests(authz -> authz
                // Public resources - no authentication required
                .requestMatchers(
                    "/login",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/favicon.ico",
                    "/error"
                ).permitAll()

                // H2 Console (development only)
                .requestMatchers("/h2-console/**").permitAll()

                // API documentation - available to authenticated users
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").authenticated()

                // Admin-only endpoints
                .requestMatchers("/users/new", "/users/*/delete").hasRole("ADMIN")
                .requestMatchers("/permissions/*/approve", "/permissions/*/revoke").hasRole("ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // Form login configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/users", true)
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )

            // Logout configuration
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=true")
                .deleteCookies("JSESSIONID", "remember-me")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )

            // Remember-me configuration
            .rememberMe(remember -> remember
                .rememberMeServices(rememberMeServices())
                .key("db-access-management-remember-me-key")
                .tokenValiditySeconds(86400 * 7) // 7 days
            )

            // Session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(2) // Allow max 2 concurrent sessions per user
                .maxSessionsPreventsLogin(false) // Invalidate oldest session
                .expiredUrl("/login?expired=true")
            )

            // CSRF protection
            .csrf(csrf -> csrf
                // Disable CSRF for H2 console (development only)
                .ignoringRequestMatchers("/h2-console/**")
                // Enable CSRF for all other endpoints
            )

            // Security headers
            .headers(headers -> headers
                // Allow H2 console frames (development only)
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                // XSS protection
                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                // Content type options
                .contentTypeOptions(contentType -> {})
                // Strict transport security (HTTPS)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
            )

            // Exception handling
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error/403")
            );

        return http.build();
    }
}
