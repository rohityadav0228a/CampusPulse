package com.campuspulse.model;

/**
 * Priority classification level assigned to campus complaints.
 * Managed independently from lifecycle status and SLA deadlines.
 */
public enum ComplaintPriority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    private final String displayName;

    ComplaintPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
