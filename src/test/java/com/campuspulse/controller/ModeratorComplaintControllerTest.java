package com.campuspulse.controller;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.ComplaintStatusHistory;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.SlaEscalationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ModeratorComplaintController.class)
class ModeratorComplaintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private SlaEscalationService slaEscalationService;

    private Map<String, Long> createSampleCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", 5L);
        counts.put("SUBMITTED", 2L);
        counts.put("UNDER_REVIEW", 1L);
        counts.put("IN_PROGRESS", 1L);
        counts.put("RESOLVED", 1L);
        return counts;
    }

    private Map<String, Long> createSampleSlaCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("OVERDUE", 1L);
        counts.put("RESPONSE_OVERDUE", 1L);
        counts.put("RESOLUTION_OVERDUE", 0L);
        counts.put("APPROACHING_DEADLINE", 1L);
        counts.put("ON_TRACK", 2L);
        counts.put("MET", 1L);
        return counts;
    }

    @Test
    @DisplayName("GET /moderator/complaints renders moderator dashboard with summary cards and complaints table")
    void shouldDisplayModeratorDashboardWithComplaints() throws Exception {
        Complaint c1 = new Complaint(
                "CP-2026-MOD00001",
                "WiFi outage in Library Hall B",
                "Students cannot connect to academic routers.",
                "Library & Study Spaces",
                "Central Library Committee"
        );
        c1.setId(1L);

        Page<Complaint> pageResult = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(complaintService.searchComplaints("", null, null, "", 0, 10)).thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getSlaCounts()).thenReturn(createSampleSlaCounts());
        when(complaintService.getAllowedCategories()).thenReturn(List.of("Library & Study Spaces"));
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Central Library Committee"));

        mockMvc.perform(get("/moderator/complaints")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("moderator/complaints/index"))
                .andExpect(model().attributeExists("complaints", "statusCounts", "slaCounts", "categories", "departments"))
                .andExpect(content().string(containsString("Moderator Portal")))
                .andExpect(content().string(containsString("Moderator Complaint Management")))
                .andExpect(content().string(containsString("CP-2026-MOD00001")));
    }

    @Test
    @DisplayName("GET /moderator/complaints applies keyword, status, category, department, and priority filters")
    void shouldFilterModeratorQueue() throws Exception {
        Complaint c1 = new Complaint(
                "CP-2026-MOD00002",
                "Hostel Water Heater Broken",
                "Block D third floor has no hot water.",
                "Hostel & Residence",
                "Hostel Administration"
        );
        c1.setPriority(ComplaintPriority.HIGH);
        c1.setStatus(ComplaintStatus.IN_PROGRESS);

        Page<Complaint> pageResult = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(complaintService.searchComplaints("water", ComplaintStatus.IN_PROGRESS, "Hostel & Residence",
                "Hostel Administration", ComplaintPriority.HIGH, "ON_TRACK", 0, 10))
                .thenReturn(pageResult);
        when(complaintService.getStatusCounts()).thenReturn(createSampleCounts());
        when(complaintService.getSlaCounts()).thenReturn(createSampleSlaCounts());

        mockMvc.perform(get("/moderator/complaints")
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("q", "water")
                        .param("status", "IN_PROGRESS")
                        .param("category", "Hostel & Residence")
                        .param("department", "Hostel Administration")
                        .param("priority", "HIGH")
                        .param("slaStatus", "ON_TRACK"))
                .andExpect(status().isOk())
                .andExpect(view().name("moderator/complaints/index"))
                .andExpect(model().attribute("selectedStatus", "IN_PROGRESS"))
                .andExpect(model().attribute("selectedPriority", "HIGH"))
                .andExpect(content().string(containsString("CP-2026-MOD00002")));
    }

    @Test
    @DisplayName("GET /moderator/complaints/{referenceId} renders complaint detail page with audit history")
    void shouldDisplayComplaintDetail() throws Exception {
        Complaint c = new Complaint(
                "CP-2026-MOD00003",
                "Lab Air Conditioner Leaking",
                "Water dripping onto computer desks in Lab 2.",
                "Infrastructure & Labs",
                "Campus Facilities & Works"
        );
        c.setId(3L);

        ComplaintStatusHistory history = new ComplaintStatusHistory(
                c, null, ComplaintStatus.SUBMITTED, "student@campus.edu", "Submitted initial grievance"
        );

        when(complaintService.getByReferenceId("CP-2026-MOD00003")).thenReturn(Optional.of(c));
        when(complaintService.getStatusHistory("CP-2026-MOD00003")).thenReturn(List.of(history));
        when(slaEscalationService.getEscalationAudits("CP-2026-MOD00003")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/moderator/complaints/CP-2026-MOD00003")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("moderator/complaints/detail"))
                .andExpect(model().attributeExists("complaint", "statusHistory", "escalationAudits"))
                .andExpect(content().string(containsString("Lab Air Conditioner Leaking")))
                .andExpect(content().string(containsString("CP-2026-MOD00003")));
    }

    @Test
    @DisplayName("GET /moderator/complaints/{referenceId} redirects to dashboard when complaint not found")
    void shouldRedirectWhenComplaintNotFound() throws Exception {
        when(complaintService.getByReferenceId("CP-UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/moderator/complaints/CP-UNKNOWN")
                        .with(user("moderator@campus.edu").roles("MODERATOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/status updates status and redirects with success message")
    void shouldUpdateStatusSuccessfully() throws Exception {
        mockMvc.perform(post("/moderator/complaints/CP-2026-MOD00003/status")
                        .with(csrf())
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("status", "IN_PROGRESS")
                        .param("note", "Assigned technician to inspect."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints/CP-2026-MOD00003"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService).updateComplaintStatus(
                eq("CP-2026-MOD00003"),
                eq(ComplaintStatus.IN_PROGRESS),
                eq("moderator@campus.edu"),
                eq("Assigned technician to inspect.")
        );
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/status rejects invalid status transition and shows error")
    void shouldRejectInvalidStatusTransition() throws Exception {
        when(complaintService.updateComplaintStatus(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Complaint is already in RESOLVED status. No status change was recorded."));

        mockMvc.perform(post("/moderator/complaints/CP-2026-MOD00003/status")
                        .with(csrf())
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("status", "RESOLVED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints/CP-2026-MOD00003"))
                .andExpect(flash().attribute("errorMessage", containsString("already in RESOLVED status")));
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/department reassigns department successfully")
    void shouldReassignDepartment() throws Exception {
        Complaint updated = new Complaint("CP-2026-MOD00003", "Title", "Desc", "Cat", "Hostel Administration");
        when(complaintService.updateDepartment("CP-2026-MOD00003", "Hostel Administration", "moderator@campus.edu"))
                .thenReturn(updated);

        mockMvc.perform(post("/moderator/complaints/CP-2026-MOD00003/department")
                        .with(csrf())
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("department", "Hostel Administration"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints/CP-2026-MOD00003"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService).updateDepartment("CP-2026-MOD00003", "Hostel Administration", "moderator@campus.edu");
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/priority updates priority level successfully")
    void shouldUpdatePriority() throws Exception {
        Complaint updated = new Complaint("CP-2026-MOD00003", "Title", "Desc", "Cat", "Dept");
        updated.setPriority(ComplaintPriority.HIGH);
        when(complaintService.updatePriority("CP-2026-MOD00003", ComplaintPriority.HIGH, "moderator@campus.edu"))
                .thenReturn(updated);

        mockMvc.perform(post("/moderator/complaints/CP-2026-MOD00003/priority")
                        .with(csrf())
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints/CP-2026-MOD00003"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService).updatePriority("CP-2026-MOD00003", ComplaintPriority.HIGH, "moderator@campus.edu");
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/resolution-note updates resolution note on resolved complaint")
    void shouldUpdateResolutionNote() throws Exception {
        Complaint updated = new Complaint("CP-2026-MOD00003", "Title", "Desc", "Cat", "Dept");
        when(complaintService.updateResolutionNote("CP-2026-MOD00003", "Replaced cooling valve.", "moderator@campus.edu"))
                .thenReturn(updated);

        mockMvc.perform(post("/moderator/complaints/CP-2026-MOD00003/resolution-note")
                        .with(csrf())
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("resolutionNote", "Replaced cooling valve."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints/CP-2026-MOD00003"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService).updateResolutionNote("CP-2026-MOD00003", "Replaced cooling valve.", "moderator@campus.edu");
    }

    @Test
    @DisplayName("POST /moderator/complaints/{id}/resolution-note rejects blank note")
    void shouldRejectBlankResolutionNote() throws Exception {
        mockMvc.perform(post("/moderator/complaints/CP-2026-MOD00003/resolution-note")
                        .with(csrf())
                        .with(user("moderator@campus.edu").roles("MODERATOR"))
                        .param("resolutionNote", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderator/complaints/CP-2026-MOD00003"))
                .andExpect(flash().attribute("errorMessage", containsString("Resolution note must not be blank")));

        verify(complaintService, never()).updateResolutionNote(any(), any(), any());
    }
}
