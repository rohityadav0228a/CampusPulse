package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.AiClassifierService;
import com.campuspulse.service.AnalyticsService;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.InsightsService;
import com.campuspulse.service.PasswordResetService;
import com.campuspulse.service.SlaEscalationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration security tests verifying:
 * - Public access to /forgot-password and /reset-password
 * - CSRF enforcement on password recovery POST requests
 * - "Forgot password?" link availability on both student and admin login views
 * - Login regression across Student, Moderator, and Admin roles
 */
@WebMvcTest(controllers = {
        PasswordResetController.class,
        AuthController.class,
        StudentAuthController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=admin123"
})
class PasswordResetSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private com.campuspulse.service.StudentService studentService;

    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private AiClassifierService aiClassifierService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private InsightsService insightsService;

    @MockitoBean
    private SlaEscalationService slaEscalationService;

    // =========================================================================
    // 1. Public Endpoint Access (PermitAll)
    // =========================================================================

    @Test
    @DisplayName("GET /forgot-password is accessible without authentication")
    void getForgotPasswordPermitAll() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reset Your Password")));
    }

    @Test
    @DisplayName("GET /reset-password is accessible without authentication")
    void getResetPasswordPermitAll() throws Exception {
        when(passwordResetService.isTokenValid("sample-public-token")).thenReturn(true);

        mockMvc.perform(get("/reset-password").param("token", "sample-public-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Set New Password")));
    }

    @Test
    @DisplayName("POST /forgot-password with CSRF is accessible without authentication")
    void postForgotPasswordPermitAllWithCsrf() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "anyone@campus.edu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?sent=true"));
    }

    // =========================================================================
    // 2. CSRF Enforcement on Recovery Endpoints
    // =========================================================================

    @Test
    @DisplayName("POST /forgot-password without CSRF is rejected with 403 Forbidden")
    void postForgotPasswordRejectsMissingCsrf() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "anyone@campus.edu"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /reset-password without CSRF is rejected with 403 Forbidden")
    void postResetPasswordRejectsMissingCsrf() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .param("token", "tok123")
                        .param("password", "ValidPass123!")
                        .param("confirmPassword", "ValidPass123!"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. UI Links: "Forgot password?" link on both login pages
    // =========================================================================

    @Test
    @DisplayName("GET /login (Admin/Staff) includes clear 'Forgot password?' link")
    void adminLoginIncludesForgotPasswordLink() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/forgot-password\"")))
                .andExpect(content().string(containsString("Forgot password?")));
    }

    @Test
    @DisplayName("GET /student/login includes clear 'Forgot password?' link")
    void studentLoginIncludesForgotPasswordLink() throws Exception {
        mockMvc.perform(get("/student/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/forgot-password\"")))
                .andExpect(content().string(containsString("Forgot password?")));
    }

    // =========================================================================
    // 4. Login Regression Tests (Student, Moderator, Admin)
    // =========================================================================

    @Test
    @DisplayName("Regression: Environment Admin login authenticates and redirects to admin complaints")
    void adminLoginRegression() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"));
    }

    @Test
    @DisplayName("Regression: Registered Student login authenticates and redirects to complaint submission")
    void studentLoginRegression() throws Exception {
        Student student = new Student("Bob Student", "bob@college.edu", "STU002", passwordEncoder.encode("BobPassword123!"));
        student.setRole("ROLE_STUDENT");
        when(studentRepository.findByEmailIgnoreCase("bob@college.edu")).thenReturn(Optional.of(student));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "bob@college.edu")
                        .param("password", "BobPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"))
                .andExpect(authenticated().withUsername("bob@college.edu").withRoles("STUDENT"));
    }

    @Test
    @DisplayName("Regression: Registered Moderator login authenticates and redirects to moderator complaints")
    void moderatorLoginRegression() throws Exception {
        Student moderator = new Student("Campus Mod", "mod@campuspulse.edu", null, passwordEncoder.encode("ModPassword123!"));
        moderator.setRole("ROLE_MODERATOR");
        when(studentRepository.findByEmailIgnoreCase("mod@campuspulse.edu")).thenReturn(Optional.of(moderator));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mod@campuspulse.edu")
                        .param("password", "ModPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"))
                .andExpect(authenticated().withUsername("mod@campuspulse.edu").withRoles("MODERATOR"));
    }

    @Test
    @DisplayName("Regression: Invalid credentials fail authentication and redirect to /login?error=true")
    void invalidLoginFails() throws Exception {
        when(studentRepository.findByEmailIgnoreCase("unknown@college.edu")).thenReturn(Optional.empty());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "unknown@college.edu")
                        .param("password", "wrongpass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"))
                .andExpect(unauthenticated());
    }
}
