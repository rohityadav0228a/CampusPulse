package com.campuspulse.service;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.repository.ComplaintRepository;
import com.campuspulse.repository.ComplaintStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing student complaint lifecycle, reference ID generation,
 * persistence, status updates, and immutable audit history logging.
 */
@Service
public class ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    public static final List<String> ALLOWED_CATEGORIES = List.of(
            "Academic Affairs",
            "Hostel & Residence",
            "Infrastructure & Labs",
            "Library & Study Spaces",
            "Canteen & Dining",
            "Transit & Campus Grounds"
    );

    public static final List<String> ALLOWED_DEPARTMENTS = List.of(
            "Academic Affairs Division",
            "Hostel Administration",
            "Campus Facilities & Works",
            "Central Library Committee",
            "Canteen Committee & Food Services",
            "Security & Transport Office"
    );

    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository complaintStatusHistoryRepository;
    private final NotificationService notificationService;
    private final com.campuspulse.config.SlaProperties slaProperties;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository) {
        this(complaintRepository, complaintStatusHistoryRepository, null, new com.campuspulse.config.SlaProperties());
    }

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository,
            NotificationService notificationService) {
        this(complaintRepository, complaintStatusHistoryRepository, notificationService, new com.campuspulse.config.SlaProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository,
            NotificationService notificationService,
            com.campuspulse.config.SlaProperties slaProperties) {
        this.complaintRepository = complaintRepository;
        this.complaintStatusHistoryRepository = complaintStatusHistoryRepository;
        this.notificationService = notificationService;
        this.slaProperties = (slaProperties != null) ? slaProperties : new com.campuspulse.config.SlaProperties();
    }

    public List<String> getAllowedCategories() {
        return ALLOWED_CATEGORIES;
    }

    public List<String> getAllowedDepartments() {
        return ALLOWED_DEPARTMENTS;
    }

    /**
     * Validates and saves a new student complaint to the MySQL database along with its
     * initial SUBMITTED audit history record in the same atomic transaction.
     *
     * @param form validated complaint form inputs
     * @return the persisted Complaint entity
     */
    @Transactional
    public Complaint createComplaint(ComplaintForm form) {
        if (!ALLOWED_CATEGORIES.contains(form.getCategory())) {
            throw new IllegalArgumentException("Invalid category: " + form.getCategory());
        }
        if (!ALLOWED_DEPARTMENTS.contains(form.getDepartment())) {
            throw new IllegalArgumentException("Invalid department: " + form.getDepartment());
        }

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String referenceId = generateUniqueReferenceId();

                LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());

                Complaint complaint = new Complaint();
                complaint.setReferenceId(referenceId);
                complaint.setTitle(form.getTitle().trim());
                complaint.setDescription(form.getDescription().trim());
                complaint.setCategory(form.getCategory().trim());
                complaint.setDepartment(form.getDepartment().trim());
                if (form.getStudentEmail() != null && !form.getStudentEmail().trim().isEmpty()) {
                    complaint.setStudentEmail(form.getStudentEmail().trim());
                }
                complaint.setStatus(ComplaintStatus.SUBMITTED);
                complaint.setCreatedAt(now);
                complaint.setResponseDeadline(now.plusHours(slaProperties.getDefaultResponseHours()));
                complaint.setResolutionDeadline(now.plusHours(slaProperties.getDefaultResolutionHours()));
                complaint.setEscalationStage(com.campuspulse.model.SlaEscalationStage.NONE);
                complaint.setAcknowledgedAt(null);
                complaint.setResolvedAt(null);

                Complaint saved = complaintRepository.save(complaint);

                // Initial audit record explicitly attributed to STUDENT
                ComplaintStatusHistory initialHistory = new ComplaintStatusHistory(
                        saved,
                        null,
                        ComplaintStatus.SUBMITTED,
                        "STUDENT",
                        "Initial grievance submission by student."
                );
                complaintStatusHistoryRepository.save(initialHistory);

                log.info("Successfully registered complaint id={} with referenceId={} and initial status audit record",
                        saved.getId(), saved.getReferenceId());
                return saved;
            } catch (DataIntegrityViolationException ex) {
                log.warn("Reference ID collision encountered during save attempt {} of {}", attempt, maxRetries);
                if (attempt == maxRetries) {
                    log.error("Exhausted retries attempting to generate unique reference ID", ex);
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("Failed to generate unique complaint reference ID.");
    }

    /**
     * Fetches all registered complaints ordered by creation timestamp descending.
     *
     * @return list of actual complaints from the database
     */
    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Retrieves complaints optionally filtered by status and/or category.
     *
     * @param status optional status filter
     * @param category optional category filter
     * @return list of matching complaints, ordered newest first
     */
    @Transactional(readOnly = true)
    public List<Complaint> getFilteredComplaints(ComplaintStatus status, String category) {
        return getFilteredComplaints(status, category, null);
    }

    /**
     * Retrieves complaints optionally filtered by status, category, and/or SLA status.
     *
     * @param status optional status filter
     * @param category optional category filter
     * @param slaStatus optional SLA status filter (OVERDUE, RESPONSE_OVERDUE, RESOLUTION_OVERDUE, APPROACHING_DEADLINE, ON_TRACK)
     * @return list of matching complaints, ordered newest first
     */
    @Transactional(readOnly = true)
    public List<Complaint> getFilteredComplaints(ComplaintStatus status, String category, String slaStatus) {
        List<Complaint> complaints;
        boolean hasStatus = (status != null);
        boolean hasCategory = (category != null && !category.isBlank());

        if (hasStatus && hasCategory) {
            complaints = complaintRepository.findAllByStatusAndCategoryOrderByCreatedAtDesc(status, category.trim());
        } else if (hasStatus) {
            complaints = complaintRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else if (hasCategory) {
            complaints = complaintRepository.findAllByCategoryOrderByCreatedAtDesc(category.trim());
        } else {
            complaints = complaintRepository.findAllByOrderByCreatedAtDesc();
        }

        if (slaStatus == null || slaStatus.isBlank() || "ALL".equalsIgnoreCase(slaStatus.trim())) {
            return complaints;
        }

        LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());
        String normalized = slaStatus.trim().toUpperCase(java.util.Locale.ROOT);

        return complaints.stream()
                .filter(c -> switch (normalized) {
                    case "OVERDUE" -> c.isResponseOverdue(now) || c.isResolutionOverdue(now);
                    case "RESPONSE_OVERDUE" -> c.isResponseOverdue(now);
                    case "RESOLUTION_OVERDUE" -> c.isResolutionOverdue(now);
                    case "APPROACHING_DEADLINE" -> c.isApproachingDeadline(now, slaProperties.getApproachingDeadlineHours());
                    case "ON_TRACK" -> !c.isResolved() && !c.isResponseOverdue(now) && !c.isResolutionOverdue(now)
                            && !c.isApproachingDeadline(now, slaProperties.getApproachingDeadlineHours());
                    case "MET" -> c.isResolved();
                    default -> true;
                })
                .toList();
    }

    /**
     * Calculates summary metrics for total complaints and breakdown by status.
     *
     * @return map containing count totals keyed by "TOTAL" and status names
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", complaintRepository.count());
        for (ComplaintStatus status : ComplaintStatus.values()) {
            counts.put(status.name(), complaintRepository.countByStatus(status));
        }
        return counts;
    }

    /**
     * Calculates summary counts for SLA compliance, overdue response, overdue resolution, and approaching deadlines.
     *
     * @return map containing SLA counts
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSlaCounts() {
        LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());
        long responseOverdue = complaintRepository.countResponseOverdue(now);
        long resolutionOverdue = complaintRepository.countResolutionOverdue(now);
        LocalDateTime threshold = now.plusHours(slaProperties.getApproachingDeadlineHours());
        long approaching = complaintRepository.countApproachingDeadline(now, threshold);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL_OVERDUE", responseOverdue + resolutionOverdue);
        counts.put("RESPONSE_OVERDUE", responseOverdue);
        counts.put("RESOLUTION_OVERDUE", resolutionOverdue);
        counts.put("APPROACHING", approaching);
        return counts;
    }

    /**
     * Updates the status of an existing complaint using the authenticated principal's username.
     *
     * @param referenceId unique reference identifier
     * @param newStatus updated lifecycle status
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updateComplaintStatus(String referenceId, ComplaintStatus newStatus) {
        return updateComplaintStatus(referenceId, newStatus, resolveCurrentActorUsername(), null);
    }

    /**
     * Updates the status of an existing complaint and saves an immutable audit record in the
     * exact same database transaction. Rolls back both operations if either fails.
     * Sets acknowledgedAt timestamp upon first departure from SUBMITTED, and resolvedAt upon reaching RESOLVED.
     * Prevents unchanged / duplicate status transitions.
     *
     * @param referenceId unique reference identifier
     * @param newStatus updated lifecycle status
     * @param actorUsername username of the authenticated administrator performing the change
     * @param note optional note explaining the resolution progress or rationale
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updateComplaintStatus(String referenceId, ComplaintStatus newStatus, String actorUsername, String note) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }
        Complaint complaint = complaintRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + referenceId));

        ComplaintStatus previousStatus = complaint.getStatus();
        if (previousStatus == newStatus) {
            log.warn("Attempted status update for {} to identical status {}. Rejecting duplicate transition.", referenceId, newStatus);
            throw new IllegalArgumentException("Complaint is already in " + newStatus.getDisplayName() + " status. No status change was recorded.");
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        String effectiveNote = (note != null && !note.isBlank()) ? note.trim() : null;

        LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());
        complaint.setStatus(newStatus);
        if (complaint.getAcknowledgedAt() == null && newStatus != ComplaintStatus.SUBMITTED) {
            complaint.setAcknowledgedAt(now);
        }
        if (newStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(now);
        } else if (previousStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(null);
        }

        Complaint updated = complaintRepository.save(complaint);

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                updated,
                previousStatus,
                newStatus,
                effectiveActor,
                effectiveNote
        );
        complaintStatusHistoryRepository.save(historyEntry);

        if (notificationService != null) {
            notificationService.queueAndDispatchStatusUpdate(updated, newStatus);
        }

        log.info("Administrator '{}' updated complaint {} status from {} to {} (note: {})",
                effectiveActor, referenceId, previousStatus, newStatus, effectiveNote);
        return updated;
    }

    /**
     * Retrieves the complete chronological status audit history for a complaint.
     *
     * @param referenceId unique reference identifier
     * @return list of status history entries ordered chronologically (oldest first)
     */
    @Transactional(readOnly = true)
    public List<ComplaintStatusHistory> getStatusHistory(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Collections.emptyList();
        }
        return complaintStatusHistoryRepository.findByComplaint_ReferenceIdOrderByCreatedAtAsc(referenceId.trim());
    }

    /**
     * Retrieves a complaint by its unique reference ID.
     *
     * @param referenceId human-readable reference identifier
     * @return Optional containing the complaint if found
     */
    @Transactional(readOnly = true)
    public Optional<Complaint> getByReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Optional.empty();
        }
        return complaintRepository.findByReferenceId(referenceId.trim());
    }

    /**
     * Resolves the current actor username from the Spring Security context, falling back
     * safely to "ADMIN" if called outside an active HTTP session (e.g., in batch processing).
     *
     * @return authenticated actor username
     */
    private String resolveCurrentActorUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return "ADMIN";
    }

    /**
     * Generates a unique, human-readable reference ID in the format CP-YYYY-XXXXXXXX.
     * Verifies against existing records in the database to avoid collisions.
     *
     * @return unique reference ID
     */
    private String generateUniqueReferenceId() {
        int year = LocalDate.now().getYear();
        String candidate;
        int attempts = 0;

        do {
            attempts++;
            if (attempts > 10) {
                // Extended entropy fallback
                candidate = String.format("CP-%d-%s", year, randomToken(10));
                break;
            }
            candidate = String.format("CP-%d-%s", year, randomToken(8));
        } while (complaintRepository.existsByReferenceId(candidate));

        return candidate;
    }

    private String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC_CHARS.charAt(RANDOM.nextInt(ALPHANUMERIC_CHARS.length())));
        }
        return sb.toString();
    }
}
