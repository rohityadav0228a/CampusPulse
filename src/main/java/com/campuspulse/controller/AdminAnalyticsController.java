package com.campuspulse.controller;

import com.campuspulse.dto.analytics.AnalyticsDashboardData;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.service.AnalyticsService;
import com.campuspulse.service.ComplaintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the administrative grievance analytics and intelligence dashboard.
 * Provides summary KPI metrics, category/department breakdowns, and trend visualizations.
 * Protected by Spring Security role-based authorization (ADMIN only).
 */
@Controller
@RequestMapping("/admin/analytics")
public class AdminAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final ComplaintService complaintService;

    public AdminAnalyticsController(AnalyticsService analyticsService, ComplaintService complaintService) {
        this.analyticsService = analyticsService;
        this.complaintService = complaintService;
    }

    /**
     * Renders the administrative grievance analytics dashboard with KPI cards,
     * status breakdown, category and department distribution, and complaint volume trends.
     *
     * @param statusParam optional status filter parameter
     * @param categoryParam optional category filter parameter
     * @param daysParam optional timeframe in days (7, 14, 30, or 0 for all-time, default 30)
     * @param model Spring MVC model
     * @return template name for administrative analytics dashboard
     */
    @GetMapping
    public String showAnalytics(
            @RequestParam(name = "status", required = false) String statusParam,
            @RequestParam(name = "category", required = false) String categoryParam,
            @RequestParam(name = "days", required = false, defaultValue = "30") Integer daysParam,
            Model model) {

        ComplaintStatus selectedStatus = null;
        if (statusParam != null && !statusParam.isBlank()) {
            try {
                selectedStatus = ComplaintStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid status filter specified for analytics: {}", statusParam);
            }
        }

        String selectedCategory = (categoryParam != null && !categoryParam.isBlank()) ? categoryParam.trim() : null;
        Integer selectedDays = (daysParam != null && daysParam >= 0) ? daysParam : 30;

        AnalyticsDashboardData data = analyticsService.getAnalyticsData(selectedStatus, selectedCategory, selectedDays);

        model.addAttribute("analytics", data);
        model.addAttribute("summary", data.summary());
        model.addAttribute("categories", complaintService.getAllowedCategories());
        model.addAttribute("statuses", ComplaintStatus.values());
        model.addAttribute("selectedStatus", selectedStatus != null ? selectedStatus.name() : "");
        model.addAttribute("selectedCategory", selectedCategory != null ? selectedCategory : "");
        model.addAttribute("selectedDays", selectedDays);

        return "admin/analytics/index";
    }
}
