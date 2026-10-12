package com.campuspulse.controller;

import com.campuspulse.model.Complaint;
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

        when(complaintService.getFilteredComplaints(null, null)).thenReturn(List.of(c1));
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));

        mockMvc.perform(get("/admin/complaints"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/complaints/index"))
                .andExpect(model().attributeExists("complaints", "statusCounts", "statuses", "categories"))
                .andExpect(content().string(containsString("Admin Complaint Management")))
                .andExpect(content().string(containsString("Authenticated Session")))
                .andExpect(content().string(containsString("CP-2026-ADM00001")))
                .andExpect(content().string(containsString("Projector Malfunction in Hall 4")));

        verify(complaintService, times(1)).getFilteredComplaints(null, null);
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

        when(complaintService.getFilteredComplaints(ComplaintStatus.SUBMITTED, "Academic Affairs"))
                .thenReturn(List.of(c1));
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
                .getFilteredComplaints(ComplaintStatus.SUBMITTED, "Academic Affairs");
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

        when(complaintService.getFilteredComplaints(null, null, "OVERDUE")).thenReturn(List.of(c1));
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

        verify(complaintService, times(1)).getFilteredComplaints(null, null, "OVERDUE");
        verify(complaintService, times(1)).getSlaCounts();
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
}
