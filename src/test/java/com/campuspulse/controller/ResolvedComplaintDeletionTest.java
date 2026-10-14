package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.service.ComplaintService;
import com.campuspulse.service.SlaEscalationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminComplaintController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=testadmin",
        "campuspulse.admin.password=SecureAdminPass123!"
})
class ResolvedComplaintDeletionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private SlaEscalationService slaEscalationService;

    @Test
    @DisplayName("ROLE_ADMIN can safely delete a resolved complaint with CSRF token")
    void adminCanDeleteResolvedComplaint() throws Exception {
        Complaint resolvedComplaint = new Complaint("CP-2026-RESOLVED1", "Title", "Desc", "Category", "Department");
        resolvedComplaint.setStatus(ComplaintStatus.RESOLVED);
        resolvedComplaint.setDeleted(true);

        when(complaintService.deleteResolvedComplaint(eq("CP-2026-RESOLVED1"), eq("testadmin"), eq("Routine archival")))
                .thenReturn(resolvedComplaint);

        mockMvc.perform(post("/admin/complaints/CP-2026-RESOLVED1/delete")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("reason", "Routine archival"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(complaintService).deleteResolvedComplaint("CP-2026-RESOLVED1", "testadmin", "Routine archival");
    }

    @Test
    @DisplayName("Attempting to delete an unresolved complaint redirects with error message")
    void rejectingDeletionOfUnresolvedComplaint() throws Exception {
        when(complaintService.deleteResolvedComplaint(eq("CP-2026-UNRESOLVED1"), eq("testadmin"), anyString()))
                .thenThrow(new IllegalStateException("Only resolved complaints can be deleted. Current status: Submitted"));

        mockMvc.perform(post("/admin/complaints/CP-2026-UNRESOLVED1/delete")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("reason", "Premature deletion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/complaints/CP-2026-UNRESOLVED1"))
                .andExpect(flash().attribute("errorMessage", "Only resolved complaints can be deleted. Current status: Submitted"));
    }

    @Test
    @DisplayName("ROLE_MODERATOR cannot delete resolved complaint (403 Forbidden)")
    void moderatorCannotDeleteComplaint() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-RESOLVED1/delete")
                        .with(user("moderator@campuspulse.edu").roles("MODERATOR"))
                        .with(csrf())
                        .param("reason", "Moderator deletion"))
                .andExpect(status().isForbidden());

        verify(complaintService, never()).deleteResolvedComplaint(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("ROLE_STUDENT cannot delete resolved complaint (403 Forbidden)")
    void studentCannotDeleteComplaint() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-RESOLVED1/delete")
                        .with(user("student@campuspulse.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("reason", "Student deletion"))
                .andExpect(status().isForbidden());

        verify(complaintService, never()).deleteResolvedComplaint(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Unauthenticated deletion attempt redirects to login (302)")
    void unauthenticatedCannotDeleteComplaint() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-RESOLVED1/delete")
                        .with(csrf())
                        .param("reason", "Unauthenticated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(complaintService, never()).deleteResolvedComplaint(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Missing CSRF token on delete request is rejected (403 Forbidden)")
    void missingCsrfRejectedOnDeletion() throws Exception {
        mockMvc.perform(post("/admin/complaints/CP-2026-RESOLVED1/delete")
                        .with(user("testadmin").roles("ADMIN"))
                        .param("reason", "CSRF attack attempt"))
                .andExpect(status().isForbidden());

        verify(complaintService, never()).deleteResolvedComplaint(anyString(), anyString(), anyString());
    }
}
