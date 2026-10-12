package com.campuspulse.controller;

import com.campuspulse.dto.insights.AdminInsightsData;
import com.campuspulse.dto.insights.SlaSummary;
import com.campuspulse.service.InsightsService;
import com.campuspulse.service.SlaEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the administrative grievance insights and AI recommendation engine.
 * Renders database-backed insights including period comparisons, unresolved department backlogs,
 * category concentrations, SLA compliance metrics, and transparent, rule-based recommendations.
 * Protected by Spring Security role-based authorization (ADMIN only).
 */
@Controller
@RequestMapping("/admin/insights")
public class AdminInsightsController {

    private static final Logger log = LoggerFactory.getLogger(AdminInsightsController.class);

    private final InsightsService insightsService;
    private final SlaEscalationService slaEscalationService;

    public AdminInsightsController(InsightsService insightsService) {
        this(insightsService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminInsightsController(
            InsightsService insightsService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SlaEscalationService slaEscalationService) {
        this.insightsService = insightsService;
        this.slaEscalationService = slaEscalationService;
    }

    /**
     * Renders the administrative grievance insights and rule-based recommendation interface.
     *
     * @param daysParam optional comparison timeframe window in days (7, 14, 30, 60, default 14)
     * @param model Spring MVC model
     * @return template name for administrative insights dashboard
     */
    @GetMapping
    public String showInsights(
            @RequestParam(name = "days", required = false, defaultValue = "14") Integer daysParam,
            Model model) {

        int selectedDays;
        if (daysParam == null || daysParam <= 0) {
            selectedDays = 14;
        } else if (daysParam == 7 || daysParam == 14 || daysParam == 30 || daysParam == 60) {
            selectedDays = daysParam;
        } else {
            // Normalize non-standard timeframe inputs safely
            log.info("Normalizing custom timeframe input days={} to 14", daysParam);
            selectedDays = 14;
        }

        AdminInsightsData insights = insightsService.getInsightsData(selectedDays);
        SlaSummary slaSummary = (slaEscalationService != null)
                ? slaEscalationService.getSlaSummary()
                : insights.slaSummary();

        model.addAttribute("insights", insights);
        model.addAttribute("comparison", insights.periodComparison());
        model.addAttribute("statusDistribution", insights.statusDistribution());
        model.addAttribute("topCategories", insights.topCategories());
        model.addAttribute("departmentBacklogs", insights.departmentBacklogs());
        model.addAttribute("recommendations", insights.recommendations());
        model.addAttribute("slaSummary", slaSummary);
        model.addAttribute("selectedDays", selectedDays);

        return "admin/insights/index";
    }
}
