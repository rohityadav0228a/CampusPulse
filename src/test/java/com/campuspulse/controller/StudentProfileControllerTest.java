package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.dto.StudentProfileForm;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = StudentProfileController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=AdminPass123!"
})
class StudentProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private StudentRepository studentRepository;

    private Student sampleStudent;

    @BeforeEach
    void setUp() {
        sampleStudent = new Student(
                "Aarav Sharma",
                "aarav.sharma@campus.edu",
                "STU-2026-101",
                "$2a$10$hashedPassword123"
        );
        sampleStudent.setId(42L);
        sampleStudent.setDepartment("Computer Science & Engineering");
        sampleStudent.setRole("ROLE_STUDENT");
        sampleStudent.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));

        when(complaintService.getAllowedDepartments())
                .thenReturn(List.of("Computer Science & Engineering", "Electrical Engineering", "Mechanical Engineering"));
    }

    // =========================================================================
    // 1. Profile Page Viewing (GET /student/profile)
    // =========================================================================

    @Test
    @DisplayName("GET /student/profile as authenticated student displays profile view with all required fields")
    void authenticatedStudentCanViewProfile() throws Exception {
        when(studentService.findByEmail("aarav.sharma@campus.edu")).thenReturn(Optional.of(sampleStudent));

        mockMvc.perform(get("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/profile"))
                .andExpect(model().attributeExists("student"))
                .andExpect(model().attributeExists("profileForm"))
                .andExpect(content().string(containsString("Aarav Sharma")))
                .andExpect(content().string(containsString("aarav.sharma@campus.edu")))
                .andExpect(content().string(containsString("STU-2026-101")))
                .andExpect(content().string(containsString("Computer Science &amp; Engineering")))
                .andExpect(content().string(containsString("ROLE_STUDENT")))
                .andExpect(content().string(containsString("15 January 2026, 10:30 AM")))
                .andExpect(content().string(containsString("/forgot-password")));
    }

    @Test
    @DisplayName("GET /student/profile displays fallback when optional studentId or department is missing")
    void displaysFallbackWhenOptionalFieldsMissing() throws Exception {
        Student minimal = new Student("Rohit Kumar", "rohit@campus.edu", null, "hashed");
        minimal.setRole("ROLE_STUDENT");
        minimal.setDepartment(null);
        minimal.setCreatedAt(null);

        when(studentService.findByEmail("rohit@campus.edu")).thenReturn(Optional.of(minimal));

        mockMvc.perform(get("/student/profile")
                        .with(user("rohit@campus.edu").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not Provided")))
                .andExpect(content().string(containsString("General Campus / Unassigned")))
                .andExpect(content().string(containsString("Not Available")));
    }

    @Test
    @DisplayName("GET /student/profile unauthenticated redirects to login")
    void unauthenticatedGetRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/student/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /student/profile redirects to login if authenticated student not found in repository")
    void studentNotFoundRedirectsToLogin() throws Exception {
        when(studentService.findByEmail("ghost@campus.edu")).thenReturn(Optional.empty());

        mockMvc.perform(get("/student/profile")
                        .with(user("ghost@campus.edu").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?logout=true"));
    }

    // =========================================================================
    // 2. Profile Updates (POST /student/profile)
    // =========================================================================

    @Test
    @DisplayName("POST /student/profile updates permitted fields and redirects with flash success")
    void authenticatedStudentCanUpdatePermittedFields() throws Exception {
        when(studentService.findByEmail("aarav.sharma@campus.edu")).thenReturn(Optional.of(sampleStudent));

        mockMvc.perform(post("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("fullName", "Aarav S. Sharma")
                        .param("department", "Artificial Intelligence"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/profile"))
                .andExpect(flash().attributeExists("successMessage"));

        ArgumentCaptor<StudentProfileForm> formCaptor = ArgumentCaptor.forClass(StudentProfileForm.class);
        verify(studentService).updateProfile(eq("aarav.sharma@campus.edu"), formCaptor.capture());

        assertEquals("Aarav S. Sharma", formCaptor.getValue().getFullName());
        assertEquals("Artificial Intelligence", formCaptor.getValue().getDepartment());
    }

    @Test
    @DisplayName("POST /student/profile with blank full name rejects input and returns form with error")
    void blankFullNameRejectsInput() throws Exception {
        when(studentService.findByEmail("aarav.sharma@campus.edu")).thenReturn(Optional.of(sampleStudent));

        mockMvc.perform(post("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("fullName", "")
                        .param("department", "Civil Engineering"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/profile"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("profileForm", "fullName"));

        verify(studentService, never()).updateProfile(anyString(), any());
    }

    @Test
    @DisplayName("POST /student/profile with full name shorter than 2 chars rejects input")
    void shortFullNameRejectsInput() throws Exception {
        when(studentService.findByEmail("aarav.sharma@campus.edu")).thenReturn(Optional.of(sampleStudent));

        mockMvc.perform(post("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("fullName", "A")
                        .param("department", "Civil Engineering"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/profile"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("profileForm", "fullName"));

        verify(studentService, never()).updateProfile(anyString(), any());
    }

    @Test
    @DisplayName("POST /student/profile without CSRF token is forbidden (HTTP 403)")
    void missingCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT"))
                        .param("fullName", "New Name")
                        .param("department", "Physics"))
                .andExpect(status().isForbidden());

        verify(studentService, never()).updateProfile(anyString(), any());
    }

    // =========================================================================
    // 3. Security & Anti-Tampering (IDOR & Mass Assignment Protections)
    // =========================================================================

    @Test
    @DisplayName("Attempts to alter email, studentId, role, password, or createdAt via form binding are strictly ignored")
    void protectedFieldsAreIgnoredOnProfileBinding() throws Exception {
        when(studentService.findByEmail("aarav.sharma@campus.edu")).thenReturn(Optional.of(sampleStudent));

        mockMvc.perform(post("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("fullName", "Legitimate Name")
                        .param("department", "Mechanical Engineering")
                        .param("email", "hacker@evil.com")
                        .param("studentId", "STU-HACKED-999")
                        .param("role", "ROLE_ADMIN")
                        .param("password", "HackedPass123!")
                        .param("createdAt", "2020-01-01T00:00:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/profile"));

        ArgumentCaptor<StudentProfileForm> formCaptor = ArgumentCaptor.forClass(StudentProfileForm.class);
        verify(studentService).updateProfile(eq("aarav.sharma@campus.edu"), formCaptor.capture());

        // StudentProfileForm DTO only has fullName and department
        assertEquals("Legitimate Name", formCaptor.getValue().getFullName());
        assertEquals("Mechanical Engineering", formCaptor.getValue().getDepartment());
    }

    @Test
    @DisplayName("IDOR prevention: Student cannot update another user's profile by injecting user id or email parameters")
    void idorAttackIsPreventedAndPrincipalAlwaysUsed() throws Exception {
        when(studentService.findByEmail("aarav.sharma@campus.edu")).thenReturn(Optional.of(sampleStudent));

        mockMvc.perform(post("/student/profile")
                        .with(user("aarav.sharma@campus.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("id", "999")
                        .param("userId", "123")
                        .param("email", "victim@campus.edu")
                        .param("fullName", "Impersonator Name")
                        .param("department", "Security Studies"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/profile"));

        // Strictly verify that updateProfile was invoked with principal's email, NOT the parameter
        verify(studentService).updateProfile(eq("aarav.sharma@campus.edu"), any(StudentProfileForm.class));
        verify(studentService, never()).updateProfile(eq("victim@campus.edu"), any());
    }
}
