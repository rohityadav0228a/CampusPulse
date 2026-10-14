package com.campuspulse.controller;

import com.campuspulse.dto.StudentProfileForm;
import com.campuspulse.model.Student;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.StudentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller managing authenticated student profile viewing and updates.
 * Strictly binds profile actions to the authenticated Spring Security principal,
 * preventing Insecure Direct Object References (IDOR) and unauthorized field manipulation.
 */
@Controller
@RequestMapping("/student/profile")
public class StudentProfileController {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileController.class);

    private final StudentService studentService;
    private final ComplaintService complaintService;

    public StudentProfileController(StudentService studentService, ComplaintService complaintService) {
        this.studentService = studentService;
        this.complaintService = complaintService;
    }

    @ModelAttribute("departments")
    public List<String> populateDepartments() {
        return complaintService.getAllowedDepartments();
    }

    /**
     * Displays the authenticated student's profile page.
     *
     * @param authentication active Spring Security principal
     * @param model Spring MVC model
     * @return profile view template or redirect if unauthenticated/not found
     */
    @GetMapping
    public String showProfile(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String principalEmail = authentication.getName();
        return studentService.findByEmail(principalEmail)
                .map(student -> {
                    model.addAttribute("student", student);
                    if (!model.containsAttribute("profileForm")) {
                        model.addAttribute("profileForm", new StudentProfileForm(
                                student.getFullName(),
                                student.getDepartment()
                        ));
                    }
                    return "student/profile";
                })
                .orElse("redirect:/student/login?logout=true");
    }

    /**
     * Processes profile updates for the currently authenticated student.
     * Only permitted fields (fullName and department) can be updated via the dedicated DTO.
     *
     * @param authentication active Spring Security principal
     * @param form validated profile update DTO
     * @param bindingResult validation results
     * @param model Spring MVC model
     * @param redirectAttributes redirect flash attributes
     * @return redirect to profile on success, or profile view with errors
     */
    @PostMapping
    public String updateProfile(
            Authentication authentication,
            @Valid @ModelAttribute("profileForm") StudentProfileForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String principalEmail = authentication.getName();
        Student student = studentService.findByEmail(principalEmail).orElse(null);
        if (student == null) {
            return "redirect:/student/login?logout=true";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("student", student);
            return "student/profile";
        }

        try {
            studentService.updateProfile(principalEmail, form);
            redirectAttributes.addFlashAttribute("successMessage", "Your profile has been updated successfully.");
            return "redirect:/student/profile";
        } catch (IllegalArgumentException ex) {
            log.warn("Profile update failed for student {}: {}", principalEmail, ex.getMessage());
            model.addAttribute("student", student);
            model.addAttribute("errorMessage", ex.getMessage());
            return "student/profile";
        } catch (Exception ex) {
            log.error("Unexpected error updating student profile for {}", principalEmail, ex);
            model.addAttribute("student", student);
            model.addAttribute("errorMessage", "An unexpected error occurred while updating your profile. Please try again.");
            return "student/profile";
        }
    }
}
