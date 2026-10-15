package com.campuspulse.dto.analytics;

import java.util.List;

/**
 * Aggregated presentation data model for the Admin Analytics Dashboard.
 * Includes complaint summaries, domain breakdowns, volume trends, and
 * student resolution satisfaction feedback metrics.
 */
public record AnalyticsDashboardData(
        AnalyticsSummary summary,
        List<CategoryBreakdown> categoryBreakdown,
        List<DepartmentBreakdown> departmentBreakdown,
        List<DailyTrendPoint> trendPoints,
        String selectedStatus,
        String selectedCategory,
        Integer selectedDays,
        FeedbackMetrics feedbackMetrics
) {

    /**
     * Backward-compatible 7-parameter constructor defaulting feedbackMetrics to empty.
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
        this(summary, categoryBreakdown, departmentBreakdown, trendPoints, selectedStatus, selectedCategory, selectedDays, FeedbackMetrics.empty());
    }

    public boolean isEmpty() {
        return summary == null || summary.getTotal() == 0;
    }
}
