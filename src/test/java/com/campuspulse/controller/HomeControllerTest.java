package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.StudentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=admin123"
})
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private StudentRepository studentRepository;

    // =========================================================================
    // Case A: Anonymous Visitor Tests
    // =========================================================================

    @Test
    @DisplayName("Case A: Anonymous GET / displays public welcome page with 4 authentication options")
    void anonymousUser_shouldSeePublicLandingPageWithFourAuthOptions() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("appName", "CampusPulse"))
                .andExpect(model().attribute("isAuthenticated", false))
                .andExpect(model().attribute("isAdmin", false))
                .andExpect(model().attribute("isModerator", false))
                .andExpect(model().attribute("isStudent", false))
                .andExpect(content().string(containsString("CampusPulse")))
                .andExpect(content().string(containsString("Resolution Flow")))
                .andExpect(content().string(containsString("Submit a Complaint")))
                .andExpect(content().string(containsString("Track Complaint Status")))
                .andExpect(content().string(containsString("Centralized Grievance Registry")))
                .andExpect(content().string(containsString("CAT-ACAD")))
                // All 4 authentication choices must be present
                .andExpect(content().string(containsString("Student Login")))
                .andExpect(content().string(containsString("Student Register")))
                .andExpect(content().string(containsString("Admin Login")))
                .andExpect(content().string(containsString("Moderator Login")))
                .andExpect(content().string(containsString("id=\"card-student-login\"")))
                .andExpect(content().string(containsString("id=\"card-student-register\"")))
                .andExpect(content().string(containsString("id=\"card-admin-login\"")))
                .andExpect(content().string(containsString("id=\"card-moderator-login\"")))
                .andExpect(content().string(containsString("id=\"hero-student-login-cta\"")))
                .andExpect(content().string(containsString("id=\"hero-student-register-cta\"")))
                .andExpect(content().string(containsString("id=\"hero-admin-login-cta\"")))
                .andExpect(content().string(containsString("id=\"hero-moderator-login-cta\"")))
                .andExpect(content().string(containsString("href=\"/student/login\"")))
                .andExpect(content().string(containsString("href=\"/student/register\"")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("id=\"nav-student-login-btn\"")))
                .andExpect(content().string(containsString("id=\"nav-admin-login-btn\"")))
                .andExpect(content().string(containsString("id=\"nav-track-btn\"")))
                // Authenticated logout and dashboard actions must NOT be visible
                .andExpect(content().string(not(containsString("id=\"btn-student-logout\""))))
                .andExpect(content().string(not(containsString("id=\"btn-admin-logout\""))))
                .andExpect(content().string(not(containsString("id=\"btn-moderator-logout\""))))
                .andExpect(content().string(not(containsString("id=\"nav-admin-dashboard-btn\""))))
                .andExpect(content().string(not(containsString("id=\"nav-moderator-dashboard-btn\""))))
                .andExpect(content().string(not(containsString("id=\"nav-admin-analytics\""))))
                .andExpect(content().string(not(containsString("id=\"nav-admin-insights\""))))
                .andExpect(content().string(not(containsString("id=\"nav-admin-users\""))))
                .andExpect(content().string(not(containsString("Sign Out"))));
    }

    // =========================================================================
    // Case B: Authenticated Student Tests
    // =========================================================================

    @Test
    @DisplayName("Case B: Authenticated student GET / redirects to /complaints/new")
    void authenticatedStudent_shouldRedirectToComplaintsNew() throws Exception {
        mockMvc.perform(get("/").with(user("aarav.sharma@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("Case B: Authenticated student with 'STUDENT' authority without ROLE_ prefix redirects to /complaints/new")
    void authenticatedStudentWithoutPrefix_shouldRedirectToComplaintsNew() throws Exception {
        mockMvc.perform(get("/").with(user("rohan@campus.edu").authorities(() -> "STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    // =========================================================================
    // Case C: Authenticated Moderator Tests
    // =========================================================================

    @Test
    @DisplayName("Case C: Authenticated moderator GET / redirects to /moderator/complaints")
    void authenticatedModerator_shouldRedirectToModeratorComplaints() throws Exception {
        mockMvc.perform(get("/").with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    // =========================================================================
    // Case D: Authenticated Administrator Tests
    // =========================================================================

    @Test
    @DisplayName("Case D: Authenticated administrator GET / redirects to /admin/complaints")
    void authenticatedAdmin_shouldRedirectToAdminComplaints() throws Exception {
        mockMvc.perform(get("/").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    @Test
    @DisplayName("Case D: User with multiple roles (ADMIN and STUDENT) resolves to ADMIN precedence and redirects to /admin/complaints")
    void userWithMultipleRoles_resolvesToAdminPrecedence() throws Exception {
        mockMvc.perform(get("/").with(user("admin_super").roles("ADMIN", "STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    // =========================================================================
    // Case E: Unknown or Unsupported Role Tests
    // =========================================================================

    @Test
    @DisplayName("Case E: Authenticated user with unknown/unsupported role is redirected to safe login error, never admin")
    void userWithUnknownRole_shouldRedirectToSafeUnauthorizedError() throws Exception {
        mockMvc.perform(get("/").with(user("visitor_unknown").roles("GUEST")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=unauthorized"));
    }

    @Test
    @DisplayName("Case E: Unknown role never receives admin access on root URL")
    void unknownRoleNeverAccessesAdmin() throws Exception {
        mockMvc.perform(get("/").with(user("suspicious_user").roles("UNKNOWN_ROLE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", not(containsString("/admin/complaints"))));
    }

    // =========================================================================
    // Workflow Independence Tests
    // =========================================================================

    @Test
    @DisplayName("Authenticated student GET / redirects to /complaints/new regardless of tracking params or session attributes")
    void studentHomepage_redirectsRegardlessOfTrackingParams() throws Exception {
        mockMvc.perform(get("/")
                        .param("referenceId", "CP-2026-TEST999")
                        .param("returnUrl", "/complaints/track?referenceId=CP-2026-TEST999")
                        .sessionAttr("lastVisitedUrl", "/complaints/track?referenceId=CP-2026-TEST999")
                        .sessionAttr("searchedReferenceId", "CP-2026-TEST999")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("Authenticated admin GET / redirects to /admin/complaints regardless of previous session attributes")
    void adminHomepage_redirectsRegardlessOfSessionAttributes() throws Exception {
        mockMvc.perform(get("/")
                        .sessionAttr("lastVisitedUrl", "/admin/analytics")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    @Test
    @DisplayName("Direct navigation to / does not depend on internal or external Referer header")
    void homepage_directNavigationDoesNotDependOnRefererHeader() throws Exception {
        // Student with referrer redirects to /complaints/new
        mockMvc.perform(get("/")
                        .header("Referer", "http://localhost:8080/complaints/track?referenceId=CP-2026-ABC")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));

        // Admin with referrer redirects to /admin/complaints
        mockMvc.perform(get("/")
                        .header("Referer", "http://localhost:8080/admin/complaints")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));

        // Anonymous with referrer gets public welcome page
        mockMvc.perform(get("/")
                        .header("Referer", "https://search.campus.edu/?q=complaint"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("Brand logo and Return Home links target / on the homepage")
    void homepage_brandLogoAndReturnHomeLinksTargetRoot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"nav-brand\"")))
                .andExpect(content().string(containsString("href=\"/\"")));
    }
}
