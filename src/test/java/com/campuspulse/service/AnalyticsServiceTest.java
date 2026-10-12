package com.campuspulse.service;

import com.campuspulse.dto.analytics.AnalyticsDashboardData;
import com.campuspulse.dto.analytics.AnalyticsSummary;
import com.campuspulse.dto.analytics.CategoryBreakdown;
import com.campuspulse.dto.analytics.DepartmentBreakdown;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private ComplaintRepository complaintRepository;
    private ComplaintService complaintService;
    private AnalyticsService analyticsService;

    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "Academic Affairs",
            "Hostel & Residence",
            "Infrastructure & Labs",
            "Library & Study Spaces",
            "Canteen & Dining",
            "Transit & Campus Grounds"
    );

    private static final List<String> ALLOWED_DEPARTMENTS = List.of(
            "Academic Affairs Division",
            "Hostel Administration",
            "Campus Facilities & Works",
            "Central Library Committee",
            "Canteen Committee & Food Services",
            "Security & Transport Office"
    );

    @BeforeEach
    void setUp() {
        complaintRepository = mock(ComplaintRepository.class);
        complaintService = mock(ComplaintService.class);

        when(complaintService.getAllowedCategories()).thenReturn(ALLOWED_CATEGORIES);
        when(complaintService.getAllowedDepartments()).thenReturn(ALLOWED_DEPARTMENTS);

        analyticsService = new AnalyticsService(complaintRepository, complaintService);
    }

    @Test
    @DisplayName("Empty database returns zero counts and 0.0% resolution rate gracefully")
    void shouldHandleEmptyDatabaseGracefully() {
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(0L);
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 30);

        assertThat(data).isNotNull();
        assertThat(data.isEmpty()).isTrue();

        AnalyticsSummary summary = data.summary();
        assertThat(summary.getTotal()).isEqualTo(0);
        assertThat(summary.getPending()).isEqualTo(0);
        assertThat(summary.getUnderReview()).isEqualTo(0);
        assertThat(summary.getInProgress()).isEqualTo(0);
        assertThat(summary.getResolved()).isEqualTo(0);
        assertThat(summary.getResolutionRate()).isEqualTo(0.0);
        assertThat(summary.getResolutionRateFormatted()).isEqualTo("0.0%");

        assertThat(data.trendPoints()).isEmpty();
    }

    @Test
    @DisplayName("Summary KPI metrics and resolution rate calculate accurately")
    void shouldCalculateSummaryMetricsAndResolutionRateAccurately() {
        // Total = 10, SUBMITTED = 2, UNDER_REVIEW = 3, IN_PROGRESS = 1, RESOLVED = 4 (Resolution rate = 40.0%)
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(10L);
        when(complaintRepository.countGroupByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.SUBMITTED, 2L},
                new Object[]{ComplaintStatus.UNDER_REVIEW, 3L},
                new Object[]{ComplaintStatus.IN_PROGRESS, 1L},
                new Object[]{ComplaintStatus.RESOLVED, 4L}
        ));
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 30);

        AnalyticsSummary summary = data.summary();
        assertThat(summary.getTotal()).isEqualTo(10);
        assertThat(summary.getPending()).isEqualTo(2);
        assertThat(summary.getUnderReview()).isEqualTo(3);
        assertThat(summary.getInProgress()).isEqualTo(1);
        assertThat(summary.getResolved()).isEqualTo(4);
        assertThat(summary.getResolutionRate()).isEqualTo(40.0);
        assertThat(summary.getResolutionRateFormatted()).isEqualTo("40.0%");
    }

    @Test
    @DisplayName("Category and department breakdowns aggregate percentages accurately")
    void shouldCalculateCategoryAndDepartmentBreakdowns() {
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(4L);
        when(complaintRepository.countGroupByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.RESOLVED, 4L}
        ));
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"Infrastructure & Labs", 3L},
                new Object[]{"Hostel & Residence", 1L}
        ));
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"Campus Facilities & Works", 3L},
                new Object[]{"Hostel Administration", 1L}
        ));
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 30);

        List<CategoryBreakdown> catList = data.categoryBreakdown();
        assertThat(catList).isNotEmpty();

        CategoryBreakdown topCat = catList.getFirst();
        assertThat(topCat.category()).isEqualTo("Infrastructure & Labs");
        assertThat(topCat.count()).isEqualTo(3);
        assertThat(topCat.percentage()).isEqualTo(75.0);
        assertThat(topCat.percentageFormatted()).isEqualTo("75.0%");

        List<DepartmentBreakdown> deptList = data.departmentBreakdown();
        assertThat(deptList).isNotEmpty();

        DepartmentBreakdown topDept = deptList.getFirst();
        assertThat(topDept.department()).isEqualTo("Campus Facilities & Works");
        assertThat(topDept.count()).isEqualTo(3);
        assertThat(topDept.percentage()).isEqualTo(75.0);
        assertThat(topDept.percentageFormatted()).isEqualTo("75.0%");
    }

    @Test
    @DisplayName("Trend points generate continuous daily series when data exists")
    void shouldGenerateContinuousTrendPoints() {
        LocalDate today = LocalDate.now();
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(2L);
        when(complaintRepository.countGroupByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());

        // Return a trend point for today
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{today, 2L}
        ));

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 7);

        assertThat(data.trendPoints()).hasSize(7);
        assertThat(data.trendPoints().getLast().count()).isEqualTo(2);
        assertThat(data.trendPoints().getFirst().count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Filtering by specific status computes filtered total correctly")
    void shouldFilterByStatusCorrectly() {
        when(complaintRepository.countWithFilters(eq(ComplaintStatus.RESOLVED), any(), any())).thenReturn(5L);
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(ComplaintStatus.RESOLVED, null, 30);

        AnalyticsSummary summary = data.summary();
        assertThat(summary.getTotal()).isEqualTo(5);
        assertThat(summary.getResolved()).isEqualTo(5);
        assertThat(summary.getPending()).isEqualTo(0);
        assertThat(summary.getResolutionRate()).isEqualTo(100.0);
        assertThat(data.selectedStatus()).isEqualTo("RESOLVED");
    }
}
