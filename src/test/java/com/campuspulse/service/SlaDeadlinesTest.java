package com.campuspulse.service;

import com.campuspulse.config.SlaProperties;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.SlaEscalationStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying SLA deadline calculation rules, timezone handling,
 * exact boundary conditions, response vs resolution SLA distinctions, and resolved complaint behavior.
 */
class SlaDeadlinesTest {

    private SlaProperties slaProperties;
    private SlaEscalationService slaEscalationService;

    @BeforeEach
    void setUp() {
        slaProperties = new SlaProperties();
        slaProperties.setDefaultResponseHours(24);
        slaProperties.setDefaultResolutionHours(72);
        slaProperties.setApproachingDeadlineHours(6);
        slaProperties.setTimezone("Asia/Kolkata");

        slaEscalationService = new SlaEscalationService(null, null, null, slaProperties);
    }

    @Test
    @DisplayName("Calculates default response deadline (24h) and resolution deadline (72h) accurately")
    void shouldCalculateDefaultDeadlinesAccurately() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 10, 0, 0);

        LocalDateTime responseDeadline = slaEscalationService.calculateResponseDeadline(created);
        LocalDateTime resolutionDeadline = slaEscalationService.calculateResolutionDeadline(created);

        assertThat(responseDeadline).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0, 0));
        assertThat(resolutionDeadline).isEqualTo(LocalDateTime.of(2026, 10, 15, 10, 0, 0));
    }

    @Test
    @DisplayName("Calculates deadlines using custom configurable targets")
    void shouldRespectCustomConfigurableTargets() {
        slaProperties.setDefaultResponseHours(12);
        slaProperties.setDefaultResolutionHours(48);

        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 9, 30, 0);

        LocalDateTime responseDeadline = slaEscalationService.calculateResponseDeadline(created);
        LocalDateTime resolutionDeadline = slaEscalationService.calculateResolutionDeadline(created);

        assertThat(responseDeadline).isEqualTo(LocalDateTime.of(2026, 10, 12, 21, 30, 0));
        assertThat(resolutionDeadline).isEqualTo(LocalDateTime.of(2026, 10, 14, 9, 30, 0));
    }

    @Test
    @DisplayName("Timezone policy consistently resolves administrative ZoneId and anchors evaluations")
    void shouldAdhereToDocumentedTimezonePolicy() {
        assertThat(slaProperties.getZoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));

        slaProperties.setTimezone("UTC");
        assertThat(slaProperties.getZoneId()).isEqualTo(ZoneId.of("UTC"));

        slaProperties.setTimezone("Invalid/Unknown_Zone");
        assertThat(slaProperties.getZoneId()).isEqualTo(ZoneId.of("Asia/Kolkata")); // fallback
    }

    @Test
    @DisplayName("Exact boundary testing: 1s before, exactly at, and 1s after response deadline")
    void shouldEvaluateExactResponseDeadlineBoundaries() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 10, 0, 0);
        LocalDateTime deadline = created.plusHours(24); // 2026-10-13 10:00:00

        Complaint complaint = new Complaint("CP-TEST-001", "Wi-Fi down", "No signal", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setCreatedAt(created);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setResponseDeadline(deadline);

        // 1 second before deadline -> NOT overdue
        LocalDateTime oneSecondBefore = deadline.minusSeconds(1);
        assertThat(complaint.isResponseOverdue(oneSecondBefore)).isFalse();

        // Exactly at deadline -> NOT overdue
        assertThat(complaint.isResponseOverdue(deadline)).isFalse();

        // 1 second after deadline -> OVERDUE
        LocalDateTime oneSecondAfter = deadline.plusSeconds(1);
        assertThat(complaint.isResponseOverdue(oneSecondAfter)).isTrue();
    }

    @Test
    @DisplayName("Exact boundary testing: 1s before, exactly at, and 1s after resolution deadline")
    void shouldEvaluateExactResolutionDeadlineBoundaries() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 10, 0, 0);
        LocalDateTime deadline = created.plusHours(72); // 2026-10-15 10:00:00

        Complaint complaint = new Complaint("CP-TEST-002", "Lab PC", "Broken mouse", "Infrastructure & Labs", "Campus Facilities & Works");
        complaint.setCreatedAt(created);
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.setAcknowledgedAt(created.plusHours(2));
        complaint.setResolutionDeadline(deadline);

        // 1 second before deadline -> NOT overdue
        LocalDateTime oneSecondBefore = deadline.minusSeconds(1);
        assertThat(complaint.isResolutionOverdue(oneSecondBefore)).isFalse();

        // Exactly at deadline -> NOT overdue
        assertThat(complaint.isResolutionOverdue(deadline)).isFalse();

        // 1 second after deadline -> OVERDUE
        LocalDateTime oneSecondAfter = deadline.plusSeconds(1);
        assertThat(complaint.isResolutionOverdue(oneSecondAfter)).isTrue();
    }

    @Test
    @DisplayName("Distinguishes response overdue from resolution overdue cleanly")
    void shouldDistinguishResponseOverdueFromResolutionOverdue() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 10, 0, 0);
        LocalDateTime responseDeadline = created.plusHours(24);
        LocalDateTime resolutionDeadline = created.plusHours(72);

        Complaint complaint = new Complaint("CP-TEST-003", "Hostel Leak", "Room 202", "Hostel & Residence", "Hostel Administration");
        complaint.setCreatedAt(created);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setResponseDeadline(responseDeadline);
        complaint.setResolutionDeadline(resolutionDeadline);

        // Case A: 30 hours after submission, unacknowledged
        LocalDateTime time30h = created.plusHours(30);
        assertThat(complaint.isResponseOverdue(time30h)).isTrue();
        assertThat(complaint.isResolutionOverdue(time30h)).isFalse();

        // Case B: Admin reviews at 35 hours (acknowledgment recorded)
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);
        complaint.setAcknowledgedAt(created.plusHours(35));

        // Now response SLA is acknowledged, so response overdue is false
        assertThat(complaint.isResponseOverdue(time30h)).isFalse();
        assertThat(complaint.isResponseOverdue(created.plusHours(40))).isFalse();
        assertThat(complaint.isResolutionOverdue(created.plusHours(40))).isFalse();

        // Case C: At 80 hours (resolution deadline passed)
        LocalDateTime time80h = created.plusHours(80);
        assertThat(complaint.isResponseOverdue(time80h)).isFalse(); // Response was acknowledged
        assertThat(complaint.isResolutionOverdue(time80h)).isTrue(); // Resolution is breached!
    }

    @Test
    @DisplayName("Resolved complaints are never marked response overdue or resolution overdue")
    void shouldNeverMarkResolvedComplaintsAsOverdue() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 10, 0, 0);
        LocalDateTime responseDeadline = created.plusHours(24);
        LocalDateTime resolutionDeadline = created.plusHours(72);

        Complaint complaint = new Complaint("CP-TEST-004", "Cafeteria food", "Cold coffee", "Canteen & Dining", "Canteen Committee & Food Services");
        complaint.setCreatedAt(created);
        complaint.setResponseDeadline(responseDeadline);
        complaint.setResolutionDeadline(resolutionDeadline);
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(created.plusHours(10));

        LocalDateTime evaluationTime = created.plusDays(30); // 30 days later

        assertThat(complaint.isResponseOverdue(evaluationTime)).isFalse();
        assertThat(complaint.isResolutionOverdue(evaluationTime)).isFalse();
        assertThat(complaint.isApproachingDeadline(evaluationTime, 6)).isFalse();
        assertThat(complaint.getSlaStatusString(evaluationTime)).isEqualTo("Resolved (Within SLA)");
    }

    @Test
    @DisplayName("Approaching deadline detection identifies tickets within warning threshold")
    void shouldDetectApproachingDeadlinesAccurately() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 10, 0, 0);
        LocalDateTime responseDeadline = created.plusHours(24); // Oct 13 10:00

        Complaint complaint = new Complaint("CP-TEST-005", "Books", "Library card", "Library & Study Spaces", "Central Library Committee");
        complaint.setCreatedAt(created);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setResponseDeadline(responseDeadline);

        // 10 hours before deadline (warning threshold is 6h) -> NOT approaching
        LocalDateTime time10hBefore = responseDeadline.minusHours(10);
        assertThat(complaint.isApproachingDeadline(time10hBefore, 6)).isFalse();

        // 4 hours before deadline -> APPROACHING
        LocalDateTime time4hBefore = responseDeadline.minusHours(4);
        assertThat(complaint.isApproachingDeadline(time4hBefore, 6)).isTrue();

        // 1 hour after deadline -> NOT approaching (it is overdue now)
        LocalDateTime time1hAfter = responseDeadline.plusHours(1);
        assertThat(complaint.isApproachingDeadline(time1hAfter, 6)).isFalse();
    }
}
