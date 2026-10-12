package com.campuspulse.model;

/**
 * Escalation lifecycle stages for complaints exceeding SLA response or resolution thresholds.
 */
public enum SlaEscalationStage {
    NONE("None", "Standard resolution lifecycle"),
    STAGE_1_RESPONSE_OVERDUE("Stage 1 Escalation", "Response SLA target breached; initial review delayed"),
    STAGE_2_RESOLUTION_OVERDUE("Stage 2 Escalation", "Resolution SLA target breached; case escalated to administrative leadership");

    private final String displayName;
    private final String description;

    SlaEscalationStage(String displayName, String description) {
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
