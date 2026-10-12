package com.campuspulse.dto.insights;

/**
 * High-level status distribution breakdown for unresolved vs resolved complaints.
 */
public record StatusDistribution(
        long totalCount,
        long unresolvedCount,
        long resolvedCount,
        long submittedCount,
        long underReviewCount,
        long inProgressCount,
        double resolutionRate,
        double unresolvedRate
) {
    public static StatusDistribution of(
            long total,
            long submitted,
            long underReview,
            long inProgress,
            long resolved) {
        long unresolved = submitted + underReview + inProgress;
        double resRate = total > 0 ? (resolved * 100.0) / total : 0.0;
        double unresRate = total > 0 ? (unresolved * 100.0) / total : 0.0;

        return new StatusDistribution(
                total,
                unresolved,
                resolved,
                submitted,
                underReview,
                inProgress,
                Math.round(resRate * 10.0) / 10.0,
                Math.round(unresRate * 10.0) / 10.0
        );
    }
}
