package com.campuspulse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the CampusPulse homepage and public landing page.
 */
@Controller
public class HomeController {

    /**
     * Handles requests to the application root.
     *
     * @param model Spring MVC model
     * @return the template name for the homepage
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", "CampusPulse");
        model.addAttribute("systemTitle", "Campus Grievance & Feedback Management System");
        return "index";
    }
}
