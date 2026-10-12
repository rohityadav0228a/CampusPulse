package com.campuspulse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Advisory category suggestion response returned by the CampusPulse AI endpoint.
 * Contains predicted category, confidence score, and heuristic warnings.
 * Suggestions are strictly advisory and never saved automatically.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiSuggestResponse {

    private boolean success;
    private String category;
    private String suggestedCategory;
    private Double confidence;
    private String warning;
    private String modelVersion;
    private String message;

    public AiSuggestResponse() {
    }

    public static AiSuggestResponse success(String category, Double confidence, String warning, String modelVersion) {
        AiSuggestResponse res = new AiSuggestResponse();
        res.success = true;
        res.category = category;
        res.suggestedCategory = category;
        res.confidence = confidence;
        res.warning = warning;
        res.modelVersion = modelVersion;
        return res;
    }

    public static AiSuggestResponse failure(String message) {
        AiSuggestResponse res = new AiSuggestResponse();
        res.success = false;
        res.message = message;
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.suggestedCategory = category;
    }

    public String getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(String suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
        this.category = suggestedCategory;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
