package com.campuspulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data transfer object encapsulating forgot password request input.
 */
public class ForgotPasswordForm {

    @NotBlank(message = "Please enter your registered email address.")
    @Email(message = "Please enter a valid email address.")
    @Size(max = 150, message = "Email address cannot exceed 150 characters.")
    private String email;

    public ForgotPasswordForm() {
    }

    public ForgotPasswordForm(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
