package com.campuspulse.repository;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ComplaintRepositoryTest {

    @Autowired
    private ComplaintRepository complaintRepository;

    @Test
    @DisplayName("Aggregate database queries calculate counts, status breakdowns, and category breakdowns accurately")
    void shouldExecuteAggregateQueriesAccurately() {
        Complaint c1 = new Complaint("CP-2026-TEST0001", "AC broken", "AC in lab 1", "Infrastructure & Labs", "Campus Facilities & Works");
        c1.setStatus(ComplaintStatus.SUBMITTED);
        complaintRepository.save(c1);

        Complaint c2 = new Complaint("CP-2026-TEST0002", "Fan broken", "Fan in room 101", "Hostel & Residence", "Hostel Administration");
        c2.setStatus(ComplaintStatus.RESOLVED);
        complaintRepository.save(c2);

        Complaint c3 = new Complaint("CP-2026-TEST0003", "PC broken", "PC in lab 2", "Infrastructure & Labs", "Campus Facilities & Works");
        c3.setStatus(ComplaintStatus.RESOLVED);
        complaintRepository.save(c3);

        long total = complaintRepository.countWithFilters(null, null, null);
        assertThat(total).isEqualTo(3);

        long resolvedCount = complaintRepository.countWithFilters(ComplaintStatus.RESOLVED, null, null);
        assertThat(resolvedCount).isEqualTo(2);

        long infraCount = complaintRepository.countWithFilters(null, "Infrastructure & Labs", null);
        assertThat(infraCount).isEqualTo(2);

        List<Object[]> statusRows = complaintRepository.countGroupByStatus(null, null);
        assertThat(statusRows).hasSize(2);

        List<Object[]> catRows = complaintRepository.countGroupByCategory(null, null, null);
        assertThat(catRows).hasSize(2);
        assertThat(catRows.get(0)[0]).isEqualTo("Infrastructure & Labs");
        assertThat(((Number) catRows.get(0)[1]).longValue()).isEqualTo(2);

        List<Object[]> deptRows = complaintRepository.countGroupByDepartment(null, null, null);
        assertThat(deptRows).hasSize(2);

        List<Object[]> trendRows = complaintRepository.countDailyTrend(null, null, null);
        assertThat(trendRows).isNotEmpty();
    }

    @Test
    @DisplayName("Aggregate database queries for period comparison and department backlogs function correctly")
    void shouldExecuteInsightsAggregateQueries() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // c1: submitted 5 days ago (Infrastructure, Campus Facilities, unresolved)
        Complaint c1 = new Complaint("CP-2026-INSIGHT1", "AC broken", "AC in lab 1", "Infrastructure & Labs", "Campus Facilities & Works");
        c1.setStatus(ComplaintStatus.SUBMITTED);
        c1.setCreatedAt(now.minusDays(5));
        complaintRepository.save(c1);

        // c2: in progress 3 days ago (Infrastructure, Campus Facilities, unresolved)
        Complaint c2 = new Complaint("CP-2026-INSIGHT2", "PC issue", "PC won't boot", "Infrastructure & Labs", "Campus Facilities & Works");
        c2.setStatus(ComplaintStatus.IN_PROGRESS);
        c2.setCreatedAt(now.minusDays(3));
        complaintRepository.save(c2);

        // c3: resolved 2 days ago (Hostel, Hostel Administration, resolved)
        Complaint c3 = new Complaint("CP-2026-INSIGHT3", "Fan noise", "Fan rattling", "Hostel & Residence", "Hostel Administration");
        c3.setStatus(ComplaintStatus.RESOLVED);
        c3.setCreatedAt(now.minusDays(2));
        complaintRepository.save(c3);

        // c4: under review 20 days ago (Hostel, Hostel Administration, unresolved)
        Complaint c4 = new Complaint("CP-2026-INSIGHT4", "Water pressure", "Low shower pressure", "Hostel & Residence", "Hostel Administration");
        c4.setStatus(ComplaintStatus.UNDER_REVIEW);
        c4.setCreatedAt(now.minusDays(20));
        complaintRepository.save(c4);

        // 1. Period comparison between recent window [now - 14d, now) and previous window [now - 28d, now - 14d)
        long currentPeriodCount = complaintRepository.countBetween(now.minusDays(14), now);
        assertThat(currentPeriodCount).isEqualTo(3); // c1, c2, c3

        long previousPeriodCount = complaintRepository.countBetween(now.minusDays(28), now.minusDays(14));
        assertThat(previousPeriodCount).isEqualTo(1); // c4

        // 2. Unresolved complaints grouped by department
        List<ComplaintStatus> unresolvedStatuses = List.of(
                ComplaintStatus.SUBMITTED,
                ComplaintStatus.UNDER_REVIEW,
                ComplaintStatus.IN_PROGRESS
        );
        List<Object[]> unresolvedDeptRows = complaintRepository.countUnresolvedGroupByDepartment(unresolvedStatuses);
        assertThat(unresolvedDeptRows).hasSize(2);

        // Campus Facilities has 2 unresolved (c1, c2), Hostel Administration has 1 unresolved (c4)
        assertThat(unresolvedDeptRows.get(0)[0]).isEqualTo("Campus Facilities & Works");
        assertThat(((Number) unresolvedDeptRows.get(0)[1]).longValue()).isEqualTo(2L);
        assertThat(unresolvedDeptRows.get(1)[0]).isEqualTo("Hostel Administration");
        assertThat(((Number) unresolvedDeptRows.get(1)[1]).longValue()).isEqualTo(1L);

        // 3. Category counts within a specific window [now - 14d, now)
        List<Object[]> recentCatRows = complaintRepository.countGroupByCategoryBetween(now.minusDays(14), now);
        assertThat(recentCatRows).hasSize(2);
        assertThat(recentCatRows.get(0)[0]).isEqualTo("Infrastructure & Labs");
        assertThat(((Number) recentCatRows.get(0)[1]).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("SLA database queries calculate response overdue, resolution overdue, and candidates correctly")
    void shouldExecuteSlaQueriesAccurately() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // c1: SUBMITTED, response deadline passed 2h ago, no acknowledgment -> response overdue candidate & count
        Complaint c1 = new Complaint("CP-SLA-REPO-1", "Lab AC", "Hot lab", "Infrastructure & Labs", "Campus Facilities & Works");
        c1.setStatus(ComplaintStatus.SUBMITTED);
        c1.setResponseDeadline(now.minusHours(2));
        c1.setResolutionDeadline(now.plusHours(40));
        c1.setEscalationStage(com.campuspulse.model.SlaEscalationStage.NONE);
        complaintRepository.save(c1);

        // c2: UNDER_REVIEW, acknowledged, resolution deadline passed 3h ago -> resolution overdue candidate & count
        Complaint c2 = new Complaint("CP-SLA-REPO-2", "Hostel Bed", "Broken frame", "Hostel & Residence", "Hostel Administration");
        c2.setStatus(ComplaintStatus.UNDER_REVIEW);
        c2.setAcknowledgedAt(now.minusHours(50));
        c2.setResponseDeadline(now.minusHours(50));
        c2.setResolutionDeadline(now.minusHours(3));
        c2.setEscalationStage(com.campuspulse.model.SlaEscalationStage.NONE);
        complaintRepository.save(c2);

        // c3: RESOLVED, deadlines passed -> should NOT be counted as overdue or candidate
        Complaint c3 = new Complaint("CP-SLA-REPO-3", "Cafeteria tray", "Missing", "Canteen & Dining", "Canteen Committee & Food Services");
        c3.setStatus(ComplaintStatus.RESOLVED);
        c3.setResponseDeadline(now.minusHours(20));
        c3.setResolutionDeadline(now.minusHours(10));
        c3.setResolvedAt(now.minusHours(5));
        c3.setEscalationStage(com.campuspulse.model.SlaEscalationStage.STAGE_2_RESOLUTION_OVERDUE);
        complaintRepository.save(c3);

        // c4: SUBMITTED, deadline in 4 hours -> approaching deadline
        Complaint c4 = new Complaint("CP-SLA-REPO-4", "Library chair", "Wobbly", "Library & Study Spaces", "Central Library Committee");
        c4.setStatus(ComplaintStatus.SUBMITTED);
        c4.setResponseDeadline(now.plusHours(4));
        c4.setResolutionDeadline(now.plusHours(50));
        c4.setEscalationStage(com.campuspulse.model.SlaEscalationStage.NONE);
        complaintRepository.save(c4);

        // Verify count queries
        long respOverdueCount = complaintRepository.countResponseOverdue(now);
        assertThat(respOverdueCount).isEqualTo(1L);

        long resOverdueCount = complaintRepository.countResolutionOverdue(now);
        assertThat(resOverdueCount).isEqualTo(1L);

        long approachingCount = complaintRepository.countApproachingDeadline(now, now.plusHours(6));
        assertThat(approachingCount).isEqualTo(1L);

        // Verify candidate queries
        List<Complaint> respCandidates = complaintRepository.findResponseOverdueCandidates(now);
        assertThat(respCandidates).extracting(Complaint::getReferenceId).containsExactly("CP-SLA-REPO-1");

        List<Complaint> resCandidates = complaintRepository.findResolutionOverdueCandidates(now);
        assertThat(resCandidates).extracting(Complaint::getReferenceId).containsExactly("CP-SLA-REPO-2");
    }
}

