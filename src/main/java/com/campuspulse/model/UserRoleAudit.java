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
 * Immutable audit entity recording administrative role transitions for user accounts.
 * Captures who performed the change, target account, old role, new role, timestamp, and rationale.
 */
@Entity
@Table(name = "user_role_audits", indexes = {
        @Index(name = "idx_user_role_audit_target_email", columnList = "target_email"),
        @Index(name = "idx_user_role_audit_changed_at", columnList = "changed_at"),
        @Index(name = "idx_user_role_audit_target_user_id", columnList = "target_user_id")
})
public class UserRoleAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_email", nullable = false, length = 150)
    private String targetEmail;

    @Column(name = "old_role", nullable = false, length = 30)
    private String oldRole;

    @Column(name = "new_role", nullable = false, length = 30)
    private String newRole;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    public UserRoleAudit() {
    }

    public UserRoleAudit(Long targetUserId, String targetEmail, String oldRole, String newRole,
                         String changedBy, String reason) {
        this.targetUserId = targetUserId;
        this.targetEmail = (targetEmail != null) ? targetEmail.trim().toLowerCase() : null;
        this.oldRole = oldRole;
        this.newRole = newRole;
        this.changedBy = changedBy;
        this.changedAt = LocalDateTime.now();
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getTargetEmail() {
        return targetEmail;
    }

    public String getOldRole() {
        return oldRole;
    }

    public String getNewRole() {
        return newRole;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getReason() {
        return reason;
    }
}
