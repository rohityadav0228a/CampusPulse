package com.campuspulse.service;

import com.campuspulse.dto.ComplaintFeedbackForm;
import com.campuspulse.dto.analytics.FeedbackMetrics;
import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintFeedback;
import com.campuspulse.model.ComplaintPriority;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.repository.ComplaintFeedbackRepository;
import com.campuspulse.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintFeedbackServiceTest {

    @Mock
    private ComplaintFeedbackRepository feedbackRepository;

    @Mock
    private ComplaintRepository complaintRepository;

    @InjectMocks
    private ComplaintFeedbackService feedbackService;

    private Complaint resolvedComplaint;
    private static final String REFERENCE_ID = "CP-2026-A1B2C3D4";
    private static final String OWNER_EMAIL = "student@campuspulse.edu";

    @BeforeEach
    void setUp() {
        resolvedComplaint = new Complaint(
                REFERENCE_ID,
                "Leaking water tap in hostel block A",
                "Continuous dripping causing water accumulation in the 2nd floor corridor.",
                "Hostel & Residence",
                "Hostel Administration",
                OWNER_EMAIL
        );
        resolvedComplaint.setId(42L);
        resolvedComplaint.setPriority(ComplaintPriority.MEDIUM);
        resolvedComplaint.setStatus(ComplaintStatus.RESOLVED);
        resolvedComplaint.setResolutionNote("Replaced valve gasket and tested water flow.");
        resolvedComplaint.setResolvedAt(LocalDateTime.now().minusHours(2));
    }

    @Test
    @DisplayName("Successfully submit feedback for resolved complaint by verified owner")
    void shouldSubmitFeedbackSuccessfullyForResolvedComplaintByOwner() {
        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Prompt and clean resolution, thank you!");

        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(feedbackRepository.existsByComplaintId(42L)).thenReturn(false);
        when(feedbackRepository.save(any(ComplaintFeedback.class))).thenAnswer(invocation -> {
            ComplaintFeedback fb = invocation.getArgument(0);
            fb.setId(101L);
            return fb;
        });

        ComplaintFeedback result = feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, form);

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals(5, result.getRating());
        assertEquals("Prompt and clean resolution, thank you!", result.getComment());
        assertEquals(OWNER_EMAIL, result.getStudentEmail());
        assertEquals(resolvedComplaint, result.getComplaint());

        ArgumentCaptor<ComplaintFeedback> captor = ArgumentCaptor.forClass(ComplaintFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getRating());
        assertEquals(OWNER_EMAIL, captor.getValue().getStudentEmail());
    }

    @Test
    @DisplayName("Allow feedback submission with null or blank optional comment")
    void shouldAllowFeedbackSubmissionWithNullOrBlankComment() {
        ComplaintFeedbackForm formWithNull = new ComplaintFeedbackForm(4, null);
        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(feedbackRepository.existsByComplaintId(42L)).thenReturn(false);
        when(feedbackRepository.save(any(ComplaintFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintFeedback result = feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, formWithNull);

        assertNotNull(result);
        assertEquals(4, result.getRating());
        assertNull(result.getComment());

        ComplaintFeedbackForm formWithBlank = new ComplaintFeedbackForm(4, "   ");
        ComplaintFeedback resultBlank = feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, formWithBlank);
        assertNull(resultBlank.getComment());
    }

    @Test
    @DisplayName("Normalize owner email (trim and lowercase) during feedback submission")
    void shouldNormalizeEmailDuringFeedbackSubmission() {
        ComplaintFeedbackForm form = new ComplaintFeedbackForm(4, "Good service");
        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(feedbackRepository.existsByComplaintId(42L)).thenReturn(false);
        when(feedbackRepository.save(any(ComplaintFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintFeedback result = feedbackService.submitFeedback(REFERENCE_ID, "  STUDENT@CampusPulse.EDU  ", form);

        assertEquals("student@campuspulse.edu", result.getStudentEmail());
    }

    @Test
    @DisplayName("Reject feedback when rating is null, less than 1, or greater than 5")
    void shouldRejectFeedbackWhenRatingIsInvalid() {
        ComplaintFeedbackForm nullRatingForm = new ComplaintFeedbackForm(null, "Nice");
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, nullRatingForm));
        assertTrue(ex1.getMessage().contains("between 1 and 5"));

        ComplaintFeedbackForm lowRatingForm = new ComplaintFeedbackForm(0, "Terrible");
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, lowRatingForm));
        assertTrue(ex2.getMessage().contains("between 1 and 5"));

        ComplaintFeedbackForm highRatingForm = new ComplaintFeedbackForm(6, "Superb");
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, highRatingForm));
        assertTrue(ex3.getMessage().contains("between 1 and 5"));

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject feedback when comment exceeds 1,000 characters")
    void shouldRejectFeedbackWhenCommentExceeds1000Characters() {
        String longComment = "a".repeat(1001);
        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, longComment);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, form));
        assertTrue(ex.getMessage().contains("1,000 characters"));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject feedback when reference ID or student email is blank")
    void shouldRejectFeedbackWhenReferenceIdOrEmailBlank() {
        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Great");

        assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback("", OWNER_EMAIL, form));

        assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, "   ", form));

        assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, null));
    }

    @Test
    @DisplayName("Reject feedback when complaint reference ID does not exist")
    void shouldRejectFeedbackWhenComplaintNotFound() {
        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Great");
        when(complaintRepository.findByReferenceId("CP-2026-NONEXIST")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                feedbackService.submitFeedback("CP-2026-NONEXIST", OWNER_EMAIL, form));
        assertTrue(ex.getMessage().contains("Complaint not found"));
    }

    @Test
    @DisplayName("Reject feedback when complaint is soft-deleted or archived")
    void shouldRejectFeedbackWhenComplaintIsDeleted() {
        resolvedComplaint.setDeleted(true);
        resolvedComplaint.setDeletedAt(LocalDateTime.now());
        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));

        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Good");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, form));
        assertTrue(ex.getMessage().contains("archived or deleted"));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject feedback when complaint status is not RESOLVED (e.g. SUBMITTED, UNDER_REVIEW, IN_PROGRESS)")
    void shouldRejectFeedbackWhenComplaintIsNotResolved() {
        ComplaintFeedbackForm form = new ComplaintFeedbackForm(4, "Update please");

        for (ComplaintStatus status : List.of(ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW, ComplaintStatus.IN_PROGRESS)) {
            resolvedComplaint.setStatus(status);
            when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, form));
            assertTrue(ex.getMessage().contains("Feedback can only be submitted for resolved complaints"));
        }

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject feedback submission when authenticated user does not own the complaint (IDOR attempt)")
    void shouldRejectFeedbackWhenUserIsNotComplaintOwner() {
        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));

        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Attempted feedback by stranger");
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, "other.student@campuspulse.edu", form));

        assertTrue(ex.getMessage().contains("not authorized to submit feedback"));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject duplicate feedback at service layer if feedback already exists for complaint")
    void shouldRejectDuplicateFeedbackAtServiceLayer() {
        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(feedbackRepository.existsByComplaintId(42L)).thenReturn(true);

        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Second submission attempt");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, form));

        assertTrue(ex.getMessage().contains("Feedback has already been submitted"));
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Catch DataIntegrityViolationException from database unique constraint and translate to IllegalStateException")
    void shouldHandleDatabaseUniqueConstraintViolationGracefully() {
        when(complaintRepository.findByReferenceId(REFERENCE_ID)).thenReturn(Optional.of(resolvedComplaint));
        when(feedbackRepository.existsByComplaintId(42L)).thenReturn(false);
        when(feedbackRepository.save(any(ComplaintFeedback.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key error on complaint_id"));

        ComplaintFeedbackForm form = new ComplaintFeedbackForm(5, "Concurrent duplicate submission");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                feedbackService.submitFeedback(REFERENCE_ID, OWNER_EMAIL, form));

        assertTrue(ex.getMessage().contains("Feedback has already been submitted"));
    }

    @Test
    @DisplayName("Retrieve feedback by complaint primary key ID and reference ID")
    void shouldRetrieveFeedbackByIdAndReferenceId() {
        ComplaintFeedback feedback = new ComplaintFeedback(resolvedComplaint, OWNER_EMAIL, 5, "Well done");
        when(feedbackRepository.findByComplaintId(42L)).thenReturn(Optional.of(feedback));
        when(feedbackRepository.findByComplaintReferenceId(REFERENCE_ID)).thenReturn(Optional.of(feedback));
        when(feedbackRepository.existsByComplaintReferenceId(REFERENCE_ID)).thenReturn(true);

        Optional<ComplaintFeedback> byId = feedbackService.getFeedbackForComplaint(42L);
        assertTrue(byId.isPresent());
        assertEquals(5, byId.get().getRating());

        Optional<ComplaintFeedback> byRef = feedbackService.getFeedbackForComplaint(REFERENCE_ID);
        assertTrue(byRef.isPresent());
        assertEquals("Well done", byRef.get().getComment());

        assertTrue(feedbackService.hasFeedback(REFERENCE_ID));
        assertFalse(feedbackService.hasFeedback("CP-NON-EXIST"));
    }

    @Test
    @DisplayName("Calculate aggregate feedback metrics for admin analytics correctly")
    void shouldCalculateAggregateFeedbackMetricsAccurately() {
        when(feedbackRepository.count()).thenReturn(10L);
        when(feedbackRepository.findAverageRating()).thenReturn(4.3);
        when(feedbackRepository.countByRatingGrouped()).thenReturn(List.of(
                new Object[]{5, 5L},
                new Object[]{4, 3L},
                new Object[]{3, 2L}
        ));

        FeedbackMetrics metrics = feedbackService.getFeedbackMetrics();

        assertNotNull(metrics);
        assertEquals(10L, metrics.totalFeedback());
        assertEquals(4.3, metrics.averageRating());
        assertEquals("4.3", metrics.averageRatingFormatted());

        assertEquals(5L, metrics.ratingDistribution().get(5));
        assertEquals(3L, metrics.ratingDistribution().get(4));
        assertEquals(2L, metrics.ratingDistribution().get(3));
        assertEquals(0L, metrics.ratingDistribution().get(2));
        assertEquals(0L, metrics.ratingDistribution().get(1));

        assertEquals(50.0, metrics.ratingPercentages().get(5));
        assertEquals(30.0, metrics.ratingPercentages().get(4));
        assertEquals(20.0, metrics.ratingPercentages().get(3));
        assertEquals(0.0, metrics.ratingPercentages().get(2));
        assertEquals(0.0, metrics.ratingPercentages().get(1));
    }

    @Test
    @DisplayName("Return empty zero-initialized FeedbackMetrics when no feedback has been submitted")
    void shouldReturnEmptyMetricsWhenNoFeedbackExists() {
        when(feedbackRepository.count()).thenReturn(0L);

        FeedbackMetrics metrics = feedbackService.getFeedbackMetrics();

        assertNotNull(metrics);
        assertEquals(0L, metrics.totalFeedback());
        assertEquals(0.0, metrics.averageRating());
        assertEquals("N/A", metrics.averageRatingFormatted());
        assertEquals(0L, metrics.ratingDistribution().get(5));
    }
}
