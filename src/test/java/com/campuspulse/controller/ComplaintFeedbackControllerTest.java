package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.dto.ComplaintFeedbackForm;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintFeedback;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.service.AiClassifierService;
import com.campuspulse.service.ComplaintFeedbackService;
import com.campuspulse.service.ComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(controllers = ComplaintController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=admin",
        "campuspulse.admin.password=AdminPass123!"
})
class ComplaintFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private AiClassifierService aiClassifierService;

    @MockitoBean
    private ComplaintFeedbackService feedbackService;

    private static final String REFERENCE_ID = "CP-2026-B8C9D0E1";
    private static final String OWNER_EMAIL = "student@campuspulse.edu";
    private Complaint resolvedComplaint;

    @BeforeEach
    void setUp() {
        resolvedComplaint = new Complaint(
                REFERENCE_ID,
                "Broken fluorescent tube in library 3rd floor",
                "Dim lighting near section C study cubicles.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                OWNER_EMAIL
        );
        resolvedComplaint.setId(55L);
        resolvedComplaint.setPriority(ComplaintPriority.MEDIUM);
        resolvedComplaint.setStatus(ComplaintStatus.RESOLVED);
        resolvedComplaint.setResolutionNote("Replaced ballast and tube lighting.");
        resolvedComplaint.setResolvedAt(LocalDateTime.now().minusHours(1));

        when(complaintService.getAllowedCategories()).thenReturn(List.of("Infrastructure & Labs"));
        when(complaintService.getAllowedDepartments()).thenReturn(List.of("Campus Facilities & Works"));
    }

    @Test
    @DisplayName("Submit feedback: authenticated student owner successfully submits 5-star rating")
    void shouldSubmitFeedbackSuccessfullyAsStudentOwner() throws Exception {
        ComplaintFeedback savedFeedback = new ComplaintFeedback(resolvedComplaint, OWNER_EMAIL, 5, "Great lighting now!");
        savedFeedback.setId(201L);

        when(feedbackService.submitFeedback(eq(REFERENCE_ID), eq(OWNER_EMAIL), any(ComplaintFeedbackForm.class)))
                .thenReturn(savedFeedback);

        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("rating", "5")
                        .param("comment", "Great lighting now!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/track?referenceId=" + REFERENCE_ID + "&feedbackSuccess=true"))
                .andExpect(flash().attributeExists("feedbackSuccessMessage"));

        verify(feedbackService).submitFeedback(eq(REFERENCE_ID), eq(OWNER_EMAIL), any(ComplaintFeedbackForm.class));
    }

    @Test
    @DisplayName("Submit feedback: alternative endpoint /complaints/feedback?referenceId=... works identically")
    void shouldSubmitFeedbackViaAlternativeEndpoint() throws Exception {
        ComplaintFeedback savedFeedback = new ComplaintFeedback(resolvedComplaint, OWNER_EMAIL, 4, null);
        savedFeedback.setId(202L);

        when(feedbackService.submitFeedback(eq(REFERENCE_ID), eq(OWNER_EMAIL), any(ComplaintFeedbackForm.class)))
                .thenReturn(savedFeedback);

        mockMvc.perform(post("/complaints/feedback")
                        .with(user(OWNER_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("referenceId", REFERENCE_ID)
                        .param("rating", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/track?referenceId=" + REFERENCE_ID + "&feedbackSuccess=true"))
                .andExpect(flash().attributeExists("feedbackSuccessMessage"));

        verify(feedbackService).submitFeedback(eq(REFERENCE_ID), eq(OWNER_EMAIL), any(ComplaintFeedbackForm.class));
    }

    @Test
    @DisplayName("Submit feedback: rejected without CSRF token (403 Forbidden)")
    void shouldRejectFeedbackSubmissionWithoutCsrf() throws Exception {
        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT"))
                        .param("rating", "5"))
                .andExpect(status().isForbidden());

        verify(feedbackService, never()).submitFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("Submit feedback: unauthenticated user is redirected to login")
    void shouldRedirectUnauthenticatedUserToLogin() throws Exception {
        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(csrf())
                        .param("rating", "5"))
                .andExpect(status().is3xxRedirection());

        verify(feedbackService, never()).submitFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("Submit feedback: moderator or admin role is forbidden from student feedback endpoint")
    void shouldForbidModeratorOrAdminFromSubmittingFeedback() throws Exception {
        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("rating", "5"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user("moderator@campuspulse.edu").roles("MODERATOR"))
                        .with(csrf())
                        .param("rating", "5"))
                .andExpect(status().isForbidden());

        verify(feedbackService, never()).submitFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("Submit feedback: non-owner student is forbidden (IDOR protection)")
    void shouldRejectFeedbackSubmissionForNonOwnerStudent() throws Exception {
        when(feedbackService.submitFeedback(eq(REFERENCE_ID), eq("other@student.edu"), any(ComplaintFeedbackForm.class)))
                .thenThrow(new AccessDeniedException("You are not authorized to submit feedback for this complaint."));

        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user("other@student.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("rating", "5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Submit feedback: validation error on missing rating redirects with error flash message")
    void shouldRedirectWithErrorWhenRatingIsMissing() throws Exception {
        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("comment", "Forgot to select a star rating"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/track?referenceId=" + REFERENCE_ID))
                .andExpect(flash().attributeExists("feedbackErrorMessage"));

        verify(feedbackService, never()).submitFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("Submit feedback: validation error when comment exceeds 1,000 characters")
    void shouldRedirectWithErrorWhenCommentExceeds1000Chars() throws Exception {
        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("rating", "4")
                        .param("comment", "x".repeat(1001)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/track?referenceId=" + REFERENCE_ID))
                .andExpect(flash().attributeExists("feedbackErrorMessage"));

        verify(feedbackService, never()).submitFeedback(any(), any(), any());
    }

    @Test
    @DisplayName("Submit feedback: duplicate feedback rejected with error flash message")
    void shouldHandleDuplicateSubmissionGracefully() throws Exception {
        when(feedbackService.submitFeedback(eq(REFERENCE_ID), eq(OWNER_EMAIL), any(ComplaintFeedbackForm.class)))
                .thenThrow(new IllegalStateException("Feedback has already been submitted for this complaint."));

        mockMvc.perform(post("/complaints/{referenceId}/feedback", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("rating", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/complaints/track?referenceId=" + REFERENCE_ID))
                .andExpect(flash().attribute("feedbackErrorMessage", "Feedback has already been submitted for this complaint."));
    }

    @Test
    @DisplayName("Track complaint: anonymous visitor on resolved complaint does NOT see feedback form or private student comments")
    void shouldHideFeedbackFormAndCommentsFromAnonymousVisitors() throws Exception {
        when(complaintService.getByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(complaintService.getStatusHistory(REFERENCE_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints/track").param("referenceId", REFERENCE_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attribute("isOwner", false))
                .andExpect(model().attribute("canSubmitFeedback", false))
                .andExpect(model().attribute("feedbackSubmitted", false))
                .andExpect(content().string(not(containsString("resolution-feedback-form"))))
                .andExpect(content().string(not(containsString("submitted-feedback-card"))))
                .andExpect(content().string(containsString("Sign in to your student account")));
    }

    @Test
    @DisplayName("Track complaint: different student on resolved complaint does NOT see feedback form or comments")
    void shouldHideFeedbackFormAndCommentsFromDifferentStudent() throws Exception {
        when(complaintService.getByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(complaintService.getStatusHistory(REFERENCE_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints/track").param("referenceId", REFERENCE_ID)
                        .with(user("stranger@student.edu").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attribute("isOwner", false))
                .andExpect(model().attribute("canSubmitFeedback", false))
                .andExpect(model().attribute("feedbackSubmitted", false))
                .andExpect(content().string(not(containsString("resolution-feedback-form"))))
                .andExpect(content().string(not(containsString("submitted-feedback-card"))));
    }

    @Test
    @DisplayName("Track complaint: verified student owner on resolved complaint sees feedback submission form")
    void shouldDisplayFeedbackFormForStudentOwnerOnResolvedComplaint() throws Exception {
        when(complaintService.getByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(complaintService.getStatusHistory(REFERENCE_ID)).thenReturn(Collections.emptyList());
        when(feedbackService.getFeedbackForComplaint(55L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/complaints/track").param("referenceId", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attribute("isOwner", true))
                .andExpect(model().attribute("canSubmitFeedback", true))
                .andExpect(model().attribute("feedbackSubmitted", false))
                .andExpect(content().string(containsString("resolution-feedback-form")))
                .andExpect(content().string(containsString("Rate the Resolution Quality")))
                .andExpect(content().string(containsString("btn-submit-feedback")));
    }

    @Test
    @DisplayName("Track complaint: verified student owner with submitted feedback sees rating & comment, not form")
    void shouldDisplaySubmittedFeedbackForStudentOwner() throws Exception {
        ComplaintFeedback submittedFeedback = new ComplaintFeedback(resolvedComplaint, OWNER_EMAIL, 5, "Excellent and fast repair!");
        submittedFeedback.setId(301L);
        submittedFeedback.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        when(complaintService.getByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(complaintService.getStatusHistory(REFERENCE_ID)).thenReturn(Collections.emptyList());
        when(feedbackService.getFeedbackForComplaint(55L)).thenReturn(Optional.of(submittedFeedback));

        mockMvc.perform(get("/complaints/track").param("referenceId", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attribute("isOwner", true))
                .andExpect(model().attribute("canSubmitFeedback", false))
                .andExpect(model().attribute("feedbackSubmitted", true))
                .andExpect(content().string(containsString("submitted-feedback-card")))
                .andExpect(content().string(containsString("Excellent and fast repair!")))
                .andExpect(content().string(containsString("5 out of 5 stars")))
                .andExpect(content().string(not(containsString("btn-submit-feedback"))));
    }

    @Test
    @DisplayName("Track complaint: student owner on UNRESOLVED complaint does NOT see feedback form")
    void shouldNotDisplayFeedbackFormWhenComplaintIsNotResolved() throws Exception {
        resolvedComplaint.setStatus(ComplaintStatus.IN_PROGRESS);
        when(complaintService.getByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(complaintService.getStatusHistory(REFERENCE_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/complaints/track").param("referenceId", REFERENCE_ID)
                        .with(user(OWNER_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("complaints/track"))
                .andExpect(model().attribute("isOwner", true))
                .andExpect(model().attribute("canSubmitFeedback", false))
                .andExpect(model().attribute("feedbackSubmitted", false))
                .andExpect(content().string(not(containsString("resolution-feedback-form"))));
    }
}
