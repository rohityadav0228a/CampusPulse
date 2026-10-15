package com.campuspulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * JPA entity representing student resolution feedback for a resolved complaint.
 * Stores 1-5 star rating, optional written comments, verified student owner association,
 * and immutable submission timestamp.
 * Enforces one feedback record per complaint via database-level unique constraint.
 */
@Entity
@Table(name = "complaint_feedback", indexes = {
        @Index(name = "idx_feedback_complaint_id", columnList = "complaint_id", unique = true),
        @Index(name = "idx_feedback_student_email", columnList = "student_email"),
        @Index(name = "idx_feedback_created_at", columnList = "created_at")
})
public class ComplaintFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Complaint complaint;

    @Column(name = "student_email", nullable = false, length = 150)
    private String studentEmail;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ComplaintFeedback() {
    }

    public ComplaintFeedback(Complaint complaint, String studentEmail, Integer rating, String comment) {
        this.complaint = complaint;
        this.studentEmail = (studentEmail != null) ? studentEmail.trim().toLowerCase() : null;
        this.rating = rating;
        this.comment = (comment != null && !comment.isBlank()) ? comment.trim() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.studentEmail != null) {
            this.studentEmail = this.studentEmail.trim().toLowerCase();
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

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = (studentEmail != null) ? studentEmail.trim().toLowerCase() : null;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = (comment != null && !comment.isBlank()) ? comment.trim() : null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
