package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.service.AiClassifierService;
import com.campuspulse.service.AnalyticsService;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.InsightsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Comprehensive verification of Spring Security authentication and role-based authorization:
 * 1. Unauthenticated route protection and redirects
 * 2. Role-based authorization checks (ADMIN vs non-ADMIN)
 * 3. Authenticated administrator access
 * 4. Form login success and failure
 * 5. Logout handling and session termination
 * 6. CSRF validation on mutating operations
 * 7. Public route accessibility for students and general visitors
 */
@WebMvcTest(controllers = {
        AdminComplaintController.class,
        AdminAnalyticsController.class,
        AdminInsightsController.class,
        ComplaintController.class,
        HomeController.class,
        AuthController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=testadmin",
        "campuspulse.admin.password=SecureAdminPass123!"
})
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private AiClassifierService aiClassifierService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private InsightsService insightsService;

    @MockitoBean
    private com.campuspulse.service.SlaEscalationService slaEscalationService;

    // =========================================================================
    // 1. Unauthenticated Access Restrictions
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated GET /admin/complaints redirects to /login")
    void unauthenticatedDashboardRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/complaints"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET /admin/complaints with slaStatus parameter redirects to /login")
    void unauthenticatedSlaFilterRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/complaints").param("slaStatus", "OVERDUE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET /admin/complaints/{refId} redirects to /login")
    void unauthenticatedDetailRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/complaints/CP-2026-TEST0001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated POST /admin/complaints/{refId}/status redirects to /login")
    void unauthenticatedPostRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-TEST0001/status")
                        .with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET /admin/analytics redirects to /login")
    void unauthenticatedAnalyticsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated GET /admin/insights redirects to /login")
    void unauthenticatedInsightsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/insights"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // =========================================================================
    // 2. Role-Based Authorization Checks (Non-ADMIN roles forbidden)
    // =========================================================================

    @Test
    @DisplayName("Authenticated user with USER role receives 403 Forbidden on dashboard")
    void userRoleForbiddenOnDashboard() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .with(user("student_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user with STUDENT role receives 403 Forbidden on detail page")
    void studentRoleForbiddenOnDetail() throws Exception {
        mockMvc.perform(get("/admin/complaints/CP-2026-TEST0001")
                        .with(user("student_user").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user with USER role receives 403 Forbidden on status modification")
    void userRoleForbiddenOnPostStatus() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-TEST0001/status")
                        .with(user("student_user").roles("USER"))
                        .with(csrf())
                        .param("status", "RESOLVED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user with USER role receives 403 Forbidden on analytics")
    void userRoleForbiddenOnAnalytics() throws Exception {
        mockMvc.perform(get("/admin/analytics")
                        .with(user("student_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user with USER role receives 403 Forbidden on insights")
    void userRoleForbiddenOnInsights() throws Exception {
        mockMvc.perform(get("/admin/insights")
                        .with(user("student_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user with USER role receives 403 Forbidden on SLA filtered complaints")
    void userRoleForbiddenOnSlaFilter() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .param("slaStatus", "OVERDUE")
                        .with(user("student_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. Authenticated Administrator Access (ADMIN role allowed)
    // =========================================================================

    @Test
    @DisplayName("Authenticated ADMIN can access /admin/analytics dashboard")
    void adminCanAccessAnalytics() throws Exception {
        when(analyticsService.getAnalyticsData(null, null, 30))
                .thenReturn(new com.campuspulse.dto.analytics.AnalyticsDashboardData(
                        new com.campuspulse.dto.analytics.AnalyticsSummary(0, 0, 0, 0, 0),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "",
                        "",
                        30
                ));
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));

        mockMvc.perform(get("/admin/analytics")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics/index"))
                .andExpect(content().string(containsString("Campus Grievance Analytics")))
                .andExpect(content().string(containsString("Log Out")));
    }

    @Test
    @DisplayName("Authenticated ADMIN can access /admin/insights dashboard")
    void adminCanAccessInsights() throws Exception {
        when(insightsService.getInsightsData(14))
                .thenReturn(new com.campuspulse.dto.insights.AdminInsightsData(
                        14,
                        new com.campuspulse.dto.insights.PeriodComparison(
                                14,
                                0L,
                                0L,
                                0L,
                                0.0,
                                com.campuspulse.dto.insights.TrendDirection.STABLE,
                                "0 complaints in the past 14 days",
                                "Sep 28 – Oct 12",
                                "Sep 14 – Sep 28"
                        ),
                        com.campuspulse.dto.insights.StatusDistribution.of(0, 0, 0, 0, 0),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        false,
                        0L
                ));

        mockMvc.perform(get("/admin/insights")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/insights/index"))
                .andExpect(content().string(containsString("AI-Powered Grievance Insights &amp; Recommendations")))
                .andExpect(content().string(containsString("Log Out")));
    }

    @Test
    @DisplayName("Authenticated ADMIN can access /admin/complaints dashboard")
    void adminCanAccessDashboard() throws Exception {
        when(complaintService.getFilteredComplaints(null, null)).thenReturn(Collections.emptyList());
        when(complaintService.getStatusCounts()).thenReturn(Map.of());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));

        mockMvc.perform(get("/admin/complaints")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(content().string(containsString("Admin Complaint Management")))
                .andExpect(content().string(containsString("Log Out")));
    }

    @Test
    @DisplayName("Authenticated ADMIN can access /admin/complaints with SLA filter")
    void adminCanAccessSlaFilter() throws Exception {
        when(complaintService.getFilteredComplaints(null, null, "OVERDUE")).thenReturn(Collections.emptyList());
        when(complaintService.getStatusCounts()).thenReturn(Map.of());
        when(complaintService.getSlaCounts()).thenReturn(Map.of("OVERDUE", 0L));
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));

        mockMvc.perform(get("/admin/complaints")
                        .param("slaStatus", "OVERDUE")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(content().string(containsString("Admin Complaint Management")));
    }

    @Test
    @DisplayName("Authenticated ADMIN can access /admin/complaints/{refId} detail page")
    void adminCanAccessDetailPage() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-TEST0001",
                "Library Wi-Fi Down",
                "No internet connection available in reading hall 2.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setId(10L);

        when(complaintService.getByReferenceId("CP-2026-TEST0001")).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/admin/complaints/CP-2026-TEST0001")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/detail"))
                .andExpect(content().string(containsString("CP-2026-TEST0001")))
                .andExpect(content().string(containsString("Library Wi-Fi Down")));
    }

    @Test
    @DisplayName("Authenticated ADMIN can update complaint status with CSRF token")
    void adminCanUpdateComplaintStatusWithCsrf() throws Exception {
        Complaint updated = new Complaint(
                "CP-2026-TEST0001",
                "Library Wi-Fi Down",
                "No internet connection available in reading hall 2.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        updated.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintService.updateComplaintStatus("CP-2026-TEST0001", ComplaintStatus.IN_PROGRESS, "testadmin", null))
                .thenReturn(updated);

        mockMvc.perform(post("/admin/complaints/CP-2026-TEST0001/status")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-TEST0001"));

        org.mockito.Mockito.verify(complaintService, org.mockito.Mockito.times(1))
                .updateComplaintStatus("CP-2026-TEST0001", ComplaintStatus.IN_PROGRESS, "testadmin", null);
    }

    // =========================================================================
    // 4. Public Route Accessibility (Students & Visitors)
    // =========================================================================

    @Test
    @DisplayName("Public visitor can access homepage (GET /) without authentication")
    void publicCanAccessHomepage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("Public visitor can view complaint registry (GET /complaints)")
    void publicCanAccessRegistry() throws Exception {
        when(complaintService.getAllComplaints()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/list"));
    }

    @Test
    @DisplayName("Public student can access submission form (GET /complaints/new)")
    void publicCanAccessNewComplaintForm() throws Exception {
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Academic Affairs Division"));

        mockMvc.perform(get("/complaints/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/form"));
    }

    @Test
    @DisplayName("Public student can access tracker page (GET /complaints/track)")
    void publicCanAccessTrackerPage() throws Exception {
        mockMvc.perform(get("/complaints/track"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"));
    }

    @Test
    @DisplayName("Public student can view submission success page (GET /complaints/success/{refId})")
    void publicCanAccessSuccessPage() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-TEST0001",
                "Mess Quality Concern",
                "Food served was cold in dinner hall.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        when(complaintService.getByReferenceId("CP-2026-TEST0001")).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/complaints/success/CP-2026-TEST0001"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/success"));
    }

    @Test
    @DisplayName("Public visitor can access login page (GET /login)")
    void publicCanAccessLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Admin Sign In")));
    }

    // =========================================================================
    // 5. Authentication, Login, and Session Lifecycle
    // =========================================================================

    @Test
    @DisplayName("Valid credentials successfully authenticate and redirect to /admin/complaints")
    void loginSucceedsWithValidCredentials() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "testadmin")
                        .param("password", "SecureAdminPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("testadmin").withRoles("ADMIN"));
    }

    @Test
    @DisplayName("Invalid password rejects authentication and redirects to /login?error=true")
    void loginFailsWithInvalidPassword() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "testadmin")
                        .param("password", "WrongPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Invalid username rejects authentication and redirects to /login?error=true")
    void loginFailsWithUnknownUser() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "nonexistent_admin")
                        .param("password", "SecureAdminPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("GET /login with ?error=true displays error banner")
    void loginPageRendersErrorMessageWhenErrorParamPresent() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Authentication Failed")))
                .andExpect(content().string(containsString("Invalid username or password")));
    }

    @Test
    @DisplayName("GET /login with ?logout=true displays logout confirmation banner")
    void loginPageRendersLogoutMessageWhenLogoutParamPresent() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Signed Out")))
                .andExpect(content().string(containsString("You have been successfully logged out")));
    }

    @Test
    @DisplayName("Already authenticated admin accessing /login is redirected to /admin/complaints")
    void authenticatedAdminVisitingLoginRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    @Test
    @DisplayName("POST /logout invalidates session and redirects to /login?logout=true")
    void logoutTerminatesSessionAndRedirects() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout=true"))
                .andExpect(unauthenticated());
    }

    // =========================================================================
    // 6. CSRF Protection
    // =========================================================================

    @Test
    @DisplayName("State-changing POST /admin/complaints/{refId}/status without CSRF token is rejected with 403 Forbidden")
    void adminPostWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-TEST0001/status")
                        .with(user("testadmin").roles("ADMIN"))
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public POST /complaints without CSRF token is rejected with 403 Forbidden")
    void publicComplaintSubmissionWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/complaints")
                        .param("title", "Test Title")
                        .param("description", "A valid test description with sufficient length.")
                        .param("category", "Academic Affairs")
                        .param("department", "Academic Affairs Division"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /logout without CSRF token is rejected with 403 Forbidden")
    void logoutWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public POST /complaints/suggest-category without CSRF token is rejected with 403 Forbidden")
    void suggestCategoryWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/complaints/suggest-category")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Broken lab computers\",\"description\":\"Cannot start PCs\"}"))
                .andExpect(status().isForbidden());
    }
}
