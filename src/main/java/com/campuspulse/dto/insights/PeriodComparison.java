package com.campuspulse.dto.insights;

import java.util.Locale;

/**
 * Encapsulates complaint volume comparison between a recent window and
 * an immediately preceding comparable window.
 */
public record PeriodComparison(
        int windowDays,
        long currentCount,
        long previousCount,
        long absoluteChange,
        Double percentageChange,
        TrendDirection direction,
        String summaryText,
        String currentPeriodLabel,
        String previousPeriodLabel
) {
    public boolean hasChange() {
        return absoluteChange != 0;
    }

    public boolean isPositiveIncrease() {
        return direction == TrendDirection.INCREASED;
    }

    public boolean isDecline() {
        return direction == TrendDirection.DECREASED;
    }

    public boolean isStable() {
        return direction == TrendDirection.STABLE;
    }

    public String formattedPercentage() {
        if (percentageChange == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%+.1f%%", percentageChange);
    }
}
