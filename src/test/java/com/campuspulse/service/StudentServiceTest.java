package com.campuspulse.service;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
