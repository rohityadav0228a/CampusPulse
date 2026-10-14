package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.AiClassifierService;
import com.campuspulse.service.AnalyticsService;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.InsightsService;
import com.campuspulse.service.SlaEscalationService;
import com.campuspulse.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        HomeController.class,
        AuthController.class,
        StudentAuthController.class,
        ComplaintController.class,
        AdminComplaintController.class,
        AdminAnalyticsController.class,
        AdminInsightsController.class,
        ModeratorComplaintController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=testadmin",
        "campuspulse.admin.password=SecureAdminPass123!"
})
class ModeratorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private StudentService studentService;

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

    @BeforeEach
    void setUp() {
        Student moderator = new Student(
                "Priya Moderator",
                "moderator@campus.edu",
                null,
                passwordEncoder.encode("ModeratorPass123!")
        );
        moderator.setRole("ROLE_MODERATOR");
        when(studentRepository.findByEmailIgnoreCase("moderator@campus.edu")).thenReturn(Optional.of(moderator));

        Student student = new Student(
                "Rohan Student",
                "rohan@campus.edu",
                "STU5555",
                passwordEncoder.encode("StudentPass123!")
        );
        student.setRole("ROLE_STUDENT");
        when(studentRepository.findByEmailIgnoreCase("rohan@campus.edu")).thenReturn(Optional.of(student));

        when(complaintService.searchComplaints(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));
        when(complaintService.getStatusCounts()).thenReturn(Collections.emptyMap());
        when(complaintService.getSlaCounts()).thenReturn(Collections.emptyMap());
        when(complaintService.getAllowedCategories()).thenReturn(Collections.emptyList());
        when(complaintService.getAllowedDepartments()).thenReturn(Collections.emptyList());
    }

    // =========================================================================
    // 1. Authentication and Role-Based Post-Login Redirection
    // =========================================================================

    @Test
    @DisplayName("Moderator form login authenticates and redirects to /moderator/complaints")
    void moderatorLoginRedirectsToModeratorDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "moderator@campus.edu")
                        .param("password", "ModeratorPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"))
                .andExpect(authenticated().withUsername("moderator@campus.edu"));
    }

    @Test
    @DisplayName("Student form login redirect remains unchanged at /complaints/new")
    void studentLoginRedirectsToComplaintForm() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "rohan@campus.edu")
                        .param("password", "StudentPass123!")
                        .param("loginType", "student"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"))
                .andExpect(authenticated().withUsername("rohan@campus.edu"));
    }

    @Test
    @DisplayName("Admin form login redirect remains unchanged at /admin/complaints")
    void adminLoginRedirectsToAdminDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "testadmin")
                        .param("password", "SecureAdminPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("testadmin"));
    }

    @Test
    @DisplayName("Authenticated moderator visiting /login is redirected to /moderator/complaints")
    void authenticatedModeratorVisitingLoginRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    @Test
    @DisplayName("Authenticated moderator visiting /student/login is redirected to /moderator/complaints")
    void authenticatedModeratorVisitingStudentLoginRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/student/login")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    @Test
    @DisplayName("Authenticated moderator visiting /student/register is redirected to /moderator/complaints")
    void authenticatedModeratorVisitingStudentRegisterRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/student/register")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    // =========================================================================
    // 2. Authorization Boundaries and Access Denials
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated request to /moderator/complaints redirects to /login")
    void unauthenticatedModeratorAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/moderator/complaints"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Student with ROLE_STUDENT is forbidden (HTTP 403) from accessing /moderator/**")
    void studentForbiddenFromModeratorEndpoints() throws Exception {
        mockMvc.perform(get("/moderator/complaints")
                        .with(user("rohan@campus.edu").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Moderator with ROLE_MODERATOR is forbidden (HTTP 403) from accessing /admin/complaints")
    void moderatorForbiddenFromAdminComplaintDashboard() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Moderator with ROLE_MODERATOR is forbidden (HTTP 403) from accessing /admin/analytics")
    void moderatorForbiddenFromAdminAnalytics() throws Exception {
        mockMvc.perform(get("/admin/analytics")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Moderator with ROLE_MODERATOR is forbidden (HTTP 403) from accessing /admin/insights")
    void moderatorForbiddenFromAdminInsights() throws Exception {
        mockMvc.perform(get("/admin/insights")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Moderator with ROLE_MODERATOR is authorized (HTTP 200) to access /moderator/complaints")
    void moderatorAuthorizedForModeratorDashboard() throws Exception {
        mockMvc.perform(get("/moderator/complaints")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Administrator with ROLE_ADMIN is authorized (HTTP 200) to access /moderator/complaints")
    void adminCanAccessModeratorDashboard() throws Exception {
        mockMvc.perform(get("/moderator/complaints")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Administrator with ROLE_ADMIN retains full access to /admin/complaints")
    void adminRetainsAccessToAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. CSRF Protection on Mutating Endpoints
    // =========================================================================

    @Test
    @DisplayName("POST /moderator/complaints/{id}/status without CSRF token is rejected with HTTP 403 Forbidden")
    void statusUpdateWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/moderator/complaints/CP-2026-TEST01/status")
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/department without CSRF token is rejected with HTTP 403 Forbidden")
    void departmentUpdateWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/moderator/complaints/CP-2026-TEST01/department")
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("department", "Hostel Administration"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/priority without CSRF token is rejected with HTTP 403 Forbidden")
    void priorityUpdateWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/moderator/complaints/CP-2026-TEST01/priority")
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("priority", "HIGH"))
                .andExpect(status().isForbidden());
    }
}
