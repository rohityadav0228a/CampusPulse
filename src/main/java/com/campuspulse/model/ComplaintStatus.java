package com.campuspulse.model;

/**
 * Lifecycle status of a campus complaint.
 */
public enum ComplaintStatus {
    SUBMITTED("Submitted", "Initial grievance filed by student"),
    UNDER_REVIEW("Under Review", "Complaint is under administrative triage"),
    IN_PROGRESS("In Progress", "Concerned department is taking corrective action"),
    RESOLVED("Resolved", "Issue has been addressed and closed");

    private final String displayName;
    private final String description;

    ComplaintStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
