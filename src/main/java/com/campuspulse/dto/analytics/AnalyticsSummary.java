package com.campuspulse.dto.analytics;

/**
 * Summary metrics for admin analytics overview.
 */
public class AnalyticsSummary {

    private final long total;
    private final long pending;        // SUBMITTED
    private final long underReview;   // UNDER_REVIEW
    private final long inProgress;    // IN_PROGRESS
    private final long resolved;      // RESOLVED
    private final double resolutionRate;
    private final String resolutionRateFormatted;

    public AnalyticsSummary(long total, long pending, long underReview, long inProgress, long resolved) {
        this.total = total;
        this.pending = pending;
        this.underReview = underReview;
        this.inProgress = inProgress;
        this.resolved = resolved;

        if (total > 0) {
            double rate = (resolved * 100.0) / total;
            this.resolutionRate = Math.round(rate * 10.0) / 10.0;
            this.resolutionRateFormatted = String.format(java.util.Locale.US, "%.1f%%", rate);
        } else {
            this.resolutionRate = 0.0;
            this.resolutionRateFormatted = "0.0%";
        }
    }

    public long getTotal() {
        return total;
    }

    public long getPending() {
        return pending;
    }

    public long getUnderReview() {
        return underReview;
    }

    public long getInProgress() {
        return inProgress;
    }

    public long getResolved() {
        return resolved;
    }

    public double getResolutionRate() {
        return resolutionRate;
    }

    public String getResolutionRateFormatted() {
        return resolutionRateFormatted;
    }
}
