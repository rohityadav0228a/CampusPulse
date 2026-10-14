package com.campuspulse.service;

import com.campuspulse.dto.StudentProfileForm;
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

    /**
     * Securely provisions or updates a moderator account with ROLE_MODERATOR.
     * Encodes raw credentials using the configured BCrypt password encoder,
     * enforces email and name requirements, and logs the administrative provisioning
     * action without leaking raw credentials.
     *
     * @param fullName full name of the moderator
     * @param email official email address for the moderator
     * @param department campus department or operational unit
     * @param rawPassword secure plaintext password (encoded immediately)
     * @return persisted Student entity with ROLE_MODERATOR
     * @throws IllegalArgumentException if required fields or password constraints are violated
     */
    @Transactional
    public Student provisionModerator(String fullName, String email, String department, String rawPassword) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required to provision a moderator.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required to provision a moderator.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Moderator password must be at least 8 characters long.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String hashedPassword = passwordEncoder.encode(rawPassword);

        Optional<Student> existingOpt = studentRepository.findByEmailIgnoreCase(normalizedEmail);
        Student moderator;
        if (existingOpt.isPresent()) {
            moderator = existingOpt.get();
            moderator.setFullName(fullName.trim());
            moderator.setPassword(hashedPassword);
            moderator.setRole("ROLE_MODERATOR");
            if (department != null && !department.isBlank()) {
                moderator.setDepartment(department.trim());
            }
            log.info("Existing account upgraded to ROLE_MODERATOR: id={}, email={}", moderator.getId(), maskEmail(normalizedEmail));
        } else {
            moderator = new Student(
                    fullName.trim(),
                    normalizedEmail,
                    null,
                    hashedPassword
            );
            moderator.setRole("ROLE_MODERATOR");
            if (department != null && !department.isBlank()) {
                moderator.setDepartment(department.trim());
            }
            log.info("New moderator account successfully provisioned: email={}", maskEmail(normalizedEmail));
        }

        return studentRepository.save(moderator);
    }

    /**
     * Updates permitted profile fields (fullName, department) for an authenticated student.
     * All security-critical and ownership fields (email, studentId, role, password, createdAt)
     * are strictly immutable and protected from alteration.
     *
     * @param email authenticated student email from security principal
     * @param form validated profile update DTO
     * @return persisted and updated Student entity
     * @throws IllegalArgumentException if student is not found or validation constraints fail
     */
    @Transactional
    public Student updateProfile(String email, StudentProfileForm form) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated student email is required.");
        }
        if (form == null) {
            throw new IllegalArgumentException("Profile form cannot be null.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        Student student = studentRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Student account not found."));

        if (form.getFullName() == null || form.getFullName().trim().isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }

        String trimmedName = form.getFullName().trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            throw new IllegalArgumentException("Full name must be between 2 and 100 characters.");
        }

        student.setFullName(trimmedName);

        if (form.getDepartment() != null && !form.getDepartment().isBlank()) {
            student.setDepartment(form.getDepartment().trim());
        } else {
            student.setDepartment(null);
        }

        Student saved = studentRepository.save(student);
        log.info("Student profile successfully updated for account id={}, email={}", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
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
