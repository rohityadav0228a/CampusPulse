package com.campuspulse.repository;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    /**
     * Finds all complaints ordered by creation timestamp in descending order (newest first).
     *
     * @return list of complaints ordered newest first
     */
    List<Complaint> findAllByOrderByCreatedAtDesc();

    /**
     * Finds all complaints filtered by status, ordered newest first.
     *
     * @param status complaint status to filter
     * @return list of matching complaints
     */
    List<Complaint> findAllByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    /**
     * Finds all complaints filtered by category, ordered newest first.
     *
     * @param category category to filter
     * @return list of matching complaints
     */
    List<Complaint> findAllByCategoryOrderByCreatedAtDesc(String category);

    /**
     * Finds all complaints filtered by both status and category, ordered newest first.
     *
     * @param status complaint status to filter
     * @param category category to filter
     * @return list of matching complaints
     */
    List<Complaint> findAllByStatusAndCategoryOrderByCreatedAtDesc(ComplaintStatus status, String category);

    /**
     * Counts complaints by status.
     *
     * @param status status to count
     * @return total count of complaints matching the status
     */
    long countByStatus(ComplaintStatus status);

    /**
     * Finds a complaint by its unique human-readable reference identifier.
     *
     * @param referenceId unique reference ID
     * @return Optional containing the complaint if found
     */
    Optional<Complaint> findByReferenceId(String referenceId);

    /**
     * Checks if a reference ID already exists in the repository.
     *
     * @param referenceId unique reference ID to verify
     * @return true if reference ID exists, false otherwise
     */
    boolean existsByReferenceId(String referenceId);

    /**
     * Counts total complaints matching optional status, category, and date filters using database aggregation.
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Complaint c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:category IS NULL OR c.category = :category) AND " +
            "(:startDate IS NULL OR c.createdAt >= :startDate)")
    long countWithFilters(
            @org.springframework.data.repository.query.Param("status") ComplaintStatus status,
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

    /**
     * Counts complaints grouped by status matching optional category and date filters using database aggregation.
     * Returns rows of [ComplaintStatus, Long count].
     */
    @org.springframework.data.jpa.repository.Query("SELECT c.status, COUNT(c) FROM Complaint c WHERE " +
            "(:category IS NULL OR c.category = :category) AND " +
            "(:startDate IS NULL OR c.createdAt >= :startDate) " +
            "GROUP BY c.status")
    List<Object[]> countGroupByStatus(
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

    /**
     * Counts complaints grouped by campus category matching optional status and date filters using database aggregation.
     * Returns rows of [String category, Long count].
     */
    @org.springframework.data.jpa.repository.Query("SELECT c.category, COUNT(c) FROM Complaint c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:category IS NULL OR c.category = :category) AND " +
            "(:startDate IS NULL OR c.createdAt >= :startDate) " +
            "GROUP BY c.category ORDER BY COUNT(c) DESC")
    List<Object[]> countGroupByCategory(
            @org.springframework.data.repository.query.Param("status") ComplaintStatus status,
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

    /**
     * Counts complaints grouped by campus department matching optional status, category, and date filters using database aggregation.
     * Returns rows of [String department, Long count].
     */
    @org.springframework.data.jpa.repository.Query("SELECT c.department, COUNT(c) FROM Complaint c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:category IS NULL OR c.category = :category) AND " +
            "(:startDate IS NULL OR c.createdAt >= :startDate) " +
            "GROUP BY c.department ORDER BY COUNT(c) DESC")
    List<Object[]> countGroupByDepartment(
            @org.springframework.data.repository.query.Param("status") ComplaintStatus status,
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

    /**
     * Counts complaints grouped by submission date matching optional filters using database aggregation.
     * Returns rows of [LocalDate/Date, Long count].
     */
    @org.springframework.data.jpa.repository.Query("SELECT CAST(c.createdAt AS LocalDate), COUNT(c) FROM Complaint c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:category IS NULL OR c.category = :category) AND " +
            "(:startDate IS NULL OR c.createdAt >= :startDate) " +
            "GROUP BY CAST(c.createdAt AS LocalDate) ORDER BY CAST(c.createdAt AS LocalDate) ASC")
    List<Object[]> countDailyTrend(
            @org.springframework.data.repository.query.Param("status") ComplaintStatus status,
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);

    /**
     * Counts complaints created within a specific timestamp window [startDate, endDate).
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Complaint c WHERE " +
            "c.createdAt >= :startDate AND c.createdAt < :endDate")
    long countBetween(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Counts unresolved complaints grouped by campus department in descending order of volume.
     * Returns rows of [String department, Long count].
     */
    @org.springframework.data.jpa.repository.Query("SELECT c.department, COUNT(c) FROM Complaint c WHERE " +
            "c.status IN (:unresolvedStatuses) " +
            "GROUP BY c.department ORDER BY COUNT(c) DESC")
    List<Object[]> countUnresolvedGroupByDepartment(
            @org.springframework.data.repository.query.Param("unresolvedStatuses") List<ComplaintStatus> unresolvedStatuses);

    /**
     * Counts complaints grouped by campus category within a specific timestamp window [startDate, endDate).
     * Returns rows of [String category, Long count].
     */
    @org.springframework.data.jpa.repository.Query("SELECT c.category, COUNT(c) FROM Complaint c WHERE " +
            "c.createdAt >= :startDate AND c.createdAt < :endDate " +
            "GROUP BY c.category ORDER BY COUNT(c) DESC")
    List<Object[]> countGroupByCategoryBetween(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Counts active complaints that have breached their initial response SLA deadline.
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Complaint c WHERE " +
            "c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND " +
            "c.acknowledgedAt IS NULL AND c.responseDeadline < :now")
    long countResponseOverdue(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /**
     * Counts active complaints that have breached their final resolution SLA deadline.
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Complaint c WHERE " +
            "c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND " +
            "c.resolutionDeadline < :now")
    long countResolutionOverdue(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /**
     * Counts active complaints approaching either response or resolution deadline within warning threshold.
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Complaint c WHERE " +
            "c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND " +
            "((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline >= :now AND c.responseDeadline <= :threshold) OR " +
            "(c.resolutionDeadline >= :now AND c.resolutionDeadline <= :threshold))")
    long countApproachingDeadline(
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
            @org.springframework.data.repository.query.Param("threshold") java.time.LocalDateTime threshold);

    /**
     * Finds complaints eligible for Stage 1 Response Escalation.
     */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Complaint c WHERE " +
            "c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND " +
            "c.acknowledgedAt IS NULL AND c.responseDeadline < :now AND " +
            "c.escalationStage = com.campuspulse.model.SlaEscalationStage.NONE")
    List<Complaint> findResponseOverdueCandidates(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /**
     * Finds complaints eligible for Stage 2 Resolution Escalation.
     */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Complaint c WHERE " +
            "c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND " +
            "c.resolutionDeadline < :now AND " +
            "c.escalationStage != com.campuspulse.model.SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE")
    List<Complaint> findResolutionOverdueCandidates(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}


