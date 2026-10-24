package com.campuspulse.config;

import com.campuspulse.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

/**
 * Spring Security configuration for CampusPulse.
 * Configures dual role-based access control (ADMIN and STUDENT), BCrypt password hashing,
 * session management, and public route exemptions for the landing page and registration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${campuspulse.admin.username:admin}")
    private String adminUsername = "admin";

    @Value("${campuspulse.admin.password:}")
    private String adminPassword;

    @Value("${campuspulse.admin.password-hash:}")
    private String adminPasswordHash;

    @Autowired(required = false)
    private StudentRepository studentRepository;

    @Autowired(required = false)
    private RoleBasedAuthenticationSuccessHandler successHandler;

    @Autowired(required = false)
    private CustomAuthenticationFailureHandler failureHandler;

    /**
     * Password encoder using BCrypt strong hashing algorithm.
     *
     * @return BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static final java.util.regex.Pattern BCRYPT_PATTERN =
            java.util.regex.Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private static String stripSurroundingQuotes(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    /**
     * UserDetailsService bean configured with administrative credentials from environment
     * variables as well as registered student accounts from the database.
     *
     * Constructs fresh UserDetails instances dynamically on each lookup to ensure
     * Spring Security's credentials erasure does not mutate or clear the in-memory admin password.
     *
     * @param passwordEncoder the BCrypt encoder bean
     * @return UserDetailsService handling both admin and student authentication
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        String normalizedAdminUsername = stripSurroundingQuotes(adminUsername);
        if (normalizedAdminUsername == null || normalizedAdminUsername.isBlank()) {
            normalizedAdminUsername = "admin";
        } else {
            normalizedAdminUsername = normalizedAdminUsername.trim();
        }

        String rawHash = stripSurroundingQuotes(adminPasswordHash);
        String rawPassword = stripSurroundingQuotes(adminPassword);

        final String finalEncodedPassword;
        if (rawHash != null && !rawHash.isBlank()) {
            String trimmedHash = rawHash.trim();
            if (!BCRYPT_PATTERN.matcher(trimmedHash).matches()) {
                throw new IllegalStateException("Configured campuspulse.admin.password-hash is not a valid BCrypt hash format.");
            }
            finalEncodedPassword = trimmedHash;
            log.info("Configured environment admin using pre-computed BCrypt hash.");
        } else if (rawPassword != null && !rawPassword.isBlank()) {
            String trimmedPassword = rawPassword.trim();
            if (BCRYPT_PATTERN.matcher(trimmedPassword).matches()) {
                finalEncodedPassword = trimmedPassword;
                log.info("Detected pre-computed BCrypt hash in admin password property; using as-is without double-encoding.");
            } else {
                finalEncodedPassword = passwordEncoder.encode(trimmedPassword);
                log.info("Configured environment admin password hashed with BCrypt.");
            }
        } else {
            // Cryptographically secure one-time password generated when environment variable is not supplied
            String autoGenerated = UUID.randomUUID().toString();
            finalEncodedPassword = passwordEncoder.encode(autoGenerated);
            log.warn("=================================================================");
            log.warn("Notice: No ADMIN_PASSWORD or ADMIN_PASSWORD_HASH set in environment.");
            log.warn("A temporary random password has been generated for admin username: {}", normalizedAdminUsername);
            log.warn("Credentials are never logged in plaintext. To configure administrative credentials, export ADMIN_PASSWORD in your environment.");
            log.warn("=================================================================");
        }

        final String effectiveAdminUsername = normalizedAdminUsername;
        return username -> {
            if (username != null && username.trim().equalsIgnoreCase(effectiveAdminUsername)) {
                return User.builder()
                        .username(effectiveAdminUsername)
                        .password(finalEncodedPassword)
                        .roles("ADMIN")
                        .build();
            }
            if (studentRepository != null && username != null) {
                return studentRepository.findByEmailIgnoreCase(username.trim())
                        .map(student -> {
                            String role = student.getRole();
                            String roleName = "STUDENT";
                            if (role != null) {
                                String trimmed = role.trim();
                                if (trimmed.startsWith("ROLE_")) {
                                    roleName = trimmed.substring(5);
                                } else if (!trimmed.isEmpty()) {
                                    roleName = trimmed;
                                }
                            }
                            return User.builder()
                                    .username(student.getEmail())
                                    .password(student.getPassword())
                                    .roles(roleName)
                                    .build();
                        })
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            }
            throw new UsernameNotFoundException("User not found: " + username);
        };
    }

    /**
     * Security filter chain defining authorization rules, custom login page,
     * logout handling, and CSRF protection.
     *
     * @param http HttpSecurity configuration builder
     * @return built SecurityFilterChain
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Protect administrative endpoints with ADMIN role only
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Protect moderator operational endpoints with MODERATOR and ADMIN roles
                        .requestMatchers("/moderator/**").hasAnyRole("MODERATOR", "ADMIN")
                        // Protect authenticated student profile endpoints with STUDENT role only
                        .requestMatchers("/student/profile", "/student/profile/**").hasRole("STUDENT")
                        // Protect complaint resolution feedback submission with STUDENT role only
                        .requestMatchers("/complaints/*/feedback", "/complaints/feedback").hasRole("STUDENT")
                        // Restrict grievance registry listing to MODERATOR and ADMIN roles only
                        .requestMatchers(HttpMethod.GET, "/complaints").hasAnyRole("MODERATOR", "ADMIN")
                        // Allow public complaint submissions via POST
                        .requestMatchers(HttpMethod.POST, "/complaints").permitAll()
                        // Allow public access to landing page, static assets, and student workflows
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/complaints/new",
                                "/complaints/suggest-category",
                                "/complaints/track",
                                "/complaints/success/**",
                                "/login",
                                "/student/login",
                                "/student/register",
                                "/forgot-password",
                                "/reset-password"
                        ).permitAll()
                        // Any remaining request requires authentication
                        .anyRequest().authenticated()
                )
                .formLogin(form -> {
                    form.loginPage("/login")
                            .loginProcessingUrl("/login")
                            .permitAll();
                    form.successHandler(successHandler != null ? successHandler : new RoleBasedAuthenticationSuccessHandler());
                    form.failureHandler(failureHandler != null ? failureHandler : new CustomAuthenticationFailureHandler());
                })
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .requestCache(requestCache -> requestCache
                        .requestCache(new org.springframework.security.web.savedrequest.HttpSessionRequestCache())
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                );

        return http.build();
    }

    /**
     * Exposes a SessionRegistry bean for session tracking and explicit invalidation.
     */
    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    /**
     * Publishes HttpSession lifecycle events to Spring application context, ensuring
     * SessionRegistry stays in sync when sessions expire or are destroyed.
     */
    @Bean
    public org.springframework.boot.web.servlet.ServletListenerRegistrationBean<org.springframework.security.web.session.HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new org.springframework.boot.web.servlet.ServletListenerRegistrationBean<>(new org.springframework.security.web.session.HttpSessionEventPublisher());
    }
}
