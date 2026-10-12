package com.campuspulse.repository;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Executes database-level paginated search combined with status, category, and SLA deadline filters.
     * Evaluates case-insensitive keyword search across referenceId, title, description, and studentEmail.
     * Uses parameterized query to prevent SQL injection and executes count query for total page calculation.
     *
     * @param searchTerm case-insensitive keyword matching referenceId, title, description, or studentEmail (null/empty for all)
     * @param status lifecycle status to filter (null for all)
     * @param category campus category to filter (null/empty for all)
     * @param slaStatus normalized SLA status code (null/empty/'ALL' for no SLA filter)
     * @param now current evaluation timestamp in institutional timezone
     * @param threshold approaching deadline upper threshold timestamp
     * @param pageable Spring Data pagination and sorting specification
     * @return page of matching complaints
     */
    @Query(value = "SELECT c FROM Complaint c WHERE " +
            "(:searchTerm IS NULL OR :searchTerm = '' OR " +
            " LOWER(c.referenceId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " (c.studentEmail IS NOT NULL AND LOWER(c.studentEmail) LIKE LOWER(CONCAT('%', :searchTerm, '%')))) AND " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:category IS NULL OR :category = '' OR c.category = :category) AND " +
            "(:department IS NULL OR :department = '' OR c.department = :department) AND " +
            "(:priority IS NULL OR c.priority = :priority) AND " +
            "(:slaStatus IS NULL OR :slaStatus = '' OR :slaStatus = 'ALL' OR " +
            " (:slaStatus = 'RESPONSE_OVERDUE' AND c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline < :now) OR " +
            " (:slaStatus = 'RESOLUTION_OVERDUE' AND c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND c.resolutionDeadline < :now) OR " +
            " (:slaStatus = 'OVERDUE' AND ((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline < :now) OR (c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND c.resolutionDeadline < :now))) OR " +
            " (:slaStatus = 'APPROACHING_DEADLINE' AND c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND ((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline >= :now AND c.responseDeadline <= :threshold) OR (c.resolutionDeadline >= :now AND c.resolutionDeadline <= :threshold))) OR " +
            " (:slaStatus = 'ON_TRACK' AND c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND NOT (c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline < :now) AND NOT (c.resolutionDeadline < :now) AND NOT ((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline >= :now AND c.responseDeadline <= :threshold) OR (c.resolutionDeadline >= :now AND c.resolutionDeadline <= :threshold))) OR " +
            " (:slaStatus = 'MET' AND c.status = com.campuspulse.model.ComplaintStatus.RESOLVED))",
            countQuery = "SELECT COUNT(c) FROM Complaint c WHERE " +
            "(:searchTerm IS NULL OR :searchTerm = '' OR " +
            " LOWER(c.referenceId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " (c.studentEmail IS NOT NULL AND LOWER(c.studentEmail) LIKE LOWER(CONCAT('%', :searchTerm, '%')))) AND " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:category IS NULL OR :category = '' OR c.category = :category) AND " +
            "(:department IS NULL OR :department = '' OR c.department = :department) AND " +
            "(:priority IS NULL OR c.priority = :priority) AND " +
            "(:slaStatus IS NULL OR :slaStatus = '' OR :slaStatus = 'ALL' OR " +
            " (:slaStatus = 'RESPONSE_OVERDUE' AND c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline < :now) OR " +
            " (:slaStatus = 'RESOLUTION_OVERDUE' AND c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND c.resolutionDeadline < :now) OR " +
            " (:slaStatus = 'OVERDUE' AND ((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline < :now) OR (c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND c.resolutionDeadline < :now))) OR " +
            " (:slaStatus = 'APPROACHING_DEADLINE' AND c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND ((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline >= :now AND c.responseDeadline <= :threshold) OR (c.resolutionDeadline >= :now AND c.resolutionDeadline <= :threshold))) OR " +
            " (:slaStatus = 'ON_TRACK' AND c.status != com.campuspulse.model.ComplaintStatus.RESOLVED AND NOT (c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline < :now) AND NOT (c.resolutionDeadline < :now) AND NOT ((c.status = com.campuspulse.model.ComplaintStatus.SUBMITTED AND c.acknowledgedAt IS NULL AND c.responseDeadline >= :now AND c.responseDeadline <= :threshold) OR (c.resolutionDeadline >= :now AND c.resolutionDeadline <= :threshold))) OR " +
            " (:slaStatus = 'MET' AND c.status = com.campuspulse.model.ComplaintStatus.RESOLVED))")
    Page<Complaint> searchWithFilters(
            @Param("searchTerm") String searchTerm,
            @Param("status") ComplaintStatus status,
            @Param("category") String category,
            @Param("department") String department,
            @Param("priority") ComplaintPriority priority,
            @Param("slaStatus") String slaStatus,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable);

    default Page<Complaint> searchWithFilters(
            String searchTerm,
            ComplaintStatus status,
            String category,
            String slaStatus,
            LocalDateTime now,
            LocalDateTime threshold,
            Pageable pageable) {
        return searchWithFilters(searchTerm, status, category, null, null, slaStatus, now, threshold, pageable);
    }
}


