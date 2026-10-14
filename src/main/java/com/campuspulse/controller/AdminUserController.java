package com.campuspulse.controller;

import com.campuspulse.model.Student;
import com.campuspulse.model.UserRoleAudit;
import com.campuspulse.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Controller for administrative user account inspection, search, role management,
 * and audit trail review. Strictly protected by ROLE_ADMIN.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserManagementService userManagementService;

    public AdminUserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    /**
     * Renders the administrative user governance dashboard with search, metrics,
     * account list, role update controls, and recent role change audit history.
     */
    @GetMapping
    public String listUsers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer pageParam,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer sizeParam,
            Model model) {

        int page = (pageParam != null && pageParam >= 0) ? pageParam : 0;
        int size = (sizeParam != null && sizeParam > 0) ? Math.min(sizeParam, 100) : 10;

        Page<Student> userPage = userManagementService.searchUsers(query, role, page, size);
        Map<String, Long> metrics = userManagementService.getRoleMetrics();
        List<UserRoleAudit> recentAudits = userManagementService.getRecentAuditHistory();

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("page", userPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalElements", userPage.getTotalElements());
        model.addAttribute("searchQuery", (query != null) ? query.trim() : "");
        model.addAttribute("roleFilter", (role != null) ? role.trim() : "ALL");
        model.addAttribute("metrics", metrics);
        model.addAttribute("recentAudits", recentAudits);
        model.addAttribute("allowedRoles", List.of(
                UserManagementService.ROLE_STUDENT,
                UserManagementService.ROLE_MODERATOR,
                UserManagementService.ROLE_ADMIN
        ));

        return "admin/users/index";
    }

    /**
     * Processes an administrative role transition request for a user account.
     * Enforces allowlist validation, explicit admin promotion confirmation,
     * last-admin protection, audit recording, and session invalidation.
     */
    @PostMapping("/{id}/role")
    public String updateUserRole(
            @PathVariable("id") Long userId,
            @RequestParam("role") String newRole,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "confirmAdminPromotion", required = false, defaultValue = "false") boolean confirmAdminPromotion,
            Principal principal,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String actorUsername = resolveActorUsername(principal, request);

        try {
            Student updated = userManagementService.updateUserRole(
                    userId,
                    newRole,
                    actorUsername,
                    reason,
                    confirmAdminPromotion
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Role for " + updated.getEmail() + " successfully updated to " + updated.getRole() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Role update rejected for user id={}: {}", userId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error updating role for user id={}", userId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An unexpected error occurred while updating user role: " + ex.getMessage());
        }

        return "redirect:/admin/users";
    }

    private String resolveActorUsername(Principal principal, HttpServletRequest request) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        if (request != null && request.getSession(false) != null) {
            Object secContext = request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
            if (secContext instanceof SecurityContext sc) {
                if (sc.getAuthentication() != null && sc.getAuthentication().getName() != null) {
                    return sc.getAuthentication().getName();
                }
            }
        }
        return "ADMIN";
    }
}
