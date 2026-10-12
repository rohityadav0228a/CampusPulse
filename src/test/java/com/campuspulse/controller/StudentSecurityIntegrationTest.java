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
        AdminComplaintController.class
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
    @DisplayName("A student visiting /login displays the admin login page and does NOT redirect to /complaints/new")
    void studentVisitingAdminLoginDisplaysAdminLoginForm() throws Exception {
        mockMvc.perform(get("/login")
                        .with(user("aarav@campus.edu").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("login"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Admin Sign In")));
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
}
