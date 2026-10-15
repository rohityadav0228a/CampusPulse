package com.campuspulse.repository;

import com.campuspulse.model.ComplaintFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for student resolution feedback on resolved complaints.
 */
@Repository
public interface ComplaintFeedbackRepository extends JpaRepository<ComplaintFeedback, Long> {

    /**
     * Finds feedback for a complaint by its numeric database ID.
     *
     * @param complaintId complaint primary key
     * @return Optional containing feedback if present
     */
    Optional<ComplaintFeedback> findByComplaintId(Long complaintId);

    /**
     * Finds feedback for a complaint by its alphanumeric reference ID.
     *
     * @param referenceId unique complaint reference identifier
     * @return Optional containing feedback if present
     */
    Optional<ComplaintFeedback> findByComplaintReferenceId(String referenceId);

    /**
     * Checks if feedback exists for a given complaint ID.
     *
     * @param complaintId complaint primary key
     * @return true if feedback has already been submitted
     */
    boolean existsByComplaintId(Long complaintId);

    /**
     * Checks if feedback exists for a given complaint reference ID.
     *
     * @param referenceId unique complaint reference identifier
     * @return true if feedback has already been submitted
     */
    boolean existsByComplaintReferenceId(String referenceId);

    /**
     * Computes the arithmetic average rating across all feedback submissions.
     *
     * @return average rating or null if no feedback exists
     */
    @Query("SELECT AVG(f.rating) FROM ComplaintFeedback f")
    Double findAverageRating();

    /**
     * Aggregates count of feedback submissions grouped by rating (1 to 5).
     *
     * @return list of Object arrays where [0] is rating (Integer) and [1] is count (Long)
     */
    @Query("SELECT f.rating, COUNT(f) FROM ComplaintFeedback f GROUP BY f.rating")
    List<Object[]> countByRatingGrouped();

    /**
     * Retrieves all feedback submissions ordered by creation timestamp descending.
     *
     * @return list of feedback ordered newest first
     */
    List<ComplaintFeedback> findAllByOrderByCreatedAtDesc();
}
