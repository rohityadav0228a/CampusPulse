package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentProfileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=testadmin",
        "campuspulse.admin.password=SecureAdminPass123!"
})
class StudentProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private StudentRepository studentRepository;

    @BeforeEach
    void setUp() {
        Student student = new Student("Aarav Student", "student@campus.edu", "STU101", "hashed");
        student.setRole("ROLE_STUDENT");
        when(studentService.findByEmail("student@campus.edu")).thenReturn(Optional.of(student));
        when(complaintService.getAllowedDepartments()).thenReturn(Collections.emptyList());
    }

    // =========================================================================
    // 1. Role-Based Access Control: ROLE_STUDENT
    // =========================================================================

    @Test
    @DisplayName("Authenticated student with ROLE_STUDENT has full access to GET /student/profile")
    void studentCanAccessProfile() throws Exception {
        mockMvc.perform(get("/student/profile")
                        .with(user("student@campus.edu").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Authenticated student with ROLE_STUDENT can submit POST /student/profile")
    void studentCanSubmitProfileUpdate() throws Exception {
        mockMvc.perform(post("/student/profile")
                        .with(user("student@campus.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("fullName", "Updated Student Name")
                        .param("department", "Information Technology"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/profile"));
    }

    // =========================================================================
    // 2. Unauthenticated Requests Redirection
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated GET /student/profile redirects to /login")
    void unauthenticatedGetRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/student/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Unauthenticated POST /student/profile redirects to /login")
    void unauthenticatedPostRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/student/profile")
                        .with(csrf())
                        .param("fullName", "Attacker")
                        .param("department", "Cyber"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // =========================================================================
    // 3. Role Boundaries: ROLE_MODERATOR and ROLE_ADMIN Forbidden
    // =========================================================================

    @Test
    @DisplayName("Moderator with ROLE_MODERATOR is forbidden (HTTP 403) from GET /student/profile")
    void moderatorForbiddenFromViewingStudentProfile() throws Exception {
        mockMvc.perform(get("/student/profile")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Moderator with ROLE_MODERATOR is forbidden (HTTP 403) from POST /student/profile")
    void moderatorForbiddenFromEditingStudentProfile() throws Exception {
        mockMvc.perform(post("/student/profile")
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .with(csrf())
                        .param("fullName", "Moderator Name")
                        .param("department", "Facilities"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin with ROLE_ADMIN is forbidden (HTTP 403) from GET /student/profile")
    void adminForbiddenFromViewingStudentProfile() throws Exception {
        mockMvc.perform(get("/student/profile")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin with ROLE_ADMIN is forbidden (HTTP 403) from POST /student/profile")
    void adminForbiddenFromEditingStudentProfile() throws Exception {
        mockMvc.perform(post("/student/profile")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("fullName", "Admin Name")
                        .param("department", "Administration"))
                .andExpect(status().isForbidden());
    }
}
