package com.campuspulse.controller;

import com.campuspulse.dto.ComplaintForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.service.ComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.campuspulse.dto.AiClassificationResult;
import com.campuspulse.service.AiClassifierService;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ComplaintController.class)
class ComplaintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private AiClassifierService aiClassifierService;

    private static final List<String> MOCK_CATEGORIES = List.of(
            "Academic Affairs",
            "Hostel & Residence",
            "Infrastructure & Labs"
    );

    private static final List<String> MOCK_DEPARTMENTS = List.of(
            "Academic Affairs Division",
            "Hostel Administration",
            "Campus Facilities & Works"
    );

    @BeforeEach
    void setUp() {
        when(complaintService.getAllowedCategories()).thenReturn(MOCK_CATEGORIES);
        when(complaintService.getAllowedDepartments()).thenReturn(MOCK_DEPARTMENTS);
    }

    @Test
    @DisplayName("GET /complaints/new renders the submission form with category and department dropdowns")
    void shouldDisplayComplaintFormWithCategoriesAndDepartments() throws Exception {
        mockMvc.perform(get("/complaints/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/form"))
                .andExpect(model().attributeExists("complaintForm"))
                .andExpect(content().string(containsString("Submit a Campus Grievance")))
                .andExpect(content().string(containsString("Academic Affairs")))
                .andExpect(content().string(containsString("Hostel Administration")))
                .andExpect(content().string(containsString("Return to Home")));
    }

    @Test
    @DisplayName("POST /complaints with empty inputs fails validation and does not save")
    void shouldFailValidationWhenFieldsAreEmpty() throws Exception {
        mockMvc.perform(post("/complaints")
                        .param("title", "")
                        .param("description", "")
                        .param("category", "")
                        .param("department", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/form"))
                .andExpect(model().attributeHasFieldErrors("complaintForm", "title", "description", "category", "department"))
                .andExpect(content().string(containsString("Title is required")))
                .andExpect(content().string(containsString("Description is required")));

        verify(complaintService, never()).createComplaint(any());
    }

    @Test
    @DisplayName("POST /complaints with invalid category or department fails validation and does not save")
    void shouldFailValidationWhenCategoryOrDepartmentNotInAllowedList() throws Exception {
        mockMvc.perform(post("/complaints")
                        .param("title", "Water Leakage Issue")
                        .param("description", "Water is continuously leaking in the 2nd floor bathroom corridor.")
                        .param("category", "Disallowed Unknown Category")
                        .param("department", "Disallowed Unknown Department"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/form"))
                .andExpect(model().attributeHasFieldErrors("complaintForm", "category", "department"))
                .andExpect(content().string(containsString("Please select a valid campus category from the list.")))
                .andExpect(content().string(containsString("Please select a valid department from the list.")));

        verify(complaintService, never()).createComplaint(any());
    }

    @Test
    @DisplayName("POST /complaints with valid input saves complaint and redirects to success page with reference ID")
    void shouldSuccessfullySaveComplaintAndRedirectToSuccess() throws Exception {
        Complaint saved = new Complaint(
                "CP-2026-TEST8888",
                "Water Leakage in Hostel B",
                "There is persistent water leakage on the second floor washroom.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        saved.setId(101L);

        when(complaintService.createComplaint(any(ComplaintForm.class))).thenReturn(saved);

        mockMvc.perform(post("/complaints")
                        .param("title", "Water Leakage in Hostel B")
                        .param("description", "There is persistent water leakage on the second floor washroom.")
                        .param("category", "Hostel & Residence")
                        .param("department", "Hostel Administration"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/success/CP-2026-TEST8888"));

        verify(complaintService, times(1)).createComplaint(any(ComplaintForm.class));
    }

    @Test
    @DisplayName("POST /complaints handles database failure by returning form with clear error and never displays success")
    void shouldReturnFormWithDatabaseErrorWhenSaveFails() throws Exception {
        when(complaintService.createComplaint(any(ComplaintForm.class)))
                .thenThrow(new RuntimeException("Simulated MySQL database failure"));

        mockMvc.perform(post("/complaints")
                        .param("title", "Water Leakage in Hostel B")
                        .param("description", "There is persistent water leakage on the second floor washroom.")
                        .param("category", "Hostel & Residence")
                        .param("department", "Hostel Administration"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/form"))
                .andExpect(model().attributeExists("databaseError"))
                .andExpect(content().string(containsString("We encountered an issue saving your complaint to the database")));

        verify(complaintService, times(1)).createComplaint(any(ComplaintForm.class));
    }

    @Test
    @DisplayName("GET /complaints/success/{referenceId} renders confirmation page with the actual saved reference ID")
    void shouldDisplaySuccessPageWhenComplaintExists() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-TEST8888",
                "Water Leakage in Hostel B",
                "There is persistent water leakage on the second floor washroom.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        complaint.setId(101L);

        when(complaintService.getByReferenceId("CP-2026-TEST8888")).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/complaints/success/CP-2026-TEST8888"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/success"))
                .andExpect(content().string(containsString("CP-2026-TEST8888")))
                .andExpect(content().string(containsString("Water Leakage in Hostel B")))
                .andExpect(content().string(containsString("Grievance Registered Successfully")));
    }

    @Test
    @DisplayName("GET /complaints/success/{referenceId} redirects to registry if reference ID is not found")
    void shouldRedirectFromSuccessPageWhenComplaintNotFound() throws Exception {
        when(complaintService.getByReferenceId("NON-EXISTENT-REF")).thenReturn(Optional.empty());

        mockMvc.perform(get("/complaints/success/NON-EXISTENT-REF"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints"));
    }

    @Test
    @DisplayName("GET /complaints renders list with actual saved complaints")
    void shouldDisplayComplaintsListPage() throws Exception {
        Complaint c1 = new Complaint(
                "CP-2026-AAAA1111",
                "Library AC Not Working",
                "Central reading hall AC unit 2 has ceased cooling.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        c1.setId(1L);

        when(complaintService.getAllComplaints()).thenReturn(List.of(c1));

        mockMvc.perform(get("/complaints"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/list"))
                .andExpect(content().string(containsString("Campus Grievance Registry")))
                .andExpect(content().string(containsString("CP-2026-AAAA1111")))
                .andExpect(content().string(containsString("Library AC Not Working")));
    }

    @Test
    @DisplayName("GET /complaints renders clean empty state when no complaints exist")
    void shouldDisplayEmptyStateWhenNoComplaints() throws Exception {
        when(complaintService.getAllComplaints()).thenReturn(List.of());

        mockMvc.perform(get("/complaints"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/list"))
                .andExpect(content().string(containsString("No Grievances Recorded Yet")));
    }

    @Test
    @DisplayName("GET /complaints/track displays tracking form without search query")
    void shouldDisplayTrackingPageWithoutSearchQuery() throws Exception {
        mockMvc.perform(get("/complaints/track"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(content().string(containsString("Track Complaint Status")))
                .andExpect(content().string(containsString("Complaint Reference ID")))
                .andExpect(content().string(containsString("CP-YYYY-XXXXXXXX")));

        verify(complaintService, never()).getByReferenceId(any());
    }

    @Test
    @DisplayName("GET /complaints/track displays validation message when reference ID is blank")
    void shouldDisplayValidationErrorWhenReferenceIdIsBlank() throws Exception {
        mockMvc.perform(get("/complaints/track").param("referenceId", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attributeExists("validationError"))
                .andExpect(content().string(containsString("Please enter a complaint reference ID.")));

        verify(complaintService, never()).getByReferenceId(any());
    }

    @Test
    @DisplayName("GET /complaints/track displays validation message when reference ID is malformed")
    void shouldDisplayValidationErrorWhenReferenceIdIsMalformed() throws Exception {
        mockMvc.perform(get("/complaints/track").param("referenceId", "INVALID-FORMAT"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attributeExists("validationError"))
                .andExpect(content().string(containsString("Invalid reference ID format")));

        verify(complaintService, never()).getByReferenceId(any());
    }

    @Test
    @DisplayName("GET /complaints/track displays not-found message when complaint does not exist")
    void shouldDisplayNotFoundMessageWhenComplaintDoesNotExist() throws Exception {
        when(complaintService.getByReferenceId("CP-2026-NOTFOUND88")).thenReturn(Optional.empty());

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-NOTFOUND88"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attributeExists("notFound"))
                .andExpect(content().string(containsString("No complaint found with reference ID")))
                .andExpect(content().string(containsString("CP-2026-NOTFOUND88")));

        verify(complaintService, times(1)).getByReferenceId("CP-2026-NOTFOUND88");
    }

    @Test
    @DisplayName("GET /complaints/track displays high-level tracking details and 4-stage stepper when found")
    void shouldDisplayComplaintTrackingDetailsWhenFound() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-FOUND001",
                "Hostel Wi-Fi Down",
                "Detailed internal description that should not be exposed",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setId(10L);

        when(complaintService.getByReferenceId("CP-2026-FOUND001")).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-FOUND001"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attributeExists("complaint"))
                .andExpect(content().string(containsString("CP-2026-FOUND001")))
                .andExpect(content().string(containsString("Hostel Wi-Fi Down")))
                .andExpect(content().string(containsString("Infrastructure &amp; Labs")))
                .andExpect(content().string(containsString("Campus Facilities &amp; Works")))
                .andExpect(content().string(containsString("Resolution Lifecycle Progress")))
                .andExpect(content().string(containsString("SUBMITTED")))
                .andExpect(content().string(not(containsString("Detailed internal description that should not be exposed"))));

        verify(complaintService, times(1)).getByReferenceId("CP-2026-FOUND001");
    }

    @Test
    @DisplayName("GET /complaints/track displays chronological status history timeline when records exist")
    void shouldDisplayChronologicalStatusTimelineWhenHistoryExists() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-HIST0001",
                "Library AC Down",
                "Internal description not shown",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        ComplaintStatusHistory h1 = new ComplaintStatusHistory(
                complaint, null, ComplaintStatus.SUBMITTED, "STUDENT", "Initial submission"
        );
        h1.setCreatedAt(LocalDateTime.of(2026, 10, 10, 10, 0));

        ComplaintStatusHistory h2 = new ComplaintStatusHistory(
                complaint, ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW, "admin_triage", "Under review note"
        );
        h2.setCreatedAt(LocalDateTime.of(2026, 10, 11, 14, 0));

        ComplaintStatusHistory h3 = new ComplaintStatusHistory(
                complaint, ComplaintStatus.UNDER_REVIEW, ComplaintStatus.IN_PROGRESS, "admin_hvac", "In progress note"
        );
        h3.setCreatedAt(LocalDateTime.of(2026, 10, 12, 9, 30));

        when(complaintService.getByReferenceId("CP-2026-HIST0001")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-HIST0001")).thenReturn(List.of(h1, h2, h3));

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-HIST0001"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attributeExists("complaint", "statusHistory"))
                .andExpect(content().string(containsString("Recorded Status History Timeline")))
                .andExpect(content().string(containsString("10 Oct 2026, 10:00 AM")))
                .andExpect(content().string(containsString("11 Oct 2026, 02:00 PM")))
                .andExpect(content().string(containsString("12 Oct 2026, 09:30 AM")))
                .andExpect(content().string(containsString("Grievance registered and queued for initial administrative assessment")))
                .andExpect(content().string(containsString("Grievance acknowledged and currently under review by campus authorities")))
                .andExpect(content().string(containsString("Departmental action and resolution measures actively underway")));

        verify(complaintService, times(1)).getByReferenceId("CP-2026-HIST0001");
        verify(complaintService, times(1)).getStatusHistory("CP-2026-HIST0001");
    }

    @Test
    @DisplayName("GET /complaints/track handles empty status history gracefully")
    void shouldDisplayEmptyTimelineGracefullyWhenNoHistoryExists() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-NOHIST01",
                "Dorm Key Lost",
                "Internal description",
                "Hostel & Residence",
                "Hostel Administration"
        );

        when(complaintService.getByReferenceId("CP-2026-NOHIST01")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-NOHIST01")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-NOHIST01"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(content().string(containsString("No historical status changes recorded for this grievance.")));
    }

    @Test
    @DisplayName("GET /complaints/track displays official resolution details when complaint is resolved")
    void shouldDisplayOfficialResolutionNoteWhenComplaintIsResolved() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-RESOLVED1",
                "Laboratory PC Power Supply",
                "Internal description",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.of(2026, 10, 12, 11, 45));
        complaint.setResolutionNote("Replaced 500W PSU unit with tested replacement in workstation Lab 3-PC04.");

        when(complaintService.getByReferenceId("CP-2026-RESOLVED1")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-RESOLVED1")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-RESOLVED1"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(content().string(containsString("Official Resolution Details")))
                .andExpect(content().string(containsString("Replaced 500W PSU unit with tested replacement in workstation Lab 3-PC04.")))
                .andExpect(content().string(containsString("Resolved on <strong>12 October 2026, 11:45 AM</strong>")));
    }

    @Test
    @DisplayName("GET /complaints/track does not display official resolution note when complaint is not resolved")
    void shouldNotDisplayResolutionNoteWhenComplaintIsNotResolved() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-ACTIVE01",
                "Wi-Fi Outage in Block C",
                "Internal description",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintService.getByReferenceId("CP-2026-ACTIVE01")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-ACTIVE01")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-ACTIVE01"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(content().string(not(containsString("Official Resolution Details"))));
    }

    @Test
    @DisplayName("GET /complaints/track does not expose internal administrative notes, usernames, or student email")
    void shouldNotExposeInternalNotesOrAdminUsernamesOrStudentContactDetails() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-PRIVACY1",
                "Heating Failure in Room 204",
                "Confidential student text that contains sensitive personal info",
                "Hostel & Residence",
                "Hostel Administration",
                "student.private@campuspulse.internal"
        );
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW);

        ComplaintStatusHistory internalEntry = new ComplaintStatusHistory(
                complaint,
                ComplaintStatus.SUBMITTED,
                ComplaintStatus.UNDER_REVIEW,
                "superadmin_secret_user",
                "CONFIDENTIAL_INTERNAL_NOTE: Dean requested priority review due to VIP campus visit."
        );
        internalEntry.setCreatedAt(LocalDateTime.of(2026, 10, 11, 15, 0));

        when(complaintService.getByReferenceId("CP-2026-PRIVACY1")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-PRIVACY1")).thenReturn(List.of(internalEntry));

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-PRIVACY1"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(content().string(not(containsString("student.private@campuspulse.internal"))))
                .andExpect(content().string(not(containsString("superadmin_secret_user"))))
                .andExpect(content().string(not(containsString("CONFIDENTIAL_INTERNAL_NOTE"))))
                .andExpect(content().string(not(containsString("Confidential student text that contains sensitive personal info"))));
    }

    @Test
    @DisplayName("POST /complaints with valid student email submits successfully")
    void shouldAcceptValidSubmissionWithStudentEmail() throws Exception {
        Complaint saved = new Complaint(
                "CP-2026-EMAIL001",
                "Water Leakage in Hostel B",
                "There is persistent water leakage on the second floor washroom.",
                "Hostel & Residence",
                "Hostel Administration",
                "student@college.edu"
        );
        saved.setId(102L);

        when(complaintService.createComplaint(any(ComplaintForm.class))).thenReturn(saved);

        mockMvc.perform(post("/complaints")
                        .param("title", "Water Leakage in Hostel B")
                        .param("description", "There is persistent water leakage on the second floor washroom.")
                        .param("category", "Hostel & Residence")
                        .param("department", "Hostel Administration")
                        .param("studentEmail", "student@college.edu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/success/CP-2026-EMAIL001"));

        verify(complaintService, times(1)).createComplaint(any(ComplaintForm.class));
    }

    @Test
    @DisplayName("POST /complaints with malformed student email rejects submission with validation error")
    void shouldFailValidationWhenStudentEmailIsMalformed() throws Exception {
        mockMvc.perform(post("/complaints")
                        .param("title", "Water Leakage in Hostel B")
                        .param("description", "There is persistent water leakage on the second floor washroom.")
                        .param("category", "Hostel & Residence")
                        .param("department", "Hostel Administration")
                        .param("studentEmail", "not-a-valid-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/form"))
                .andExpect(model().attributeHasFieldErrors("complaintForm", "studentEmail"))
                .andExpect(content().string(containsString("Please enter a valid email address")));

        verify(complaintService, never()).createComplaint(any());
    }

    @Test
    @DisplayName("POST /complaints/suggest-category returns suggested category and confidence without persisting")
    void shouldSuccessfullyPredictCategoryWhenInputIsValid() throws Exception {
        String title = "Broken laboratory computers";
        String description = "Multiple desktop machines in lab 3 fail to boot.";
        String expectedCombined = title + ". " + description;

        when(aiClassifierService.classify(eq(expectedCombined)))
                .thenReturn(AiClassificationResult.success("Infrastructure & Labs", 0.885, "Heuristic estimate", "1.0.0"));

        mockMvc.perform(post("/complaints/suggest-category")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.category", is("Infrastructure & Labs")))
                .andExpect(jsonPath("$.suggestedCategory", is("Infrastructure & Labs")))
                .andExpect(jsonPath("$.confidence", is(0.885)))
                .andExpect(jsonPath("$.warning", is("Heuristic estimate")))
                .andExpect(jsonPath("$.modelVersion", is("1.0.0")));

        verify(complaintService, never()).createComplaint(any());
    }

    @Test
    @DisplayName("POST /complaints/suggest-category rejects blank title with 400 Bad Request")
    void shouldFailValidationWhenTitleIsBlankForSuggestion() throws Exception {
        mockMvc.perform(post("/complaints/suggest-category")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"description\":\"Some long description of the complaint.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Title is required")));

        verify(aiClassifierService, never()).classify(any());
    }

    @Test
    @DisplayName("POST /complaints/suggest-category rejects text shorter than 5 characters with 400 Bad Request")
    void shouldFailValidationWhenCombinedTextIsTooShortForSuggestion() throws Exception {
        mockMvc.perform(post("/complaints/suggest-category")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lab\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("must contain at least 5 characters")));

        verify(aiClassifierService, never()).classify(any());
    }

    @Test
    @DisplayName("POST /complaints/suggest-category returns 422 when AI suggests invalid category not in whitelist")
    void shouldReturnUnprocessableEntityWhenAiCategoryIsDisallowed() throws Exception {
        String title = "Random grievance title";
        when(aiClassifierService.classify(any()))
                .thenReturn(AiClassificationResult.invalidCategory("Unknown Category Not Allowed"));

        mockMvc.perform(post("/complaints/suggest-category")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"Detail of issue\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("could not be verified against allowed campus categories")));
    }

    @Test
    @DisplayName("POST /complaints/suggest-category returns 503 when AI microservice is unavailable")
    void shouldReturnServiceUnavailableWhenAiMicroserviceFails() throws Exception {
        String title = "Water cooler broken";
        when(aiClassifierService.classify(any()))
                .thenReturn(AiClassificationResult.unavailable("Connection timeout to Python microservice"));

        mockMvc.perform(post("/complaints/suggest-category")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"Cooler is leaking water on 2nd floor\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("temporarily unavailable")));
    }

    @Test
    @DisplayName("GET /complaints/success/{referenceId} masks student email and never discloses cleartext email")
    void shouldMaskStudentEmailOnSuccessPage() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-PRIV001",
                "Private student complaint",
                "Detailed description of hostel room issue.",
                "Hostel & Residence",
                "Hostel Administration",
                "john.student@university.edu"
        );
        complaint.setId(201L);

        when(complaintService.getByReferenceId("CP-2026-PRIV001")).thenReturn(Optional.of(complaint));

        mockMvc.perform(get("/complaints/success/CP-2026-PRIV001"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/success"))
                .andExpect(content().string(containsString("j***@university.edu")))
                .andExpect(content().string(not(containsString("john.student@university.edu"))));
    }

    @Test
    @DisplayName("GET /complaints/track does not disclose student email or internal status history notes")
    void shouldNotDiscloseStudentEmailOrInternalNotesOnTrackPage() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-PRIV002",
                "Track privacy verification",
                "Issue description.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "sensitive.student@campus.edu"
        );
        complaint.setId(202L);

        ComplaintStatusHistory historyEntry = new ComplaintStatusHistory(
                complaint,
                ComplaintStatus.SUBMITTED,
                ComplaintStatus.UNDER_REVIEW,
                "admin",
                "INTERNAL SECRET NOTE: Admin suspects malicious user behavior."
        );

        when(complaintService.getByReferenceId("CP-2026-PRIV002")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-PRIV002")).thenReturn(List.of(historyEntry));

        mockMvc.perform(get("/complaints/track").param("referenceId", "CP-2026-PRIV002"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(content().string(not(containsString("sensitive.student@campus.edu"))))
                .andExpect(content().string(not(containsString("INTERNAL SECRET NOTE"))));
    }

    @Test
    @DisplayName("GET /complaints does not disclose student emails")
    void shouldNotDiscloseStudentEmailsOnPublicRegistry() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-PRIV003",
                "Registry Privacy Check",
                "Registry issue description",
                "Academics & Faculty",
                "Academic Affairs Office",
                "hidden.student@campus.edu"
        );
        complaint.setId(203L);

        when(complaintService.getAllComplaints()).thenReturn(List.of(complaint));

        mockMvc.perform(get("/complaints"))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/list"))
                .andExpect(content().string(containsString("CP-2026-PRIV003")))
                .andExpect(content().string(not(containsString("hidden.student@campus.edu"))));
    }
}
