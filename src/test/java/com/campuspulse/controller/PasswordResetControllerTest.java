package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Controller tests verifying the password recovery endpoints:
 * - GET and POST /forgot-password
 * - GET and POST /reset-password
 * - CSRF enforcement, validation error rendering, generic messages, and redirection
 */
@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=admin123"
})
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private StudentRepository studentRepository;

    // =========================================================================
    // 1. GET /forgot-password
    // =========================================================================

    @Test
    @DisplayName("GET /forgot-password renders forgot password page with back button")
    void getForgotPasswordRendersForm() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attributeExists("forgotPasswordForm"))
                .andExpect(content().string(containsString("Reset Your Password")))
                .andExpect(content().string(containsString("Send Password Reset Link")));
    }

    @Test
    @DisplayName("GET /forgot-password?sent=true renders generic inbox message")
    void getForgotPasswordWithSentFlagRendersMessage() throws Exception {
        mockMvc.perform(get("/forgot-password").param("sent", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(content().string(containsString("Check Your Inbox")))
                .andExpect(content().string(containsString("If an account exists for that email address")));
    }

    // =========================================================================
    // 2. POST /forgot-password
    // =========================================================================

    @Test
    @DisplayName("POST /forgot-password with valid email invokes service and redirects with generic message")
    void postForgotPasswordWithValidEmailRedirectsWithGenericMessage() throws Exception {
        doNothing().when(passwordResetService).requestPasswordReset(eq("user@college.edu"), anyString());

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "user@college.edu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?sent=true"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(passwordResetService).requestPasswordReset(eq("user@college.edu"), anyString());
    }

    @Test
    @DisplayName("POST /forgot-password with non-existent email returns identical generic response")
    void postForgotPasswordNonExistentEmailReturnsGenericResponse() throws Exception {
        doNothing().when(passwordResetService).requestPasswordReset(eq("nonexistent@college.edu"), anyString());

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "nonexistent@college.edu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?sent=true"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /forgot-password without CSRF token returns 403 Forbidden")
    void postForgotPasswordWithoutCsrfForbidden() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "test@college.edu"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /forgot-password with invalid email format returns form with validation error")
    void postForgotPasswordInvalidEmailReturnsForm() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().hasErrors());
    }

    // =========================================================================
    // 3. GET /reset-password
    // =========================================================================

    @Test
    @DisplayName("GET /reset-password with valid token renders form with tokenValid=true")
    void getResetPasswordWithValidTokenRendersForm() throws Exception {
        when(passwordResetService.isTokenValid("valid-token-123")).thenReturn(true);

        mockMvc.perform(get("/reset-password").param("token", "valid-token-123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("tokenValid", true))
                .andExpect(model().attribute("token", "valid-token-123"))
                .andExpect(content().string(containsString("Set New Password")))
                .andExpect(content().string(containsString("Update Password")));
    }

    @Test
    @DisplayName("GET /reset-password with invalid or expired token displays safe error message")
    void getResetPasswordWithInvalidTokenShowsErrorMessage() throws Exception {
        when(passwordResetService.isTokenValid("expired-token-999")).thenReturn(false);

        mockMvc.perform(get("/reset-password").param("token", "expired-token-999"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("tokenValid", false))
                .andExpect(content().string(containsString("This password reset link is invalid, expired, or has already been used")));
    }

    @Test
    @DisplayName("GET /reset-password without token displays token missing error")
    void getResetPasswordWithoutTokenShowsError() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("tokenValid", false))
                .andExpect(content().string(containsString("Password reset token is missing")));
    }

    // =========================================================================
    // 4. POST /reset-password
    // =========================================================================

    @Test
    @DisplayName("POST /reset-password without CSRF token returns 403 Forbidden")
    void postResetPasswordWithoutCsrfForbidden() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .param("token", "test-token")
                        .param("password", "ValidPass123!")
                        .param("confirmPassword", "ValidPass123!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /reset-password with password mismatch returns form with validation error")
    void postResetPasswordMismatchReturnsForm() throws Exception {
        when(passwordResetService.isTokenValid("token-abc")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "token-abc")
                        .param("password", "SecretPass123!")
                        .param("confirmPassword", "DifferentPass123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    @DisplayName("POST /reset-password with password shorter than 8 characters returns validation error")
    void postResetPasswordTooShortReturnsError() throws Exception {
        when(passwordResetService.isTokenValid("token-abc")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "token-abc")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Password must be at least 8 characters long")));
    }

    @Test
    @DisplayName("POST /reset-password with valid student reset redirects to student login with reset=true")
    void postResetPasswordForStudentRedirectsToStudentLogin() throws Exception {
        Student student = new Student("Rohit", "rohit@college.edu", "STU1", "hash");
        student.setRole("ROLE_STUDENT");

        when(passwordResetService.resetPassword("token-valid-student", "ValidNewPass123!"))
                .thenReturn(student);

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "token-valid-student")
                        .param("password", "ValidNewPass123!")
                        .param("confirmPassword", "ValidNewPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?reset=true"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /reset-password with valid moderator reset redirects to staff/admin login with reset=true")
    void postResetPasswordForModeratorRedirectsToStaffLogin() throws Exception {
        Student moderator = new Student("Mod", "mod@college.edu", null, "hash");
        moderator.setRole("ROLE_MODERATOR");

        when(passwordResetService.resetPassword("token-valid-mod", "ValidNewPass123!"))
                .thenReturn(moderator);

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "token-valid-mod")
                        .param("password", "ValidNewPass123!")
                        .param("confirmPassword", "ValidNewPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset=true"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("POST /reset-password with already consumed or expired token renders error message")
    void postResetPasswordReusedTokenShowsError() throws Exception {
        doThrow(new IllegalArgumentException("This password reset link is invalid, expired, or has already been used."))
                .when(passwordResetService).resetPassword("reused-token", "ValidPass123!");

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "reused-token")
                        .param("password", "ValidPass123!")
                        .param("confirmPassword", "ValidPass123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("tokenValid", false))
                .andExpect(content().string(containsString("This password reset link is invalid, expired, or has already been used")));
    }
}
