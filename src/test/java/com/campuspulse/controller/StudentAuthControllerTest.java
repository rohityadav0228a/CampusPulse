package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.dto.StudentRegisterForm;
import com.campuspulse.model.Student;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StudentAuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=admin123"
})
class StudentAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private StudentRepository studentRepository;

    @Test
    @DisplayName("GET /student/login should display student login view")
    void shouldDisplayStudentLoginPage() throws Exception {
        mockMvc.perform(get("/student/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/login"))
                .andExpect(content().string(containsString("Student Sign In")))
                .andExpect(content().string(containsString("Student Email")))
                .andExpect(content().string(containsString("Register here")));
    }

    @Test
    @DisplayName("GET /student/register should display student registration view")
    void shouldDisplayStudentRegisterPage() throws Exception {
        mockMvc.perform(get("/student/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/register"))
                .andExpect(content().string(containsString("Student Registration")))
                .andExpect(content().string(containsString("Full Name")))
                .andExpect(content().string(containsString("securely hashed with BCrypt")));
    }

    @Test
    @DisplayName("POST /student/register with valid input should register student and redirect to login")
    void shouldRegisterStudentSuccessfullyAndRedirect() throws Exception {
        Student createdStudent = new Student();
        createdStudent.setEmail("priya.sharma@campus.edu");
        when(studentService.registerStudent(any(StudentRegisterForm.class))).thenReturn(createdStudent);

        mockMvc.perform(post("/student/register")
                        .with(csrf())
                        .param("fullName", "Priya Sharma")
                        .param("email", "priya.sharma@campus.edu")
                        .param("studentId", "STU2024099")
                        .param("department", "Electronics")
                        .param("password", "Pass@word123")
                        .param("confirmPassword", "Pass@word123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?registered=true"));

        verify(studentService).registerStudent(any(StudentRegisterForm.class));
    }

    @Test
    @DisplayName("POST /student/register with validation errors should redisplay form with errors")
    void shouldRejectInvalidRegistrationInput() throws Exception {
        mockMvc.perform(post("/student/register")
                        .with(csrf())
                        .param("fullName", "")
                        .param("email", "not-an-email")
                        .param("studentId", "STU")
                        .param("department", "CSE")
                        .param("password", "123") // too short
                        .param("confirmPassword", "456"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/register"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("POST /student/register with duplicate email should show error message")
    void shouldHandleDuplicateEmailRegistration() throws Exception {
        when(studentService.registerStudent(any(StudentRegisterForm.class)))
                .thenThrow(new IllegalArgumentException("A student account with email test@campus.edu already exists."));

        mockMvc.perform(post("/student/register")
                        .with(csrf())
                        .param("fullName", "Test Student")
                        .param("email", "test@campus.edu")
                        .param("studentId", "STU12345")
                        .param("department", "Mechanical")
                        .param("password", "SecurePassword123!")
                        .param("confirmPassword", "SecurePassword123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/register"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("already exists")));
    }
}
