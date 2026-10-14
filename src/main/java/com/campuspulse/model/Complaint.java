package com.campuspulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity representing a student grievance/complaint.
 */
@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaint_reference_id", columnList = "reference_id", unique = true),
        @Index(name = "idx_complaint_created_at", columnList = "created_at"),
        @Index(name = "idx_complaint_response_deadline", columnList = "response_deadline"),
        @Index(name = "idx_complaint_resolution_deadline", columnList = "resolution_deadline"),
        @Index(name = "idx_complaint_escalation_stage", columnList = "escalation_stage"),
        @Index(name = "idx_complaint_priority", columnList = "priority"),
        @Index(name = "idx_complaint_department", columnList = "department"),
        @Index(name = "idx_complaint_deleted", columnList = "deleted")
})
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, unique = true, updatable = false, length = 32)
    private String referenceId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(name = "student_email", length = 150)
    private String studentEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "resolution_deadline")
    private LocalDateTime resolutionDeadline;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_stage", length = 30)
    private SlaEscalationStage escalationStage = SlaEscalationStage.NONE;

    @Column(name = "last_escalated_at")
    private LocalDateTime lastEscalatedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'MEDIUM'")
    private ComplaintPriority priority = ComplaintPriority.MEDIUM;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    public Complaint() {
        this.escalationStage = SlaEscalationStage.NONE;
        this.priority = ComplaintPriority.MEDIUM;
    }

    public Complaint(String referenceId, String title, String description, String category, String department) {
        this(referenceId, title, description, category, department, null);
    }

    public Complaint(String referenceId, String title, String description, String category, String department, String studentEmail) {
        this.referenceId = referenceId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.department = department;
        this.studentEmail = studentEmail;
        this.status = ComplaintStatus.SUBMITTED;
        this.createdAt = LocalDateTime.now();
        this.responseDeadline = this.createdAt.plusHours(24);
        this.resolutionDeadline = this.createdAt.plusHours(72);
        this.escalationStage = SlaEscalationStage.NONE;
        this.priority = ComplaintPriority.MEDIUM;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = ComplaintStatus.SUBMITTED;
        }
        if (this.responseDeadline == null) {
            this.responseDeadline = this.createdAt.plusHours(24);
        }
        if (this.resolutionDeadline == null) {
            this.resolutionDeadline = this.createdAt.plusHours(72);
        }
        if (this.escalationStage == null) {
            this.escalationStage = SlaEscalationStage.NONE;
        }
        if (this.priority == null) {
            this.priority = ComplaintPriority.MEDIUM;
        }
    }

    @PostLoad
    protected void onPostLoad() {
        if (this.priority == null) {
            this.priority = ComplaintPriority.MEDIUM;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResponseDeadline() {
        return responseDeadline;
    }

    public void setResponseDeadline(LocalDateTime responseDeadline) {
        this.responseDeadline = responseDeadline;
    }

    public LocalDateTime getResolutionDeadline() {
        return resolutionDeadline;
    }

    public void setResolutionDeadline(LocalDateTime resolutionDeadline) {
        this.resolutionDeadline = resolutionDeadline;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public SlaEscalationStage getEscalationStage() {
        return escalationStage;
    }

    public void setEscalationStage(SlaEscalationStage escalationStage) {
        this.escalationStage = escalationStage;
    }

    public LocalDateTime getLastEscalatedAt() {
        return lastEscalatedAt;
    }

    public void setLastEscalatedAt(LocalDateTime lastEscalatedAt) {
        this.lastEscalatedAt = lastEscalatedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    // =========================================================================
    // SLA Domain Helper Methods
    // =========================================================================

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public boolean isResolved() {
        return status == ComplaintStatus.RESOLVED;
    }

    /**
     * Determines whether the initial response SLA has been breached.
     * Response is overdue ONLY if the complaint has not yet been acknowledged,
     * is not resolved, and the current evaluation time has exceeded the response deadline.
     */
    public boolean isResponseOverdue(LocalDateTime now) {
        if (isResolved() || isAcknowledged()) {
            return false;
        }
        return responseDeadline != null && now.isAfter(responseDeadline);
    }

    public boolean isResponseOverdue() {
        return isResponseOverdue(LocalDateTime.now());
    }

    /**
     * Determines whether the resolution SLA has been breached.
     * Resolution is overdue if the complaint has not been resolved
     * and the current evaluation time has exceeded the resolution deadline.
     */
    public boolean isResolutionOverdue(LocalDateTime now) {
        if (isResolved()) {
            return false;
        }
        return resolutionDeadline != null && now.isAfter(resolutionDeadline);
    }

    public boolean isResolutionOverdue() {
        return isResolutionOverdue(LocalDateTime.now());
    }

    /**
     * Determines whether a complaint is approaching its SLA deadline within the specified hours threshold.
     */
    public boolean isApproachingDeadline(LocalDateTime now, int hoursThreshold) {
        if (isResolved() || isResponseOverdue(now) || isResolutionOverdue(now)) {
            return false;
        }
        // Approaching response deadline
        if (!isAcknowledged() && responseDeadline != null) {
            LocalDateTime warningStart = responseDeadline.minusHours(hoursThreshold);
            if (!now.isBefore(warningStart) && now.isBefore(responseDeadline)) {
                return true;
            }
        }
        // Approaching resolution deadline
        if (resolutionDeadline != null) {
            LocalDateTime warningStart = resolutionDeadline.minusHours(hoursThreshold);
            if (!now.isBefore(warningStart) && now.isBefore(resolutionDeadline)) {
                return true;
            }
        }
        return false;
    }

    public boolean isApproachingDeadline() {
        return isApproachingDeadline(LocalDateTime.now(), 6);
    }

    /**
     * Returns a human-readable SLA summary label for administration.
     */
    public String getSlaStatusString(LocalDateTime now) {
        if (isResolved()) {
            if (resolvedAt != null && resolutionDeadline != null && resolvedAt.isAfter(resolutionDeadline)) {
                return "Resolved (Late)";
            }
            return "Resolved (Within SLA)";
        }
        if (isResolutionOverdue(now)) {
            return "Resolution Overdue";
        }
        if (isResponseOverdue(now)) {
            return "Response Overdue";
        }
        if (isApproachingDeadline(now, 6)) {
            return "Approaching Deadline";
        }
        return "On Track";
    }

    public String getSlaStatusString() {
        return getSlaStatusString(LocalDateTime.now());
    }

    public ComplaintPriority getPriority() {
        return priority != null ? priority : ComplaintPriority.MEDIUM;
    }

    public void setPriority(ComplaintPriority priority) {
        this.priority = (priority != null) ? priority : ComplaintPriority.MEDIUM;
    }

    /**
     * Returns masked student email for public display (e.g. s***@college.edu)
     * preventing disclosure of personal email addresses on public pages.
     */
    public String getMaskedStudentEmail() {
        if (studentEmail == null || studentEmail.isBlank()) {
            return null;
        }
        String trimmed = studentEmail.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? trimmed.substring(atIndex) : "");
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(atIndex);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getDeletionReason() {
        return deletionReason;
    }

    public void setDeletionReason(String deletionReason) {
        this.deletionReason = deletionReason;
    }
}

