package com.campuspulse.service;

import com.campuspulse.dto.StudentRegisterForm;
import com.campuspulse.model.Student;
import com.campuspulse.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service managing student registration, account creation, and credential validation.
 * Uses BCrypt strong password hashing and enforces case-insensitive email uniqueness.
 */
@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository studentRepository, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new student account after validating input constraints and email uniqueness.
     * Encodes the password securely using BCrypt.
     *
     * @param form validated registration form DTO
     * @return persisted Student entity
     * @throws IllegalArgumentException if validation rules fail
     */
    @Transactional
    public Student registerStudent(StudentRegisterForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Registration form cannot be null.");
        }

        if (!form.isPasswordMatching()) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        String normalizedEmail = (form.getEmail() != null) ? form.getEmail().trim().toLowerCase() : "";
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Student email address is required.");
        }

        if (studentRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Registration rejected: an account already exists with email {}", maskEmail(normalizedEmail));
            throw new IllegalArgumentException("An account with this email address already exists. Please sign in instead.");
        }

        String hashedPassword = passwordEncoder.encode(form.getPassword());

        Student student = new Student(
                form.getFullName() != null ? form.getFullName().trim() : null,
                normalizedEmail,
                form.getStudentId() != null ? form.getStudentId().trim() : null,
                hashedPassword
        );

        if (form.getDepartment() != null && !form.getDepartment().isBlank()) {
            student.setDepartment(form.getDepartment().trim());
        }

        Student saved = studentRepository.save(student);
        log.info("Student successfully registered: id={}, email={}", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
    }

    /**
     * Finds a student by case-insensitive email address.
     *
     * @param email student email
     * @return optional containing matching student
     */
    @Transactional(readOnly = true)
    public Optional<Student> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(email.trim());
    }

    /**
     * Checks if a student account exists for the given email.
     *
     * @param email student email
     * @return true if exists
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return studentRepository.existsByEmailIgnoreCase(email.trim());
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[empty]";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
