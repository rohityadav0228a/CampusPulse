package com.campuspulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form DTO for student complaint submissions.
 * Prevents mass-assignment vulnerabilities by isolating user input from the JPA entity.
 */
public class ComplaintForm {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 120, message = "Title must be between 5 and 120 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    private String description;

    @NotBlank(message = "Please select a category")
    private String category;

    @NotBlank(message = "Please select a department")
    private String department;

    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String studentEmail;

    @Size(max = 100, message = "Hostel name cannot exceed 100 characters")
    private String hostelName;

    @Size(max = 30, message = "Room number cannot exceed 30 characters")
    private String roomNumber;

    @Size(max = 30, message = "Floor number cannot exceed 30 characters")
    private String floorNumber;

    public ComplaintForm() {
    }

    public ComplaintForm(String title, String description, String category, String department) {
        this(title, description, category, department, null);
    }

    public ComplaintForm(String title, String description, String category, String department, String studentEmail) {
        this(title, description, category, department, studentEmail, null, null, null);
    }

    public ComplaintForm(String title, String description, String category, String department,
                         String studentEmail, String hostelName, String roomNumber, String floorNumber) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.department = department;
        this.studentEmail = studentEmail;
        this.hostelName = hostelName;
        this.roomNumber = roomNumber;
        this.floorNumber = floorNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getHostelName() {
        return hostelName;
    }

    public void setHostelName(String hostelName) {
        this.hostelName = hostelName != null ? hostelName.trim() : null;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber != null ? roomNumber.trim() : null;
    }

    public String getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(String floorNumber) {
        this.floorNumber = floorNumber != null ? floorNumber.trim() : null;
    }
}
