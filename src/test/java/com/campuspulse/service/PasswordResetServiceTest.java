package com.campuspulse.service;

import com.campuspulse.model.PasswordResetToken;
import com.campuspulse.model.Student;
import com.campuspulse.repository.PasswordResetTokenRepository;
import com.campuspulse.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying PasswordResetService:
 * - Cryptographic token generation and SHA-256 hashing
 * - Generic anti-enumeration response for existing and non-existent accounts
 * - Single-use token enforcement and atomic consumption
 * - Invalid, expired, and reused token rejection
 * - BCrypt password encoding and role preservation
 * - Session invalidation and active token cleanup
 * - Email and IP sliding-window rate limiting
 * - Safe email delivery failure handling
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private UserManagementService userManagementService;

    private PasswordEncoder passwordEncoder;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        passwordResetService = new PasswordResetService(
                passwordResetTokenRepository,
                studentRepository,
                emailNotificationService,
                passwordEncoder,
                userManagementService,
                "https://campuspulse.edu",
                15,
                3,
                10,
                "admin"
        );
    }

    // =========================================================================
    // 1. Token Generation, Hashing, and Generic Anti-Enumeration Response
    // =========================================================================

    @Test
    @DisplayName("requestPasswordReset creates hashed token, saves entity, and emails reset link for existing student")
    void requestPasswordResetGeneratesHashAndSendsEmail() {
        Student student = new Student("Rohit Yadav", "rohit@college.edu", "STU101", passwordEncoder.encode("OldPassword123!"));
        student.setId(42L);
        student.setRole("ROLE_STUDENT");

        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(studentRepository.findByEmailIgnoreCase("rohit@college.edu")).thenReturn(Optional.of(student));

        passwordResetService.requestPasswordReset("rohit@college.edu", "192.168.1.100");

        // Verify token saved with SHA-256 hash (64 hex characters), not raw token
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(1)).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertNotNull(savedToken);
        assertEquals("rohit@college.edu", savedToken.getEmail());
        assertEquals(42L, savedToken.getUserId());
        assertEquals(64, savedToken.getTokenHash().length());
        assertFalse(savedToken.isUsed());
        assertEquals("192.168.1.100", savedToken.getRequesterIp());

        // Verify email dispatched with reset URL containing raw token
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService, times(1)).sendPasswordResetEmail(
                eq("rohit@college.edu"),
                urlCaptor.capture(),
                eq(15)
        );

        String sentUrl = urlCaptor.getValue();
        assertTrue(sentUrl.startsWith("https://campuspulse.edu/reset-password?token="));

        // Verify hash stored in DB matches hash of token sent in URL
        String rawTokenInUrl = sentUrl.substring(sentUrl.indexOf("token=") + 6);
        assertEquals(savedToken.getTokenHash(), passwordResetService.hashToken(rawTokenInUrl));
        // Verify raw token was NOT saved in tokenHash directly
        assertNotEquals(rawTokenInUrl, savedToken.getTokenHash());
    }

    @Test
    @DisplayName("requestPasswordReset for nonexistent email completes silently without saving token or sending email")
    void requestPasswordResetForNonExistentEmailReturnsSilently() {
        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(studentRepository.findByEmailIgnoreCase("unknown@college.edu")).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("unknown@college.edu", "10.0.0.1");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendPasswordResetEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("requestPasswordReset for environment admin username does not create shadow database account")
    void requestPasswordResetForEnvironmentAdminDoesNotCreateShadowAccount() {
        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);

        passwordResetService.requestPasswordReset("admin", "10.0.0.1");

        verify(studentRepository, never()).findByEmailIgnoreCase(anyString());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendPasswordResetEmail(anyString(), anyString(), anyInt());
    }

    // =========================================================================
    // 2. Sliding-Window Rate Limiting
    // =========================================================================

    @Test
    @DisplayName("requestPasswordReset suppresses token generation and email dispatch when email rate limit is breached")
    void requestPasswordResetSuppressesWhenEmailRateLimitExceeded() {
        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(eq("victim@college.edu"), any(LocalDateTime.class)))
                .thenReturn(3L); // max is 3

        passwordResetService.requestPasswordReset("victim@college.edu", "192.168.1.50");

        verify(studentRepository, never()).findByEmailIgnoreCase(anyString());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendPasswordResetEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("requestPasswordReset suppresses token generation when requester IP rate limit is breached")
    void requestPasswordResetSuppressesWhenIpRateLimitExceeded() {
        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(eq("203.0.113.42"), any(LocalDateTime.class)))
                .thenReturn(10L); // max is 10

        passwordResetService.requestPasswordReset("user@college.edu", "203.0.113.42");

        verify(studentRepository, never()).findByEmailIgnoreCase(anyString());
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendPasswordResetEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("isRateLimited returns true when email or IP reaches threshold")
    void isRateLimitedIdentifiesThresholds() {
        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(eq("test@edu.com"), any(LocalDateTime.class)))
                .thenReturn(3L);
        assertTrue(passwordResetService.isRateLimited("test@edu.com", "1.2.3.4"));

        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(eq("safe@edu.com"), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(eq("flooding.ip"), any(LocalDateTime.class)))
                .thenReturn(10L);
        assertTrue(passwordResetService.isRateLimited("safe@edu.com", "flooding.ip"));

        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(eq("safe.ip"), any(LocalDateTime.class)))
                .thenReturn(2L);
        assertFalse(passwordResetService.isRateLimited("safe@edu.com", "safe.ip"));
    }

    // =========================================================================
    // 3. Token Validity Checks (Valid, Expired, Used, Missing)
    // =========================================================================

    @Test
    @DisplayName("isTokenValid returns true for active unexpired unconsumed token")
    void isTokenValidReturnsTrueForActiveToken() {
        String rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(tokenHash, "user@college.edu", 1L, LocalDateTime.now().plusMinutes(10), "127.0.0.1");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertTrue(passwordResetService.isTokenValid(rawToken));
    }

    @Test
    @DisplayName("isTokenValid returns false for expired token")
    void isTokenValidReturnsFalseForExpiredToken() {
        String rawToken = "expired_raw_token_value_12345";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(tokenHash, "user@college.edu", 1L, LocalDateTime.now().minusMinutes(5), "127.0.0.1");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertFalse(passwordResetService.isTokenValid(rawToken));
    }

    @Test
    @DisplayName("isTokenValid returns false for already used token")
    void isTokenValidReturnsFalseForUsedToken() {
        String rawToken = "already_used_token_value_98765";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(tokenHash, "user@college.edu", 1L, LocalDateTime.now().plusMinutes(10), "127.0.0.1");
        token.setUsed(true);

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertFalse(passwordResetService.isTokenValid(rawToken));
    }

    @Test
    @DisplayName("isTokenValid returns false for null, blank, or non-existent token")
    void isTokenValidReturnsFalseForMissingToken() {
        assertFalse(passwordResetService.isTokenValid(null));
        assertFalse(passwordResetService.isTokenValid("   "));

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertFalse(passwordResetService.isTokenValid("non_existent_token_value"));
    }

    // =========================================================================
    // 4. Password Reset Execution, BCrypt, Role Preservation, and Session Invalidation
    // =========================================================================

    @Test
    @DisplayName("resetPassword successfully updates BCrypt hash, preserves role, invalidates active tokens and user sessions")
    void resetPasswordSuccessfullyUpdatesStudentPassword() {
        String rawToken = "valid_raw_token_xyz_123456789";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(tokenHash, "rohit@college.edu", 15L, LocalDateTime.now().plusMinutes(12), "127.0.0.1");
        Student student = new Student("Rohit Yadav", "rohit@college.edu", "STU101", passwordEncoder.encode("OldPass123!"));
        student.setId(15L);
        student.setRole("ROLE_STUDENT");
        student.setDepartment("Computer Science");

        when(passwordResetTokenRepository.consumeTokenAtomically(eq(tokenHash), any(LocalDateTime.class))).thenReturn(1);
        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(studentRepository.findByEmailIgnoreCase("rohit@college.edu")).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student result = passwordResetService.resetPassword(rawToken, "NewSecurePassword456!");

        assertNotNull(result);
        assertEquals("ROLE_STUDENT", result.getRole(), "Role must be preserved");
        assertEquals("Rohit Yadav", result.getFullName());
        assertEquals("Computer Science", result.getDepartment());
        assertEquals("STU101", result.getStudentId());

        // Verify password was securely encoded with BCrypt
        assertTrue(passwordEncoder.matches("NewSecurePassword456!", result.getPassword()));
        assertFalse(result.getPassword().equals("NewSecurePassword456!"), "Plaintext password must never be stored");

        // Verify active token cleanup and session invalidation
        verify(passwordResetTokenRepository, times(1)).invalidateAllActiveForEmail(eq("rohit@college.edu"), any(LocalDateTime.class));
        verify(userManagementService, times(1)).invalidateUserSessions("rohit@college.edu");
    }

    @Test
    @DisplayName("resetPassword preserves ROLE_MODERATOR and ROLE_ADMIN during password update")
    void resetPasswordPreservesModeratorAndAdminRoles() {
        String rawTokenMod = "raw_token_moderator_12345";
        String tokenHashMod = passwordResetService.hashToken(rawTokenMod);

        PasswordResetToken tokenMod = new PasswordResetToken(tokenHashMod, "moderator@campuspulse.edu", 99L, LocalDateTime.now().plusMinutes(10), "127.0.0.1");
        Student moderator = new Student("Campus Moderator", "moderator@campuspulse.edu", null, passwordEncoder.encode("OldModPass123!"));
        moderator.setId(99L);
        moderator.setRole("ROLE_MODERATOR");

        when(passwordResetTokenRepository.consumeTokenAtomically(eq(tokenHashMod), any(LocalDateTime.class))).thenReturn(1);
        when(passwordResetTokenRepository.findByTokenHash(tokenHashMod)).thenReturn(Optional.of(tokenMod));
        when(studentRepository.findByEmailIgnoreCase("moderator@campuspulse.edu")).thenReturn(Optional.of(moderator));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student updatedMod = passwordResetService.resetPassword(rawTokenMod, "NewModeratorPass789!");
        assertEquals("ROLE_MODERATOR", updatedMod.getRole(), "Moderator role must be preserved");
        assertTrue(passwordEncoder.matches("NewModeratorPass789!", updatedMod.getPassword()));

        verify(userManagementService, times(1)).invalidateUserSessions("moderator@campuspulse.edu");
    }

    @Test
    @DisplayName("resetPassword rejects token if atomic consumption returns 0 (already used or expired)")
    void resetPasswordRejectsReusedOrExpiredToken() {
        String rawToken = "already_consumed_or_expired_token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        when(passwordResetTokenRepository.consumeTokenAtomically(eq(tokenHash), any(LocalDateTime.class))).thenReturn(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                passwordResetService.resetPassword(rawToken, "ValidPassword123!"));

        assertTrue(ex.getMessage().contains("invalid, expired, or has already been used"));
        verify(studentRepository, never()).save(any());
        verify(userManagementService, never()).invalidateUserSessions(anyString());
    }

    @Test
    @DisplayName("resetPassword rejects passwords shorter than 8 characters")
    void resetPasswordRejectsShortPasswords() {
        assertThrows(IllegalArgumentException.class, () ->
                passwordResetService.resetPassword("token123", "short"));
    }

    // =========================================================================
    // 5. Email Delivery Failure Handling
    // =========================================================================

    @Test
    @DisplayName("requestPasswordReset gracefully handles email delivery failure without breaking flow")
    void requestPasswordResetHandlesMailExceptionGracefully() {
        Student student = new Student("Alice Student", "alice@college.edu", "STU102", passwordEncoder.encode("Password123!"));
        student.setId(50L);

        when(passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(studentRepository.findByEmailIgnoreCase("alice@college.edu")).thenReturn(Optional.of(student));

        doThrow(new MailSendException("SMTP relay timeout"))
                .when(emailNotificationService).sendPasswordResetEmail(anyString(), anyString(), anyInt());

        // Must complete normally without throwing exception
        passwordResetService.requestPasswordReset("alice@college.edu", "127.0.0.1");

        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
    }
}
