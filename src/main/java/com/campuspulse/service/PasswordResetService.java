package com.campuspulse.service;

import com.campuspulse.model.PasswordResetToken;
import com.campuspulse.model.Student;
import com.campuspulse.repository.PasswordResetTokenRepository;
import com.campuspulse.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service orchestrating the secure password recovery lifecycle:
 * - Anti-enumeration email processing with constant user feedback.
 * - Single-use, cryptographically secure token generation (32 bytes entropy).
 * - Cryptographic SHA-256 token hashing for zero-plaintext token persistence.
 * - Strict 15-minute token expiry enforcement.
 * - Sliding window rate-limiting by email address and client IP.
 * - Atomic token verification and consumption to prevent race conditions.
 * - BCrypt password hashing, session revocation, and active token cleanup.
 * - Role preservation guaranteeing no role changes through password recovery.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final StudentRepository studentRepository;
    private final EmailNotificationService emailNotificationService;
    private final PasswordEncoder passwordEncoder;
    private final UserManagementService userManagementService;

    private final String baseUrl;
    private final int expiryMinutes;
    private final int emailMaxRequests;
    private final int ipMaxRequests;
    private final String adminUsername;

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            StudentRepository studentRepository,
            EmailNotificationService emailNotificationService,
            PasswordEncoder passwordEncoder,
            UserManagementService userManagementService,
            @Value("${campuspulse.app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${campuspulse.auth.reset.expiry-minutes:15}") int expiryMinutes,
            @Value("${campuspulse.auth.reset.rate-limit.email-max:3}") int emailMaxRequests,
            @Value("${campuspulse.auth.reset.rate-limit.ip-max:10}") int ipMaxRequests,
            @Value("${campuspulse.admin.username:admin}") String adminUsername) {

        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.studentRepository = studentRepository;
        this.emailNotificationService = emailNotificationService;
        this.passwordEncoder = passwordEncoder;
        this.userManagementService = userManagementService;
        this.baseUrl = (baseUrl != null) ? baseUrl.replaceAll("/+$", "") : "http://localhost:8080";
        this.expiryMinutes = (expiryMinutes > 0) ? expiryMinutes : 15;
        this.emailMaxRequests = (emailMaxRequests > 0) ? emailMaxRequests : 3;
        this.ipMaxRequests = (ipMaxRequests > 0) ? ipMaxRequests : 10;
        this.adminUsername = (adminUsername != null) ? adminUsername.trim() : "admin";
    }

    /**
     * Processes a password reset request safely.
     * Prevents account enumeration: returns normally without leaking whether the account exists.
     * Rate-limits requests by email and requester IP to mitigate brute-force and email flooding.
     *
     * @param rawEmail user-submitted email address
     * @param requesterIp client IP address for rate-limiting and auditing
     */
    @Transactional
    public void requestPasswordReset(String rawEmail, String requesterIp) {
        if (rawEmail == null || rawEmail.isBlank()) {
            log.debug("Password reset requested with empty email address");
            return;
        }

        String normalizedEmail = rawEmail.trim().toLowerCase();
        String effectiveIp = (requesterIp != null && !requesterIp.isBlank()) ? requesterIp.trim() : "unknown";

        // Check rate limiting before creating tokens or sending email
        if (isRateLimited(normalizedEmail, effectiveIp)) {
            log.warn("Password reset rate limit exceeded for requesterIp={} and email={}. Request suppressed.",
                    effectiveIp, maskEmail(normalizedEmail));
            return;
        }

        // Environment-configured admin account note:
        // If an email matches the environment admin username without a DB account,
        // we do not create a separate shadow DB account. We log a safe notice and return.
        if (normalizedEmail.equalsIgnoreCase(adminUsername)) {
            log.info("Password reset requested for system environment admin username '{}'. " +
                    "Environment-configured admin credentials cannot be altered via database self-service.", adminUsername);
            return;
        }

        Optional<Student> studentOpt = studentRepository.findByEmailIgnoreCase(normalizedEmail);
        if (studentOpt.isEmpty()) {
            log.info("Password reset requested for non-existent account: {}", maskEmail(normalizedEmail));
            return;
        }

        Student student = studentOpt.get();

        // Generate 32 bytes of cryptographic randomness (256-bit entropy)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);

        // Compute SHA-256 hash for secure database persistence
        String tokenHash = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(expiryMinutes);

        PasswordResetToken resetToken = new PasswordResetToken(
                tokenHash,
                student.getEmail(),
                student.getId(),
                expiresAt,
                effectiveIp
        );

        passwordResetTokenRepository.save(resetToken);

        String resetUrl = baseUrl + "/reset-password?token=" + rawToken;

        try {
            emailNotificationService.sendPasswordResetEmail(student.getEmail(), resetUrl, expiryMinutes);
            log.info("Password reset email successfully dispatched for user id={} (masked: {})",
                    student.getId(), maskEmail(student.getEmail()));
        } catch (Exception ex) {
            log.error("Failed to deliver password reset email to user id={} (masked: {}): {}",
                    student.getId(), maskEmail(student.getEmail()), ex.getMessage());
        }
    }

    /**
     * Checks whether the request breaches either the email or IP sliding-window rate limit.
     *
     * @param email target email
     * @param requesterIp client IP address
     * @return true if rate limit is exceeded
     */
    @Transactional(readOnly = true)
    public boolean isRateLimited(String email, String requesterIp) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expiryMinutes);

        if (email != null && !email.isBlank()) {
            long emailCount = passwordResetTokenRepository.countByEmailIgnoreCaseAndCreatedAtAfter(email.trim().toLowerCase(), cutoff);
            if (emailCount >= emailMaxRequests) {
                return true;
            }
        }

        if (requesterIp != null && !requesterIp.isBlank() && !"unknown".equalsIgnoreCase(requesterIp)) {
            long ipCount = passwordResetTokenRepository.countByRequesterIpAndCreatedAtAfter(requesterIp.trim(), cutoff);
            if (ipCount >= ipMaxRequests) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validates whether a raw reset token is present, active, unexpired, and unconsumed.
     *
     * @param rawToken unhashed raw token string
     * @return true if token is currently valid
     */
    @Transactional(readOnly = true)
    public boolean isTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        String tokenHash = hashToken(rawToken.trim());
        return passwordResetTokenRepository.findByTokenHash(tokenHash)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    /**
     * Retrieves valid token entity by raw token string without consuming it.
     *
     * @param rawToken raw token string
     * @return Optional containing token if valid, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> getValidToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(rawToken.trim());
        return passwordResetTokenRepository.findByTokenHash(tokenHash)
                .filter(PasswordResetToken::isValid);
    }

    /**
     * Atomically consumes the token, validates the new password, hashes it using BCrypt,
     * updates the student account, invalidates all other active tokens for that email,
     * and invalidates all existing HTTP sessions for that user.
     * Preserves the existing role and profile attributes.
     *
     * @param rawToken raw unhashed reset token
     * @param newPassword validated plaintext password
     * @return updated Student entity
     * @throws IllegalArgumentException if token is invalid/expired/used or password rules fail
     */
    @Transactional
    public Student resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("This password reset link is invalid, expired, or has already been used.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters long.");
        }

        String tokenHash = hashToken(rawToken.trim());
        LocalDateTime now = LocalDateTime.now();

        // Atomic token consumption: ensures simultaneous concurrent requests cannot reuse a token
        int rowsUpdated = passwordResetTokenRepository.consumeTokenAtomically(tokenHash, now);
        if (rowsUpdated == 0) {
            log.warn("Rejected password reset attempt: token hash is invalid, already consumed, or expired.");
            throw new IllegalArgumentException("This password reset link is invalid, expired, or has already been used.");
        }

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("This password reset link is invalid, expired, or has already been used."));

        Student student = studentRepository.findByEmailIgnoreCase(token.getEmail())
                .orElseThrow(() -> new IllegalStateException("Account associated with this reset token could not be found."));

        // Preserve existing role strictly
        String existingRole = student.getRole();

        // Encode new password with BCrypt
        String hashedPassword = passwordEncoder.encode(newPassword);
        student.setPassword(hashedPassword);
        student.setRole(existingRole); // Explicitly ensure role remains unaltered

        Student savedStudent = studentRepository.save(student);

        // Invalidate all other outstanding reset tokens for this account
        passwordResetTokenRepository.invalidateAllActiveForEmail(savedStudent.getEmail(), now);

        // Expire all active HTTP sessions for this user so old sessions cannot remain active
        if (userManagementService != null) {
            userManagementService.invalidateUserSessions(savedStudent.getEmail());
        }

        log.info("Password successfully reset for account id={}, email={}, role={}",
                savedStudent.getId(), maskEmail(savedStudent.getEmail()), savedStudent.getRole());

        return savedStudent;
    }

    /**
     * Calculates the SHA-256 cryptographic hash of a token string.
     *
     * @param rawToken plaintext raw token
     * @return 64-character lowercase hexadecimal hash string
     */
    public String hashToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", ex);
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[empty]";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
