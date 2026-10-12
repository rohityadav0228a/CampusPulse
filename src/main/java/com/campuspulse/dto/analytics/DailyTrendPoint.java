package com.campuspulse.dto.analytics;

/**
 * Single data point on the complaint timeline / trend series.
 */
public record DailyTrendPoint(
        String date,
        String label,
        long count
) {
}
