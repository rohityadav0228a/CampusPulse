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
import com.campuspulse.service.StudentService;
import com.campuspulse.service.UserManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression tests verifying that:
 * 1. Configured environment admin can log in repeatedly without credentials being wiped or mutated.
 * 2. Invalid passwords are consistently rejected without locking or breaking the account.
 * 3. Database-backed admins, moderators, and students log in seamlessly.
 * 4. Password reset operations cannot break or mutate the configured admin account.
 * 5. Role restrictions are enforced (/admin/** requires ADMIN; /moderator/** requires MODERATOR or ADMIN).
 * 6. Authentication failure responses never expose sensitive credentials, hashes, or stack traces.
 */
@WebMvcTest(controllers = {
        AuthController.class,
        StudentAuthController.class,
        HomeController.class,
        AdminComplaintController.class,
        ModeratorComplaintController.class,
        ComplaintController.class,
        PasswordResetController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=mainadmin",
        "campuspulse.admin.password=MainAdminSecret123!"
})
class AdminAuthenticationRegressionTest {

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

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("Environment admin credentials work repeatedly across consecutive logins (no credential loss)")
    void environmentAdminCanLoginRepeatedly() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", "mainadmin")
                            .param("password", "MainAdminSecret123!"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/complaints"))
                    .andExpect(authenticated().withUsername("mainadmin").withRoles("ADMIN"));
        }
    }

    @Test
    @DisplayName("Failed login attempt with bad password does not corrupt subsequent valid logins")
    void failedLoginDoesNotCorruptSubsequentAdminLogins() throws Exception {
        // 1. Initial valid login
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mainadmin")
                        .param("password", "MainAdminSecret123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("mainadmin").withRoles("ADMIN"));

        // 2. Invalid password attempt
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mainadmin")
                        .param("password", "WrongSecretPassword!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"))
                .andExpect(unauthenticated());

        // 3. Immediately follow with valid password - must succeed
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mainadmin")
                        .param("password", "MainAdminSecret123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("mainadmin").withRoles("ADMIN"));
    }

    @Test
    @DisplayName("Database-backed admin login succeeds and redirects to /admin/complaints")
    void databaseBackedAdminCanLogin() throws Exception {
        Student dbAdmin = new Student("DB Administrator", "dbadmin@campus.edu", null,
                passwordEncoder.encode("DbAdminSecurePassword!"));
        dbAdmin.setRole("ROLE_ADMIN");
        when(studentRepository.findByEmailIgnoreCase("dbadmin@campus.edu")).thenReturn(Optional.of(dbAdmin));

        // Multiple logins for database-backed admin
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", "dbadmin@campus.edu")
                            .param("password", "DbAdminSecurePassword!"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/complaints"))
                    .andExpect(authenticated().withUsername("dbadmin@campus.edu").withRoles("ADMIN"));
        }
    }

    @Test
    @DisplayName("Database-backed moderator login succeeds and redirects to /moderator/complaints")
    void databaseBackedModeratorCanLogin() throws Exception {
        Student moderator = new Student("Mod Officer", "officer@campus.edu", null,
                passwordEncoder.encode("ModOfficerSecret!"));
        moderator.setRole("ROLE_MODERATOR");
        when(studentRepository.findByEmailIgnoreCase("officer@campus.edu")).thenReturn(Optional.of(moderator));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "officer@campus.edu")
                        .param("password", "ModOfficerSecret!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"))
                .andExpect(authenticated().withUsername("officer@campus.edu").withRoles("MODERATOR"));
    }

    @Test
    @DisplayName("Database-backed student login succeeds and redirects to /complaints/new")
    void databaseBackedStudentCanLogin() throws Exception {
        Student student = new Student("Student User", "student@campus.edu", "STU101",
                passwordEncoder.encode("StudentPass456!"));
        student.setRole("ROLE_STUDENT");
        when(studentRepository.findByEmailIgnoreCase("student@campus.edu")).thenReturn(Optional.of(student));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "student@campus.edu")
                        .param("password", "StudentPass456!")
                        .param("loginType", "student"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/new"))
                .andExpect(authenticated().withUsername("student@campus.edu").withRoles("STUDENT"));
    }

    @Test
    @DisplayName("Student is forbidden (403) from accessing /admin/** routes")
    void studentCannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .with(user("student@campus.edu").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Moderator is forbidden (403) from accessing /admin/** routes")
    void moderatorCannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/complaints")
                        .with(user("mod@campus.edu").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can access /admin/** and /moderator/** routes")
    void adminCanAccessAdminAndModeratorRoutes() throws Exception {
        when(complaintService.searchComplaints(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(complaintService.getStatusCounts()).thenReturn(Collections.emptyMap());
        when(complaintService.getAllowedCategories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/complaints")
                        .with(user("mainadmin").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/moderator/complaints")
                        .with(user("mainadmin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Authentication failure redirects safely without exposing sensitive data")
    void failureResponseDoesNotExposeSensitiveData() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "nonexistent")
                        .param("password", "SuperSecretAttempt!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"))
                .andExpect(unauthenticated())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(not(containsString("SuperSecretAttempt!"))));
    }

    @Test
    @DisplayName("Password reset attempt for environment admin does not corrupt admin login")
    void passwordResetAttemptDoesNotCorruptAdminLogin() throws Exception {
        // Request password reset via controller
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "admin@campuspulse.edu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?sent=true"));

        // Admin login still functions perfectly
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mainadmin")
                        .param("password", "MainAdminSecret123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(authenticated().withUsername("mainadmin").withRoles("ADMIN"));
    }
}
