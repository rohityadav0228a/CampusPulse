package com.campuspulse.dto.analytics;

/**
 * Metric breakdown of complaints for a specific campus category.
 */
public record CategoryBreakdown(
        String category,
        long count,
        double percentage,
        String percentageFormatted,
        String color
) {

    /**
     * Backward-compatible constructor defaulting the visual chart color based on category.
     */
    public CategoryBreakdown(String category, long count, double percentage, String percentageFormatted) {
        this(category, count, percentage, percentageFormatted, defaultColorForCategory(category));
    }

    public static CategoryBreakdown of(String category, long count, long total) {
        double pct = total > 0 ? (count * 100.0 / total) : 0.0;
        double roundedPct = Math.round(pct * 10.0) / 10.0;
        String formatted = String.format(java.util.Locale.US, "%.1f%%", pct);
        String color = defaultColorForCategory(category);
        return new CategoryBreakdown(category, count, roundedPct, formatted, color);
    }

    public static String defaultColorForCategory(String category) {
        if (category == null) return "#64748B";
        return switch (category.trim()) {
            case "Academic Affairs" -> "#4F46E5";        // Indigo
            case "Hostel & Residence" -> "#7C3AED";      // Violet
            case "Infrastructure & Labs" -> "#2563EB";   // Blue
            case "Library & Study Spaces" -> "#0D9488";  // Teal
            case "Canteen & Dining" -> "#EA580C";        // Orange
            case "Transit & Campus Grounds" -> "#16A34A";// Green
            default -> "#6366F1";
        };
    }
}
