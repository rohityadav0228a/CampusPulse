package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Complaint;
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
import java.util.List;
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
        ModeratorComplaintController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=adminuser",
        "campuspulse.admin.password=AdminPass123!"
})
class StudentSecurityIntegrationTest {

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
        Student student = new Student(
                "Aarav Sharma",
                "aarav@campus.edu",
                "STU1001",
                passwordEncoder.encode("StudentPass123!")
        );
        student.setRole("ROLE_STUDENT");
        when(studentRepository.findByEmailIgnoreCase("aarav@campus.edu")).thenReturn(Optional.of(student));
    }

    @Test
    @DisplayName("Public routes: /, /student/login, /student/register, /login, /complaints/track are accessible unauthenticated")
    void publicRoutesAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/student/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/student/register"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("login"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Admin Sign In")));

        mockMvc.perform(get("/complaints/track"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An unauthenticated request to /login displays the admin login page")
    void unauthenticatedRequestToLoginDisplaysAdminLoginForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("login"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Admin Sign In")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Enter your administrative credentials")));
    }

    @Test
    @DisplayName("An authenticated student visiting /login redirects to /complaints/new")
    void studentVisitingAdminLoginRedirectsToComplaintsNew() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("An authenticated admin visiting /login redirects to /admin/complaints")
    void adminVisitingLoginRedirectsToAdminDashboard() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("adminuser").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    @Test
    @DisplayName("Unauthenticated user accessing /admin/complaints is redirected to /login")
    void unauthenticatedAdminAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/complaints"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Student with ROLE_STUDENT is forbidden (HTTP 403) from accessing /admin/**")
    void studentForbiddenFromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin with ROLE_ADMIN can access /admin/complaints")
    void adminCanAccessAdminDashboard() throws Exception {
        when(complaintService.searchComplaints(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));
        when(complaintService.getStatusCounts()).thenReturn(Collections.emptyMap());
        when(complaintService.getAllowedCategories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/complaints")
                        .with(user("adminuser").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Student form login authenticates and redirects to /complaints/new")
    void studentLoginRedirectsToComplaintForm() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "aarav@campus.edu")
                        .param("password", "StudentPass123!")
                        .param("loginType", "student"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"))
                .andExpect(authenticated().withUsername("aarav@campus.edu"));
    }

    @Test
    @DisplayName("Admin form login authenticates and redirects to /admin/complaints")
    void adminLoginRedirectsToAdminDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "adminuser")
                        .param("password", "AdminPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("adminuser"));
    }

    @Test
    @DisplayName("Failed student login redirects to /student/login?error=true")
    void failedStudentLoginRedirectsToStudentLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "aarav@campus.edu")
                        .param("password", "WrongPassword!")
                        .param("loginType", "student"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?error=true"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Failed admin login redirects to /login?error=true")
    void failedAdminLoginRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "adminuser")
                        .param("password", "WrongPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"))
                .andExpect(unauthenticated());
    }

    // =========================================================================
    // Grievance Registry Access & Complaint Submission Authorization Tests
    // =========================================================================

    @Test
    @DisplayName("Anonymous GET /complaints redirects to /login")
    void anonymousGetComplaintsRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/complaints"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("ROLE_STUDENT receives HTTP 403 on GET /complaints")
    void studentForbiddenFromComplaintsRegistry() throws Exception {
        mockMvc.perform(get("/complaints")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_MODERATOR can access GET /complaints successfully")
    void moderatorCanAccessComplaintsRegistry() throws Exception {
        when(complaintService.getAllComplaints()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_ADMIN can access GET /complaints successfully")
    void adminCanAccessComplaintsRegistry() throws Exception {
        when(complaintService.getAllComplaints()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints")
                        .with(user("adminuser").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Anonymous POST /complaints with CSRF and valid form data reaches submission handler")
    void anonymousCanSubmitComplaintWithCsrf() throws Exception {
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Engineering"));
        Complaint complaint = new Complaint("CP-2026-ANON0001", "Lab Issue", "Computer not working in lab.", "Academic Affairs", "Engineering");
        when(complaintService.createComplaint(any())).thenReturn(complaint);

        mockMvc.perform(post("/complaints")
                        .with(csrf())
                        .param("title", "Lab Issue")
                        .param("description", "Computer not working in lab.")
                        .param("category", "Academic Affairs")
                        .param("department", "Engineering"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/success/CP-2026-ANON0001"));
    }

    @Test
    @DisplayName("Authenticated student can submit complaints (POST /complaints with CSRF)")
    void studentCanSubmitComplaintWithCsrf() throws Exception {
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Engineering"));
        Complaint complaint = new Complaint("CP-2026-STU00001", "Lab Issue", "Computer not working in lab.", "Academic Affairs", "Engineering");
        when(complaintService.createComplaint(any())).thenReturn(complaint);

        mockMvc.perform(post("/complaints")
                        .with(csrf())
                        .with(user("aarav@campus.edu").roles("STUDENT"))
                        .param("title", "Lab Issue")
                        .param("description", "Computer not working in lab.")
                        .param("category", "Academic Affairs")
                        .param("department", "Engineering"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/success/CP-2026-STU00001"));
    }

    @Test
    @DisplayName("Student with ROLE_STUDENT is forbidden (HTTP 403) from accessing /moderator/**")
    void studentForbiddenFromModeratorEndpoints() throws Exception {
        mockMvc.perform(get("/moderator/complaints")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated student visiting /login is redirected to /complaints/new")
    void authenticatedStudentVisitingLoginRedirectsToComplaintsNew() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("Authenticated student POST /logout redirects to root / and terminates session")
    void studentLogoutRedirectsToRoot() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("aarav@campus.edu").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());
    }

    // =========================================================================
    // Role-Based Login and Registration Redirects for Authenticated Users
    // =========================================================================

    @Test
    @DisplayName("Authenticated admin visiting /student/login redirects to /admin/complaints")
    void adminVisitingStudentLoginRedirectsToAdminComplaints() throws Exception {
        mockMvc.perform(get("/student/login")
                        .with(user("adminuser").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    @Test
    @DisplayName("Authenticated moderator visiting /login redirects to /moderator/complaints")
    void moderatorVisitingLoginRedirectsToModeratorComplaints() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    @Test
    @DisplayName("Authenticated moderator visiting /student/login redirects to /moderator/complaints")
    void moderatorVisitingStudentLoginRedirectsToModeratorComplaints() throws Exception {
        mockMvc.perform(get("/student/login")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    @Test
    @DisplayName("Authenticated student visiting /student/login redirects to /complaints/new")
    void studentVisitingStudentLoginRedirectsToComplaintsNew() throws Exception {
        mockMvc.perform(get("/student/login")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("Authenticated student visiting GET /student/register redirects to /complaints/new")
    void studentVisitingRegisterRedirectsToComplaintsNew() throws Exception {
        mockMvc.perform(get("/student/register")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("Authenticated admin visiting GET /student/register redirects to /admin/complaints")
    void adminVisitingRegisterRedirectsToAdminComplaints() throws Exception {
        mockMvc.perform(get("/student/register")
                        .with(user("adminuser").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"));
    }

    @Test
    @DisplayName("Authenticated moderator visiting GET /student/register redirects to /moderator/complaints")
    void moderatorVisitingRegisterRedirectsToModeratorComplaints() throws Exception {
        mockMvc.perform(get("/student/register")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"));
    }

    @Test
    @DisplayName("Authenticated user POSTing to /student/register is redirected to their portal without creating account")
    void authenticatedUserCannotPostRegister() throws Exception {
        mockMvc.perform(post("/student/register")
                        .with(csrf())
                        .with(user("aarav@campus.edu").roles("STUDENT"))
                        .param("fullName", "Rogue Account")
                        .param("email", "rogue@campus.edu")
                        .param("password", "Password123!")
                        .param("confirmPassword", "Password123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"));
    }

    @Test
    @DisplayName("Anonymous users cannot access /admin/analytics, /admin/insights, /admin/users")
    void anonymousCannotAccessAdminTools() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/admin/insights"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
