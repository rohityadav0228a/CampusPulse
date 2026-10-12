package com.campuspulse.dto;

/**
 * Result representation for internal AI classification processing.
 */
public record AiClassificationResult(
        Status status,
        String category,
        Double confidence,
        String warning,
        String modelVersion,
        String errorMessage
) {
    public enum Status {
        SUCCESS,
        INVALID_CATEGORY,
        SERVICE_UNAVAILABLE,
        INVALID_INPUT
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isCategoryInvalid() {
        return status == Status.INVALID_CATEGORY;
    }

    public boolean isUnavailable() {
        return status == Status.SERVICE_UNAVAILABLE;
    }

    public static AiClassificationResult success(String category, Double confidence, String warning, String modelVersion) {
        return new AiClassificationResult(Status.SUCCESS, category, confidence, warning, modelVersion, null);
    }

    public static AiClassificationResult invalidCategory(String category) {
        return new AiClassificationResult(Status.INVALID_CATEGORY, category, null, null, null,
                "AI suggested category is not recognized in the campus categories whitelist.");
    }

    public static AiClassificationResult unavailable(String errorMessage) {
        return new AiClassificationResult(Status.SERVICE_UNAVAILABLE, null, null, null, null, errorMessage);
    }

    public static AiClassificationResult invalidInput(String errorMessage) {
        return new AiClassificationResult(Status.INVALID_INPUT, null, null, null, null, errorMessage);
    }
}
