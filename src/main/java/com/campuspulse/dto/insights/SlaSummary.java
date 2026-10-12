package com.campuspulse.dto.insights;

/**
 * Metric summary of active SLA compliance, response breaches, resolution breaches,
 * and complaints approaching deadline.
 */
public record SlaSummary(
        long totalComplaints,
        long activeUnresolved,
        long responseOverdueCount,
        long resolutionOverdueCount,
        long approachingDeadlineCount,
        long compliantCount,
        double complianceRate
) {
    public static SlaSummary of(
            long totalComplaints,
            long activeUnresolved,
            long responseOverdue,
            long resolutionOverdue,
            long approaching) {

        long totalOverdue = responseOverdue + resolutionOverdue;
        long compliant = Math.max(0, activeUnresolved - totalOverdue);
        double rate = activeUnresolved > 0 ? (compliant * 100.0) / activeUnresolved : 100.0;

        return new SlaSummary(
                totalComplaints,
                activeUnresolved,
                responseOverdue,
                resolutionOverdue,
                approaching,
                compliant,
                Math.round(rate * 10.0) / 10.0
        );
    }

    public boolean isBreached() {
        return responseOverdueCount > 0 || resolutionOverdueCount > 0;
    }

    public long activeUnresolvedCount() {
        return activeUnresolved;
    }
}
