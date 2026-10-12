package com.campuspulse.service;

import com.campuspulse.config.SlaProperties;
import com.campuspulse.dto.insights.SlaSummary;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintEscalationAudit;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.SlaEscalationStage;
import com.campuspulse.repository.ComplaintEscalationAuditRepository;
import com.campuspulse.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SlaEscalationService verifying automatic overdue detection,
 * strict duplicate prevention across repeated runs, monotonic stage progression,
 * resolved complaint exclusions, and immutable audit logging.
 */
@ExtendWith(MockitoExtension.class)
class SlaEscalationServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintEscalationAuditRepository escalationAuditRepository;

    @Mock
    private NotificationService notificationService;

    private SlaProperties slaProperties;
    private SlaEscalationService escalationService;

    @BeforeEach
    void setUp() {
        slaProperties = new SlaProperties();
        slaProperties.setDefaultResponseHours(24);
        slaProperties.setDefaultResolutionHours(72);
        slaProperties.setApproachingDeadlineHours(6);
        slaProperties.setTimezone("Asia/Kolkata");
        slaProperties.setAdminEscalationEmail("escalations-admin@campuspulse.internal");

        escalationService = new SlaEscalationService(
                complaintRepository,
                escalationAuditRepository,
                notificationService,
                slaProperties
        );
    }

    @Test
    @DisplayName("Detects response overdue complaint and escalates to Stage 1 with audit and notification")
    void shouldEscalateResponseOverdueComplaintToStage1() {
        LocalDateTime now = escalationService.getCurrentEvaluationTime();
        LocalDateTime pastResponseDeadline = now.minusHours(2);

        Complaint complaint = new Complaint("CP-ESC-001", "Lab AC not cooling", "Temperature 32C", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setResponseDeadline(pastResponseDeadline);
        complaint.setResolutionDeadline(now.plusHours(40));
        complaint.setEscalationStage(SlaEscalationStage.NONE);

        when(complaintRepository.findResponseOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(complaint));
        when(complaintRepository.findResolutionOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(escalationAuditRepository.existsByComplaint_ReferenceIdAndEscalationStage(
                eq("CP-ESC-001"), eq(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE)))
                .thenReturn(false);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        int escalated = escalationService.processEscalations();

        assertThat(escalated).isEqualTo(1);
        assertThat(complaint.getEscalationStage()).isEqualTo(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE);
        assertThat(complaint.getLastEscalatedAt()).isNotNull();

        // Verify audit persistence
        ArgumentCaptor<ComplaintEscalationAudit> auditCaptor = ArgumentCaptor.forClass(ComplaintEscalationAudit.class);
        verify(escalationAuditRepository, times(1)).save(auditCaptor.capture());
        ComplaintEscalationAudit savedAudit = auditCaptor.getValue();
        assertThat(savedAudit.getComplaintReferenceId()).isEqualTo("CP-ESC-001");
        assertThat(savedAudit.getEscalationStage()).isEqualTo(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE);
        assertThat(savedAudit.getPreviousStage()).isEqualTo(SlaEscalationStage.NONE);
        assertThat(savedAudit.getReason()).contains("Response SLA target (24h) breached");
        assertThat(savedAudit.getRecipientEmail()).isEqualTo("escalations-admin@campuspulse.internal");

        // Verify outbox dispatch
        verify(notificationService, times(1)).queueAndDispatchEscalationNotification(
                eq(complaint),
                eq(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE),
                any(String.class),
                eq("escalations-admin@campuspulse.internal")
        );
    }

    @Test
    @DisplayName("Detects resolution overdue complaint and escalates to Stage 2")
    void shouldEscalateResolutionOverdueComplaintToStage2() {
        LocalDateTime now = escalationService.getCurrentEvaluationTime();
        LocalDateTime pastResolutionDeadline = now.minusHours(5);

        Complaint complaint = new Complaint("CP-ESC-002", "Hostel plumbing", "Pipe leaking", "Hostel & Residence", "Hostel Administration");
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.setAcknowledgedAt(now.minusHours(50));
        complaint.setResponseDeadline(now.minusHours(50));
        complaint.setResolutionDeadline(pastResolutionDeadline);
        complaint.setEscalationStage(SlaEscalationStage.NONE);

        when(complaintRepository.findResponseOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(complaintRepository.findResolutionOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(complaint));
        when(escalationAuditRepository.existsByComplaint_ReferenceIdAndEscalationStage(
                eq("CP-ESC-002"), eq(SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE)))
                .thenReturn(false);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        int escalated = escalationService.processEscalations();

        assertThat(escalated).isEqualTo(1);
        assertThat(complaint.getEscalationStage()).isEqualTo(SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE);

        // Verify audit persistence
        ArgumentCaptor<ComplaintEscalationAudit> auditCaptor = ArgumentCaptor.forClass(ComplaintEscalationAudit.class);
        verify(escalationAuditRepository, times(1)).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEscalationStage()).isEqualTo(SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE);
        assertThat(auditCaptor.getValue().getReason()).contains("Resolution SLA target (72h) breached");
    }

    @Test
    @DisplayName("Duplicate Prevention: Repeated scheduler runs do not re-escalate already escalated complaint")
    void shouldPreventDuplicateEscalationsAcrossRepeatedRuns() {
        LocalDateTime now = escalationService.getCurrentEvaluationTime();

        Complaint complaint = new Complaint("CP-ESC-003", "Mess hygiene", "Dirty tables", "Canteen & Dining", "Canteen Committee & Food Services");
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setResponseDeadline(now.minusHours(3));
        // Complaint already escalated to Stage 1 in a previous run
        complaint.setEscalationStage(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE);

        when(complaintRepository.findResponseOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(complaint));
        when(complaintRepository.findResolutionOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        int count = escalationService.processEscalations();

        assertThat(count).isEqualTo(0);
        verify(complaintRepository, never()).save(any(Complaint.class));
        verify(escalationAuditRepository, never()).save(any(ComplaintEscalationAudit.class));
        verify(notificationService, never()).queueAndDispatchEscalationNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Duplicate Prevention: Existing audit record halts re-escalation even if entity stage was un-synchronized")
    void shouldPreventDuplicateEscalationWhenAuditRecordExists() {
        LocalDateTime now = escalationService.getCurrentEvaluationTime();

        Complaint complaint = new Complaint("CP-ESC-004", "Classroom mic", "Static noise", "Academic Affairs", "Academic Affairs Division");
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setResponseDeadline(now.minusHours(4));
        complaint.setEscalationStage(SlaEscalationStage.NONE);

        when(complaintRepository.findResponseOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(complaint));
        when(complaintRepository.findResolutionOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(escalationAuditRepository.existsByComplaint_ReferenceIdAndEscalationStage(
                eq("CP-ESC-004"), eq(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE)))
                .thenReturn(true); // Audit already exists!

        int count = escalationService.processEscalations();

        assertThat(count).isEqualTo(0);
        verify(complaintRepository, never()).save(any(Complaint.class));
        verify(escalationAuditRepository, never()).save(any(ComplaintEscalationAudit.class));
    }

    @Test
    @DisplayName("Monotonic Stage Progression: Stage 1 escalates to Stage 2 when resolution deadline passes")
    void shouldProgressMonotonicallyFromStage1ToStage2() {
        LocalDateTime now = escalationService.getCurrentEvaluationTime();

        Complaint complaint = new Complaint("CP-ESC-005", "Library heater", "Not turning on", "Library & Study Spaces", "Central Library Committee");
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        complaint.setAcknowledgedAt(now.minusHours(80));
        complaint.setResolutionDeadline(now.minusHours(8));
        complaint.setEscalationStage(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE); // Currently at Stage 1

        when(complaintRepository.findResponseOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(complaintRepository.findResolutionOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(complaint));
        when(escalationAuditRepository.existsByComplaint_ReferenceIdAndEscalationStage(
                eq("CP-ESC-005"), eq(SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE)))
                .thenReturn(false);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = escalationService.processEscalations();

        assertThat(count).isEqualTo(1);
        assertThat(complaint.getEscalationStage()).isEqualTo(SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE);

        ArgumentCaptor<ComplaintEscalationAudit> auditCaptor = ArgumentCaptor.forClass(ComplaintEscalationAudit.class);
        verify(escalationAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getPreviousStage()).isEqualTo(SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE);
        assertThat(auditCaptor.getValue().getEscalationStage()).isEqualTo(SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE);
    }

    @Test
    @DisplayName("Resolved complaints are strictly never escalated")
    void shouldNeverEscalateResolvedComplaints() {
        LocalDateTime now = escalationService.getCurrentEvaluationTime();

        Complaint resolvedComplaint = new Complaint("CP-ESC-006", "Security gate", "Card scanner", "Security & Transport Office", "Security & Transport Office");
        resolvedComplaint.setStatus(ComplaintStatus.RESOLVED);
        resolvedComplaint.setResolvedAt(now.minusHours(10));
        resolvedComplaint.setResponseDeadline(now.minusHours(50));
        resolvedComplaint.setResolutionDeadline(now.minusHours(20));

        when(complaintRepository.findResponseOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(resolvedComplaint));
        when(complaintRepository.findResolutionOverdueCandidates(any(LocalDateTime.class)))
                .thenReturn(List.of(resolvedComplaint));

        int count = escalationService.processEscalations();

        assertThat(count).isEqualTo(0);
        verify(complaintRepository, never()).save(any(Complaint.class));
        verify(escalationAuditRepository, never()).save(any(ComplaintEscalationAudit.class));
    }

    @Test
    @DisplayName("SLA Summary calculation accurately aggregates active unresolved, overdue, and compliance rate")
    void shouldCalculateSlaSummaryAccurately() {
        when(complaintRepository.count()).thenReturn(10L);
        when(complaintRepository.countByStatus(ComplaintStatus.RESOLVED)).thenReturn(4L);
        when(complaintRepository.countResponseOverdue(any(LocalDateTime.class))).thenReturn(1L);
        when(complaintRepository.countResolutionOverdue(any(LocalDateTime.class))).thenReturn(1L);
        when(complaintRepository.countApproachingDeadline(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2L);

        SlaSummary summary = escalationService.getSlaSummary();

        assertThat(summary.totalComplaints()).isEqualTo(10L);
        assertThat(summary.activeUnresolved()).isEqualTo(6L);
        assertThat(summary.responseOverdueCount()).isEqualTo(1L);
        assertThat(summary.resolutionOverdueCount()).isEqualTo(1L);
        assertThat(summary.approachingDeadlineCount()).isEqualTo(2L);
        assertThat(summary.compliantCount()).isEqualTo(4L); // 6 active - 2 overdue = 4 compliant
        assertThat(summary.complianceRate()).isEqualTo(66.7); // 4 / 6 * 100 = 66.7%
        assertThat(summary.isBreached()).isTrue();
    }
}
