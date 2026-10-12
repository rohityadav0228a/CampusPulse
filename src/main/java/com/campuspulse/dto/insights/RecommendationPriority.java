package com.campuspulse.dto.insights;

/**
 * Priority classification for administrative grievance recommendations.
 */
public enum RecommendationPriority {
    HIGH("High Priority", "urgent"),
    MEDIUM("Medium Priority", "warning"),
    INFO("Informational", "info"),
    POSITIVE("Operational Strength", "success");

    private final String displayName;
    private final String badgeType;

    RecommendationPriority(String displayName, String badgeType) {
        this.displayName = displayName;
        this.badgeType = badgeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeType() {
        return badgeType;
    }
}
