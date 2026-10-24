package com.campuspulse.config;

import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit regression tests verifying:
 * 1. Spring Security credential erasure (CredentialsContainer.eraseCredentials()) does not mutate
 *    in-memory credentials across repeated logins.
 * 2. BCrypt hashes provided via configuration are validated at startup and not double-hashed.
 * 3. Enclosing quotes and whitespace in configuration properties are sanitized properly.
 * 4. Database-backed admin, moderator, and student lookup is preserved.
 */
class SecurityConfigAdminCredentialsTest {

    private PasswordEncoder passwordEncoder;
    private StudentRepository studentRepository;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        studentRepository = mock(StudentRepository.class);
    }

    @Test
    @DisplayName("EraseCredentials does not mutate credentials for subsequent admin lookups")
    void eraseCredentialsDoesNotMutateAdminPasswordAcrossLogins() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPassword", "SuperSecret123!");
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);

        for (int attempt = 1; attempt <= 10; attempt++) {
            UserDetails userDetails = userDetailsService.loadUserByUsername("admin");
            assertThat(userDetails.getUsername()).isEqualTo("admin");
            assertThat(userDetails.getPassword()).isNotNull();
            assertThat(passwordEncoder.matches("SuperSecret123!", userDetails.getPassword()))
                    .as("Password must match on attempt %d", attempt)
                    .isTrue();

            // Simulate ProviderManager.authenticate() credentials erasure
            if (userDetails instanceof CredentialsContainer container) {
                container.eraseCredentials();
            }
            assertThat(userDetails.getPassword())
                    .as("Instance credentials must be erased on current reference")
                    .isNull();
        }
    }

    @Test
    @DisplayName("Invalid BCrypt hash in campuspulse.admin.password-hash fails fast at startup")
    void invalidPasswordHashThrowsIllegalStateException() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPasswordHash", "not-a-valid-bcrypt-hash");
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        assertThatThrownBy(() -> config.userDetailsService(passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid BCrypt hash format");
    }

    @Test
    @DisplayName("Valid BCrypt hash in campuspulse.admin.password-hash is used as-is without double-encoding")
    void validPasswordHashUsedAsIs() {
        String precomputedHash = passwordEncoder.encode("SecurePassFromHash!");
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPasswordHash", precomputedHash);
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);
        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getPassword()).isEqualTo(precomputedHash);
        assertThat(passwordEncoder.matches("SecurePassFromHash!", userDetails.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Pre-computed BCrypt hash supplied in password property is detected and not double-encoded")
    void precomputedHashInPasswordPropertyDetected() {
        String precomputedHash = passwordEncoder.encode("AlreadyHashedPass!");
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPassword", precomputedHash);
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);
        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getPassword()).isEqualTo(precomputedHash);
        assertThat(passwordEncoder.matches("AlreadyHashedPass!", userDetails.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Surrounding quotes from .env files are stripped from admin username and password")
    void surroundingQuotesAreStripped() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "\"custom_admin\"");
        ReflectionTestUtils.setField(config, "adminPassword", "'QuotedPassword123!'");
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);
        UserDetails userDetails = userDetailsService.loadUserByUsername("custom_admin");

        assertThat(userDetails.getUsername()).isEqualTo("custom_admin");
        assertThat(passwordEncoder.matches("QuotedPassword123!", userDetails.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Admin lookup is case-insensitive and trims input whitespace")
    void adminLookupHandlesCaseAndWhitespace() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPassword", "AdminPass123!");
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);

        UserDetails lowerCase = userDetailsService.loadUserByUsername("admin");
        UserDetails upperCase = userDetailsService.loadUserByUsername("ADMIN");
        UserDetails withWhitespace = userDetailsService.loadUserByUsername("  admin  ");

        assertThat(lowerCase.getUsername()).isEqualTo("admin");
        assertThat(upperCase.getUsername()).isEqualTo("admin");
        assertThat(withWhitespace.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Database-backed accounts load properly with roles preserved")
    void databaseBackedAccountsLoadProperly() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "envadmin");
        ReflectionTestUtils.setField(config, "adminPassword", "EnvPass123!");
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        Student dbAdmin = new Student("DB Admin", "dbadmin@campus.edu", null, passwordEncoder.encode("DbAdminPass!"));
        dbAdmin.setRole("ROLE_ADMIN");
        when(studentRepository.findByEmailIgnoreCase("dbadmin@campus.edu")).thenReturn(Optional.of(dbAdmin));

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);
        UserDetails userDetails = userDetailsService.loadUserByUsername("dbadmin@campus.edu");

        assertThat(userDetails.getUsername()).isEqualTo("dbadmin@campus.edu");
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(passwordEncoder.matches("DbAdminPass!", userDetails.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Unknown user throws UsernameNotFoundException")
    void unknownUserThrowsUsernameNotFoundException() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "envadmin");
        ReflectionTestUtils.setField(config, "adminPassword", "EnvPass123!");
        ReflectionTestUtils.setField(config, "studentRepository", studentRepository);

        when(studentRepository.findByEmailIgnoreCase("unknown@campus.edu")).thenReturn(Optional.empty());

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@campus.edu"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
