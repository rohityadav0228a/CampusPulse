package com.campuspulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Immutable audit entity recording administrative deletion/archival of resolved grievances.
 * Permanently preserves the complaint reference ID, title, resolution note, department, category,
 * acting administrator, and deletion rationale, surviving the operation permanently.
 */
@Entity
@Table(name = "complaint_deletion_audits", indexes = {
        @Index(name = "idx_deletion_audit_ref_id", columnList = "reference_id"),
        @Index(name = "idx_deletion_audit_deleted_at", columnList = "deleted_at")
})
public class ComplaintDeletionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id")
    private Long complaintId;

    @Column(name = "reference_id", nullable = false, length = 32)
    private String referenceId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "student_email", length = 150)
    private String studentEmail;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "deleted_by", nullable = false, length = 100)
    private String deletedBy;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private LocalDateTime deletedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    public ComplaintDeletionAudit() {
    }

    public ComplaintDeletionAudit(Long complaintId, String referenceId, String title, String category, String department,
                                  String studentEmail, String resolutionNote, String deletedBy,
                                  LocalDateTime deletedAt, String reason) {
        this.complaintId = complaintId;
        this.referenceId = referenceId;
        this.title = title;
        this.category = category;
        this.department = department;
        this.studentEmail = studentEmail;
        this.resolutionNote = resolutionNote;
        this.deletedBy = deletedBy;
        this.deletedAt = (deletedAt != null) ? deletedAt : LocalDateTime.now();
        this.reason = reason;
    }

    public ComplaintDeletionAudit(Long complaintId, String referenceId, String title, String category, String department,
                                  String studentEmail, String deletedBy, String reason, LocalDateTime deletedAt) {
        this(complaintId, referenceId, title, category, department, studentEmail, null, deletedBy, deletedAt, reason);
    }

    public ComplaintDeletionAudit(String referenceId, String title, String category, String department,
                                  String studentEmail, String resolutionNote, String deletedBy,
                                  LocalDateTime deletedAt, String reason) {
        this(null, referenceId, title, category, department, studentEmail, resolutionNote, deletedBy, deletedAt, reason);
    }

    @PrePersist
    protected void onCreate() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getDepartment() {
        return department;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getReason() {
        return reason;
    }

    public String getDeletionReason() {
        return reason;
    }
}
