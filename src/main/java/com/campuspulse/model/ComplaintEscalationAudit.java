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
 * Immutable audit entity recording automated SLA escalation events for grievances.
 * Persists complaint reference, escalation stage, trigger timestamp, and objective reason,
 * strictly omitting sensitive student descriptions or student personal details.
 */
@Entity
@Table(name = "complaint_escalation_audits", indexes = {
        @Index(name = "idx_escalation_complaint_id", columnList = "complaint_id"),
        @Index(name = "idx_escalation_reference_id", columnList = "reference_id"),
        @Index(name = "idx_escalation_stage", columnList = "escalation_stage"),
        @Index(name = "idx_escalation_triggered_at", columnList = "triggered_at")
})
public class ComplaintEscalationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(name = "reference_id", nullable = false, length = 32)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_stage", nullable = false, length = 30)
    private SlaEscalationStage escalationStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_stage", length = 30)
    private SlaEscalationStage previousStage;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "notified_recipient", length = 150)
    private String notifiedRecipient;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private LocalDateTime triggeredAt;

    public ComplaintEscalationAudit() {
    }

    public ComplaintEscalationAudit(
            Complaint complaint,
            String referenceId,
            SlaEscalationStage escalationStage,
            SlaEscalationStage previousStage,
            String reason,
            String notifiedRecipient) {
        this.complaint = complaint;
        this.referenceId = referenceId;
        this.escalationStage = escalationStage;
        this.previousStage = previousStage;
        this.reason = reason;
        this.notifiedRecipient = notifiedRecipient;
        this.triggeredAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.triggeredAt == null) {
            this.triggeredAt = LocalDateTime.now();
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

    public SlaEscalationStage getEscalationStage() {
        return escalationStage;
    }

    public void setEscalationStage(SlaEscalationStage escalationStage) {
        this.escalationStage = escalationStage;
    }

    public SlaEscalationStage getPreviousStage() {
        return previousStage;
    }

    public void setPreviousStage(SlaEscalationStage previousStage) {
        this.previousStage = previousStage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNotifiedRecipient() {
        return notifiedRecipient;
    }

    public void setNotifiedRecipient(String notifiedRecipient) {
        this.notifiedRecipient = notifiedRecipient;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public String getRecipientEmail() {
        return notifiedRecipient;
    }

    public String getComplaintReferenceId() {
        return referenceId;
    }
}
