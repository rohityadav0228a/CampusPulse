package com.campuspulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data transfer object encapsulating permitted student profile update fields.
 * Immutable fields such as email, student ID, role, password hash, and creation date
 * are strictly omitted to prevent mass assignment vulnerabilities.
 */
public class StudentProfileForm {

    @NotBlank(message = "Full name is required.")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
    private String fullName;

    @Size(max = 100, message = "Department name cannot exceed 100 characters.")
    private String department;

    public StudentProfileForm() {
    }

    public StudentProfileForm(String fullName, String department) {
        this.fullName = fullName;
        this.department = department;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
