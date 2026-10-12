package com.campuspulse.dto.insights;

import java.util.List;

/**
 * Transparent, evidence-backed administrative recommendation.
 */
public record RecommendationItem(
        String id,
        String title,
        RecommendationPriority priority,
        String category,
        String observation,
        String evidence,
        String recommendation,
        List<String> suggestedActions
) {}
