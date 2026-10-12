package com.campuspulse.dto.analytics;

/**
 * Metric breakdown of complaints for a specific campus department.
 */
public record DepartmentBreakdown(
        String department,
        long count,
        double percentage,
        String percentageFormatted
) {
    public static DepartmentBreakdown of(String department, long count, long total) {
        double pct = total > 0 ? (count * 100.0 / total) : 0.0;
        double roundedPct = Math.round(pct * 10.0) / 10.0;
        String formatted = String.format(java.util.Locale.US, "%.1f%%", pct);
        return new DepartmentBreakdown(department, count, roundedPct, formatted);
    }
}
