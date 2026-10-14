package com.campuspulse.controller;

import com.campuspulse.dto.ForgotPasswordForm;
import com.campuspulse.dto.ResetPasswordForm;
import com.campuspulse.model.Student;
import com.campuspulse.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller handling password recovery workflows:
 * - Requesting a password reset link (GET and POST /forgot-password)
 * - Verifying reset tokens and choosing a new password (GET and POST /reset-password)
 */
@Controller
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    public static final String GENERIC_RESET_SENT_MESSAGE =
            "If an account exists for that email address, a password reset link has been sent. Please check your inbox and spam folder.";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Renders the forgot password request view.
     *
     * @param sent optional query flag indicating email has been dispatched
     * @param authentication active authentication
     * @param model Spring MVC model
     * @return forgot password template name
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(
            @RequestParam(name = "sent", required = false) String sent,
            Authentication authentication,
            Model model
    ) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return redirectAuthenticatedUser(authentication);
        }

        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }

        if (sent != null && !model.containsAttribute("successMessage")) {
            model.addAttribute("successMessage", GENERIC_RESET_SENT_MESSAGE);
        }

        return "auth/forgot-password";
    }

    /**
     * Processes a forgot password request form submission.
     * Enforces anti-enumeration: always redirects with the exact same success message
     * regardless of whether the email is registered, non-existent, or rate-limited.
     *
     * @param form validated forgot password form DTO
     * @param bindingResult validation results
     * @param request HTTP request for client IP extraction
     * @param redirectAttributes flash attributes container
     * @param model Spring MVC model
     * @return redirect to forgot-password with sent flag, or form view on validation error
     */
    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        String clientIp = getClientIp(request);
        passwordResetService.requestPasswordReset(form.getEmail(), clientIp);

        redirectAttributes.addFlashAttribute("successMessage", GENERIC_RESET_SENT_MESSAGE);
        return "redirect:/forgot-password?sent=true";
    }

    /**
     * Renders the reset password form after validating the token.
     *
     * @param token raw token query parameter
     * @param model Spring MVC model
     * @return reset password template name
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(
            @RequestParam(name = "token", required = false) String token,
            Model model
    ) {
        if (token == null || token.isBlank()) {
            model.addAttribute("tokenValid", false);
            model.addAttribute("errorMessage", "Password reset token is missing. Please use the link provided in your email.");
            return "auth/reset-password";
        }

        boolean isValid = passwordResetService.isTokenValid(token.trim());
        if (!isValid) {
            model.addAttribute("tokenValid", false);
            model.addAttribute("errorMessage", "This password reset link is invalid, expired, or has already been used. Please request a new password reset link.");
            return "auth/reset-password";
        }

        model.addAttribute("tokenValid", true);
        model.addAttribute("token", token.trim());
        if (!model.containsAttribute("resetPasswordForm")) {
            model.addAttribute("resetPasswordForm", new ResetPasswordForm(token.trim()));
        }

        return "auth/reset-password";
    }

    /**
     * Processes new password submission and updates the user's credentials atomically.
     *
     * @param form validated reset password form DTO
     * @param bindingResult validation results
     * @param redirectAttributes flash attributes container
     * @param model Spring MVC model
     * @return redirect to role-appropriate login page on success, or form view on error
     */
    @PostMapping("/reset-password")
    public String processResetPassword(
            @Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (!form.isPasswordMatching()) {
            bindingResult.rejectValue("confirmPassword", "error.resetPasswordForm", "Passwords do not match.");
        }

        String rawToken = form.getToken();

        if (bindingResult.hasErrors()) {
            boolean isValid = passwordResetService.isTokenValid(rawToken);
            model.addAttribute("tokenValid", isValid);
            model.addAttribute("token", rawToken);
            return "auth/reset-password";
        }

        try {
            Student updatedStudent = passwordResetService.resetPassword(rawToken, form.getPassword());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Your password has been successfully reset. Please sign in with your new password.");

            // Direct user back to the role-appropriate login portal
            String role = updatedStudent.getRole();
            if (role != null && (role.contains("MODERATOR") || role.contains("ADMIN"))) {
                return "redirect:/login?reset=true";
            }
            return "redirect:/student/login?reset=true";

        } catch (IllegalArgumentException ex) {
            log.warn("Password reset rejected: {}", ex.getMessage());
            model.addAttribute("tokenValid", false);
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/reset-password";
        } catch (Exception ex) {
            log.error("Unexpected error during password reset", ex);
            model.addAttribute("tokenValid", false);
            model.addAttribute("errorMessage", "An error occurred while resetting your password. Please try again or request a new reset link.");
            return "auth/reset-password";
        }
    }

    private String redirectAuthenticatedUser(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> "ROLE_ADMIN".equals(r) || "ADMIN".equals(r));
        if (isAdmin) {
            return "redirect:/admin/complaints";
        }
        boolean isModerator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> "ROLE_MODERATOR".equals(r) || "MODERATOR".equals(r));
        if (isModerator) {
            return "redirect:/moderator/complaints";
        }
        return "redirect:/complaints/new";
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
    }
}
