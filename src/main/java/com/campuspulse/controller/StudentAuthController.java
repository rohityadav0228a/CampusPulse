package com.campuspulse.controller;

import com.campuspulse.dto.StudentRegisterForm;
import com.campuspulse.service.StudentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller managing student authentication and registration workflows.
 */
@Controller
@RequestMapping("/student")
public class StudentAuthController {

    private static final Logger log = LoggerFactory.getLogger(StudentAuthController.class);

    private final StudentService studentService;

    public StudentAuthController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Renders the student login page.
     *
     * @param error optional flag indicating failed authentication
     * @param logout optional flag indicating successful logout
     * @param registered optional flag indicating successful registration
     * @param authentication active authentication
     * @param model Spring MVC model
     * @return student login template name
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout,
            @RequestParam(name = "registered", required = false) String registered,
            Authentication authentication,
            Model model
    ) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(authentication.getName())) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_ADMIN".equals(r) || "ADMIN".equals(r));
            boolean isModerator = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_MODERATOR".equals(r) || "MODERATOR".equals(r));
            boolean isStudent = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_STUDENT".equals(r) || "STUDENT".equals(r)
                            || (r != null && r.contains("STUDENT")));
            if (isAdmin) {
                return "redirect:/admin/complaints";
            }
            if (isModerator) {
                return "redirect:/moderator/complaints";
            }
            if (isStudent) {
                return "redirect:/complaints/new";
            }
            model.addAttribute("errorMessage", "Your account does not have authorization to access this portal.");
            return "student/login";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid student email or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been successfully signed out.");
        }
        if (registered != null && !model.containsAttribute("successMessage")) {
            model.addAttribute("successMessage", "Account created successfully! Please sign in with your credentials.");
        }

        return "student/login";
    }

    /**
     * Renders the student registration form.
     *
     * @param authentication active authentication
     * @param model Spring MVC model
     * @return student registration template name
     */
    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(authentication.getName())) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_ADMIN".equals(r) || "ADMIN".equals(r));
            boolean isModerator = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_MODERATOR".equals(r) || "MODERATOR".equals(r));
            boolean isStudent = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_STUDENT".equals(r) || "STUDENT".equals(r)
                            || (r != null && r.contains("STUDENT")));
            if (isAdmin) {
                return "redirect:/admin/complaints";
            }
            if (isModerator) {
                return "redirect:/moderator/complaints";
            }
            if (isStudent) {
                return "redirect:/complaints/new";
            }
            return "redirect:/login?error=unauthorized";
        }

        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new StudentRegisterForm());
        }
        return "student/register";
    }

    /**
     * Processes student registration form submissions with validation and password confirmation.
     * Authenticated users are prevented from creating unintended accounts.
     *
     * @param form validated registration form DTO
     * @param bindingResult validation results
     * @param authentication active authentication
     * @param model Spring MVC model
     * @param redirectAttributes flash attributes container
     * @return redirect to login on success, or registration form on error
     */
    @PostMapping("/register")
    public String processRegistration(
            @Valid @ModelAttribute("registerForm") StudentRegisterForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(authentication.getName())) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_ADMIN".equals(r) || "ADMIN".equals(r));
            boolean isModerator = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_MODERATOR".equals(r) || "MODERATOR".equals(r));
            if (isAdmin) {
                return "redirect:/admin/complaints";
            }
            if (isModerator) {
                return "redirect:/moderator/complaints";
            }
            return "redirect:/complaints/new";
        }

        if (!form.isPasswordMatching()) {
            bindingResult.rejectValue("confirmPassword", "error.registerForm", "Passwords do not match.");
        }

        if (bindingResult.hasErrors()) {
            return "student/register";
        }

        try {
            studentService.registerStudent(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Account successfully created! Please sign in with your student credentials.");
            return "redirect:/student/login?registered=true";
        } catch (IllegalArgumentException ex) {
            log.warn("Registration rejected: {}", ex.getMessage());
            model.addAttribute("errorMessage", ex.getMessage());
            return "student/register";
        } catch (Exception ex) {
            log.error("Unexpected error during student registration", ex);
            model.addAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
            return "student/register";
        }
    }
}
