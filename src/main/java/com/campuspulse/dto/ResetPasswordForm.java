package com.campuspulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data transfer object encapsulating password reset input and validation rules.
 */
public class ResetPasswordForm {

    @NotBlank(message = "Password reset token is required.")
    private String token;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long.")
    private String password;

    @NotBlank(message = "Please confirm your new password.")
    private String confirmPassword;

    public ResetPasswordForm() {
    }

    public ResetPasswordForm(String token) {
        this.token = token;
    }

    public ResetPasswordForm(String token, String password, String confirmPassword) {
        this.token = token;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }

    /**
     * Verifies that the new password and confirmation password match exactly.
     *
     * @return true if both are non-null and identical
     */
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
