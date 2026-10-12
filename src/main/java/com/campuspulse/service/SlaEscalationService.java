package com.campuspulse.service;

import com.campuspulse.config.SlaProperties;
import com.campuspulse.dto.insights.SlaSummary;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintEscalationAudit;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.SlaEscalationStage;
import com.campuspulse.repository.ComplaintEscalationAuditRepository;
import com.campuspulse.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Service managing SLA deadlines, timezone-anchored calculations, overdue detection,
 * duplicate-prevented escalations, immutable escalation audit trail logging, and outbox alerts.
 */
@Service
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintEscalationAuditRepository escalationAuditRepository;
    private final NotificationService notificationService;
    private final SlaProperties slaProperties;

    public SlaEscalationService(
            ComplaintRepository complaintRepository,
            ComplaintEscalationAuditRepository escalationAuditRepository,
            NotificationService notificationService,
            SlaProperties slaProperties) {
        this.complaintRepository = complaintRepository;
        this.escalationAuditRepository = escalationAuditRepository;
        this.notificationService = notificationService;
        this.slaProperties = slaProperties;
    }

    /**
     * Calculates the initial response deadline from complaint creation timestamp
     * using the documented administrative timezone policy.
     */
    public LocalDateTime calculateResponseDeadline(LocalDateTime createdAt) {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(slaProperties.getZoneId());
        }
        return createdAt.plusHours(slaProperties.getDefaultResponseHours());
    }

    /**
     * Calculates the final resolution deadline from complaint creation timestamp
     * using the documented administrative timezone policy.
     */
    public LocalDateTime calculateResolutionDeadline(LocalDateTime createdAt) {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(slaProperties.getZoneId());
        }
        return createdAt.plusHours(slaProperties.getDefaultResolutionHours());
    }

    /**
     * Obtains the current evaluation timestamp in the configured administrative timezone.
     */
    public LocalDateTime getCurrentEvaluationTime() {
        return LocalDateTime.now(slaProperties.getZoneId());
    }

    /**
     * Scans active complaints, detects response and resolution SLA breaches,
     * transitions complaint escalation stages monotonically, persists immutable audit records,
     * and queues administrative alert notifications into the outbox.
     * Prevents repeated duplicate escalations through entity stage checks and audit verification.
     * Never escalates resolved complaints.
     *
     * @return count of newly triggered escalations
     */
    @Transactional
    public int processEscalations() {
        LocalDateTime now = getCurrentEvaluationTime();
        int escalationCount = 0;

        // 1. Process Stage 1: Response Overdue Candidates
        List<Complaint> responseCandidates = complaintRepository.findResponseOverdueCandidates(now);
        for (Complaint complaint : responseCandidates) {
            if (shouldEscalateResponse(complaint, now)) {
                escalateComplaint(complaint, SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE, now);
                escalationCount++;
            }
        }

        // 2. Process Stage 2: Resolution Overdue Candidates
        List<Complaint> resolutionCandidates = complaintRepository.findResolutionOverdueCandidates(now);
        for (Complaint complaint : resolutionCandidates) {
            if (shouldEscalateResolution(complaint, now)) {
                escalateComplaint(complaint, SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE, now);
                escalationCount++;
            }
        }

        if (escalationCount > 0) {
            log.info("SLA escalation run completed: {} complaint(s) escalated to new stages", escalationCount);
        }
        return escalationCount;
    }

    private boolean shouldEscalateResponse(Complaint complaint, LocalDateTime now) {
        // Never escalate resolved complaints
        if (complaint.isResolved()) {
            return false;
        }
        // If already acknowledged, response SLA is satisfied
        if (complaint.isAcknowledged()) {
            return false;
        }
        // Strict duplicate prevention: only escalate if stage is NONE
        if (complaint.getEscalationStage() != SlaEscalationStage.NONE) {
            return false;
        }
        // Double-check audit record existence
        if (escalationAuditRepository.existsByComplaint_ReferenceIdAndEscalationStage(
                complaint.getReferenceId(), SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE)) {
            return false;
        }
        // Must be past response deadline
        return complaint.getResponseDeadline() != null && now.isAfter(complaint.getResponseDeadline());
    }

    private boolean shouldEscalateResolution(Complaint complaint, LocalDateTime now) {
        // Never escalate resolved complaints
        if (complaint.isResolved()) {
            return false;
        }
        // Strict duplicate prevention: cannot escalate if already at Stage 2
        if (complaint.getEscalationStage() == SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE) {
            return false;
        }
        // Double-check audit record existence
        if (escalationAuditRepository.existsByComplaint_ReferenceIdAndEscalationStage(
                complaint.getReferenceId(), SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE)) {
            return false;
        }
        // Must be past resolution deadline
        return complaint.getResolutionDeadline() != null && now.isAfter(complaint.getResolutionDeadline());
    }

    private void escalateComplaint(Complaint complaint, SlaEscalationStage targetStage, LocalDateTime now) {
        SlaEscalationStage previousStage = complaint.getEscalationStage();
        complaint.setEscalationStage(targetStage);
        complaint.setLastEscalatedAt(now);
        Complaint savedComplaint = complaintRepository.save(complaint);

        String reason;
        if (targetStage == SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE) {
            long overdueHours = Math.max(1, Duration.between(complaint.getResponseDeadline(), now).toHours());
            reason = String.format("Response SLA target (%dh) breached by %dh. Initial acknowledgment pending in %s.",
                    slaProperties.getDefaultResponseHours(), overdueHours, complaint.getDepartment());
        } else {
            long overdueHours = Math.max(1, Duration.between(complaint.getResolutionDeadline(), now).toHours());
            reason = String.format("Resolution SLA target (%dh) breached by %dh. Case remains unresolved in %s.",
                    slaProperties.getDefaultResolutionHours(), overdueHours, complaint.getDepartment());
        }

        // Persist immutable escalation audit record
        ComplaintEscalationAudit audit = new ComplaintEscalationAudit(
                savedComplaint,
                savedComplaint.getReferenceId(),
                targetStage,
                previousStage,
                reason,
                slaProperties.getAdminEscalationEmail()
        );
        escalationAuditRepository.save(audit);

        // Queue alert in notification outbox
        if (notificationService != null) {
            notificationService.queueAndDispatchEscalationNotification(
                    savedComplaint,
                    targetStage,
                    reason,
                    slaProperties.getAdminEscalationEmail()
            );
        }

        log.warn("Escalated complaint {} from {} to {} (Reason: {})",
                savedComplaint.getReferenceId(), previousStage, targetStage, reason);
    }

    /**
     * Retrieves all escalation audit trail entries for a complaint ordered chronologically.
     */
    @Transactional(readOnly = true)
    public List<ComplaintEscalationAudit> getEscalationAudits(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Collections.emptyList();
        }
        return escalationAuditRepository.findByComplaint_ReferenceIdOrderByTriggeredAtAsc(referenceId.trim());
    }

    /**
     * Computes real-time SLA metrics across all complaints using database queries.
     */
    @Transactional(readOnly = true)
    public SlaSummary getSlaSummary() {
        LocalDateTime now = getCurrentEvaluationTime();
        long totalComplaints = complaintRepository.count();
        long resolved = complaintRepository.countByStatus(ComplaintStatus.RESOLVED);
        long activeUnresolved = Math.max(0, totalComplaints - resolved);

        long responseOverdue = complaintRepository.countResponseOverdue(now);
        long resolutionOverdue = complaintRepository.countResolutionOverdue(now);
        LocalDateTime warningThreshold = now.plusHours(slaProperties.getApproachingDeadlineHours());
        long approaching = complaintRepository.countApproachingDeadline(now, warningThreshold);

        return SlaSummary.of(
                totalComplaints,
                activeUnresolved,
                responseOverdue,
                resolutionOverdue,
                approaching
        );
    }
}
