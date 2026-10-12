package com.campuspulse.controller;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintEscalationAudit;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.SlaEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Controller for administrative review and status lifecycle management of campus complaints,
 * including chronological status audit trail inspection, SLA deadline compliance, and escalation audits.
 */
@Controller
@RequestMapping("/admin/complaints")
public class AdminComplaintController {

    private static final Logger log = LoggerFactory.getLogger(AdminComplaintController.class);

    private final ComplaintService complaintService;
    private final SlaEscalationService slaEscalationService;

    public AdminComplaintController(ComplaintService complaintService) {
        this(complaintService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminComplaintController(
            ComplaintService complaintService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SlaEscalationService slaEscalationService) {
        this.complaintService = complaintService;
        this.slaEscalationService = slaEscalationService;
    }

    /**
     * Renders the administrative grievance dashboard with summary counts, filter controls,
     * SLA compliance metrics, and filtered complaint records.
     *
     * @param statusParam optional status filter parameter
     * @param categoryParam optional category filter parameter
     * @param slaStatusParam optional SLA status filter parameter
     * @param model Spring MVC model
     * @return template name for administrative dashboard
     */
    @GetMapping
    public String dashboard(
            @RequestParam(name = "status", required = false) String statusParam,
            @RequestParam(name = "category", required = false) String categoryParam,
            @RequestParam(name = "slaStatus", required = false) String slaStatusParam,
            Model model) {

        ComplaintStatus selectedStatus = null;
        if (statusParam != null && !statusParam.isBlank()) {
            try {
                selectedStatus = ComplaintStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid status filter specified: {}", statusParam);
            }
        }

        String selectedCategory = (categoryParam != null && !categoryParam.isBlank()) ? categoryParam.trim() : null;
        String selectedSlaStatus = (slaStatusParam != null && !slaStatusParam.isBlank()) ? slaStatusParam.trim().toUpperCase() : "";

        List<Complaint> complaints = (!selectedSlaStatus.isBlank() && !"ALL".equalsIgnoreCase(selectedSlaStatus))
                ? complaintService.getFilteredComplaints(selectedStatus, selectedCategory, selectedSlaStatus)
                : complaintService.getFilteredComplaints(selectedStatus, selectedCategory);
        Map<String, Long> statusCounts = complaintService.getStatusCounts();
        Map<String, Long> slaCounts = complaintService.getSlaCounts();

        model.addAttribute("complaints", complaints);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("slaCounts", slaCounts);
        model.addAttribute("selectedStatus", selectedStatus != null ? selectedStatus.name() : "");
        model.addAttribute("selectedCategory", selectedCategory != null ? selectedCategory : "");
        model.addAttribute("selectedSlaStatus", selectedSlaStatus);
        model.addAttribute("statuses", ComplaintStatus.values());
        model.addAttribute("categories", complaintService.getAllowedCategories());

        return "admin/complaints/index";
    }

    /**
     * Renders the administrative detail view for a specific complaint, including full description,
     * status update controls, chronological status audit history, and immutable SLA escalation audits.
     *
     * @param referenceId unique reference identifier
     * @param model Spring MVC model
     * @param redirectAttributes flash attributes container
     * @return template name for detail view or redirect to dashboard if not found
     */
    @GetMapping("/{referenceId}")
    public String complaintDetail(
            @PathVariable("referenceId") String referenceId,
            Model model,
            RedirectAttributes redirectAttributes) {

        return complaintService.getByReferenceId(referenceId)
                .map(complaint -> {
                    List<ComplaintStatusHistory> statusHistory = complaintService.getStatusHistory(referenceId);
                    List<ComplaintEscalationAudit> escalationAudits = (slaEscalationService != null)
                            ? slaEscalationService.getEscalationAudits(referenceId)
                            : Collections.emptyList();
                    model.addAttribute("complaint", complaint);
                    model.addAttribute("statuses", ComplaintStatus.values());
                    model.addAttribute("statusHistory", statusHistory);
                    model.addAttribute("escalationAudits", escalationAudits);
                    return "admin/complaints/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Complaint not found with reference ID: " + referenceId);
                    return "redirect:/admin/complaints";
                });
    }

    /**
     * Handles administrative status update submissions. Obtains the actor identity strictly from the
     * authenticated Spring Security principal (never from client request parameters).
     * Applies the status update and audit record atomically.
     *
     * @param referenceId unique reference identifier
     * @param statusParam new status string submitted by administrator
     * @param note optional explanatory resolution note
     * @param authentication active Spring Security authentication token
     * @param redirectAttributes flash attributes container
     * @return redirect to the complaint detail page
     */
    @PostMapping("/{referenceId}/status")
    public String updateStatus(
            @PathVariable("referenceId") String referenceId,
            @RequestParam("status") String statusParam,
            @RequestParam(name = "note", required = false) String note,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (statusParam == null || statusParam.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Status must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        ComplaintStatus newStatus;
        try {
            newStatus = ComplaintStatus.valueOf(statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid status update attempted for {}: {}", referenceId, statusParam);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid status value provided: " + statusParam);
            return "redirect:/admin/complaints/" + referenceId;
        }

        // Actor username is strictly obtained from the authenticated Spring Security principal
        String actorUsername = resolveActorUsername(principal, request);

        try {
            complaintService.updateComplaintStatus(referenceId, newStatus, actorUsername, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Status successfully updated to " + newStatus.getDisplayName() + ".");
        } catch (IllegalArgumentException ex) {
            log.warn("Status update rejected for complaint {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to update status for complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to update status: " + ex.getMessage());
        }

        return "redirect:/admin/complaints/" + referenceId;
    }

    private String resolveActorUsername(java.security.Principal principal, jakarta.servlet.http.HttpServletRequest request) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        if (request != null && request.getSession(false) != null) {
            Object secContext = request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
            if (secContext instanceof org.springframework.security.core.context.SecurityContext sc) {
                if (sc.getAuthentication() != null && sc.getAuthentication().getName() != null) {
                    return sc.getAuthentication().getName();
                }
            }
        }
        return "ADMIN";
    }
}
