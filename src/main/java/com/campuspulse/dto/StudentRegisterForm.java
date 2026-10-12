package com.campuspulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data transfer object encapsulating student registration form input and server-side validation rules.
 */
public class StudentRegisterForm {

    @NotBlank(message = "Full name is required.")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
    private String fullName;

    @NotBlank(message = "Student email address is required.")
    @Email(message = "Please provide a valid email address.")
    @Size(max = 150, message = "Email address cannot exceed 150 characters.")
    private String email;

    @Size(max = 50, message = "Student ID cannot exceed 50 characters.")
    private String studentId;

    @Size(max = 100, message = "Department name cannot exceed 100 characters.")
    private String department;

    @NotBlank(message = "Password is required.")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters long.")
    private String password;

    @NotBlank(message = "Please confirm your password.")
    private String confirmPassword;

    public StudentRegisterForm() {
    }

    public StudentRegisterForm(String fullName, String email, String studentId, String password, String confirmPassword) {
        this.fullName = fullName;
        this.email = email;
        this.studentId = studentId;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }

    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
