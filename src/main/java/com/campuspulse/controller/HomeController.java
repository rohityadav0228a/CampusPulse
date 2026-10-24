package com.campuspulse.controller;

import com.campuspulse.model.Student;
import com.campuspulse.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the CampusPulse application root (GET /).
 *
 * Implements an authentication-first role-based entry flow:
 * - Anonymous visitors (Case A): Renders the public welcome and authentication-choice page.
 * - Authenticated Student (Case B): Redirects to /complaints/new.
 * - Authenticated Moderator (Case C): Redirects to /moderator/complaints.
 * - Authenticated Administrator (Case D): Redirects to /admin/complaints.
 * - Unknown or Unsupported Role (Case E): Safely redirects to /login?error=unauthorized (never grants admin access).
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final StudentService studentService;

    @Autowired
    public HomeController(@Autowired(required = false) StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Handles requests to the application root.
     * Evaluates authenticated Spring Security principal and routes according to user role.
     *
     * @param model Spring MVC model
     * @param authentication active Spring Security authentication token
     * @param request current HTTP servlet request
     * @return redirect destination for authenticated users, or 'index' template for anonymous visitors
     */
    @GetMapping("/")
    public String index(Model model, Authentication authentication, HttpServletRequest request) {
        Authentication auth = resolveAuthentication(authentication, request);
        boolean isAuthenticated = (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(auth.getName()));

        if (isAuthenticated) {
            boolean isAdmin = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_ADMIN".equals(r) || "ADMIN".equals(r));
            if (isAdmin) {
                log.info("Authenticated admin '{}' visiting root URL. Redirecting to admin complaints dashboard.", auth.getName());
                return "redirect:/admin/complaints";
            }

            boolean isModerator = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_MODERATOR".equals(r) || "MODERATOR".equals(r));
            if (isModerator) {
                log.info("Authenticated moderator '{}' visiting root URL. Redirecting to moderator complaints dashboard.", auth.getName());
                return "redirect:/moderator/complaints";
            }

            boolean isStudent = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(r -> "ROLE_STUDENT".equals(r) || "STUDENT".equals(r)
                            || (r != null && r.contains("STUDENT")));
            if (isStudent) {
                log.info("Authenticated student '{}' visiting root URL. Redirecting to complaint submission.", auth.getName());
                return "redirect:/complaints/new";
            }

            // Case E: Unknown or unsupported role - Safely reject with unauthorized error
            log.warn("Authenticated user '{}' with unrecognized authorities {} visiting root URL. Redirecting to safe sign-in error.",
                    auth.getName(), auth.getAuthorities());
            return "redirect:/login?error=unauthorized";
        }

        // Case A: Anonymous visitor - Render public welcome page
        model.addAttribute("appName", "CampusPulse");
        model.addAttribute("systemTitle", "Campus Grievance & Feedback Management System");
        model.addAttribute("isAuthenticated", false);
        model.addAttribute("isAdmin", false);
        model.addAttribute("isModerator", false);
        model.addAttribute("isStudent", false);
        model.addAttribute("currentUserRole", "ANONYMOUS");
        model.addAttribute("displayName", null);
        model.addAttribute("userEmail", null);

        return "index";
    }

    private Authentication resolveAuthentication(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication;
        }
        if (request != null && request.getUserPrincipal() instanceof Authentication auth
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(auth.getName())) {
            return auth;
        }
        Authentication scAuth = SecurityContextHolder.getContext().getAuthentication();
        if (scAuth != null && scAuth.isAuthenticated()
                && !(scAuth instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(scAuth.getName())) {
            return scAuth;
        }
        return null;
    }
}
