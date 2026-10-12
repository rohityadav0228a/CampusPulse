package com.campuspulse.controller;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.service.ComplaintService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.campuspulse.dto.AiClassificationResult;
import com.campuspulse.dto.AiSuggestRequest;
import com.campuspulse.dto.AiSuggestResponse;
import com.campuspulse.service.AiClassifierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Controller handling student grievance submission, viewing, tracking, and AI category suggestions.
 */
@Controller
@RequestMapping("/complaints")
public class ComplaintController {

    private static final Logger log = LoggerFactory.getLogger(ComplaintController.class);
    private static final Pattern REFERENCE_ID_PATTERN = Pattern.compile("^CP-\\d{4}-[A-Za-z0-9]{8,10}$");

    private final ComplaintService complaintService;
    private final AiClassifierService aiClassifierService;

    public ComplaintController(ComplaintService complaintService, AiClassifierService aiClassifierService) {
        this.complaintService = complaintService;
        this.aiClassifierService = aiClassifierService;
    }

    /**
     * Advisory AI category suggestion endpoint.
     * Takes title and description, sends combined text to Python microservice, validates
     * the predicted category against whitelist, and returns the suggested category with confidence.
     * Never persists or forces the suggestion.
     *
     * @param request grievance title and description
     * @param bindingResult validation results
     * @return advisory category suggestion or friendly error
     */
    @PostMapping(value = "/suggest-category", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<AiSuggestResponse> suggestCategory(
            @Valid @RequestBody AiSuggestRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().getFirst().getDefaultMessage();
            return ResponseEntity.badRequest().body(AiSuggestResponse.failure(errorMsg));
        }

        String combinedText = request.toCombinedText();
        if (combinedText.trim().length() < 5) {
            return ResponseEntity.badRequest().body(
                    AiSuggestResponse.failure("Complaint title and description combined must contain at least 5 characters."));
        }

        try {
            AiClassificationResult result = aiClassifierService.classify(combinedText);
            if (result.isSuccess()) {
                return ResponseEntity.ok(AiSuggestResponse.success(
                        result.category(),
                        result.confidence(),
                        result.warning(),
                        result.modelVersion()
                ));
            } else if (result.isCategoryInvalid()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                        AiSuggestResponse.failure("The AI suggestion could not be verified against allowed campus categories. Please select manually."));
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                        AiSuggestResponse.failure("AI category classification is temporarily unavailable. Please select a category manually."));
            }
        } catch (Exception ex) {
            log.warn("Category suggestion encountered an unexpected error: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    AiSuggestResponse.failure("AI category classification is temporarily unavailable. Please select a category manually."));
        }
    }

    @ModelAttribute("categories")
    public List<String> populateCategories() {
        return complaintService.getAllowedCategories();
    }

    @ModelAttribute("departments")
    public List<String> populateDepartments() {
        return complaintService.getAllowedDepartments();
    }

    /**
     * Renders the student complaint submission form.
     *
     * @param model Spring MVC model
     * @return template name for complaint form
     */
    @GetMapping("/new")
    public String showComplaintForm(Model model) {
        if (!model.containsAttribute("complaintForm")) {
            model.addAttribute("complaintForm", new ComplaintForm());
        }
        return "complaints/form";
    }

    /**
     * Handles student complaint submissions with server-side validation and database persistence.
     *
     * @param complaintForm user submitted form inputs
     * @param bindingResult validation result container
     * @param model Spring MVC model
     * @return redirect to success page on success, or form view with errors
     */
    @PostMapping
    public String submitComplaint(
            @Valid @ModelAttribute("complaintForm") ComplaintForm complaintForm,
            BindingResult bindingResult,
            Model model) {

        // Validate category against predefined allowed whitelist
        if (complaintForm.getCategory() != null && !complaintForm.getCategory().isBlank()
                && !complaintService.getAllowedCategories().contains(complaintForm.getCategory())) {
            bindingResult.rejectValue("category", "error.category", "Please select a valid campus category from the list.");
        }

        // Validate department against predefined allowed whitelist
        if (complaintForm.getDepartment() != null && !complaintForm.getDepartment().isBlank()
                && !complaintService.getAllowedDepartments().contains(complaintForm.getDepartment())) {
            bindingResult.rejectValue("department", "error.department", "Please select a valid department from the list.");
        }

        // If validation errors are present, re-render form with error feedback
        if (bindingResult.hasErrors()) {
            return "complaints/form";
        }

        // Attempt persistence to database
        try {
            Complaint saved = complaintService.createComplaint(complaintForm);
            return "redirect:/complaints/success/" + saved.getReferenceId();
        } catch (Exception ex) {
            log.error("Failed to persist complaint to MySQL database", ex);
            model.addAttribute("databaseError",
                    "We encountered an issue saving your complaint to the database. Please verify the database connection or try again shortly.");
            return "complaints/form";
        }
    }

    /**
     * Displays confirmation page with actual saved complaint reference ID.
     *
     * @param referenceId human-readable reference identifier
     * @param model Spring MVC model
     * @return success view template name or redirect to complaints list if not found
     */
    @GetMapping("/success/{referenceId}")
    public String showSuccessPage(@PathVariable("referenceId") String referenceId, Model model) {
        return complaintService.getByReferenceId(referenceId)
                .map(complaint -> {
                    model.addAttribute("complaint", complaint);
                    return "complaints/success";
                })
                .orElse("redirect:/complaints");
    }

    /**
     * Displays list of all registered complaints ordered newest first.
     *
     * @param model Spring MVC model
     * @return template name for complaints list
     */
    @GetMapping
    public String listComplaints(Model model) {
        List<Complaint> complaints = complaintService.getAllComplaints();
        model.addAttribute("complaints", complaints);
        return "complaints/list";
    }

    /**
     * Handles student complaint tracking by reference ID.
     *
     * @param referenceId query parameter entered by user
     * @param model Spring MVC model
     * @return tracking template view name
     */
    @GetMapping("/track")
    public String trackComplaint(
            @RequestParam(name = "referenceId", required = false) String referenceId,
            Model model) {

        if (referenceId == null) {
            return "complaints/track";
        }

        String trimmed = referenceId.trim();
        model.addAttribute("searchedReferenceId", trimmed);

        if (trimmed.isEmpty()) {
            model.addAttribute("validationError", "Please enter a complaint reference ID.");
            return "complaints/track";
        }

        if (!REFERENCE_ID_PATTERN.matcher(trimmed).matches()) {
            model.addAttribute("validationError",
                    "Invalid reference ID format. The ID must follow the 'CP-YYYY-XXXXXXXX' pattern (e.g., CP-2026-A8F3B91C).");
            return "complaints/track";
        }

        String normalizedRef = trimmed.toUpperCase();
        model.addAttribute("searchedReferenceId", normalizedRef);

        complaintService.getByReferenceId(normalizedRef)
                .ifPresentOrElse(
                        complaint -> model.addAttribute("complaint", complaint),
                        () -> model.addAttribute("notFound", true)
                );

        return "complaints/track";
    }
}
