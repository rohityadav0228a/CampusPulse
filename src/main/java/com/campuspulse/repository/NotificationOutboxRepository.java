package com.campuspulse.repository;

import com.campuspulse.model.NotificationOutbox;
import com.campuspulse.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA repository for managing persistent notification outbox records.
 * Provides atomic row-level claiming queries to prevent concurrent workers from
 * sending duplicate notifications.
 */
@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    /**
     * Finds outbox items by status ordered by creation timestamp ascending.
     *
     * @param status notification status
     * @return matching outbox entries
     */
    List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    /**
     * Finds all outbox entries for a specific complaint reference ID ordered newest first.
     *
     * @param referenceId unique complaint reference ID
     * @return matching outbox history
     */
    List<NotificationOutbox> findByReferenceIdOrderByCreatedAtDesc(String referenceId);

    /**
     * Finds outbox items matching any of the given statuses, ordered chronologically.
     *
     * @param statuses collection of notification statuses
     * @return matching outbox entries
     */
    List<NotificationOutbox> findByStatusInOrderByCreatedAtAsc(Collection<NotificationStatus> statuses);

    /**
     * Counts outbox items by current status.
     *
     * @param status notification status
     * @return count of matching items
     */
    long countByStatus(NotificationStatus status);

    /**
     * Identifies IDs of notifications eligible for delivery retry.
     * Selects items whose status is PENDING, FAILED, or PROCESSING but stale (worker timeout),
     * where backoff timestamp has elapsed, and attempt limit has not been exceeded.
     *
     * @param now current timestamp to compare against nextRetryAt
     * @param maxAttempts maximum allowed attempts before permanent failure
     * @param staleThreshold threshold before a PROCESSING record is considered abandoned
     * @return list of eligible notification IDs ordered oldest first
     */
    @Query("SELECT n.id FROM NotificationOutbox n WHERE (n.status = com.campuspulse.model.NotificationStatus.PENDING "
            + "OR n.status = com.campuspulse.model.NotificationStatus.FAILED "
            + "OR (n.status = com.campuspulse.model.NotificationStatus.PROCESSING AND n.lastAttemptedAt < :staleThreshold)) "
            + "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now) "
            + "AND n.attemptCount < :maxAttempts "
            + "ORDER BY n.createdAt ASC")
    List<Long> findEligibleCandidateIds(
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleThreshold") LocalDateTime staleThreshold
    );

    /**
     * Atomically claims a notification by transitioning its status to PROCESSING.
     * Uses an atomic database UPDATE condition so that only ONE concurrent worker can successfully
     * update the record. Returns 1 if claimed by this worker, or 0 if claimed by a concurrent worker.
     *
     * @param id target notification ID
     * @param now timestamp of claim attempt
     * @param staleThreshold threshold before a PROCESSING record is considered abandoned
     * @return number of rows updated (1 if successfully claimed, 0 if lost race)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NotificationOutbox n SET n.status = com.campuspulse.model.NotificationStatus.PROCESSING, "
            + "n.lastAttemptedAt = :now "
            + "WHERE n.id = :id AND (n.status = com.campuspulse.model.NotificationStatus.PENDING "
            + "OR n.status = com.campuspulse.model.NotificationStatus.FAILED "
            + "OR (n.status = com.campuspulse.model.NotificationStatus.PROCESSING AND n.lastAttemptedAt < :staleThreshold))")
    int claimNotificationForProcessing(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("staleThreshold") LocalDateTime staleThreshold
    );
}
