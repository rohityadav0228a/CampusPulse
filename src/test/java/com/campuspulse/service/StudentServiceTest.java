package com.campuspulse.service;

import com.campuspulse.dto.StudentProfileForm;
import com.campuspulse.dto.StudentRegisterForm;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentRepository, passwordEncoder);
    }

    private StudentRegisterForm createValidForm() {
        StudentRegisterForm form = new StudentRegisterForm();
        form.setFullName("Aarav Sharma");
        form.setEmail("aarav.sharma@campus.edu");
        form.setStudentId("STU2024001");
        form.setDepartment("Computer Science");
        form.setPassword("StrongSecret123!");
        form.setConfirmPassword("StrongSecret123!");
        return form;
    }

    @Test
    @DisplayName("Should register new student with BCrypt-hashed password and ROLE_STUDENT")
    void shouldRegisterNewStudentSuccessfully() {
        StudentRegisterForm form = createValidForm();
        when(studentRepository.existsByEmailIgnoreCase("aarav.sharma@campus.edu")).thenReturn(false);
        when(passwordEncoder.encode("StrongSecret123!")).thenReturn("$2a$10$hashedPasswordSample");
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student registered = studentService.registerStudent(form);

        assertNotNull(registered);
        assertEquals("Aarav Sharma", registered.getFullName());
        assertEquals("aarav.sharma@campus.edu", registered.getEmail());
        assertEquals("STU2024001", registered.getStudentId());
        assertEquals("Computer Science", registered.getDepartment());
        assertEquals("$2a$10$hashedPasswordSample", registered.getPassword());
        assertEquals("ROLE_STUDENT", registered.getRole());

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertEquals("aarav.sharma@campus.edu", captor.getValue().getEmail());
    }

    @Test
    @DisplayName("Should reject registration if email is already registered")
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        StudentRegisterForm form = createValidForm();
        when(studentRepository.existsByEmailIgnoreCase("aarav.sharma@campus.edu")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                studentService.registerStudent(form)
        );

        assertTrue(ex.getMessage().contains("already exists"));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject registration if password and confirm password do not match")
    void shouldThrowExceptionWhenPasswordsDoNotMatch() {
        StudentRegisterForm form = createValidForm();
        form.setConfirmPassword("MismatchSecret999!");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                studentService.registerStudent(form)
        );

        assertTrue(ex.getMessage().contains("do not match"));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should find student by email case-insensitively")
    void shouldFindStudentByEmail() {
        Student student = new Student();
        student.setEmail("aarav.sharma@campus.edu");
        when(studentRepository.findByEmailIgnoreCase("AARAV.SHARMA@CAMPUS.EDU")).thenReturn(Optional.of(student));

        Optional<Student> found = studentService.findByEmail("AARAV.SHARMA@CAMPUS.EDU");
        assertTrue(found.isPresent());
        assertEquals("aarav.sharma@campus.edu", found.get().getEmail());
    }

    @Test
    @DisplayName("Should provision new moderator account with ROLE_MODERATOR and hashed password")
    void shouldProvisionNewModeratorSuccessfully() {
        when(studentRepository.findByEmailIgnoreCase("moderator@campus.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecureModPass123!")).thenReturn("$2a$10$hashedModPass");
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student moderator = studentService.provisionModerator(
                "Priya Moderator",
                "moderator@campus.edu",
                "Grievance Cell",
                "SecureModPass123!"
        );

        assertNotNull(moderator);
        assertEquals("Priya Moderator", moderator.getFullName());
        assertEquals("moderator@campus.edu", moderator.getEmail());
        assertEquals("Grievance Cell", moderator.getDepartment());
        assertEquals("$2a$10$hashedModPass", moderator.getPassword());
        assertEquals("ROLE_MODERATOR", moderator.getRole());

        verify(studentRepository).save(any(Student.class));
    }

    @Test
    @DisplayName("Should upgrade existing account to ROLE_MODERATOR when provisioned")
    void shouldUpgradeExistingAccountToModerator() {
        Student existing = new Student("Aarav Sharma", "aarav@campus.edu", "STU1", "$2a$10$oldPass");
        existing.setRole("ROLE_STUDENT");

        when(studentRepository.findByEmailIgnoreCase("aarav@campus.edu")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("NewElevatedPass123!")).thenReturn("$2a$10$newHashedPass");
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Student upgraded = studentService.provisionModerator(
                "Aarav Sharma",
                "aarav@campus.edu",
                "Campus Affairs",
                "NewElevatedPass123!"
        );

        assertNotNull(upgraded);
        assertEquals("ROLE_MODERATOR", upgraded.getRole());
        assertEquals("$2a$10$newHashedPass", upgraded.getPassword());
        assertEquals("Campus Affairs", upgraded.getDepartment());
    }

    @Test
    @DisplayName("Should reject moderator provisioning if name, email, or password constraints are violated")
    void shouldRejectInvalidModeratorProvisioning() {
        assertThrows(IllegalArgumentException.class, () ->
                studentService.provisionModerator("", "mod@campus.edu", "Dept", "Password123!")
        );

        assertThrows(IllegalArgumentException.class, () ->
                studentService.provisionModerator("Name", "  ", "Dept", "Password123!")
        );

        assertThrows(IllegalArgumentException.class, () ->
                studentService.provisionModerator("Name", "mod@campus.edu", "Dept", "short")
        );
    }

    // =========================================================================
    // Student Profile Management Tests
    // =========================================================================

    @Test
    @DisplayName("Should update student profile full name and department successfully")
    void shouldUpdateStudentProfileSuccessfully() {
        Student student = new Student("Aarav Sharma", "aarav@campus.edu", "STU100", "$2a$10$hashedPass");
        student.setId(10L);
        student.setDepartment("Computer Science");
        student.setRole("ROLE_STUDENT");
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        student.setCreatedAt(created);

        when(studentRepository.findByEmailIgnoreCase("aarav@campus.edu")).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileForm form = new StudentProfileForm("Aarav S. Sharma", "Data Science & AI");
        Student updated = studentService.updateProfile("aarav@campus.edu", form);

        assertNotNull(updated);
        assertEquals("Aarav S. Sharma", updated.getFullName());
        assertEquals("Data Science & AI", updated.getDepartment());
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("Should clear department to null when empty or blank string provided")
    void shouldClearDepartmentWhenBlank() {
        Student student = new Student("Aarav Sharma", "aarav@campus.edu", "STU100", "$2a$10$hashedPass");
        student.setDepartment("Computer Science");

        when(studentRepository.findByEmailIgnoreCase("aarav@campus.edu")).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileForm form = new StudentProfileForm("Aarav Sharma", "   ");
        Student updated = studentService.updateProfile("aarav@campus.edu", form);

        assertNull(updated.getDepartment());
    }

    @Test
    @DisplayName("Should throw exception when updating profile of non-existent student")
    void shouldThrowExceptionWhenStudentNotFound() {
        when(studentRepository.findByEmailIgnoreCase("unknown@campus.edu")).thenReturn(Optional.empty());

        StudentProfileForm form = new StudentProfileForm("New Name", "New Dept");
        assertThrows(IllegalArgumentException.class, () ->
                studentService.updateProfile("unknown@campus.edu", form)
        );

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should throw exception when full name is blank or invalid length on profile update")
    void shouldThrowExceptionWhenFullNameInvalid() {
        Student student = new Student("Aarav Sharma", "aarav@campus.edu", "STU100", "$2a$10$hashedPass");
        when(studentRepository.findByEmailIgnoreCase("aarav@campus.edu")).thenReturn(Optional.of(student));

        assertThrows(IllegalArgumentException.class, () ->
                studentService.updateProfile("aarav@campus.edu", new StudentProfileForm("", "Dept"))
        );

        assertThrows(IllegalArgumentException.class, () ->
                studentService.updateProfile("aarav@campus.edu", new StudentProfileForm("   ", "Dept"))
        );

        assertThrows(IllegalArgumentException.class, () ->
                studentService.updateProfile("aarav@campus.edu", new StudentProfileForm("A", "Dept"))
        );

        assertThrows(IllegalArgumentException.class, () ->
                studentService.updateProfile("aarav@campus.edu", new StudentProfileForm("A".repeat(101), "Dept"))
        );

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should preserve all immutable protected fields during profile update")
    void shouldPreserveProtectedFieldsOnProfileUpdate() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 8, 10, 9, 15);
        Student student = new Student("Original Name", "student@campus.edu", "STU-SECURE-999", "$2a$10$OriginalHashedPassword");
        student.setId(77L);
        student.setRole("ROLE_STUDENT");
        student.setCreatedAt(originalCreatedAt);
        student.setDepartment("Original Dept");

        when(studentRepository.findByEmailIgnoreCase("student@campus.edu")).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileForm form = new StudentProfileForm("Updated Legal Name", "Advanced Robotics");
        Student result = studentService.updateProfile("student@campus.edu", form);

        // Permitted fields updated
        assertEquals("Updated Legal Name", result.getFullName());
        assertEquals("Advanced Robotics", result.getDepartment());

        // Strictly verify protected fields remain 100% unaltered
        assertEquals(77L, result.getId());
        assertEquals("student@campus.edu", result.getEmail());
        assertEquals("STU-SECURE-999", result.getStudentId());
        assertEquals("ROLE_STUDENT", result.getRole());
        assertEquals("$2a$10$OriginalHashedPassword", result.getPassword());
        assertEquals(originalCreatedAt, result.getCreatedAt());
    }
}
