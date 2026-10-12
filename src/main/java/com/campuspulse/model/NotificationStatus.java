package com.campuspulse.model;

/**
 * Status of an email notification in the transactional outbox.
 */
public enum NotificationStatus {
    /**
     * Notification is queued and awaiting initial delivery or scheduled retry.
     */
    PENDING,

    /**
     * Notification is claimed by an active worker thread for delivery.
     * Prevents concurrent workers from delivering the same notification simultaneously.
     */
    PROCESSING,

    /**
     * Notification was successfully delivered via SMTP.
     */
    SENT,

    /**
     * Notification delivery failed (e.g., SMTP unreachable, timeout); eligible for retry.
     */
    FAILED,

    /**
     * Notification delivery failed permanently (e.g. maximum retry attempts exceeded,
     * or permanently invalid email address); will not be retried.
     */
    PERMANENTLY_FAILED,

    /**
     * Notification was skipped safely because no valid recipient email was provided.
     */
    SKIPPED_NO_EMAIL
}
