package com.campuspulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity representing a persistent notification outbox item.
 * Decouples complaint status updates from external SMTP delivery,
 * guaranteeing that email delivery failures do not roll back complaint records,
 * and enabling reliable retries without duplicate notifications.
 */
@Entity
@Table(name = "notification_outbox", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_reference_id", columnList = "reference_id"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at"),
        @Index(name = "idx_outbox_next_retry_at", columnList = "next_retry_at")
})
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(name = "reference_id", nullable = false, length = 32)
    private String referenceId;

    @Column(name = "recipient_email", length = 150)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 30)
    private NotificationType notificationType = NotificationType.STATUS_UPDATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_stage", length = 30)
    private SlaEscalationStage escalationStage;

    @Column(name = "escalation_reason", length = 1000)
    private String escalationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", nullable = false, length = 30)
    private ComplaintStatus targetStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    public NotificationOutbox() {
        this.notificationType = NotificationType.STATUS_UPDATE;
    }

    public NotificationOutbox(Complaint complaint, String referenceId, String recipientEmail, ComplaintStatus targetStatus) {
        this.complaint = complaint;
        this.referenceId = referenceId;
        this.recipientEmail = recipientEmail;
        this.targetStatus = targetStatus;
        this.notificationType = NotificationType.STATUS_UPDATE;
        if (recipientEmail == null || recipientEmail.isBlank()) {
            this.status = NotificationStatus.SKIPPED_NO_EMAIL;
            this.lastError = "No student email address provided";
        } else {
            this.status = NotificationStatus.PENDING;
        }
        this.createdAt = LocalDateTime.now();
    }

    public NotificationOutbox(Complaint complaint, String referenceId, String recipientEmail, SlaEscalationStage escalationStage, String escalationReason) {
        this.complaint = complaint;
        this.referenceId = referenceId;
        this.recipientEmail = recipientEmail;
        this.targetStatus = (complaint != null && complaint.getStatus() != null) ? complaint.getStatus() : ComplaintStatus.SUBMITTED;
        this.notificationType = NotificationType.SLA_ESCALATION;
        this.escalationStage = escalationStage;
        this.escalationReason = escalationReason;
        if (recipientEmail == null || recipientEmail.isBlank()) {
            this.status = NotificationStatus.SKIPPED_NO_EMAIL;
            this.lastError = "No administrator escalation email address configured";
        } else {
            this.status = NotificationStatus.PENDING;
        }
        this.createdAt = LocalDateTime.now();
    }

    public NotificationOutbox(Complaint complaint, String referenceId, String recipientEmail, NotificationType notificationType) {
        this.complaint = complaint;
        this.referenceId = referenceId;
        this.recipientEmail = recipientEmail;
        this.targetStatus = (complaint != null && complaint.getStatus() != null) ? complaint.getStatus() : ComplaintStatus.SUBMITTED;
        this.notificationType = (notificationType != null) ? notificationType : NotificationType.STATUS_UPDATE;
        if (recipientEmail == null || recipientEmail.isBlank()) {
            this.status = NotificationStatus.SKIPPED_NO_EMAIL;
            this.lastError = "No student email address provided";
        } else {
            this.status = NotificationStatus.PENDING;
        }
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public ComplaintStatus getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(ComplaintStatus targetStatus) {
        this.targetStatus = targetStatus;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public void setLastAttemptedAt(LocalDateTime lastAttemptedAt) {
        this.lastAttemptedAt = lastAttemptedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public SlaEscalationStage getEscalationStage() {
        return escalationStage;
    }

    public void setEscalationStage(SlaEscalationStage escalationStage) {
        this.escalationStage = escalationStage;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }
}

