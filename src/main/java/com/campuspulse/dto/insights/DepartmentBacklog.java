package com.campuspulse.dto.insights;

/**
 * Metric breakdown of unresolved complaints per campus department.
 */
public record DepartmentBacklog(
        String department,
        long unresolvedCount,
        long totalCount,
        double backlogPercentage,
        double campusSharePercentage,
        boolean highBacklogRisk
) {
    public static DepartmentBacklog of(
            String department,
            long unresolvedCount,
            long totalCount,
            long totalCampusUnresolved) {
        double backlogPct = totalCount > 0 ? (unresolvedCount * 100.0) / totalCount : 0.0;
        double sharePct = totalCampusUnresolved > 0 ? (unresolvedCount * 100.0) / totalCampusUnresolved : 0.0;
        boolean isRisk = unresolvedCount >= 3 && (sharePct >= 30.0 || backlogPct >= 65.0);

        return new DepartmentBacklog(
                department,
                unresolvedCount,
                totalCount,
                Math.round(backlogPct * 10.0) / 10.0,
                Math.round(sharePct * 10.0) / 10.0,
                isRisk
        );
    }
}
