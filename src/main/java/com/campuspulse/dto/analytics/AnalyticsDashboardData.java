package com.campuspulse.dto.analytics;

import java.util.List;

/**
 * Aggregated presentation data model for the Admin Analytics Dashboard.
 * Includes complaint summaries, domain breakdowns, status breakdowns, volume trends,
 * and student resolution satisfaction feedback metrics.
 */
public record AnalyticsDashboardData(
        AnalyticsSummary summary,
        List<CategoryBreakdown> categoryBreakdown,
        List<DepartmentBreakdown> departmentBreakdown,
        List<DailyTrendPoint> trendPoints,
        String selectedStatus,
        String selectedCategory,
        Integer selectedDays,
        FeedbackMetrics feedbackMetrics,
        List<StatusBreakdown> statusBreakdown
) {

    /**
     * Backward-compatible 8-parameter constructor defaulting statusBreakdown to empty list.
     */
    public AnalyticsDashboardData(
            AnalyticsSummary summary,
            List<CategoryBreakdown> categoryBreakdown,
            List<DepartmentBreakdown> departmentBreakdown,
            List<DailyTrendPoint> trendPoints,
            String selectedStatus,
            String selectedCategory,
            Integer selectedDays,
            FeedbackMetrics feedbackMetrics
    ) {
        this(summary, categoryBreakdown, departmentBreakdown, trendPoints, selectedStatus, selectedCategory, selectedDays, feedbackMetrics, List.of());
    }

    /**
     * Backward-compatible 7-parameter constructor defaulting feedbackMetrics to empty and statusBreakdown to empty list.
     */
    public AnalyticsDashboardData(
            AnalyticsSummary summary,
            List<CategoryBreakdown> categoryBreakdown,
            List<DepartmentBreakdown> departmentBreakdown,
            List<DailyTrendPoint> trendPoints,
            String selectedStatus,
            String selectedCategory,
            Integer selectedDays
    ) {
        this(summary, categoryBreakdown, departmentBreakdown, trendPoints, selectedStatus, selectedCategory, selectedDays, FeedbackMetrics.empty(), List.of());
    }

    public boolean isEmpty() {
        return summary == null || summary.getTotal() == 0;
    }
}
