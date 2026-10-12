package com.campuspulse.dto.insights;

/**
 * Metric summary of complaint category volume and campus share.
 */
public record CategoryInsight(
        String category,
        long totalCount,
        double sharePercentage,
        long currentPeriodCount,
        long previousPeriodCount,
        boolean isTopCategory
) {
    public static CategoryInsight of(
            String category,
            long totalCount,
            long campusTotal,
            long currentPeriodCount,
            long previousPeriodCount,
            boolean isTop) {
        double share = campusTotal > 0 ? (totalCount * 100.0) / campusTotal : 0.0;
        return new CategoryInsight(
                category,
                totalCount,
                Math.round(share * 10.0) / 10.0,
                currentPeriodCount,
                previousPeriodCount,
                isTop
        );
    }
}
