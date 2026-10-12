package com.campuspulse.service;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.NotificationOutbox;
import com.campuspulse.model.NotificationStatus;
import com.campuspulse.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service managing the persistent notification outbox lifecycle.
 * Coordinates outbox persistence, immediate dispatch, failure recovery, atomic claiming,
 * exponential backoff, and retry mechanisms.
 * Guarantees that external SMTP delivery issues never roll back or fail complaint status updates.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final NotificationOutboxRepository outboxRepository;
    private final EmailNotificationService emailNotificationService;

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final long staleThresholdMs;

    public NotificationService(
            NotificationOutboxRepository outboxRepository,
            EmailNotificationService emailNotificationService) {
        this(outboxRepository, emailNotificationService, 3, 30000L, 2.0, 300000L, 300000L);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(
            NotificationOutboxRepository outboxRepository,
            EmailNotificationService emailNotificationService,
            @Value("${campuspulse.notifications.retry.max-attempts:3}") int maxAttempts,
            @Value("${campuspulse.notifications.retry.initial-backoff-ms:30000}") long initialBackoffMs,
            @Value("${campuspulse.notifications.retry.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${campuspulse.notifications.retry.max-backoff-ms:300000}") long maxBackoffMs,
            @Value("${campuspulse.notifications.retry.stale-threshold-ms:300000}") long staleThresholdMs) {
        this.outboxRepository = outboxRepository;
        this.emailNotificationService = emailNotificationService;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
        this.staleThresholdMs = staleThresholdMs;
    }

    /**
     * Queues and immediately dispatches a status update notification for eligible statuses
     * (UNDER_REVIEW, IN_PROGRESS, RESOLVED).
     * If recipient email is missing, safely records SKIPPED_NO_EMAIL without sending.
     * If recipient email is permanently malformed, safely records PERMANENTLY_FAILED.
     * If SMTP delivery fails, marks the outbox item as FAILED with exponential backoff without throwing,
     * ensuring the caller's complaint update transaction commits successfully.
     *
     * @param complaint persisted complaint entity
     * @param newStatus updated status
     * @return persisted NotificationOutbox entry, or null if status is not eligible
     */
    @Transactional
    public NotificationOutbox queueAndDispatchStatusUpdate(Complaint complaint, ComplaintStatus newStatus) {
        if (!isEligibleStatus(newStatus)) {
            log.debug("Status {} is not eligible for automated student email notification. Skipping.", newStatus);
            return null;
        }

        String recipientEmail = complaint.getStudentEmail();
        boolean hasEmail = (recipientEmail != null && !recipientEmail.trim().isEmpty());

        NotificationOutbox outbox = new NotificationOutbox(
                complaint,
                complaint.getReferenceId(),
                hasEmail ? recipientEmail.trim() : null,
                newStatus
        );

        if (!hasEmail) {
            outbox.setStatus(NotificationStatus.SKIPPED_NO_EMAIL);
            outbox.setLastError("No student email address provided on complaint submission");
            outbox.setNextRetryAt(null);
            NotificationOutbox saved = outboxRepository.save(outbox);
            log.info("Complaint {} has no student email address. Recorded outbox entry as SKIPPED_NO_EMAIL.",
                    complaint.getReferenceId());
            return saved;
        }

        if (isPermanentlyInvalidEmail(recipientEmail)) {
            outbox.setStatus(NotificationStatus.PERMANENTLY_FAILED);
            outbox.setLastError("Permanently invalid email format: " + maskEmail(recipientEmail));
            outbox.setNextRetryAt(null);
            NotificationOutbox saved = outboxRepository.save(outbox);
            log.warn("Complaint {} has permanently invalid email format {}. Recorded as PERMANENTLY_FAILED.",
                    complaint.getReferenceId(), maskEmail(recipientEmail));
            return saved;
        }

        // Initialize in PROCESSING state to guarantee concurrent scheduled workers
        // do not race against this immediate dispatch attempt.
        outbox.setStatus(NotificationStatus.PROCESSING);
        outbox.setLastAttemptedAt(LocalDateTime.now());
        outbox.setNextRetryAt(null);
        NotificationOutbox savedOutbox = outboxRepository.save(outbox);

        // Immediate dispatch attempt with fault tolerance
        try {
            emailNotificationService.sendNotification(
                    savedOutbox.getRecipientEmail(),
                    savedOutbox.getReferenceId(),
                    savedOutbox.getTargetStatus()
            );

            savedOutbox.setStatus(NotificationStatus.SENT);
            savedOutbox.setSentAt(LocalDateTime.now());
            savedOutbox.setLastAttemptedAt(LocalDateTime.now());
            savedOutbox.setAttemptCount(1);
            savedOutbox.setLastError(null);
            savedOutbox.setNextRetryAt(null);
            log.info("Email notification successfully sent for referenceId={} to {}",
                    savedOutbox.getReferenceId(), maskEmail(savedOutbox.getRecipientEmail()));
            return outboxRepository.save(savedOutbox);
        } catch (Exception ex) {
            log.warn("Immediate email delivery failed for referenceId={} (marked for backoff retry): {}",
                    savedOutbox.getReferenceId(), ex.getMessage());
            savedOutbox.setLastAttemptedAt(LocalDateTime.now());
            savedOutbox.setAttemptCount(1);
            savedOutbox.setLastError(truncateError(ex.getMessage()));

            boolean permanent = isPermanentFailure(ex) || (1 >= maxAttempts);
            if (permanent) {
                savedOutbox.setStatus(NotificationStatus.PERMANENTLY_FAILED);
                savedOutbox.setNextRetryAt(null);
                log.warn("Immediate failure for referenceId={} determined to be permanent. Marked PERMANENTLY_FAILED.",
                        savedOutbox.getReferenceId());
            } else {
                savedOutbox.setStatus(NotificationStatus.FAILED);
                long backoffMs = calculateBackoffDelayMs(1);
                savedOutbox.setNextRetryAt(LocalDateTime.now().plus(backoffMs, ChronoUnit.MILLIS));
                log.info("Scheduled retry for referenceId={} with initial backoff {}ms",
                        savedOutbox.getReferenceId(), backoffMs);
            }
            return outboxRepository.save(savedOutbox);
        }
    }

    /**
     * Queues and immediately dispatches an administrative SLA breach escalation alert.
     * If SMTP delivery fails, marks the outbox item as FAILED with exponential backoff without throwing,
     * ensuring the caller's escalation audit transaction commits successfully.
     *
     * @param complaint persisted complaint entity
     * @param stage escalation stage
     * @param reason objective breach explanation
     * @param adminEmail configured administrative recipient
     * @return persisted NotificationOutbox entry
     */
    @Transactional
    public NotificationOutbox queueAndDispatchEscalationNotification(
            Complaint complaint,
            com.campuspulse.model.SlaEscalationStage stage,
            String reason,
            String adminEmail) {

        boolean hasEmail = (adminEmail != null && !adminEmail.trim().isEmpty());

        NotificationOutbox outbox = new NotificationOutbox(
                complaint,
                complaint.getReferenceId(),
                hasEmail ? adminEmail.trim() : null,
                stage,
                reason
        );

        if (!hasEmail) {
            outbox.setStatus(NotificationStatus.SKIPPED_NO_EMAIL);
            outbox.setLastError("No administrator escalation email address configured");
            outbox.setNextRetryAt(null);
            NotificationOutbox saved = outboxRepository.save(outbox);
            log.warn("Escalation for complaint {} skipped email delivery: no admin email configured",
                    complaint.getReferenceId());
            return saved;
        }

        if (isPermanentlyInvalidEmail(adminEmail.trim())) {
            outbox.setStatus(NotificationStatus.PERMANENTLY_FAILED);
            outbox.setLastError("Permanently invalid admin escalation email address");
            outbox.setNextRetryAt(null);
            NotificationOutbox saved = outboxRepository.save(outbox);
            return saved;
        }

        NotificationOutbox savedOutbox = outboxRepository.save(outbox);

        try {
            emailNotificationService.sendEscalationNotification(
                    adminEmail.trim(),
                    complaint.getReferenceId(),
                    stage,
                    reason,
                    complaint.getCategory(),
                    complaint.getDepartment()
            );

            savedOutbox.setStatus(NotificationStatus.SENT);
            savedOutbox.setSentAt(LocalDateTime.now());
            savedOutbox.setLastAttemptedAt(LocalDateTime.now());
            savedOutbox.setAttemptCount(1);
            savedOutbox.setLastError(null);
            savedOutbox.setNextRetryAt(null);
            log.info("Immediate SLA escalation email dispatched for referenceId={} stage={}",
                    complaint.getReferenceId(), stage);
            return outboxRepository.save(savedOutbox);
        } catch (Exception ex) {
            log.warn("Immediate escalation email delivery failed for referenceId={} (marked for backoff retry): {}",
                    savedOutbox.getReferenceId(), ex.getMessage());

            savedOutbox.setAttemptCount(1);
            savedOutbox.setLastAttemptedAt(LocalDateTime.now());
            savedOutbox.setLastError(truncateError(ex.getMessage()));

            if (isPermanentFailure(ex)) {
                savedOutbox.setStatus(NotificationStatus.PERMANENTLY_FAILED);
                savedOutbox.setNextRetryAt(null);
            } else {
                savedOutbox.setStatus(NotificationStatus.FAILED);
                long backoffMs = calculateBackoffDelayMs(1);
                savedOutbox.setNextRetryAt(LocalDateTime.now().plus(backoffMs, ChronoUnit.MILLIS));
                log.info("Scheduled retry for escalation referenceId={} with initial backoff {}ms",
                        savedOutbox.getReferenceId(), backoffMs);
            }
            return outboxRepository.save(savedOutbox);
        }
    }

    /**
     * Executes retry delivery for eligible PENDING and FAILED notifications.
     * Prevents duplicate deliveries and concurrent worker conflicts via atomic database claims.
     * Honors exponential backoff and maximum retry thresholds.
     *
     * @return number of successfully delivered notifications
     */
    @Transactional
    public int processPendingAndFailedRetries() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleThreshold = now.minus(staleThresholdMs, ChronoUnit.MILLIS);

        List<Long> candidateIds = outboxRepository.findEligibleCandidateIds(now, maxAttempts, staleThreshold);
        if (candidateIds.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (Long id : candidateIds) {
            boolean delivered = processSingleNotificationRetry(id, now, staleThreshold);
            if (delivered) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * Attempts to claim and deliver a single notification by ID.
     *
     * @param id target notification ID
     * @param now current timestamp
     * @param staleThreshold threshold before a PROCESSING record is considered abandoned
     * @return true if successfully delivered via SMTP, false otherwise
     */
    @Transactional
    public boolean processSingleNotificationRetry(Long id, LocalDateTime now, LocalDateTime staleThreshold) {
        // Atomic database claim: only ONE concurrent worker can successfully transition row to PROCESSING
        int claimed = outboxRepository.claimNotificationForProcessing(id, now, staleThreshold);
        if (claimed == 0) {
            log.debug("Notification id={} could not be claimed (already claimed or processed by another worker)", id);
            return false;
        }

        NotificationOutbox outbox = outboxRepository.findById(id).orElse(null);
        if (outbox == null) {
            return false;
        }

        // Validate recipient email
        if (outbox.getRecipientEmail() == null || outbox.getRecipientEmail().isBlank()) {
            outbox.setStatus(NotificationStatus.SKIPPED_NO_EMAIL);
            outbox.setLastError("No student email address provided");
            outbox.setNextRetryAt(null);
            outboxRepository.save(outbox);
            return false;
        }

        if (isPermanentlyInvalidEmail(outbox.getRecipientEmail())) {
            outbox.setStatus(NotificationStatus.PERMANENTLY_FAILED);
            outbox.setLastError("Permanently invalid recipient email address: " + maskEmail(outbox.getRecipientEmail()));
            outbox.setNextRetryAt(null);
            outboxRepository.save(outbox);
            return false;
        }

        try {
            if (outbox.getNotificationType() == com.campuspulse.model.NotificationType.SLA_ESCALATION) {
                String cat = (outbox.getComplaint() != null && outbox.getComplaint().getCategory() != null)
                        ? outbox.getComplaint().getCategory() : "General";
                String dept = (outbox.getComplaint() != null && outbox.getComplaint().getDepartment() != null)
                        ? outbox.getComplaint().getDepartment() : "General";
                emailNotificationService.sendEscalationNotification(
                        outbox.getRecipientEmail(),
                        outbox.getReferenceId(),
                        outbox.getEscalationStage(),
                        outbox.getEscalationReason(),
                        cat,
                        dept
                );
            } else {
                emailNotificationService.sendNotification(
                        outbox.getRecipientEmail(),
                        outbox.getReferenceId(),
                        outbox.getTargetStatus()
                );
            }

            outbox.setStatus(NotificationStatus.SENT);
            outbox.setSentAt(LocalDateTime.now());
            outbox.setLastAttemptedAt(LocalDateTime.now());
            outbox.setAttemptCount(outbox.getAttemptCount() + 1);
            outbox.setLastError(null);
            outbox.setNextRetryAt(null);
            outboxRepository.save(outbox);

            log.info("Retry delivery succeeded for notification id={} referenceId={}",
                    outbox.getId(), outbox.getReferenceId());
            return true;
        } catch (Exception ex) {
            int newAttemptCount = outbox.getAttemptCount() + 1;
            outbox.setAttemptCount(newAttemptCount);
            outbox.setLastAttemptedAt(LocalDateTime.now());
            outbox.setLastError(truncateError(ex.getMessage()));

            boolean permanent = isPermanentFailure(ex) || (newAttemptCount >= maxAttempts);
            if (permanent) {
                outbox.setStatus(NotificationStatus.PERMANENTLY_FAILED);
                outbox.setNextRetryAt(null);
                log.warn("Notification id={} referenceId={} transitioned to PERMANENTLY_FAILED (attempt {} of {}): {}",
                        outbox.getId(), outbox.getReferenceId(), newAttemptCount, maxAttempts, ex.getMessage());
            } else {
                outbox.setStatus(NotificationStatus.FAILED);
                long backoffMs = calculateBackoffDelayMs(newAttemptCount);
                outbox.setNextRetryAt(LocalDateTime.now().plus(backoffMs, ChronoUnit.MILLIS));
                log.warn("Notification id={} referenceId={} retry attempt {} of {} failed (next retry in {}ms): {}",
                        outbox.getId(), outbox.getReferenceId(), newAttemptCount, maxAttempts, backoffMs, ex.getMessage());
            }
            outboxRepository.save(outbox);
            return false;
        }
    }

    /**
     * Legacy entry point maintained for backward compatibility. Delegates to atomic retry processing.
     *
     * @return number of successfully delivered notifications
     */
    @Transactional
    public int retryFailedNotifications() {
        return processPendingAndFailedRetries();
    }

    /**
     * Calculates exponential backoff delay in milliseconds for a given attempt index.
     * Uses: initialBackoffMs * (multiplier ^ (attempt - 1)), capped at maxBackoffMs.
     *
     * @param attempt 1-indexed attempt number
     * @return delay in milliseconds
     */
    public long calculateBackoffDelayMs(int attempt) {
        if (attempt <= 1) {
            return initialBackoffMs;
        }
        double factor = Math.pow(backoffMultiplier, attempt - 1);
        long delay = (long) (initialBackoffMs * factor);
        return Math.min(delay, maxBackoffMs);
    }

    /**
     * Determines whether an exception represents an unrecoverable, permanent SMTP rejection
     * (e.g. SMTP 5xx error codes, invalid mailbox, rejected address).
     *
     * @param ex caught exception
     * @return true if permanent failure, false if transient
     */
    public boolean isPermanentFailure(Throwable ex) {
        if (ex == null) {
            return false;
        }
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("550") || lower.contains("551") || lower.contains("552")
                    || lower.contains("553") || lower.contains("554")
                    || lower.contains("invalid address")
                    || lower.contains("recipient address rejected")
                    || lower.contains("user unknown")
                    || lower.contains("mailbox unavailable")
                    || lower.contains("address rejected")
                    || lower.contains("permanently invalid")) {
                return true;
            }
        }
        if (ex.getCause() != null && ex.getCause() != ex) {
            return isPermanentFailure(ex.getCause());
        }
        return false;
    }

    /**
     * Validates whether an email string is structurally valid.
     *
     * @param email candidate email
     * @return true if permanently invalid
     */
    public boolean isPermanentlyInvalidEmail(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        return !EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Retrieves chronological outbox notifications for a given complaint reference ID.
     *
     * @param referenceId unique complaint reference ID
     * @return list of outbox records
     */
    @Transactional(readOnly = true)
    public List<NotificationOutbox> getOutboxForComplaint(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return List.of();
        }
        return outboxRepository.findByReferenceIdOrderByCreatedAtDesc(referenceId.trim());
    }

    /**
     * Counts outbox items by status.
     *
     * @param status notification status
     * @return count of matching items
     */
    @Transactional(readOnly = true)
    public long countByStatus(NotificationStatus status) {
        return outboxRepository.countByStatus(status);
    }

    /**
     * Checks if status transition qualifies for student email notification.
     * Notifications are sent for UNDER_REVIEW, IN_PROGRESS, and RESOLVED.
     */
    public boolean isEligibleStatus(ComplaintStatus status) {
        return status == ComplaintStatus.UNDER_REVIEW
                || status == ComplaintStatus.IN_PROGRESS
                || status == ComplaintStatus.RESOLVED;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public long getStaleThresholdMs() {
        return staleThresholdMs;
    }

    private String truncateError(String error) {
        if (error == null) {
            return "Unknown delivery error";
        }
        return error.length() > 950 ? error.substring(0, 950) + "..." : error;
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
