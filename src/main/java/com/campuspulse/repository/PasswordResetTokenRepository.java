package com.campuspulse.repository;

import com.campuspulse.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for PasswordResetToken persistence and atomic token operations.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds a token entry by its unique SHA-256 hash.
     *
     * @param tokenHash hex-encoded SHA-256 token hash
     * @return Optional containing the token entity if found
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Retrieves all active (unused) tokens associated with a given email.
     *
     * @param email target user email
     * @return list of active tokens
     */
    List<PasswordResetToken> findByEmailIgnoreCaseAndUsedFalse(String email);

    /**
     * Counts reset requests created for a specific email since a given cutoff time (used for rate-limiting).
     *
     * @param email target user email
     * @param cutoff cutoff timestamp
     * @return number of requests created after cutoff
     */
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, LocalDateTime cutoff);

    /**
     * Counts reset requests initiated from a specific requester IP address since a cutoff time (used for IP rate-limiting).
     *
     * @param requesterIp client IP address
     * @param cutoff cutoff timestamp
     * @return number of requests from the given IP after cutoff
     */
    long countByRequesterIpAndCreatedAtAfter(String requesterIp, LocalDateTime cutoff);

    /**
     * Atomically consumes a token if it is valid (not used and not expired).
     * Guarantees race-condition prevention across concurrent requests.
     *
     * @param tokenHash hex-encoded SHA-256 token hash
     * @param now current timestamp for consumption and expiry check
     * @return number of rows updated (1 if successfully consumed, 0 if already consumed or expired)
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true, t.usedAt = :now " +
           "WHERE t.tokenHash = :tokenHash AND t.used = false AND t.expiresAt > :now")
    int consumeTokenAtomically(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /**
     * Invalidates all active (unused) tokens for a given account after a successful password reset.
     *
     * @param email target user email
     * @param now timestamp of invalidation
     * @return number of invalidated tokens
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true, t.usedAt = :now " +
           "WHERE LOWER(t.email) = LOWER(:email) AND t.used = false")
    int invalidateAllActiveForEmail(@Param("email") String email, @Param("now") LocalDateTime now);
}
