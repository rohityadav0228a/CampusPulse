package com.campuspulse.controller;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.service.ComplaintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminComplaintController.class)
class AdminComplaintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private com.campuspulse.service.SlaEscalationService slaEscalationService;

    private Map<String, Long> createSampleCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", 4L);
        counts.put("SUBMITTED", 1L);
        counts.put("UNDER_REVIEW", 1L);
        counts.put("IN_PROGRESS", 1L);
        counts.put("RESOLVED", 1L);
        return counts;
    }

    @Test
    @DisplayName("GET /admin/complaints renders dashboard view with complaints list and status metrics")
    void shouldDisplayAdminDashboardWithAllComplaints() throws Exception {
        Complaint c1 = new Complaint(
                "CP-2026-ADM00001",
                "Projector Malfunction in Hall 4",
                "HDMI cable disconnected and bulb replacement needed.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        c1.setId(1L);

        Page<Complaint> pageResult = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(complaintService.searchComplaints("", null, null, "", 0, 10)).thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attributeExists("complaints", "complaintsPage", "statusCounts", "statuses", "categories"))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("totalElements", 1L))
                .andExpect(content().string(containsString("Admin Complaint Management")))
                .andExpect(content().string(containsString("Authenticated Session")))
                .andExpect(content().string(containsString("CP-2026-ADM00001")))
                .andExpect(content().string(containsString("Projector Malfunction in Hall 4")));

        verify(complaintService, times(1)).searchComplaints("", null, null, "", 0, 10);
        verify(complaintService, times(1)).getStatusCounts();
    }

    @Test
    @DisplayName("GET /admin/complaints with filter params passes status and category to service")
    void shouldFilterComplaintsByStatusAndCategory() throws Exception {
        Complaint c1 = new Complaint(
                "CP-2026-ADM00002",
                "Course Registration Error",
                "System throws timeout during elective course registration.",
                "Academic Affairs",
                "Academic Affairs Division"
        );
        c1.setId(2L);

        Page<Complaint> pageResult = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(complaintService.searchComplaints("", ComplaintStatus.SUBMITTED, "Academic Affairs", "", 0, 10))
                .thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Academic Affairs"));

        mockMvc.perform(get("/admin/complaints")
                        .param("status", "SUBMITTED")
                        .param("category", "Academic Affairs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attribute("selectedStatus", "SUBMITTED"))
                .andExpect(model().attribute("selectedCategory", "Academic Affairs"))
                .andExpect(content().string(containsString("CP-2026-ADM00002")));

        verify(complaintService, times(1))
                .searchComplaints("", ComplaintStatus.SUBMITTED, "Academic Affairs", "", 0, 10);
    }

    @Test
    @DisplayName("GET /admin/complaints/{refId} displays complaint detail page with full description and chronological audit history")
    void shouldDisplayComplaintDetailPageWithFullDescription() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-ADM00003",
                "Water Cooler Contaminated",
                "Water cooler on 3rd floor Block C dispensing brownish water with odor.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        complaint.setId(3L);

        ComplaintStatusHistory h1 = new ComplaintStatusHistory(
                complaint,
                null,
                ComplaintStatus.SUBMITTED,
                "STUDENT",
                "Initial grievance submission by student."
        );
        h1.setCreatedAt(LocalDateTime.of(2026, 10, 11, 10, 0));

        ComplaintStatusHistory h2 = new ComplaintStatusHistory(
                complaint,
                ComplaintStatus.SUBMITTED,
                ComplaintStatus.UNDER_REVIEW,
                "warden_admin",
                "Water sample dispatched to testing lab."
        );
        h2.setCreatedAt(LocalDateTime.of(2026, 10, 11, 11, 30));

        when(complaintService.getByReferenceId("CP-2026-ADM00003")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-ADM00003")).thenReturn(List.of(h1, h2));

        mockMvc.perform(get("/admin/complaints/CP-2026-ADM00003"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/detail"))
                .andExpect(model().attributeExists("complaint", "statuses", "statusHistory"))
                .andExpect(content().string(containsString("CP-2026-ADM00003")))
                .andExpect(content().string(containsString("Water Cooler Contaminated")))
                .andExpect(content().string(containsString("dispensing brownish water with odor.")))
                .andExpect(content().string(containsString("Status Audit History")))
                .andExpect(content().string(containsString("STUDENT")))
                .andExpect(content().string(containsString("warden_admin")))
                .andExpect(content().string(containsString("Water sample dispatched to testing lab.")))
                .andExpect(content().string(containsString("Update Status")));

        verify(complaintService, times(1)).getByReferenceId("CP-2026-ADM00003");
        verify(complaintService, times(1)).getStatusHistory("CP-2026-ADM00003");
    }

    @Test
    @DisplayName("GET /admin/complaints/{refId} redirects to dashboard when complaint not found")
    void shouldRedirectToDashboardWhenComplaintDetailNotFound() throws Exception {
        when(complaintService.getByReferenceId("NON-EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/complaints/NON-EXISTENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(complaintService, times(1)).getByReferenceId("NON-EXISTENT");
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/status successfully updates status and redirects")
    void shouldSuccessfullyUpdateComplaintStatusAndRedirect() throws Exception {
        Complaint updated = new Complaint(
                "CP-2026-ADM00003",
                "Water Cooler Contaminated",
                "Full description",
                "Hostel & Residence",
                "Hostel Administration"
        );
        updated.setStatus(ComplaintStatus.IN_PROGRESS);

        when(complaintService.updateComplaintStatus(eq("CP-2026-ADM00003"), eq(ComplaintStatus.IN_PROGRESS), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00003/status")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00003"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService, times(1))
                .updateComplaintStatus("CP-2026-ADM00003", ComplaintStatus.IN_PROGRESS, "ADMIN", null);
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/status records authenticated actor and optional note")
    void shouldUpdateStatusWithAuthenticatedActorAndNote() throws Exception {
        Complaint updated = new Complaint(
                "CP-2026-ADM00003",
                "Water Cooler Contaminated",
                "Full description",
                "Hostel & Residence",
                "Hostel Administration"
        );
        updated.setStatus(ComplaintStatus.RESOLVED);

        when(complaintService.updateComplaintStatus("CP-2026-ADM00003", ComplaintStatus.RESOLVED, "chief_engineer", "Filter replaced."))
                .thenReturn(updated);

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00003/status")
                        .with(user("chief_engineer").roles("ADMIN"))
                        .param("status", "RESOLVED")
                        .param("note", "Filter replaced."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00003"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService, times(1))
                .updateComplaintStatus("CP-2026-ADM00003", ComplaintStatus.RESOLVED, "chief_engineer", "Filter replaced.");
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/status rejects unchanged status and redirects with error flash")
    void shouldRejectUnchangedStatusUpdateAndRedirectWithError() throws Exception {
        when(complaintService.updateComplaintStatus(eq("CP-2026-ADM00003"), eq(ComplaintStatus.SUBMITTED), any(), any()))
                .thenThrow(new IllegalArgumentException("Complaint is already in Submitted status. No status change was recorded."));

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00003/status")
                        .param("status", "SUBMITTED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00003"))
                .andExpect(flash().attribute("errorMessage", "Complaint is already in Submitted status. No status change was recorded."));
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/status rejects invalid status and redirects with error")
    void shouldRejectInvalidStatusUpdateAndRedirectWithError() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00003/status")
                        .param("status", "INVALID_UNKNOWN_STATUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00003"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(complaintService, never()).updateComplaintStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/status rejects blank status and redirects with error")
    void shouldRejectBlankStatusUpdateAndRedirectWithError() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00003/status")
                        .param("status", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00003"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(complaintService, never()).updateComplaintStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/complaints with slaStatus parameter passes SLA filter to service and renders SLA counts")
    void shouldFilterComplaintsBySlaStatus() throws Exception {
        Complaint c1 = new Complaint("CP-SLA-001", "Lab AC failure", "No cool air", "Infrastructure & Labs", "Campus Facilities & Works");
        c1.setStatus(ComplaintStatus.SUBMITTED);
        c1.setResponseDeadline(LocalDateTime.now().minusHours(2));

        Page<Complaint> pageResult = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(complaintService.searchComplaints("", null, null, "OVERDUE", 0, 10)).thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getSlaCounts()).thenReturn(Map.of("OVERDUE", 1L, "RESPONSE_OVERDUE", 1L, "ON_TRACK", 0L));
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints").param("slaStatus", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attribute("selectedSlaStatus", "OVERDUE"))
                .andExpect(model().attributeExists("slaCounts"))
                .andExpect(content().string(containsString("CP-SLA-001")))
                .andExpect(content().string(containsString("Resp Overdue")));

        verify(complaintService, times(1)).searchComplaints("", null, null, "OVERDUE", 0, 10);
        verify(complaintService, times(1)).getSlaCounts();
    }

    @Test
    @DisplayName("GET /admin/complaints with search query and pagination passes parameters to service")
    void shouldSearchComplaintsWithKeywordAndPaginationParams() throws Exception {
        Complaint c1 = new Complaint("CP-2026-SEARCH1", "Wi-Fi down", "Library wifi broken", "Library & Study Spaces", "Central Library Committee");
        c1.setId(10L);
        Page<Complaint> pageResult = new PageImpl<>(List.of(c1), PageRequest.of(1, 20), 21);

        when(complaintService.searchComplaints("wifi", null, null, "", 1, 20)).thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Library & Study Spaces"));

        mockMvc.perform(get("/admin/complaints")
                        .param("q", "wifi")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attribute("searchQuery", "wifi"))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("pageSize", 20))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("totalElements", 21L))
                .andExpect(content().string(containsString("CP-2026-SEARCH1")));

        verify(complaintService, times(1)).searchComplaints("wifi", null, null, "", 1, 20);
    }

    @Test
    @DisplayName("GET /admin/complaints with negative page and excessive size clamps parameters safely")
    void shouldHandleInvalidPageAndSizeSafely() throws Exception {
        Page<Complaint> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);
        when(complaintService.searchComplaints("", null, null, "", 0, 100)).thenReturn(emptyPage);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints")
                        .param("page", "-5")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("pageSize", 100));

        verify(complaintService, times(1)).searchComplaints("", null, null, "", 0, 100);
    }

    @Test
    @DisplayName("GET /admin/complaints preserves search keyword and filter criteria across pagination links")
    void shouldPreserveSearchAndFilterParametersInPaginationLinks() throws Exception {
        List<Complaint> items = List.of(
                new Complaint("CP-2026-PAG01", "Lab issue 1", "desc 1", "Infrastructure & Labs", "Campus Facilities & Works"),
                new Complaint("CP-2026-PAG02", "Lab issue 2", "desc 2", "Infrastructure & Labs", "Campus Facilities & Works")
        );
        Page<Complaint> pageResult = new PageImpl<>(items, PageRequest.of(1, 10), 35);

        when(complaintService.searchComplaints("lab", ComplaintStatus.SUBMITTED, "Infrastructure & Labs", "OVERDUE", 1, 10))
                .thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints")
                        .param("q", "lab")
                        .param("status", "SUBMITTED")
                        .param("category", "Infrastructure & Labs")
                        .param("slaStatus", "OVERDUE")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(content().string(containsString("q=lab")))
                .andExpect(content().string(containsString("status=SUBMITTED")))
                .andExpect(content().string(containsString("category=Infrastructure%20%26%20Labs")))
                .andExpect(content().string(containsString("slaStatus=OVERDUE")));
    }

    @Test
    @DisplayName("GET /admin/complaints displays empty state when search produces no results")
    void shouldRenderEmptyStateWhenNoMatchingComplaintsFound() throws Exception {
        Page<Complaint> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        when(complaintService.searchComplaints("nonexistent", null, null, "", 0, 10)).thenReturn(emptyPage);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(content().string(containsString("No Matching Complaints Found")))
                .andExpect(content().string(containsString("Clear All Filters &amp; Search")));
    }

    @Test
    @DisplayName("GET /admin/complaints/{refId} renders SLA targets, deadlines, and escalation audit trail")
    void shouldDisplayComplaintDetailPageWithSlaAndEscalations() throws Exception {
        Complaint complaint = new Complaint(
                "CP-SLA-DETAIL",
                "Heater Malfunction",
                "Room 404 radiator cold",
                "Hostel & Residence",
                "Hostel Administration"
        );
        complaint.setCreatedAt(LocalDateTime.of(2026, 10, 10, 10, 0));
        complaint.setResponseDeadline(LocalDateTime.of(2026, 10, 11, 10, 0));
        complaint.setResolutionDeadline(LocalDateTime.of(2026, 10, 13, 10, 0));
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setEscalationStage(com.campuspulse.model.SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE);

        com.campuspulse.model.ComplaintEscalationAudit audit = new com.campuspulse.model.ComplaintEscalationAudit(
                complaint,
                "CP-SLA-DETAIL",
                com.campuspulse.model.SlaEscalationStage.STAGE_1_RESPONSE_OVERDUE,
                com.campuspulse.model.SlaEscalationStage.NONE,
                "Response SLA target (24h) breached by 5h.",
                "admin-escalations@campuspulse.internal"
        );
        audit.setTriggeredAt(LocalDateTime.of(2026, 10, 11, 15, 0));

        when(complaintService.getByReferenceId("CP-SLA-DETAIL")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-SLA-DETAIL")).thenReturn(Collections.emptyList());
        when(slaEscalationService.getEscalationAudits("CP-SLA-DETAIL")).thenReturn(List.of(audit));

        mockMvc.perform(get("/admin/complaints/CP-SLA-DETAIL"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/detail"))
                .andExpect(model().attributeExists("complaint", "escalationAudits"))
                .andExpect(content().string(containsString("SLA Targets &amp; Service Deadlines")))
                .andExpect(content().string(containsString("Automatic SLA Escalation Trail")))
                .andExpect(content().string(containsString("Response SLA target (24h) breached by 5h.")))
                .andExpect(content().string(containsString("admin-escalations@campuspulse.internal")));

        verify(slaEscalationService, times(1)).getEscalationAudits("CP-SLA-DETAIL");
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/resolution-note updates resolution note and redirects with success")
    void shouldUpdateResolutionNoteWhenValid() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00004/resolution-note")
                        .param("resolutionNote", "Power unit replaced with tested spare."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00004"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService, times(1))
                .updateResolutionNote(eq("CP-2026-ADM00004"), eq("Power unit replaced with tested spare."), any());
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/resolution-note rejects blank note and redirects with error")
    void shouldRejectBlankResolutionNoteCorrection() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00004/resolution-note")
                        .param("resolutionNote", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00004"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(complaintService, never()).updateResolutionNote(any(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/resolution-note rejects overly long note and redirects with error")
    void shouldRejectOverlyLongResolutionNoteCorrection() throws Exception {
        String tooLong = "X".repeat(1001);
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00004/resolution-note")
                        .param("resolutionNote", tooLong))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00004"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(complaintService, never()).updateResolutionNote(any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/complaints/{refId} displays resolution note and correction form when resolved")
    void shouldDisplayResolutionNoteAndCorrectionFormOnDetailPageWhenResolved() throws Exception {
        Complaint complaint = new Complaint(
                "CP-2026-DETAIL-RES",
                "Laboratory Workstation Fix",
                "Workstation motherboard fault",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolutionNote("Motherboard replaced and workstation diagnostics completed.");
        complaint.setResolvedAt(LocalDateTime.of(2026, 10, 12, 10, 0));

        when(complaintService.getByReferenceId("CP-2026-DETAIL-RES")).thenReturn(Optional.of(complaint));
        when(complaintService.getStatusHistory("CP-2026-DETAIL-RES")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/complaints/CP-2026-DETAIL-RES"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/detail"))
                .andExpect(content().string(containsString("Official Resolution Note")))
                .andExpect(content().string(containsString("Motherboard replaced and workstation diagnostics completed.")))
                .andExpect(content().string(containsString("Correct Resolution Note")));
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/status rejects note exceeding 1000 characters")
    void shouldRejectOverlyLongNoteInStatusUpdate() throws Exception {
        String longNote = "Z".repeat(1001);
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00004/status")
                        .param("status", "RESOLVED")
                        .param("note", longNote))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00004"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(complaintService, never()).updateComplaintStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/complaints filters by department and priority")
    void shouldFilterComplaintsByDepartmentAndPriority() throws Exception {
        Complaint c = new Complaint("CP-2026-FILT01", "Lab AC issue", "desc", "Infrastructure & Labs", "Campus Facilities & Works");
        c.setPriority(ComplaintPriority.HIGH);

        Page<Complaint> pageResult = new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1);
        when(complaintService.searchComplaints("", null, null, "Campus Facilities & Works", ComplaintPriority.HIGH, "", 0, 10))
                .thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Campus Facilities & Works"));
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints")
                        .param("department", "Campus Facilities & Works")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attribute("selectedDepartment", "Campus Facilities & Works"))
                .andExpect(model().attribute("selectedPriority", "HIGH"))
                .andExpect(model().attributeExists("departments", "priorities"))
                .andExpect(content().string(containsString("CP-2026-FILT01")));

        verify(complaintService, times(1))
                .searchComplaints("", null, null, "Campus Facilities & Works", ComplaintPriority.HIGH, "", 0, 10);
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/department updates department and redirects")
    void shouldSuccessfullyUpdateDepartment() throws Exception {
        Complaint c = new Complaint("CP-2026-ADM00005", "Title", "Desc", "Infrastructure & Labs", "Hostel Administration");
        when(complaintService.updateDepartment(eq("CP-2026-ADM00005"), eq("Hostel Administration"), any()))
                .thenReturn(c);

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00005/department")
                        .param("department", "Hostel Administration"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00005"))
                .andExpect(flash().attribute("successMessage", "Department successfully reassigned to Hostel Administration."));

        verify(complaintService, times(1))
                .updateDepartment(eq("CP-2026-ADM00005"), eq("Hostel Administration"), any());
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/department rejects blank or service-rejected values")
    void shouldRejectInvalidDepartmentUpdate() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00005/department")
                        .param("department", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00005"))
                .andExpect(flash().attribute("errorMessage", "Department must not be blank."));

        verify(complaintService, never()).updateDepartment(any(), any(), any());

        when(complaintService.updateDepartment(eq("CP-2026-ADM00005"), eq("Invalid Dept"), any()))
                .thenThrow(new IllegalArgumentException("Invalid department: Invalid Dept"));

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00005/department")
                        .param("department", "Invalid Dept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00005"))
                .andExpect(flash().attribute("errorMessage", "Invalid department: Invalid Dept"));
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/category updates category and redirects")
    void shouldSuccessfullyUpdateCategory() throws Exception {
        Complaint c = new Complaint("CP-2026-ADM00006", "Title", "Desc", "Library & Study Spaces", "Campus Facilities & Works");
        when(complaintService.updateCategory(eq("CP-2026-ADM00006"), eq("Library & Study Spaces"), any()))
                .thenReturn(c);

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00006/category")
                        .param("category", "Library & Study Spaces"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00006"))
                .andExpect(flash().attribute("successMessage", "Category successfully corrected to Library & Study Spaces."));

        verify(complaintService, times(1))
                .updateCategory(eq("CP-2026-ADM00006"), eq("Library & Study Spaces"), any());
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/category rejects blank or service-rejected values")
    void shouldRejectInvalidCategoryUpdate() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00006/category")
                        .param("category", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00006"))
                .andExpect(flash().attribute("errorMessage", "Category must not be blank."));

        verify(complaintService, never()).updateCategory(any(), any(), any());

        when(complaintService.updateCategory(eq("CP-2026-ADM00006"), eq("Invalid Cat"), any()))
                .thenThrow(new IllegalArgumentException("Invalid category: Invalid Cat"));

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00006/category")
                        .param("category", "Invalid Cat"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00006"))
                .andExpect(flash().attribute("errorMessage", "Invalid category: Invalid Cat"));
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/priority updates priority and redirects")
    void shouldSuccessfullyUpdatePriority() throws Exception {
        Complaint c = new Complaint("CP-2026-ADM00007", "Title", "Desc", "Infrastructure & Labs", "Campus Facilities & Works");
        c.setPriority(ComplaintPriority.CRITICAL);
        when(complaintService.updatePriority(eq("CP-2026-ADM00007"), eq(ComplaintPriority.CRITICAL), any()))
                .thenReturn(c);

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00007/priority")
                        .param("priority", "CRITICAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00007"))
                .andExpect(flash().attribute("successMessage", "Priority successfully updated to Critical."));

        verify(complaintService, times(1))
                .updatePriority(eq("CP-2026-ADM00007"), eq(ComplaintPriority.CRITICAL), any());
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/priority rejects blank, invalid enum, or unchanged values")
    void shouldRejectInvalidPriorityUpdate() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00007/priority")
                        .param("priority", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00007"))
                .andExpect(flash().attribute("errorMessage", "Priority must not be blank."));

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00007/priority")
                        .param("priority", "INVALID_PRIORITY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00007"))
                .andExpect(flash().attribute("errorMessage", "Invalid priority value provided: INVALID_PRIORITY"));

        when(complaintService.updatePriority(eq("CP-2026-ADM00007"), eq(ComplaintPriority.MEDIUM), any()))
                .thenThrow(new IllegalArgumentException("Complaint priority is already set to Medium"));

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00007/priority")
                        .param("priority", "MEDIUM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00007"))
                .andExpect(flash().attribute("errorMessage", "Complaint priority is already set to Medium"));
    }

    @Test
    @DisplayName("POST /admin/complaints/{refId}/assignment updates department and category atomically")
    void shouldSuccessfullyUpdateAssignment() throws Exception {
        Complaint c = new Complaint("CP-2026-ADM00008", "Title", "Desc", "Hostel & Residence", "Hostel Administration");
        when(complaintService.updateAssignment(eq("CP-2026-ADM00008"), eq("Hostel Administration"), eq("Hostel & Residence"), any()))
                .thenReturn(c);

        mockMvc.perform(post("/admin/complaints/CP-2026-ADM00008/assignment")
                        .param("department", "Hostel Administration")
                        .param("category", "Hostel & Residence"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-ADM00008"))
                .andExpect(flash().attribute("successMessage", "Complaint assignment and category successfully updated."));

        verify(complaintService, times(1))
                .updateAssignment(eq("CP-2026-ADM00008"), eq("Hostel Administration"), eq("Hostel & Residence"), any());
    }

    @Test
    @DisplayName("GET /admin/complaints preserves department and priority across pagination links")
    void shouldPreserveDepartmentAndPriorityInPaginationLinks() throws Exception {
        List<Complaint> items = List.of(
                new Complaint("CP-2026-PAG03", "Dept issue 1", "desc 1", "Infrastructure & Labs", "Campus Facilities & Works")
        );
        Page<Complaint> pageResult = new PageImpl<>(items, PageRequest.of(1, 10), 25);

        when(complaintService.searchComplaints("water", ComplaintStatus.SUBMITTED, "Hostel & Residence", "Hostel Administration", ComplaintPriority.CRITICAL, "OVERDUE", 1, 10))
                .thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Hostel Administration"));
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Hostel & Residence"));

        mockMvc.perform(get("/admin/complaints")
                        .param("q", "water")
                        .param("status", "SUBMITTED")
                        .param("category", "Hostel & Residence")
                        .param("department", "Hostel Administration")
                        .param("priority", "CRITICAL")
                        .param("slaStatus", "OVERDUE")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(content().string(containsString("department=Hostel%20Administration")))
                .andExpect(content().string(containsString("priority=CRITICAL")));
    }
}
