package com.campuspulse.service;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintDeletionAudit;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.repository.ComplaintDeletionAuditRepository;
import com.campuspulse.repository.ComplaintRepository;
import com.campuspulse.repository.ComplaintStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final ComplaintDeletionAuditRepository complaintDeletionAuditRepository;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository) {
        this(complaintRepository, complaintStatusHistoryRepository, null, new com.campuspulse.config.SlaProperties(), null);
    }

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository,
            NotificationService notificationService) {
        this(complaintRepository, complaintStatusHistoryRepository, notificationService, new com.campuspulse.config.SlaProperties(), null);
    }

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository,
            NotificationService notificationService,
            com.campuspulse.config.SlaProperties slaProperties) {
        this(complaintRepository, complaintStatusHistoryRepository, notificationService, slaProperties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository complaintStatusHistoryRepository,
            NotificationService notificationService,
            com.campuspulse.config.SlaProperties slaProperties,
            ComplaintDeletionAuditRepository complaintDeletionAuditRepository) {
        this.complaintRepository = complaintRepository;
        this.complaintStatusHistoryRepository = complaintStatusHistoryRepository;
        this.notificationService = notificationService;
        this.slaProperties = (slaProperties != null) ? slaProperties : new com.campuspulse.config.SlaProperties();
        this.complaintDeletionAuditRepository = complaintDeletionAuditRepository;
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

                enqueueSubmissionConfirmationSafely(saved);

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
     * Safely triggers submission confirmation notification enqueueing.
     * Uses transaction synchronization when an active transaction is present,
     * ensuring that the complaint transaction has committed before notification processing begins,
     * and guaranteeing that any email queue failure never rolls back the persisted complaint.
     *
     * @param complaint persisted complaint entity
     */
    private void enqueueSubmissionConfirmationSafely(Complaint complaint) {
        if (notificationService == null || complaint == null) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notificationService.queueAndDispatchSubmissionConfirmation(complaint);
                    } catch (Exception ex) {
                        log.error("Failed to enqueue submission confirmation for complaint {} after transaction commit: {}",
                                complaint.getReferenceId(), ex.getMessage(), ex);
                    }
                }
            });
        } else {
            try {
                notificationService.queueAndDispatchSubmissionConfirmation(complaint);
            } catch (Exception ex) {
                log.error("Failed to enqueue submission confirmation for complaint {}: {}",
                        complaint.getReferenceId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Enqueues an initial submission confirmation notification for a complaint.
     * Idempotent: duplicate calls for the same reference ID will not create duplicate emails.
     *
     * @param complaint persisted complaint entity
     * @return persisted NotificationOutbox entry, or null if duplicate or skipped
     */
    @Transactional
    public com.campuspulse.model.NotificationOutbox queueSubmissionConfirmation(Complaint complaint) {
        if (notificationService == null || complaint == null) {
            return null;
        }
        return notificationService.queueAndDispatchSubmissionConfirmation(complaint);
    }

    /**
     * Fetches all registered complaints ordered by creation timestamp descending.
     *
     * @return list of actual complaints from the database
     */
    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
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
            complaints = complaintRepository.findAllByStatusAndCategoryAndDeletedFalseOrderByCreatedAtDesc(status, category.trim());
        } else if (hasStatus) {
            complaints = complaintRepository.findAllByStatusAndDeletedFalseOrderByCreatedAtDesc(status);
        } else if (hasCategory) {
            complaints = complaintRepository.findAllByCategoryAndDeletedFalseOrderByCreatedAtDesc(category.trim());
        } else {
            complaints = complaintRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
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

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_RESOLUTION_NOTE_LENGTH = 1000;

    /**
     * Executes database-level paginated search combined with status, category, and SLA deadline filters.
     * Backwards-compatible overload delegating to the department/priority-aware method.
     */
    @Transactional(readOnly = true)
    public Page<Complaint> searchComplaints(
            String searchTerm,
            ComplaintStatus status,
            String category,
            String slaStatus,
            int page,
            int size) {
        return searchComplaints(searchTerm, status, category, null, null, slaStatus, page, size);
    }

    /**
     * Executes database-level paginated search combined with status, category, and SLA deadline filters
     * using the provided Pageable. Backwards-compatible overload.
     */
    @Transactional(readOnly = true)
    public Page<Complaint> searchComplaints(
            String searchTerm,
            ComplaintStatus status,
            String category,
            String slaStatus,
            Pageable pageable) {
        return searchComplaints(searchTerm, status, category, null, null, slaStatus, pageable);
    }

    /**
     * Executes database-level paginated search combined with status, category, department, priority,
     * and SLA deadline filters.
     * Normalizes page number (zero-based, min 0), page size (clamped between 1 and 100), and ensures
     * deterministic sorting (newest first by createdAt DESC, id DESC).
     *
     * @param searchTerm case-insensitive search keyword (optional, matched across referenceId, title, description, studentEmail)
     * @param status lifecycle status to filter (optional)
     * @param category campus category to filter (optional)
     * @param department campus department to filter (optional)
     * @param priority complaint priority level to filter (optional)
     * @param slaStatus SLA status code (optional: OVERDUE, RESPONSE_OVERDUE, RESOLUTION_OVERDUE, APPROACHING_DEADLINE, ON_TRACK, MET, ALL)
     * @param page zero-based page number (defaults to 0 if negative)
     * @param size page size (defaults to 10, max 100)
     * @return page of matching complaints
     */
    @Transactional(readOnly = true)
    public Page<Complaint> searchComplaints(
            String searchTerm,
            ComplaintStatus status,
            String category,
            String department,
            ComplaintPriority priority,
            String slaStatus,
            int page,
            int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return searchComplaints(searchTerm, status, category, department, priority, slaStatus, pageable);
    }

    /**
     * Executes database-level paginated search combined with status, category, department, priority,
     * and SLA deadline filters using the provided Pageable. Validates and normalizes inputs to preserve safe execution.
     *
     * @param searchTerm case-insensitive search keyword (optional)
     * @param status lifecycle status to filter (optional)
     * @param category campus category to filter (optional)
     * @param department campus department to filter (optional)
     * @param priority complaint priority level to filter (optional)
     * @param slaStatus SLA status code (optional)
     * @param pageable pagination and sorting parameters
     * @return page of matching complaints
     */
    @Transactional(readOnly = true)
    public Page<Complaint> searchComplaints(
            String searchTerm,
            ComplaintStatus status,
            String category,
            String department,
            ComplaintPriority priority,
            String slaStatus,
            Pageable pageable) {

        String effectiveSearch = (searchTerm != null && !searchTerm.isBlank()) ? searchTerm.trim() : null;
        String effectiveCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        String effectiveDepartment = (department != null && !department.isBlank()) ? department.trim() : null;
        String normalizedSla = normalizeSlaStatus(slaStatus);

        Pageable effectivePageable = normalizePageable(pageable);

        LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());
        LocalDateTime threshold = now.plusHours(slaProperties.getApproachingDeadlineHours());

        if (effectiveDepartment != null || priority != null) {
            return complaintRepository.searchWithFilters(
                    effectiveSearch,
                    status,
                    effectiveCategory,
                    effectiveDepartment,
                    priority,
                    normalizedSla,
                    now,
                    threshold,
                    effectivePageable
            );
        } else {
            return complaintRepository.searchWithFilters(
                    effectiveSearch,
                    status,
                    effectiveCategory,
                    normalizedSla,
                    now,
                    threshold,
                    effectivePageable
            );
        }
    }

    private String normalizeSlaStatus(String slaStatus) {
        if (slaStatus == null || slaStatus.isBlank() || "ALL".equalsIgnoreCase(slaStatus.trim())) {
            return null;
        }
        String upper = slaStatus.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (upper) {
            case "OVERDUE", "RESPONSE_OVERDUE", "RESOLUTION_OVERDUE", "APPROACHING_DEADLINE", "ON_TRACK", "MET" -> upper;
            default -> null;
        };
    }

    private Pageable normalizePageable(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, defaultSort);
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort == null || sort.isUnsorted()) {
            sort = defaultSort;
        } else {
            // Ensure stable secondary tie-breaker if id is not included in sort
            boolean hasIdSort = false;
            for (Sort.Order order : sort) {
                if ("id".equalsIgnoreCase(order.getProperty())) {
                    hasIdSort = true;
                    break;
                }
            }
            if (!hasIdSort) {
                sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
            }
        }

        return PageRequest.of(page, size, sort);
    }

    /**
     * Calculates summary metrics for total complaints and breakdown by status.
     *
     * @return map containing count totals keyed by "TOTAL" and status names
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", complaintRepository.countByDeletedFalse());
        for (ComplaintStatus status : ComplaintStatus.values()) {
            counts.put(status.name(), complaintRepository.countByStatusAndDeletedFalse(status));
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
        if (effectiveNote != null && effectiveNote.length() > MAX_RESOLUTION_NOTE_LENGTH) {
            throw new IllegalArgumentException("Resolution note cannot exceed " + MAX_RESOLUTION_NOTE_LENGTH + " characters.");
        }

        LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());
        complaint.setStatus(newStatus);
        if (complaint.getAcknowledgedAt() == null && newStatus != ComplaintStatus.SUBMITTED) {
            complaint.setAcknowledgedAt(now);
        }
        if (newStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(now);
            if (effectiveNote != null) {
                complaint.setResolutionNote(effectiveNote);
            }
        } else if (previousStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(null);
            complaint.setResolutionNote(null);
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
     * Updates or corrects the resolution note of an already-resolved complaint in an authorized, auditable workflow.
     * Validates that the complaint exists, is in RESOLVED status, and the note satisfies length constraints.
     * Records an immutable audit history record with actor attribution and update rationale.
     *
     * @param referenceId unique reference identifier
     * @param resolutionNote updated resolution note
     * @param actorUsername username of the authorized administrator performing the update
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updateResolutionNote(String referenceId, String resolutionNote, String actorUsername) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be blank");
        }
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new IllegalArgumentException("Resolution note cannot be blank");
        }
        String trimmedNote = resolutionNote.trim();
        if (trimmedNote.length() > MAX_RESOLUTION_NOTE_LENGTH) {
            throw new IllegalArgumentException("Resolution note cannot exceed " + MAX_RESOLUTION_NOTE_LENGTH + " characters.");
        }

        Complaint complaint = complaintRepository.findByReferenceId(referenceId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + referenceId));

        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new IllegalArgumentException("Cannot update resolution note on a non-resolved complaint.");
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        complaint.setResolutionNote(trimmedNote);
        Complaint updated = complaintRepository.save(complaint);

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                updated,
                ComplaintStatus.RESOLVED,
                ComplaintStatus.RESOLVED,
                effectiveActor,
                "Resolution note corrected: " + trimmedNote
        );
        complaintStatusHistoryRepository.save(historyEntry);

        log.info("Administrator '{}' updated resolution note for complaint {}", effectiveActor, referenceId);
        return updated;
    }

    /**
     * Reassigns a complaint to an authorized campus department.
     * Validates that the department belongs to the controlled ALLOWED_DEPARTMENTS set and prevents
     * redundant no-op updates. Records an immutable audit log entry capturing the previous and new department.
     *
     * @param referenceId unique reference identifier
     * @param newDepartment target department name from controlled list
     * @param actorUsername username of administrator performing reassignment
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updateDepartment(String referenceId, String newDepartment, String actorUsername) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be blank");
        }
        if (newDepartment == null || newDepartment.isBlank()) {
            throw new IllegalArgumentException("Department cannot be blank");
        }
        String trimmedDept = newDepartment.trim();
        if (!ALLOWED_DEPARTMENTS.contains(trimmedDept)) {
            throw new IllegalArgumentException("Invalid department: " + trimmedDept);
        }

        Complaint complaint = complaintRepository.findByReferenceId(referenceId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + referenceId));

        String previousDepartment = complaint.getDepartment();
        if (trimmedDept.equals(previousDepartment)) {
            throw new IllegalArgumentException("Complaint is already assigned to " + trimmedDept);
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        complaint.setDepartment(trimmedDept);
        Complaint updated = complaintRepository.save(complaint);

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                updated,
                updated.getStatus(),
                updated.getStatus(),
                effectiveActor,
                "Department reassigned from '" + previousDepartment + "' to '" + trimmedDept + "'"
        );
        complaintStatusHistoryRepository.save(historyEntry);

        log.info("Administrator '{}' reassigned department for complaint {} from '{}' to '{}'",
                effectiveActor, referenceId, previousDepartment, trimmedDept);
        return updated;
    }

    /**
     * Corrects the campus category of a complaint (overriding or amending the AI recommendation).
     * Validates that the category belongs to the controlled ALLOWED_CATEGORIES set and prevents
     * redundant no-op updates. Records an immutable audit log entry capturing previous and new category.
     *
     * @param referenceId unique reference identifier
     * @param newCategory target category name from controlled list
     * @param actorUsername username of administrator performing correction
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updateCategory(String referenceId, String newCategory, String actorUsername) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be blank");
        }
        if (newCategory == null || newCategory.isBlank()) {
            throw new IllegalArgumentException("Category cannot be blank");
        }
        String trimmedCategory = newCategory.trim();
        if (!ALLOWED_CATEGORIES.contains(trimmedCategory)) {
            throw new IllegalArgumentException("Invalid category: " + trimmedCategory);
        }

        Complaint complaint = complaintRepository.findByReferenceId(referenceId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + referenceId));

        String previousCategory = complaint.getCategory();
        if (trimmedCategory.equals(previousCategory)) {
            throw new IllegalArgumentException("Complaint is already categorized as " + trimmedCategory);
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        complaint.setCategory(trimmedCategory);
        Complaint updated = complaintRepository.save(complaint);

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                updated,
                updated.getStatus(),
                updated.getStatus(),
                effectiveActor,
                "Category corrected from '" + previousCategory + "' to '" + trimmedCategory + "'"
        );
        complaintStatusHistoryRepository.save(historyEntry);

        log.info("Administrator '{}' corrected category for complaint {} from '{}' to '{}'",
                effectiveActor, referenceId, previousCategory, trimmedCategory);
        return updated;
    }

    /**
     * Updates the priority level of a complaint.
     * Managed independently from lifecycle status and SLA calculation policies.
     * Validates that priority is valid and changed. Records an immutable audit log entry.
     *
     * @param referenceId unique reference identifier
     * @param newPriority updated priority level (LOW, MEDIUM, HIGH, CRITICAL)
     * @param actorUsername username of administrator performing update
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updatePriority(String referenceId, ComplaintPriority newPriority, String actorUsername) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be blank");
        }
        if (newPriority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }

        Complaint complaint = complaintRepository.findByReferenceId(referenceId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + referenceId));

        ComplaintPriority previousPriority = complaint.getPriority();
        if (previousPriority == newPriority) {
            throw new IllegalArgumentException("Complaint priority is already set to " + newPriority.getDisplayName());
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        complaint.setPriority(newPriority);
        Complaint updated = complaintRepository.save(complaint);

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                updated,
                updated.getStatus(),
                updated.getStatus(),
                effectiveActor,
                "Priority updated from " + previousPriority.name() + " to " + newPriority.name()
        );
        complaintStatusHistoryRepository.save(historyEntry);

        log.info("Administrator '{}' updated priority for complaint {} from {} to {}",
                effectiveActor, referenceId, previousPriority, newPriority);
        return updated;
    }

    /**
     * Updates both department assignment and category correction in an atomic, auditable operation.
     * Validates both fields against controlled lists and prevents duplicate no-op submissions.
     *
     * @param referenceId unique reference identifier
     * @param newDepartment target department
     * @param newCategory target category
     * @param actorUsername username of administrator
     * @return updated Complaint entity
     */
    @Transactional
    public Complaint updateAssignment(String referenceId, String newDepartment, String newCategory, String actorUsername) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be blank");
        }
        if (newDepartment == null || newDepartment.isBlank()) {
            throw new IllegalArgumentException("Department cannot be blank");
        }
        if (newCategory == null || newCategory.isBlank()) {
            throw new IllegalArgumentException("Category cannot be blank");
        }

        String trimmedDept = newDepartment.trim();
        String trimmedCat = newCategory.trim();

        if (!ALLOWED_DEPARTMENTS.contains(trimmedDept)) {
            throw new IllegalArgumentException("Invalid department: " + trimmedDept);
        }
        if (!ALLOWED_CATEGORIES.contains(trimmedCat)) {
            throw new IllegalArgumentException("Invalid category: " + trimmedCat);
        }

        Complaint complaint = complaintRepository.findByReferenceId(referenceId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + referenceId));

        String prevDept = complaint.getDepartment();
        String prevCat = complaint.getCategory();

        boolean deptChanged = !trimmedDept.equals(prevDept);
        boolean catChanged = !trimmedCat.equals(prevCat);

        if (!deptChanged && !catChanged) {
            throw new IllegalArgumentException("Both department and category are unchanged.");
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        complaint.setDepartment(trimmedDept);
        complaint.setCategory(trimmedCat);
        Complaint updated = complaintRepository.save(complaint);

        String note;
        if (deptChanged && catChanged) {
            note = "Department reassigned from '" + prevDept + "' to '" + trimmedDept + "'; Category corrected from '" + prevCat + "' to '" + trimmedCat + "'";
        } else if (deptChanged) {
            note = "Department reassigned from '" + prevDept + "' to '" + trimmedDept + "'";
        } else {
            note = "Category corrected from '" + prevCat + "' to '" + trimmedCat + "'";
        }

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                updated,
                updated.getStatus(),
                updated.getStatus(),
                effectiveActor,
                note
        );
        complaintStatusHistoryRepository.save(historyEntry);

        log.info("Administrator '{}' updated assignment for complaint {}: {}", effectiveActor, referenceId, note);
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
     * Retrieves an active (non-deleted) complaint by its unique reference ID.
     *
     * @param referenceId human-readable reference identifier
     * @return Optional containing the complaint if found and not deleted
     */
    @Transactional(readOnly = true)
    public Optional<Complaint> getByReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Optional.empty();
        }
        return complaintRepository.findByReferenceIdAndDeletedFalse(referenceId.trim());
    }

    /**
     * Retrieves a complaint by reference ID regardless of deletion status.
     *
     * @param referenceId human-readable reference identifier
     * @return Optional containing the complaint if found
     */
    @Transactional(readOnly = true)
    public Optional<Complaint> getByReferenceIdIncludingDeleted(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Optional.empty();
        }
        return complaintRepository.findByReferenceId(referenceId.trim());
    }

    /**
     * Safely deletes (soft-deletes) a resolved complaint in an authorized atomic transaction.
     * Enforces that the complaint must exist, must not be already deleted, and must have status RESOLVED.
     * Records a permanent ComplaintDeletionAudit entry that survives the operation.
     * Records an immutable status history audit entry.
     *
     * @param referenceId unique reference identifier
     * @param actorUsername username of the administrator performing the deletion
     * @param reason administrative reason for deletion
     * @return the soft-deleted Complaint
     */
    @Transactional
    public Complaint deleteResolvedComplaint(String referenceId, String actorUsername, String reason) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Complaint reference ID cannot be blank");
        }
        String trimmedRef = referenceId.trim();
        Complaint complaint = complaintRepository.findByReferenceId(trimmedRef)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with reference ID: " + trimmedRef));

        if (complaint.isDeleted()) {
            throw new IllegalStateException("Complaint has already been deleted: " + trimmedRef);
        }

        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            log.warn("Unauthorized attempt to delete unresolved complaint {} in status {}", trimmedRef, complaint.getStatus());
            throw new IllegalStateException("Only resolved complaints can be deleted. Current status: " + complaint.getStatus().getDisplayName());
        }

        String effectiveActor = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : resolveCurrentActorUsername();

        String effectiveReason = (reason != null && !reason.isBlank())
                ? reason.trim()
                : "Resolved complaint safely archived/deleted by administrator";

        LocalDateTime now = LocalDateTime.now(slaProperties.getZoneId());
        complaint.setDeleted(true);
        complaint.setDeletedAt(now);
        complaint.setDeletedBy(effectiveActor);
        complaint.setDeletionReason(effectiveReason);

        Complaint saved = complaintRepository.save(complaint);

        // Record surviving deletion audit
        ComplaintDeletionAudit deletionAudit = new ComplaintDeletionAudit(
                saved.getId(),
                saved.getReferenceId(),
                saved.getTitle(),
                saved.getCategory(),
                saved.getDepartment(),
                saved.getStudentEmail(),
                effectiveActor,
                effectiveReason,
                now
        );
        if (complaintDeletionAuditRepository != null) {
            complaintDeletionAuditRepository.save(deletionAudit);
        }

        // Record status history audit
        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                saved,
                ComplaintStatus.RESOLVED,
                ComplaintStatus.RESOLVED,
                effectiveActor,
                "Complaint safely archived/deleted: " + effectiveReason
        );
        complaintStatusHistoryRepository.save(historyEntry);

        log.info("Administrator '{}' safely deleted resolved complaint {} (reason: {})", effectiveActor, trimmedRef, effectiveReason);
        return saved;
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
