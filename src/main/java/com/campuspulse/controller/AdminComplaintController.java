package com.campuspulse.controller;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintEscalationAudit;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.SlaEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
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
     * Renders the administrative grievance dashboard with summary counts, keyword search,
     * filter controls, SLA compliance metrics, and Spring Data paginated complaint records.
     *
     * @param searchParam optional free-text search query across referenceId, title, description, and studentEmail
     * @param statusParam optional status filter parameter
     * @param categoryParam optional category filter parameter
     * @param slaStatusParam optional SLA status filter parameter
     * @param pageParam zero-based page number (defaults to 0)
     * @param sizeParam page size (defaults to 10)
     * @param model Spring MVC model
     * @return template name for administrative dashboard
     */
    @GetMapping
    public String dashboard(
            @RequestParam(name = "q", required = false) String searchParam,
            @RequestParam(name = "status", required = false) String statusParam,
            @RequestParam(name = "category", required = false) String categoryParam,
            @RequestParam(name = "department", required = false) String departmentParam,
            @RequestParam(name = "priority", required = false) String priorityParam,
            @RequestParam(name = "slaStatus", required = false) String slaStatusParam,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer pageParam,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer sizeParam,
            Model model) {

        int page = (pageParam != null && pageParam >= 0) ? pageParam : 0;
        int size = (sizeParam != null && sizeParam > 0) ? Math.min(sizeParam, ComplaintService.MAX_PAGE_SIZE) : ComplaintService.DEFAULT_PAGE_SIZE;

        ComplaintStatus selectedStatus = null;
        if (statusParam != null && !statusParam.isBlank()) {
            try {
                selectedStatus = ComplaintStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid status filter specified: {}", statusParam);
            }
        }

        String selectedCategory = (categoryParam != null && !categoryParam.isBlank()) ? categoryParam.trim() : null;
        String selectedDepartment = (departmentParam != null && !departmentParam.isBlank()) ? departmentParam.trim() : null;

        ComplaintPriority selectedPriority = null;
        if (priorityParam != null && !priorityParam.isBlank()) {
            try {
                selectedPriority = ComplaintPriority.valueOf(priorityParam.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid priority filter specified: {}", priorityParam);
            }
        }

        String selectedSlaStatus = (slaStatusParam != null && !slaStatusParam.isBlank()) ? slaStatusParam.trim().toUpperCase() : "";
        String searchQuery = (searchParam != null && !searchParam.isBlank()) ? searchParam.trim() : "";

        Page<Complaint> complaintsPage = (selectedDepartment != null || selectedPriority != null)
                ? complaintService.searchComplaints(
                        searchQuery,
                        selectedStatus,
                        selectedCategory,
                        selectedDepartment,
                        selectedPriority,
                        selectedSlaStatus,
                        page,
                        size
                )
                : complaintService.searchComplaints(
                        searchQuery,
                        selectedStatus,
                        selectedCategory,
                        selectedSlaStatus,
                        page,
                        size
                );

        Map<String, Long> statusCounts = complaintService.getStatusCounts();
        Map<String, Long> slaCounts = complaintService.getSlaCounts();

        model.addAttribute("complaintsPage", complaintsPage);
        model.addAttribute("complaints", complaintsPage.getContent());
        model.addAttribute("searchQuery", searchQuery);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("slaCounts", slaCounts);
        model.addAttribute("selectedStatus", selectedStatus != null ? selectedStatus.name() : "");
        model.addAttribute("selectedCategory", selectedCategory != null ? selectedCategory : "");
        model.addAttribute("selectedDepartment", selectedDepartment != null ? selectedDepartment : "");
        model.addAttribute("selectedPriority", selectedPriority != null ? selectedPriority.name() : "");
        model.addAttribute("selectedSlaStatus", selectedSlaStatus);
        model.addAttribute("currentPage", complaintsPage.getNumber());
        model.addAttribute("totalPages", complaintsPage.getTotalPages());
        model.addAttribute("totalElements", complaintsPage.getTotalElements());
        model.addAttribute("pageSize", complaintsPage.getSize());
        model.addAttribute("pageSizeOptions", List.of(10, 20, 50));
        model.addAttribute("statuses", ComplaintStatus.values());
        model.addAttribute("categories", complaintService.getAllowedCategories());
        model.addAttribute("departments", complaintService.getAllowedDepartments());
        model.addAttribute("priorities", ComplaintPriority.values());

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
                    model.addAttribute("departments", complaintService.getAllowedDepartments());
                    model.addAttribute("categories", complaintService.getAllowedCategories());
                    model.addAttribute("priorities", ComplaintPriority.values());
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

        if (note != null && note.trim().length() > ComplaintService.MAX_RESOLUTION_NOTE_LENGTH) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Resolution note cannot exceed " + ComplaintService.MAX_RESOLUTION_NOTE_LENGTH + " characters.");
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

    /**
     * Handles administrative resolution note corrections for already-resolved grievances.
     * Enforces non-blank content, maximum length constraints, and auditable history tracking.
     *
     * @param referenceId unique reference identifier
     * @param resolutionNote updated resolution note content
     * @param principal authenticated admin principal
     * @param request HTTP servlet request
     * @param redirectAttributes flash attributes container
     * @return redirect to complaint detail view
     */
    @PostMapping("/{referenceId}/resolution-note")
    public String updateResolutionNote(
            @PathVariable("referenceId") String referenceId,
            @RequestParam("resolutionNote") String resolutionNote,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (resolutionNote == null || resolutionNote.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resolution note must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        if (resolutionNote.trim().length() > ComplaintService.MAX_RESOLUTION_NOTE_LENGTH) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Resolution note cannot exceed " + ComplaintService.MAX_RESOLUTION_NOTE_LENGTH + " characters.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        String actorUsername = resolveActorUsername(principal, request);

        try {
            complaintService.updateResolutionNote(referenceId, resolutionNote, actorUsername);
            redirectAttributes.addFlashAttribute("successMessage", "Resolution note successfully updated.");
        } catch (IllegalArgumentException ex) {
            log.warn("Resolution note update rejected for complaint {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to update resolution note for complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to update resolution note: " + ex.getMessage());
        }

        return "redirect:/admin/complaints/" + referenceId;
    }

    /**
     * Handles department reassignment for a complaint.
     * Enforces validated, controlled department values, CSRF protection, and audit logging.
     */
    @PostMapping("/{referenceId}/department")
    public String updateDepartment(
            @PathVariable("referenceId") String referenceId,
            @RequestParam("department") String department,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (department == null || department.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Department must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        String actorUsername = resolveActorUsername(principal, request);

        try {
            Complaint updated = complaintService.updateDepartment(referenceId, department, actorUsername);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Department successfully reassigned to " + updated.getDepartment() + ".");
        } catch (IllegalArgumentException ex) {
            log.warn("Department reassignment rejected for complaint {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to reassign department for complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to reassign department: " + ex.getMessage());
        }

        return "redirect:/admin/complaints/" + referenceId;
    }

    /**
     * Handles administrative category correction (e.g. amending AI suggestion).
     * Enforces validated, controlled category values, CSRF protection, and audit logging.
     */
    @PostMapping("/{referenceId}/category")
    public String updateCategory(
            @PathVariable("referenceId") String referenceId,
            @RequestParam("category") String category,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (category == null || category.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Category must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        String actorUsername = resolveActorUsername(principal, request);

        try {
            Complaint updated = complaintService.updateCategory(referenceId, category, actorUsername);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Category successfully corrected to " + updated.getCategory() + ".");
        } catch (IllegalArgumentException ex) {
            log.warn("Category correction rejected for complaint {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to correct category for complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to correct category: " + ex.getMessage());
        }

        return "redirect:/admin/complaints/" + referenceId;
    }

    /**
     * Handles complaint priority level updates.
     * Enforces valid ComplaintPriority values, CSRF protection, and audit logging.
     */
    @PostMapping("/{referenceId}/priority")
    public String updatePriority(
            @PathVariable("referenceId") String referenceId,
            @RequestParam("priority") String priorityParam,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (priorityParam == null || priorityParam.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Priority must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        ComplaintPriority newPriority;
        try {
            newPriority = ComplaintPriority.valueOf(priorityParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid priority value provided for {}: {}", referenceId, priorityParam);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid priority value provided: " + priorityParam);
            return "redirect:/admin/complaints/" + referenceId;
        }

        String actorUsername = resolveActorUsername(principal, request);

        try {
            Complaint updated = complaintService.updatePriority(referenceId, newPriority, actorUsername);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Priority successfully updated to " + updated.getPriority().getDisplayName() + ".");
        } catch (IllegalArgumentException ex) {
            log.warn("Priority update rejected for complaint {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to update priority for complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to update priority: " + ex.getMessage());
        }

        return "redirect:/admin/complaints/" + referenceId;
    }

    /**
     * Handles simultaneous assignment of department and category correction.
     */
    @PostMapping("/{referenceId}/assignment")
    public String updateAssignment(
            @PathVariable("referenceId") String referenceId,
            @RequestParam("department") String department,
            @RequestParam("category") String category,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (department == null || department.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Department must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }
        if (category == null || category.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Category must not be blank.");
            return "redirect:/admin/complaints/" + referenceId;
        }

        String actorUsername = resolveActorUsername(principal, request);

        try {
            complaintService.updateAssignment(referenceId, department, category, actorUsername);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Complaint assignment and category successfully updated.");
        } catch (IllegalArgumentException ex) {
            log.warn("Assignment update rejected for complaint {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to update assignment for complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to update assignment: " + ex.getMessage());
        }

        return "redirect:/admin/complaints/" + referenceId;
    }

    /**
     * Safely deletes (soft-deletes) an existing resolved complaint.
     * Accessible strictly by ROLE_ADMIN only.
     * Rechecks authorization and resolved status on the server.
     * Unresolved complaints or missing/already-deleted records are handled gracefully with appropriate flash messages.
     *
     * @param referenceId unique reference identifier
     * @param reason administrative rationale for complaint deletion/archival
     * @param principal authenticated admin principal
     * @param request HTTP request
     * @param redirectAttributes redirect attributes
     * @return redirect to admin complaints dashboard with flash message
     */
    @PostMapping("/{referenceId}/delete")
    public String deleteResolvedComplaint(
            @PathVariable("referenceId") String referenceId,
            @RequestParam(name = "reason", required = false) String reason,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String actorUsername = resolveActorUsername(principal, request);

        try {
            complaintService.deleteResolvedComplaint(referenceId, actorUsername, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Complaint " + referenceId + " was safely deleted and archived with a permanent audit record.");
            return "redirect:/admin/complaints";
        } catch (IllegalArgumentException ex) {
            log.warn("Complaint deletion rejected for {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/complaints";
        } catch (IllegalStateException ex) {
            log.warn("Complaint deletion invalid state for {}: {}", referenceId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/complaints/" + referenceId;
        } catch (Exception ex) {
            log.error("Unexpected error deleting complaint {}", referenceId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete complaint: " + ex.getMessage());
            return "redirect:/admin/complaints/" + referenceId;
        }
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
