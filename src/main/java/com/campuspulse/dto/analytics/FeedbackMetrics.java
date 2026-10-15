package com.campuspulse.dto.analytics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregate metrics data transfer object for student resolution feedback.
 * Used in administrative analytics dashboards to visualize satisfaction trends
 * without exposing private student comments.
 */
public record FeedbackMetrics(
        long totalFeedback,
        double averageRating,
        String averageRatingFormatted,
        Map<Integer, Long> ratingDistribution,
        Map<Integer, Double> ratingPercentages
) {

    /**
     * Creates an empty, zero-initialized FeedbackMetrics record.
     *
     * @return empty metrics record
     */
    public static FeedbackMetrics empty() {
        Map<Integer, Long> dist = new LinkedHashMap<>();
        Map<Integer, Double> pcts = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            dist.put(i, 0L);
            pcts.put(i, 0.0);
        }
        return new FeedbackMetrics(0L, 0.0, "N/A", dist, pcts);
    }
}
