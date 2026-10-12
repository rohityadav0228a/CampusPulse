package com.campuspulse.service;

import com.campuspulse.dto.insights.AdminInsightsData;
import com.campuspulse.dto.insights.DepartmentBacklog;
import com.campuspulse.dto.insights.PeriodComparison;
import com.campuspulse.dto.insights.RecommendationItem;
import com.campuspulse.dto.insights.RecommendationPriority;
import com.campuspulse.dto.insights.StatusDistribution;
import com.campuspulse.dto.insights.TrendDirection;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintService complaintService;

    @InjectMocks
    private InsightsService insightsService;

    private static final List<String> CATEGORIES = List.of(
            "Academic Affairs",
            "Hostel & Residence",
            "Infrastructure & Labs",
            "Campus Security & Safety",
            "Cafeteria & Dining",
            "Administrative & Accounts"
    );

    private static final List<String> DEPARTMENTS = List.of(
            "Campus Facilities & Works",
            "Hostel Administration",
            "Academic Affairs Division",
            "Campus Security Command",
            "Student Welfare & Dining"
    );

    @BeforeEach
    void setUp() {
        when(complaintService.getAllowedCategories()).thenReturn(CATEGORIES);
        when(complaintService.getAllowedDepartments()).thenReturn(DEPARTMENTS);
    }

    @Test
    @DisplayName("Empty database yields safe 0 metrics, stable trend, and baseline initialization recommendation")
    void shouldHandleEmptyDatabaseGracefully() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(0L);
        when(complaintRepository.countBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);
        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        assertThat(data).isNotNull();
        assertThat(data.hasData()).isFalse();
        assertThat(data.timeframeDays()).isEqualTo(14);

        StatusDistribution status = data.statusDistribution();
        assertThat(status.totalCount()).isEqualTo(0);
        assertThat(status.unresolvedCount()).isEqualTo(0);
        assertThat(status.resolvedCount()).isEqualTo(0);
        assertThat(status.resolutionRate()).isEqualTo(0.0);

        PeriodComparison comp = data.periodComparison();
        assertThat(comp.currentCount()).isEqualTo(0);
        assertThat(comp.previousCount()).isEqualTo(0);
        assertThat(comp.direction()).isEqualTo(TrendDirection.STABLE);

        assertThat(data.recommendations()).hasSize(1);
        RecommendationItem rec = data.recommendations().get(0);
        assertThat(rec.id()).isEqualTo("REC-EMPTY-DATA");
        assertThat(rec.priority()).isEqualTo(RecommendationPriority.INFO);
        assertThat(rec.evidence()).contains("Total registered complaints: 0");
    }

    @Test
    @DisplayName("Surge in complaint volume triggers high/medium priority surge recommendation without fabricated numbers")
    void shouldTriggerSurgeRecommendationWhenVolumeIncreases() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(14L);
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.SUBMITTED, 4L},
                new Object[]{ComplaintStatus.IN_PROGRESS, 4L},
                new Object[]{ComplaintStatus.RESOLVED, 6L}
        ));
        // Current: 10 complaints in past 14 days, Previous: 4 complaints in earlier 14 days (+150%)
        LocalDateTime now = LocalDateTime.now();
        when(complaintRepository.countBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    LocalDateTime start = inv.getArgument(0);
                    // If start is within 15 days, it's current window
                    if (start.isAfter(now.minusDays(15))) {
                        return 10L;
                    }
                    return 4L;
                });

        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        PeriodComparison comp = data.periodComparison();
        assertThat(comp.currentCount()).isEqualTo(10L);
        assertThat(comp.previousCount()).isEqualTo(4L);
        assertThat(comp.absoluteChange()).isEqualTo(6L);
        assertThat(comp.percentageChange()).isEqualTo(150.0);
        assertThat(comp.direction()).isEqualTo(TrendDirection.INCREASED);

        Optional<RecommendationItem> surgeRec = data.recommendations().stream()
                .filter(r -> "REC-SURGE-ALERT".equals(r.id()))
                .findFirst();

        assertThat(surgeRec).isPresent();
        assertThat(surgeRec.get().priority()).isEqualTo(RecommendationPriority.HIGH);
        assertThat(surgeRec.get().evidence()).contains("Current 14-day window recorded 10 complaints versus 4");
    }

    @Test
    @DisplayName("Decline in complaints triggers informational calming trend recommendation")
    void shouldTriggerDeclineRecommendationWhenVolumeDecreases() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(10L);
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.RESOLVED, 8L},
                new Object[]{ComplaintStatus.UNDER_REVIEW, 2L}
        ));

        LocalDateTime now = LocalDateTime.now();
        // Current: 2 complaints, Previous: 8 complaints (-75.0%)
        when(complaintRepository.countBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    LocalDateTime start = inv.getArgument(0);
                    if (start.isAfter(now.minusDays(15))) {
                        return 2L;
                    }
                    return 8L;
                });

        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        PeriodComparison comp = data.periodComparison();
        assertThat(comp.currentCount()).isEqualTo(2L);
        assertThat(comp.previousCount()).isEqualTo(8L);
        assertThat(comp.percentageChange()).isEqualTo(-75.0);
        assertThat(comp.direction()).isEqualTo(TrendDirection.DECREASED);

        Optional<RecommendationItem> declineRec = data.recommendations().stream()
                .filter(r -> "REC-VOLUME-DECLINE".equals(r.id()))
                .findFirst();

        assertThat(declineRec).isPresent();
        assertThat(declineRec.get().priority()).isEqualTo(RecommendationPriority.INFO);
        assertThat(declineRec.get().evidence()).contains("Recorded 2 complaints over the past 14 days versus 8");
    }

    @Test
    @DisplayName("Zero previous period baseline avoids division by zero and formats safe message")
    void shouldHandleZeroPreviousBaselineCleanly() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(5L);
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.SUBMITTED, 5L}
        ));

        LocalDateTime now = LocalDateTime.now();
        // Current: 5 complaints, Previous: 0 complaints
        when(complaintRepository.countBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    LocalDateTime start = inv.getArgument(0);
                    if (start.isAfter(now.minusDays(15))) {
                        return 5L;
                    }
                    return 0L;
                });

        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        PeriodComparison comp = data.periodComparison();
        assertThat(comp.currentCount()).isEqualTo(5L);
        assertThat(comp.previousCount()).isEqualTo(0L);
        assertThat(comp.percentageChange()).isNull(); // No division by zero!
        assertThat(comp.direction()).isEqualTo(TrendDirection.INCREASED);
        assertThat(comp.summaryText()).contains("+5 complaints (new volume, no complaints in prior 14 days)");
    }

    @Test
    @DisplayName("Large unresolved department backlog is flagged as high risk and generates targeted recommendation")
    void shouldIdentifyDepartmentBacklogsAndTriggerRecommendation() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(10L);
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.SUBMITTED, 4L},
                new Object[]{ComplaintStatus.UNDER_REVIEW, 2L},
                new Object[]{ComplaintStatus.RESOLVED, 4L}
        ));
        when(complaintRepository.countBetween(any(), any())).thenReturn(1L);
        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());

        // Total unresolved = 6 (4 submitted + 2 under review)
        // Campus Facilities & Works has 5 unresolved out of 6 total complaints in that department
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(List.<Object[]>of(
                new Object[]{"Campus Facilities & Works", 5L},
                new Object[]{"Hostel Administration", 1L}
        ));
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(List.<Object[]>of(
                new Object[]{"Campus Facilities & Works", 6L},
                new Object[]{"Hostel Administration", 4L}
        ));

        AdminInsightsData data = insightsService.getInsightsData(14);

        assertThat(data.highRiskBacklogCount()).isEqualTo(1L);

        DepartmentBacklog backlog = data.departmentBacklogs().stream()
                .filter(b -> "Campus Facilities & Works".equals(b.department()))
                .findFirst().orElseThrow();

        assertThat(backlog.unresolvedCount()).isEqualTo(5L);
        assertThat(backlog.totalCount()).isEqualTo(6L);
        assertThat(backlog.highBacklogRisk()).isTrue();
        assertThat(backlog.campusSharePercentage()).isEqualTo(83.3); // 5/6 = 83.3% of campus unresolved

        Optional<RecommendationItem> backlogRec = data.recommendations().stream()
                .filter(r -> r.id().startsWith("REC-BACKLOG-CAMPUS_FACILITIES"))
                .findFirst();

        assertThat(backlogRec).isPresent();
        assertThat(backlogRec.get().priority()).isEqualTo(RecommendationPriority.HIGH);
        assertThat(backlogRec.get().evidence()).contains("Campus Facilities & Works holds 5 unresolved complaints (83.3%");
    }

    @Test
    @DisplayName("High category dominance generates systemic friction recommendation")
    void shouldTriggerCategoryDominanceRecommendation() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(10L);
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.RESOLVED, 10L}
        ));
        when(complaintRepository.countBetween(any(), any())).thenReturn(0L);

        // Infrastructure & Labs accounts for 6 of 10 complaints (60%)
        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(List.<Object[]>of(
                new Object[]{"Infrastructure & Labs", 6L},
                new Object[]{"Hostel & Residence", 4L}
        ));
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        Optional<RecommendationItem> catRec = data.recommendations().stream()
                .filter(r -> "REC-CAT-DOMINANCE".equals(r.id()))
                .findFirst();

        assertThat(catRec).isPresent();
        assertThat(catRec.get().evidence()).contains("Infrastructure & Labs accounts for 6 out of 10 total campus complaints (60.0%");
    }

    @Test
    @DisplayName("Bottleneck of complaints awaiting initial review generates triage velocity recommendation")
    void shouldTriggerTriageDelayRecommendation() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(8L);
        // 5 complaints in SUBMITTED (62.5%), 3 in RESOLVED
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.SUBMITTED, 5L},
                new Object[]{ComplaintStatus.RESOLVED, 3L}
        ));
        when(complaintRepository.countBetween(any(), any())).thenReturn(0L);
        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        Optional<RecommendationItem> triageRec = data.recommendations().stream()
                .filter(r -> "REC-TRIAGE-DELAY".equals(r.id()))
                .findFirst();

        assertThat(triageRec).isPresent();
        assertThat(triageRec.get().priority()).isEqualTo(RecommendationPriority.HIGH);
        assertThat(triageRec.get().evidence()).contains("5 complaints are currently in SUBMITTED status (62.5%");
    }

    @Test
    @DisplayName("High resolution rate and balanced workload triggers operational strength praise")
    void shouldTriggerHighResolutionPraiseWhenThresholdMet() {
        when(complaintRepository.countWithFilters(null, null, null)).thenReturn(10L);
        // 8 resolved (80%), 2 in progress
        when(complaintRepository.countGroupByStatus(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.RESOLVED, 8L},
                new Object[]{ComplaintStatus.IN_PROGRESS, 2L}
        ));
        when(complaintRepository.countBetween(any(), any())).thenReturn(0L);
        when(complaintRepository.countGroupByCategory(null, null, null)).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countUnresolvedGroupByDepartment(any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(null, null, null)).thenReturn(Collections.emptyList());

        AdminInsightsData data = insightsService.getInsightsData(14);

        Optional<RecommendationItem> praiseRec = data.recommendations().stream()
                .filter(r -> "REC-HIGH-RESOLUTION".equals(r.id()))
                .findFirst();

        assertThat(praiseRec).isPresent();
        assertThat(praiseRec.get().priority()).isEqualTo(RecommendationPriority.POSITIVE);
        assertThat(praiseRec.get().evidence()).contains("8 out of 10 registered complaints have reached RESOLVED status (80.0% resolution rate)");
    }
}
