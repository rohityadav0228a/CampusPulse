package com.campuspulse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data transfer object encapsulating student feedback submission for a resolved complaint.
 * Enforces server-side constraints on rating range and comment length.
 */
public class ComplaintFeedbackForm {

    @NotNull(message = "Please provide a rating from 1 to 5 stars.")
    @Min(value = 1, message = "Rating must be between 1 and 5 stars.")
    @Max(value = 5, message = "Rating must be between 1 and 5 stars.")
    private Integer rating;

    @Size(max = 1000, message = "Feedback comment cannot exceed 1,000 characters.")
    private String comment;

    public ComplaintFeedbackForm() {
    }

    public ComplaintFeedbackForm(Integer rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
