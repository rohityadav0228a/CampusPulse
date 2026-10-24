package com.campuspulse.service;

import com.campuspulse.dto.analytics.AnalyticsDashboardData;
import com.campuspulse.dto.analytics.AnalyticsSummary;
import com.campuspulse.dto.analytics.CategoryBreakdown;
import com.campuspulse.dto.analytics.DepartmentBreakdown;
import com.campuspulse.dto.analytics.StatusBreakdown;
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

    @Test
    @DisplayName("Status breakdown computes counts, percentages, and distinct colors for all statuses")
    void shouldCalculateStatusBreakdownAccurately() {
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(20L);
        when(complaintRepository.countGroupByStatus(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{ComplaintStatus.SUBMITTED, 5L},
                new Object[]{ComplaintStatus.UNDER_REVIEW, 3L},
                new Object[]{ComplaintStatus.IN_PROGRESS, 2L},
                new Object[]{ComplaintStatus.RESOLVED, 10L}
        ));
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 30);
        List<StatusBreakdown> statusList = data.statusBreakdown();

        assertThat(statusList).hasSize(4);

        StatusBreakdown submitted = statusList.stream()
                .filter(s -> s.status() == ComplaintStatus.SUBMITTED).findFirst().orElseThrow();
        assertThat(submitted.count()).isEqualTo(5L);
        assertThat(submitted.percentage()).isEqualTo(25.0);
        assertThat(submitted.percentageFormatted()).isEqualTo("25.0%");
        assertThat(submitted.color()).isEqualTo("#F59E0B");

        StatusBreakdown resolved = statusList.stream()
                .filter(s -> s.status() == ComplaintStatus.RESOLVED).findFirst().orElseThrow();
        assertThat(resolved.count()).isEqualTo(10L);
        assertThat(resolved.percentage()).isEqualTo(50.0);
        assertThat(resolved.percentageFormatted()).isEqualTo("50.0%");
        assertThat(resolved.color()).isEqualTo("#10B981");
    }

    @Test
    @DisplayName("Category breakdown includes all predefined categories with designated colors")
    void shouldIncludeAllAllowedCategoriesWithDesignatedColors() {
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(10L);
        when(complaintRepository.countGroupByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"Hostel & Residence", 6L},
                new Object[]{"Academic Affairs", 4L}
        ));
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 30);
        List<CategoryBreakdown> categories = data.categoryBreakdown();

        assertThat(categories).hasSize(6);

        CategoryBreakdown hostel = categories.stream()
                .filter(c -> "Hostel & Residence".equals(c.category())).findFirst().orElseThrow();
        assertThat(hostel.count()).isEqualTo(6L);
        assertThat(hostel.percentage()).isEqualTo(60.0);
        assertThat(hostel.color()).isEqualTo("#7C3AED");

        CategoryBreakdown academic = categories.stream()
                .filter(c -> "Academic Affairs".equals(c.category())).findFirst().orElseThrow();
        assertThat(academic.count()).isEqualTo(4L);
        assertThat(academic.percentage()).isEqualTo(40.0);
        assertThat(academic.color()).isEqualTo("#4F46E5");

        CategoryBreakdown emptyCat = categories.stream()
                .filter(c -> "Canteen & Dining".equals(c.category())).findFirst().orElseThrow();
        assertThat(emptyCat.count()).isEqualTo(0L);
        assertThat(emptyCat.percentage()).isEqualTo(0.0);
        assertThat(emptyCat.percentageFormatted()).isEqualTo("0.0%");
        assertThat(emptyCat.color()).isEqualTo("#EA580C");
    }

    @Test
    @DisplayName("Empty dataset calculates 0 counts and 0.0% for category and status breakdown without error")
    void shouldHandleEmptyDatasetInBreakdownsWithoutError() {
        when(complaintRepository.countWithFilters(any(), any(), any())).thenReturn(0L);
        when(complaintRepository.countGroupByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByCategory(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countGroupByDepartment(any(), any(), any())).thenReturn(Collections.emptyList());
        when(complaintRepository.countDailyTrend(any(), any(), any())).thenReturn(Collections.emptyList());

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(null, null, 30);

        List<StatusBreakdown> statuses = data.statusBreakdown();
        assertThat(statuses).hasSize(4);
        for (StatusBreakdown sb : statuses) {
            assertThat(sb.count()).isEqualTo(0L);
            assertThat(sb.percentage()).isEqualTo(0.0);
            assertThat(sb.percentageFormatted()).isEqualTo("0.0%");
            assertThat(sb.color()).isNotBlank();
        }

        List<CategoryBreakdown> categories = data.categoryBreakdown();
        assertThat(categories).hasSize(6);
        for (CategoryBreakdown cb : categories) {
            assertThat(cb.count()).isEqualTo(0L);
            assertThat(cb.percentage()).isEqualTo(0.0);
            assertThat(cb.percentageFormatted()).isEqualTo("0.0%");
            assertThat(cb.color()).isNotBlank();
        }
    }
}
