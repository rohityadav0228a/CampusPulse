package com.campuspulse.dto.insights;

import java.util.List;

/**
 * Aggregate container holding all calculated insights and recommendations for the admin view,
 * including active SLA compliance and overdue grievance indicators.
 */
public record AdminInsightsData(
        int timeframeDays,
        PeriodComparison periodComparison,
        StatusDistribution statusDistribution,
        List<CategoryInsight> topCategories,
        List<DepartmentBacklog> departmentBacklogs,
        List<RecommendationItem> recommendations,
        boolean hasData,
        long highRiskBacklogCount,
        SlaSummary slaSummary
) {
    public AdminInsightsData(
            int timeframeDays,
            PeriodComparison periodComparison,
            StatusDistribution statusDistribution,
            List<CategoryInsight> topCategories,
            List<DepartmentBacklog> departmentBacklogs,
            List<RecommendationItem> recommendations,
            boolean hasData,
            long highRiskBacklogCount) {
        this(timeframeDays, periodComparison, statusDistribution, topCategories,
                departmentBacklogs, recommendations, hasData, highRiskBacklogCount,
                SlaSummary.of(0, 0, 0, 0, 0));
    }
}

