package com.campuspulse.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller handling authentication views such as the admin login page.
 */
@Controller
public class AuthController {

    /**
     * Renders the admin login page.
     *
     * @param error optional error query parameter indicating failed authentication
     * @param logout optional logout query parameter indicating successful logout
     * @param authentication current authentication object
     * @param model Spring MVC model
     * @return login template name, or redirect to admin dashboard if already authenticated
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout,
            Authentication authentication,
            Model model
    ) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/admin/complaints";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been successfully logged out.");
        }

        return "login";
    }
}
