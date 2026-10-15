package com.campuspulse.service;

import com.campuspulse.dto.ComplaintFeedbackForm;
import com.campuspulse.dto.analytics.FeedbackMetrics;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintFeedback;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.repository.ComplaintFeedbackRepository;
import com.campuspulse.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing student resolution feedback on resolved complaints.
 * Strictly enforces complaint status (RESOLVED only), ownership (verified student owner only),
 * non-deleted status, and one feedback per complaint via service logic and database constraints.
 */
@Service
public class ComplaintFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintFeedbackService.class);

    private final ComplaintFeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;

    public ComplaintFeedbackService(ComplaintFeedbackRepository feedbackRepository,
                                  ComplaintRepository complaintRepository) {
        this.feedbackRepository = feedbackRepository;
        this.complaintRepository = complaintRepository;
    }

    /**
     * Submits student resolution feedback for a resolved complaint.
     * Enforces ownership, resolved status, non-deleted condition, rating range (1-5),
     * and duplicate submission prevention.
     *
     * @param referenceId unique reference identifier of the complaint
     * @param authenticatedStudentEmail email of the currently authenticated student
     * @param form validated feedback submission DTO
     * @return persisted ComplaintFeedback entity
     * @throws IllegalArgumentException if input parameters are invalid or complaint not found
     * @throws IllegalStateException if complaint is unresolved, deleted, or duplicate feedback attempted
     * @throws AccessDeniedException if authenticated user does not own the complaint
     */
    @Transactional
    public ComplaintFeedback submitFeedback(String referenceId, String authenticatedStudentEmail, ComplaintFeedbackForm form) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Complaint reference ID cannot be blank.");
        }
        if (authenticatedStudentEmail == null || authenticatedStudentEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated student email is required.");
        }
        if (form == null) {
            throw new IllegalArgumentException("Feedback form cannot be null.");
        }
        if (form.getRating() == null || form.getRating() < 1 || form.getRating() > 5) {
            throw new IllegalArgumentException("Feedback rating must be an integer between 1 and 5.");
        }
        if (form.getComment() != null && form.getComment().trim().length() > 1000) {
            throw new IllegalArgumentException("Feedback comment cannot exceed 1,000 characters.");
        }

        String trimmedRef = referenceId.trim();
        Complaint complaint = complaintRepository.findByReferenceId(trimmedRef)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + trimmedRef));

        // Reject feedback on archived / soft-deleted complaints
        if (complaint.isDeleted()) {
            log.warn("Feedback submission rejected for deleted complaint {}", trimmedRef);
            throw new IllegalStateException("Feedback cannot be submitted for archived or deleted complaints.");
        }

        // Enforce RESOLVED status
        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            log.warn("Feedback submission rejected for unresolved complaint {} in status {}", trimmedRef, complaint.getStatus());
            throw new IllegalStateException("Feedback can only be submitted for resolved complaints. Current status: " + complaint.getStatus().getDisplayName());
        }

        // Enforce ownership: only the authenticated complaint owner may submit feedback
        String complaintOwnerEmail = complaint.getStudentEmail();
        if (complaintOwnerEmail == null || !complaintOwnerEmail.trim().equalsIgnoreCase(authenticatedStudentEmail.trim())) {
            log.warn("Unauthorized feedback attempt on complaint {} by user '{}' (owner is '{}')",
                    trimmedRef, authenticatedStudentEmail, complaintOwnerEmail);
            throw new AccessDeniedException("You are not authorized to submit feedback for this complaint.");
        }

        // Enforce duplicate prevention at service layer
        if (feedbackRepository.existsByComplaintId(complaint.getId())) {
            log.warn("Duplicate feedback rejected at service layer for complaint {}", trimmedRef);
            throw new IllegalStateException("Feedback has already been submitted for this complaint.");
        }

        ComplaintFeedback feedback = new ComplaintFeedback(
                complaint,
                authenticatedStudentEmail.trim().toLowerCase(),
                form.getRating(),
                (form.getComment() != null && !form.getComment().isBlank()) ? form.getComment().trim() : null
        );

        try {
            ComplaintFeedback saved = feedbackRepository.save(feedback);
            log.info("Feedback successfully submitted for complaint {}: rating={}", trimmedRef, saved.getRating());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Guard against race conditions and concurrent submissions via database unique constraint
            log.warn("Duplicate feedback rejected by database unique constraint for complaint {}", trimmedRef);
            throw new IllegalStateException("Feedback has already been submitted for this complaint.");
        }
    }

    /**
     * Retrieves resolution feedback for a given complaint ID, if present.
     *
     * @param complaintId database primary key of complaint
     * @return Optional containing feedback
     */
    @Transactional(readOnly = true)
    public Optional<ComplaintFeedback> getFeedbackForComplaint(Long complaintId) {
        if (complaintId == null) {
            return Optional.empty();
        }
        return feedbackRepository.findByComplaintId(complaintId);
    }

    /**
     * Retrieves resolution feedback for a given complaint reference ID, if present.
     *
     * @param referenceId unique reference identifier
     * @return Optional containing feedback
     */
    @Transactional(readOnly = true)
    public Optional<ComplaintFeedback> getFeedbackForComplaint(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Optional.empty();
        }
        return feedbackRepository.findByComplaintReferenceId(referenceId.trim());
    }

    /**
     * Checks if feedback exists for a complaint by reference ID.
     *
     * @param referenceId unique reference identifier
     * @return true if feedback exists
     */
    @Transactional(readOnly = true)
    public boolean hasFeedback(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return false;
        }
        return feedbackRepository.existsByComplaintReferenceId(referenceId.trim());
    }

    /**
     * Computes aggregate resolution feedback metrics for administrative analytics.
     * Computes total submissions, average star rating, and distribution across 1 to 5 stars.
     * Keeps private student comments strictly hidden.
     *
     * @return FeedbackMetrics containing aggregate statistics
     */
    @Transactional(readOnly = true)
    public FeedbackMetrics getFeedbackMetrics() {
        long total = feedbackRepository.count();
        if (total == 0) {
            return FeedbackMetrics.empty();
        }

        Double avgRating = feedbackRepository.findAverageRating();
        double effectiveAvg = (avgRating != null) ? avgRating : 0.0;
        String formattedAvg = String.format(Locale.US, "%.1f", effectiveAvg);

        List<Object[]> distributionRows = feedbackRepository.countByRatingGrouped();
        Map<Integer, Long> rawCounts = new HashMap<>();
        for (Object[] row : distributionRows) {
            Integer rating = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            rawCounts.put(rating, count);
        }

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        Map<Integer, Double> percentages = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            long count = rawCounts.getOrDefault(stars, 0L);
            distribution.put(stars, count);
            double pct = (total > 0) ? (count * 100.0 / total) : 0.0;
            percentages.put(stars, Math.round(pct * 10.0) / 10.0);
        }

        return new FeedbackMetrics(total, effectiveAvg, formattedAvg, distribution, percentages);
    }
}
