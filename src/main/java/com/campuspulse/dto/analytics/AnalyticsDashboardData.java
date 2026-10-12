package com.campuspulse.dto.analytics;

import java.util.List;

/**
 * Aggregated presentation data model for the Admin Analytics Dashboard.
 */
public record AnalyticsDashboardData(
        AnalyticsSummary summary,
        List<CategoryBreakdown> categoryBreakdown,
        List<DepartmentBreakdown> departmentBreakdown,
        List<DailyTrendPoint> trendPoints,
        String selectedStatus,
        String selectedCategory,
        Integer selectedDays
) {
    public boolean isEmpty() {
        return summary == null || summary.getTotal() == 0;
    }
}
