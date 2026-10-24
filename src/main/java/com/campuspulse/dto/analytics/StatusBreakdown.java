package com.campuspulse.dto.analytics;

import com.campuspulse.model.ComplaintStatus;

/**
 * Metric breakdown of complaints for a specific complaint lifecycle status.
 */
public record StatusBreakdown(
        ComplaintStatus status,
        String statusName,
        String displayName,
        long count,
        double percentage,
        String percentageFormatted,
        String color
) {

    public static StatusBreakdown of(ComplaintStatus status, long count, long total) {
        double pct = total > 0 ? (count * 100.0 / total) : 0.0;
        double roundedPct = Math.round(pct * 10.0) / 10.0;
        String formatted = String.format(java.util.Locale.US, "%.1f%%", pct);
        String color = defaultColorForStatus(status);
        String displayName = status != null ? status.getDisplayName() : "Unknown";
        String statusName = status != null ? status.name() : "";
        return new StatusBreakdown(status, statusName, displayName, count, roundedPct, formatted, color);
    }

    public static String defaultColorForStatus(ComplaintStatus status) {
        if (status == null) return "#64748B";
        return switch (status) {
            case SUBMITTED -> "#F59E0B";   // Amber
            case UNDER_REVIEW -> "#3B82F6"; // Blue
            case IN_PROGRESS -> "#8B5CF6";  // Purple
            case RESOLVED -> "#10B981";     // Emerald
        };
    }
}
