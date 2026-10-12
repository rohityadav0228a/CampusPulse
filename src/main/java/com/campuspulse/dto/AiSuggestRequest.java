package com.campuspulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for AI-assisted grievance category suggestion.
 * Requires at least a title and validates field lengths before delegating to the ML microservice.
 */
public class AiSuggestRequest {

    @NotBlank(message = "Title is required for category suggestion")
    @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    public AiSuggestRequest() {
    }

    public AiSuggestRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Combines title and description into a single text body for classification.
     *
     * @return non-null combined text
     */
    public String toCombinedText() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (description != null && !description.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(". ");
            }
            sb.append(description.trim());
        }
        return sb.toString();
    }
}
