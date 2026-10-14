package com.campuspulse.repository;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

    @Test
    @DisplayName("searchWithFilters searches referenceId, title, description, and studentEmail case-insensitively")
    void searchWithFiltersMatchesAllFieldsCaseInsensitively() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime threshold = now.plusHours(6);
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        Complaint c1 = new Complaint("CP-2026-REF101", "Lab Projector Defect", "Bulb burnt out", "Infrastructure & Labs", "Campus Facilities & Works");
        c1.setStudentEmail("student.one@campuspulse.internal");
        c1.setCreatedAt(now.minusHours(5));
        complaintRepository.save(c1);

        Complaint c2 = new Complaint("CP-2026-REF102", "Hostel Heating Cold", "Radiator valve leaking", "Hostel & Residence", "Hostel Administration");
        c2.setStudentEmail("student.two@campuspulse.internal");
        c2.setCreatedAt(now.minusHours(4));
        complaintRepository.save(c2);

        Complaint c3 = new Complaint("CP-2026-REF103", "Library Noise Issue", "Group study room loud screaming", "Library & Study Spaces", "Central Library Committee");
        c3.setStudentEmail("alex.johnson@campuspulse.internal");
        c3.setCreatedAt(now.minusHours(3));
        complaintRepository.save(c3);

        // 1. Search by referenceId (case-insensitive: lowercase match)
        Page<Complaint> refMatch = complaintRepository.searchWithFilters("ref101", null, null, null, now, threshold, pageable);
        assertThat(refMatch.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-2026-REF101");

        // 2. Search by title (case-insensitive: mixed case match)
        Page<Complaint> titleMatch = complaintRepository.searchWithFilters("pRoJeCtOr", null, null, null, now, threshold, pageable);
        assertThat(titleMatch.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-2026-REF101");

        // 3. Search by description (case-insensitive: partial phrase match)
        Page<Complaint> descMatch = complaintRepository.searchWithFilters("vAlVe LeAkInG", null, null, null, now, threshold, pageable);
        assertThat(descMatch.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-2026-REF102");

        // 4. Search by studentEmail (case-insensitive: domain or prefix)
        Page<Complaint> emailMatch = complaintRepository.searchWithFilters("ALEX.JOHNSON", null, null, null, now, threshold, pageable);
        assertThat(emailMatch.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-2026-REF103");

        // 5. Search with no matches returns empty page
        Page<Complaint> noMatch = complaintRepository.searchWithFilters("completely_absent_term", null, null, null, now, threshold, pageable);
        assertThat(noMatch.getContent()).isEmpty();
        assertThat(noMatch.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("searchWithFilters combines search keyword with status, category, and SLA filters")
    void searchWithFiltersCombinedWithStatusCategoryAndSla() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime threshold = now.plusHours(6);
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // c1: Infrastructure, SUBMITTED, response deadline passed (RESPONSE_OVERDUE), keyword: "printer"
        Complaint c1 = new Complaint("CP-COMBO-1", "Lab Printer Jam", "3D printer jammed", "Infrastructure & Labs", "Campus Facilities & Works");
        c1.setStatus(ComplaintStatus.SUBMITTED);
        c1.setResponseDeadline(now.minusHours(3));
        c1.setResolutionDeadline(now.plusHours(40));
        complaintRepository.save(c1);

        // c2: Infrastructure, IN_PROGRESS, response SLA acknowledged (ON_TRACK), keyword: "printer"
        Complaint c2 = new Complaint("CP-COMBO-2", "Lab Laser Printer", "Paper tray cracked", "Infrastructure & Labs", "Campus Facilities & Works");
        c2.setStatus(ComplaintStatus.IN_PROGRESS);
        c2.setAcknowledgedAt(now.minusHours(10));
        c2.setResponseDeadline(now.minusHours(10));
        c2.setResolutionDeadline(now.plusHours(20)); // not overdue and not approaching threshold
        complaintRepository.save(c2);

        // c3: Library, SUBMITTED, response deadline passed (RESPONSE_OVERDUE), keyword: "printer"
        Complaint c3 = new Complaint("CP-COMBO-3", "Library Desk Printer", "Out of toner", "Library & Study Spaces", "Central Library Committee");
        c3.setStatus(ComplaintStatus.SUBMITTED);
        c3.setResponseDeadline(now.minusHours(1));
        c3.setResolutionDeadline(now.plusHours(30));
        complaintRepository.save(c3);

        // c4: Infrastructure, RESOLVED (MET), keyword: "printer"
        Complaint c4 = new Complaint("CP-COMBO-4", "Network Printer Fixed", "Driver installed", "Infrastructure & Labs", "Campus Facilities & Works");
        c4.setStatus(ComplaintStatus.RESOLVED);
        complaintRepository.save(c4);

        // Keyword "printer" + status SUBMITTED -> c1, c3
        Page<Complaint> submittedPrinters = complaintRepository.searchWithFilters("printer", ComplaintStatus.SUBMITTED, null, null, now, threshold, pageable);
        assertThat(submittedPrinters.getContent()).extracting(Complaint::getReferenceId).containsExactlyInAnyOrder("CP-COMBO-1", "CP-COMBO-3");

        // Keyword "printer" + status SUBMITTED + category "Infrastructure & Labs" -> c1
        Page<Complaint> infraSubmitted = complaintRepository.searchWithFilters("printer", ComplaintStatus.SUBMITTED, "Infrastructure & Labs", null, now, threshold, pageable);
        assertThat(infraSubmitted.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-COMBO-1");

        // Keyword "printer" + slaStatus "RESPONSE_OVERDUE" -> c1, c3
        Page<Complaint> respOverduePrinters = complaintRepository.searchWithFilters("printer", null, null, "RESPONSE_OVERDUE", now, threshold, pageable);
        assertThat(respOverduePrinters.getContent()).extracting(Complaint::getReferenceId).containsExactlyInAnyOrder("CP-COMBO-1", "CP-COMBO-3");

        // Keyword "printer" + category "Infrastructure & Labs" + slaStatus "RESPONSE_OVERDUE" -> c1 only
        Page<Complaint> comboFilter = complaintRepository.searchWithFilters("printer", null, "Infrastructure & Labs", "RESPONSE_OVERDUE", now, threshold, pageable);
        assertThat(comboFilter.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-COMBO-1");

        // Keyword "printer" + slaStatus "MET" -> c4 only
        Page<Complaint> metPrinters = complaintRepository.searchWithFilters("printer", null, null, "MET", now, threshold, pageable);
        assertThat(metPrinters.getContent()).extracting(Complaint::getReferenceId).containsExactly("CP-COMBO-4");
    }

    @Test
    @DisplayName("searchWithFilters handles empty search text and executes database pagination across pages")
    void searchWithFiltersPaginationAndEmptySearch() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime threshold = now.plusHours(6);

        for (int i = 1; i <= 5; i++) {
            Complaint c = new Complaint("CP-PAG-" + i, "Issue number " + i, "Description for item " + i, "Infrastructure & Labs", "Campus Facilities & Works");
            c.setCreatedAt(now.minusMinutes(10 * i));
            complaintRepository.save(c);
        }

        // Empty string and null search term should return all 5 complaints paginated
        Page<Complaint> emptySearchPage = complaintRepository.searchWithFilters("", null, null, null, now, threshold, PageRequest.of(0, 10));
        assertThat(emptySearchPage.getTotalElements()).isEqualTo(5);

        Page<Complaint> nullSearchPage = complaintRepository.searchWithFilters(null, null, null, null, now, threshold, PageRequest.of(0, 10));
        assertThat(nullSearchPage.getTotalElements()).isEqualTo(5);

        // Page 0 with size 2 (first page)
        Page<Complaint> firstPage = complaintRepository.searchWithFilters("", null, null, null, now, threshold,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(firstPage.getNumber()).isEqualTo(0);
        assertThat(firstPage.getSize()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();

        // Page 1 with size 2 (middle page)
        Page<Complaint> middlePage = complaintRepository.searchWithFilters("", null, null, null, now, threshold,
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(middlePage.getNumber()).isEqualTo(1);
        assertThat(middlePage.getContent()).hasSize(2);
        assertThat(middlePage.hasNext()).isTrue();
        assertThat(middlePage.hasPrevious()).isTrue();

        // Page 2 with size 2 (last page)
        Page<Complaint> lastPage = complaintRepository.searchWithFilters("", null, null, null, now, threshold,
                PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(lastPage.getNumber()).isEqualTo(2);
        assertThat(lastPage.getContent()).hasSize(1);
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("searchWithFilters filters by department and priority accurately")
    void shouldFilterByDepartmentAndPriority() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime threshold = now.plusHours(6);

        Complaint c1 = new Complaint("CP-2026-DP1", "Hostel Wi-Fi", "Slow internet", "Hostel & Residence", "Hostel Administration");
        c1.setPriority(ComplaintPriority.CRITICAL);
        complaintRepository.save(c1);

        Complaint c2 = new Complaint("CP-2026-DP2", "Library PC", "Mouse broken", "Library & Study Spaces", "Central Library Committee");
        c2.setPriority(ComplaintPriority.LOW);
        complaintRepository.save(c2);

        Complaint c3 = new Complaint("CP-2026-DP3", "Hostel Water", "Leak in washroom", "Hostel & Residence", "Hostel Administration");
        c3.setPriority(ComplaintPriority.HIGH);
        complaintRepository.save(c3);

        // Filter by Department only
        Page<Complaint> deptPage = complaintRepository.searchWithFilters(
                null, null, null, "Hostel Administration", null, null, now, threshold, PageRequest.of(0, 10)
        );
        assertThat(deptPage.getContent()).hasSize(2);
        assertThat(deptPage.getContent()).allMatch(c -> c.getDepartment().equals("Hostel Administration"));

        // Filter by Priority only
        Page<Complaint> priorityPage = complaintRepository.searchWithFilters(
                null, null, null, null, ComplaintPriority.CRITICAL, null, now, threshold, PageRequest.of(0, 10)
        );
        assertThat(priorityPage.getContent()).hasSize(1);
        assertThat(priorityPage.getContent().get(0).getReferenceId()).isEqualTo("CP-2026-DP1");

        // Filter by Department and Priority combined
        Page<Complaint> combinedPage = complaintRepository.searchWithFilters(
                null, null, null, "Hostel Administration", ComplaintPriority.HIGH, null, now, threshold, PageRequest.of(0, 10)
        );
        assertThat(combinedPage.getContent()).hasSize(1);
        assertThat(combinedPage.getContent().get(0).getReferenceId()).isEqualTo("CP-2026-DP3");

        // Filter by Keyword + Department + Priority combined
        Page<Complaint> keywordCombined = complaintRepository.searchWithFilters(
                "washroom", null, null, "Hostel Administration", ComplaintPriority.HIGH, null, now, threshold, PageRequest.of(0, 10)
        );
        assertThat(keywordCombined.getContent()).hasSize(1);
        assertThat(keywordCombined.getContent().get(0).getReferenceId()).isEqualTo("CP-2026-DP3");

        // Non-matching priority
        Page<Complaint> emptyPage = complaintRepository.searchWithFilters(
                null, null, null, "Central Library Committee", ComplaintPriority.CRITICAL, null, now, threshold, PageRequest.of(0, 10)
        );
        assertThat(emptyPage.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Existing complaints without explicit priority default cleanly to MEDIUM on persistence and load")
    void shouldHandleNullPriorityCleanlyWithDefaultMedium() {
        Complaint complaint = new Complaint("CP-2026-LEGACY1", "Old ticket", "Legacy ticket before priority field", "Hostel & Residence", "Hostel Administration");
        complaint.setPriority(null);
        assertThat(complaint.getPriority()).isEqualTo(ComplaintPriority.MEDIUM);

        Complaint saved = complaintRepository.saveAndFlush(complaint);
        assertThat(saved.getPriority()).isEqualTo(ComplaintPriority.MEDIUM);

        Complaint reloaded = complaintRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(ComplaintPriority.MEDIUM);
    }

    @Test
    @DisplayName("Soft-deleted complaints (deleted = true) are excluded from active searches, counts, and aggregations")
    void shouldExcludeSoftDeletedComplaintsFromAllQueriesAndAggregations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(24);

        Complaint active = new Complaint("CP-2026-ACTIVE01", "Active issue", "Details", "Infrastructure & Labs", "Campus Facilities & Works");
        active.setStatus(ComplaintStatus.RESOLVED);
        active.setDeleted(false);
        complaintRepository.save(active);

        Complaint deleted = new Complaint("CP-2026-DELETED01", "Deleted issue", "Details", "Infrastructure & Labs", "Campus Facilities & Works");
        deleted.setStatus(ComplaintStatus.RESOLVED);
        deleted.setDeleted(true);
        deleted.setDeletedAt(now);
        deleted.setDeletedBy("admin");
        deleted.setDeletionReason("Test soft delete");
        complaintRepository.save(deleted);

        // countByDeletedFalse
        assertThat(complaintRepository.countByDeletedFalse()).isEqualTo(1);

        // findByReferenceIdAndDeletedFalse
        assertThat(complaintRepository.findByReferenceIdAndDeletedFalse("CP-2026-ACTIVE01")).isPresent();
        assertThat(complaintRepository.findByReferenceIdAndDeletedFalse("CP-2026-DELETED01")).isEmpty();

        // countWithFilters
        assertThat(complaintRepository.countWithFilters(ComplaintStatus.RESOLVED, null, null)).isEqualTo(1);

        // searchWithFilters
        Page<Complaint> searchPage = complaintRepository.searchWithFilters(
                null, null, null, null, null, null, now, threshold, PageRequest.of(0, 10)
        );
        assertThat(searchPage.getContent()).hasSize(1);
        assertThat(searchPage.getContent().get(0).getReferenceId()).isEqualTo("CP-2026-ACTIVE01");
    }
}

