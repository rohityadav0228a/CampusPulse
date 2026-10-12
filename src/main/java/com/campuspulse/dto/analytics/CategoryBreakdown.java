package com.campuspulse.dto.analytics;

/**
 * Metric breakdown of complaints for a specific campus category.
 */
public record CategoryBreakdown(
        String category,
        long count,
        double percentage,
        String percentageFormatted
) {
    public static CategoryBreakdown of(String category, long count, long total) {
        double pct = total > 0 ? (count * 100.0 / total) : 0.0;
        double roundedPct = Math.round(pct * 10.0) / 10.0;
        String formatted = String.format(java.util.Locale.US, "%.1f%%", pct);
        return new CategoryBreakdown(category, count, roundedPct, formatted);
    }
}
