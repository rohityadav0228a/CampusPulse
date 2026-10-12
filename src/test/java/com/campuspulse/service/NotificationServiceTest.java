package com.campuspulse.service;

import com.campuspulse.model.Complaint;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.model.NotificationOutbox;
import com.campuspulse.model.NotificationStatus;
import com.campuspulse.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite verifying NotificationService outbox queuing,
 * atomic database claiming, concurrency conflict protection, exponential backoff,
 * retry limit enforcement, and permanent failure detection.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private NotificationService notificationService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                outboxRepository,
                emailNotificationService,
                3,       // maxAttempts
                30000L,  // initialBackoffMs (30s)
                2.0,     // backoffMultiplier
                300000L, // maxBackoffMs (5 min)
                300000L  // staleThresholdMs (5 min)
        );

        sampleComplaint = new Complaint(
                "CP-2026-NOTIF001",
                "Damaged Study Desks",
                "Desks on 3rd floor are broken.",
                "Infrastructure & Labs",
                "Campus Facilities & Works",
                "student.notif@college.edu"
        );
    }

    // =========================================================================
    // 1. Immediate Dispatch Tests
    // =========================================================================

    @Test
    @DisplayName("queueAndDispatchStatusUpdate successfully sends email and marks outbox record SENT for UNDER_REVIEW")
    void dispatchesEmailAndMarksSentForUnderReview() {
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailNotificationService).sendNotification(
                eq("student.notif@college.edu"),
                eq("CP-2026-NOTIF001"),
                eq(ComplaintStatus.UNDER_REVIEW)
        );

        NotificationOutbox result = notificationService.queueAndDispatchStatusUpdate(
                sampleComplaint,
                ComplaintStatus.UNDER_REVIEW
        );

        assertNotNull(result);
        assertEquals(NotificationStatus.SENT, result.getStatus());
        assertEquals("student.notif@college.edu", result.getRecipientEmail());
        assertEquals(ComplaintStatus.UNDER_REVIEW, result.getTargetStatus());
        assertEquals(1, result.getAttemptCount());
        assertNotNull(result.getSentAt());
        assertNull(result.getLastError());
        assertNull(result.getNextRetryAt());

        verify(emailNotificationService, times(1)).sendNotification(
                "student.notif@college.edu",
                "CP-2026-NOTIF001",
                ComplaintStatus.UNDER_REVIEW
        );
    }

    @Test
    @DisplayName("queueAndDispatchStatusUpdate skips SUBMITTED status and creates no outbox record")
    void skipsSubmittedStatus() {
        NotificationOutbox result = notificationService.queueAndDispatchStatusUpdate(
                sampleComplaint,
                ComplaintStatus.SUBMITTED
        );

        assertNull(result);
        verify(outboxRepository, never()).save(any());
        verify(emailNotificationService, never()).sendNotification(any(), any(), any());
    }

    @Test
    @DisplayName("queueAndDispatchStatusUpdate safely records SKIPPED_NO_EMAIL when complaint has null email")
    void recordsSkippedNoEmailWhenEmailIsNull() {
        sampleComplaint.setStudentEmail(null);
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationOutbox result = notificationService.queueAndDispatchStatusUpdate(
                sampleComplaint,
                ComplaintStatus.UNDER_REVIEW
        );

        assertNotNull(result);
        assertEquals(NotificationStatus.SKIPPED_NO_EMAIL, result.getStatus());
        assertNull(result.getRecipientEmail());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("No student email address"));

        verify(emailNotificationService, never()).sendNotification(any(), any(), any());
    }

    @Test
    @DisplayName("queueAndDispatchStatusUpdate marks PERMANENTLY_FAILED immediately when email format is malformed")
    void recordsPermanentlyFailedWhenEmailFormatIsMalformed() {
        sampleComplaint.setStudentEmail("not-an-email");
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationOutbox result = notificationService.queueAndDispatchStatusUpdate(
                sampleComplaint,
                ComplaintStatus.RESOLVED
        );

        assertNotNull(result);
        assertEquals(NotificationStatus.PERMANENTLY_FAILED, result.getStatus());
        assertTrue(result.getLastError().contains("Permanently invalid email format"));
        assertNull(result.getNextRetryAt());

        verify(emailNotificationService, never()).sendNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Transient delivery failure marks outbox item FAILED with backoff without failing caller transaction")
    void transientDeliveryFailureMarksOutboxFailedWithBackoff() {
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new MailSendException("SMTP host unreachable"))
                .when(emailNotificationService).sendNotification(any(), any(), any());

        NotificationOutbox result = notificationService.queueAndDispatchStatusUpdate(
                sampleComplaint,
                ComplaintStatus.UNDER_REVIEW
        );

        assertNotNull(result);
        assertEquals(NotificationStatus.FAILED, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertNotNull(result.getLastAttemptedAt());
        assertNull(result.getSentAt());
        assertNotNull(result.getNextRetryAt(), "Must schedule next retry timestamp via backoff");
        assertTrue(result.getLastError().contains("SMTP host unreachable"));
    }

    @Test
    @DisplayName("Immediate permanent SMTP 5xx failure transitions outbox directly to PERMANENTLY_FAILED")
    void immediatePermanentSmtpFailureMarksPermanentlyFailed() {
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new MailSendException("550 5.1.1 User unknown / Mailbox does not exist"))
                .when(emailNotificationService).sendNotification(any(), any(), any());

        NotificationOutbox result = notificationService.queueAndDispatchStatusUpdate(
                sampleComplaint,
                ComplaintStatus.UNDER_REVIEW
        );

        assertNotNull(result);
        assertEquals(NotificationStatus.PERMANENTLY_FAILED, result.getStatus());
        assertNull(result.getNextRetryAt());
        assertTrue(result.getLastError().contains("550"));
    }

    // =========================================================================
    // 2. Concurrency Claim & Duplicate-Delivery Protection Tests
    // =========================================================================

    @Test
    @DisplayName("Duplicate delivery is strictly prevented when concurrent worker claims notification first")
    void duplicateDeliveryPreventedWhenClaimLostToConcurrentWorker() {
        // Candidate is identified
        when(outboxRepository.findEligibleCandidateIds(any(), anyInt(), any()))
                .thenReturn(List.of(101L));

        // Atomic claim fails (returns 0 because another concurrent worker or thread claimed it first)
        when(outboxRepository.claimNotificationForProcessing(eq(101L), any(), any()))
                .thenReturn(0);

        int delivered = notificationService.processPendingAndFailedRetries();

        assertEquals(0, delivered, "Must not count or deliver notifications claimed by another worker");
        // Crucial verification: SMTP sender must NEVER be invoked for an un-claimed row
        verify(emailNotificationService, never()).sendNotification(any(), any(), any());
        verify(outboxRepository, never()).findById(101L);
    }

    @Test
    @DisplayName("Concurrent worker threads racing to process the exact same notification results in exactly one delivery")
    void concurrentWorkersRaceProducesExactlyOneDelivery() throws Exception {
        NotificationOutbox outbox = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-CONCURRENT",
                "student.concurrent@college.edu",
                ComplaintStatus.RESOLVED
        );
        outbox.setId(777L);
        outbox.setStatus(NotificationStatus.PROCESSING);

        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(outboxRepository.claimNotificationForProcessing(eq(777L), any(), any()))
                .thenAnswer(inv -> claimed.compareAndSet(false, true) ? 1 : 0);
        when(outboxRepository.findById(777L)).thenReturn(Optional.of(outbox));
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(5);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<Boolean> worker1 = executor.submit(() ->
                notificationService.processSingleNotificationRetry(777L, now, threshold));
        java.util.concurrent.Future<Boolean> worker2 = executor.submit(() ->
                notificationService.processSingleNotificationRetry(777L, now, threshold));

        boolean res1 = worker1.get();
        boolean res2 = worker2.get();
        executor.shutdown();

        // Exactly one worker must succeed (XOR condition), and the losing worker must gracefully skip
        assertTrue(res1 ^ res2, "Exactly one worker thread must claim and deliver the notification");
        verify(emailNotificationService, times(1)).sendNotification(
                "student.concurrent@college.edu",
                "CP-2026-CONCURRENT",
                ComplaintStatus.RESOLVED
        );
    }

    // =========================================================================
    // 3. Retry Execution, Success, and Preservation Tests
    // =========================================================================

    @Test
    @DisplayName("Successful retry delivery updates existing outbox record to SENT in-place without creating new entries")
    void successfulRetryUpdatesExistingRecordInPlace() {
        NotificationOutbox existingOutbox = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-RETRY001",
                "student.retry@college.edu",
                ComplaintStatus.IN_PROGRESS
        );
        existingOutbox.setId(201L);
        existingOutbox.setStatus(NotificationStatus.PROCESSING);
        existingOutbox.setAttemptCount(1);
        existingOutbox.setLastError("Previous timeout");

        when(outboxRepository.findEligibleCandidateIds(any(), anyInt(), any()))
                .thenReturn(List.of(201L));
        when(outboxRepository.claimNotificationForProcessing(eq(201L), any(), any()))
                .thenReturn(1);
        when(outboxRepository.findById(201L))
                .thenReturn(Optional.of(existingOutbox));
        when(outboxRepository.save(any(NotificationOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int delivered = notificationService.processPendingAndFailedRetries();

        assertEquals(1, delivered);
        assertEquals(NotificationStatus.SENT, existingOutbox.getStatus());
        assertEquals(2, existingOutbox.getAttemptCount());
        assertEquals(201L, existingOutbox.getId(), "Must preserve original record ID");
        assertNotNull(existingOutbox.getSentAt());
        assertNull(existingOutbox.getLastError());
        assertNull(existingOutbox.getNextRetryAt());

        verify(emailNotificationService, times(1)).sendNotification(
                "student.retry@college.edu",
                "CP-2026-RETRY001",
                ComplaintStatus.IN_PROGRESS
        );
    }

    // =========================================================================
    // 4. Backoff and Retry Limit Tests
    // =========================================================================

    @Test
    @DisplayName("calculateBackoffDelayMs computes exponential delays accurately and caps at maximum backoff")
    void calculateBackoffDelayMsHonorsExponentialFormula() {
        // initialBackoffMs = 30000 (30s), multiplier = 2.0, maxBackoffMs = 300000 (5m)
        assertEquals(30000L, notificationService.calculateBackoffDelayMs(1));
        assertEquals(60000L, notificationService.calculateBackoffDelayMs(2));
        assertEquals(120000L, notificationService.calculateBackoffDelayMs(3));
        assertEquals(240000L, notificationService.calculateBackoffDelayMs(4));
        assertEquals(300000L, notificationService.calculateBackoffDelayMs(5)); // capped at 300000
        assertEquals(300000L, notificationService.calculateBackoffDelayMs(10)); // capped
    }

    @Test
    @DisplayName("Transient retry failure increments attempt count, updates FAILED status, and applies backoff")
    void retryTransientFailureAppliesBackoff() {
        NotificationOutbox existingOutbox = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-RETRY002",
                "student.retry@college.edu",
                ComplaintStatus.RESOLVED
        );
        existingOutbox.setId(202L);
        existingOutbox.setStatus(NotificationStatus.PROCESSING);
        existingOutbox.setAttemptCount(1);

        when(outboxRepository.findEligibleCandidateIds(any(), anyInt(), any()))
                .thenReturn(List.of(202L));
        when(outboxRepository.claimNotificationForProcessing(eq(202L), any(), any()))
                .thenReturn(1);
        when(outboxRepository.findById(202L))
                .thenReturn(Optional.of(existingOutbox));
        when(outboxRepository.save(any(NotificationOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        doThrow(new MailSendException("Temporary network connection refused"))
                .when(emailNotificationService).sendNotification(any(), any(), any());

        int delivered = notificationService.processPendingAndFailedRetries();

        assertEquals(0, delivered);
        assertEquals(NotificationStatus.FAILED, existingOutbox.getStatus());
        assertEquals(2, existingOutbox.getAttemptCount());
        assertNotNull(existingOutbox.getNextRetryAt());
        assertTrue(existingOutbox.getLastError().contains("Temporary network connection refused"));
    }

    @Test
    @DisplayName("Reaching maximum retry count transitions notification to PERMANENTLY_FAILED and ends retries")
    void reachingMaxAttemptsTransitionsToPermanentlyFailed() {
        NotificationOutbox existingOutbox = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-RETRY003",
                "student.retry@college.edu",
                ComplaintStatus.RESOLVED
        );
        existingOutbox.setId(203L);
        existingOutbox.setStatus(NotificationStatus.PROCESSING);
        existingOutbox.setAttemptCount(2); // this retry will be attempt 3 (equal to maxAttempts = 3)

        when(outboxRepository.findEligibleCandidateIds(any(), anyInt(), any()))
                .thenReturn(List.of(203L));
        when(outboxRepository.claimNotificationForProcessing(eq(203L), any(), any()))
                .thenReturn(1);
        when(outboxRepository.findById(203L))
                .thenReturn(Optional.of(existingOutbox));
        when(outboxRepository.save(any(NotificationOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        doThrow(new MailSendException("SMTP relay down"))
                .when(emailNotificationService).sendNotification(any(), any(), any());

        int delivered = notificationService.processPendingAndFailedRetries();

        assertEquals(0, delivered);
        assertEquals(NotificationStatus.PERMANENTLY_FAILED, existingOutbox.getStatus(),
                "Must transition to PERMANENTLY_FAILED after reaching maximum attempts limit");
        assertEquals(3, existingOutbox.getAttemptCount());
        assertNull(existingOutbox.getNextRetryAt(), "Must not schedule future retries for permanently failed items");
    }

    @Test
    @DisplayName("Permanent SMTP 5xx error during retry transitions immediately to PERMANENTLY_FAILED")
    void permanentSmtpErrorTransitionsImmediatelyToPermanentlyFailed() {
        NotificationOutbox existingOutbox = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-RETRY004",
                "student.invalid@college.edu",
                ComplaintStatus.RESOLVED
        );
        existingOutbox.setId(204L);
        existingOutbox.setStatus(NotificationStatus.PROCESSING);
        existingOutbox.setAttemptCount(1); // attempt 1 < maxAttempts (3)

        when(outboxRepository.findEligibleCandidateIds(any(), anyInt(), any()))
                .thenReturn(List.of(204L));
        when(outboxRepository.claimNotificationForProcessing(eq(204L), any(), any()))
                .thenReturn(1);
        when(outboxRepository.findById(204L))
                .thenReturn(Optional.of(existingOutbox));
        when(outboxRepository.save(any(NotificationOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        doThrow(new MailSendException("554 5.7.1 Recipient address rejected: Access denied"))
                .when(emailNotificationService).sendNotification(any(), any(), any());

        int delivered = notificationService.processPendingAndFailedRetries();

        assertEquals(0, delivered);
        assertEquals(NotificationStatus.PERMANENTLY_FAILED, existingOutbox.getStatus());
        assertNull(existingOutbox.getNextRetryAt());
    }

    @Test
    @DisplayName("Retry processing safely marks missing recipient emails as SKIPPED_NO_EMAIL without sending")
    void retryHandlesMissingEmailSafely() {
        NotificationOutbox noEmailItem = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-RETRY005",
                null,
                ComplaintStatus.RESOLVED
        );
        noEmailItem.setId(205L);
        noEmailItem.setStatus(NotificationStatus.PROCESSING);

        when(outboxRepository.findEligibleCandidateIds(any(), anyInt(), any()))
                .thenReturn(List.of(205L));
        when(outboxRepository.claimNotificationForProcessing(eq(205L), any(), any()))
                .thenReturn(1);
        when(outboxRepository.findById(205L))
                .thenReturn(Optional.of(noEmailItem));
        when(outboxRepository.save(any(NotificationOutbox.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int delivered = notificationService.processPendingAndFailedRetries();

        assertEquals(0, delivered);
        assertEquals(NotificationStatus.SKIPPED_NO_EMAIL, noEmailItem.getStatus());
        verify(emailNotificationService, never()).sendNotification(any(), any(), any());
    }

    // =========================================================================
    // 5. Query and Helper Tests
    // =========================================================================

    @Test
    @DisplayName("getOutboxForComplaint returns chronological outbox history for reference ID")
    void getOutboxForComplaintReturnsHistory() {
        NotificationOutbox item = new NotificationOutbox(
                sampleComplaint,
                "CP-2026-NOTIF001",
                "test@college.edu",
                ComplaintStatus.RESOLVED
        );

        when(outboxRepository.findByReferenceIdOrderByCreatedAtDesc("CP-2026-NOTIF001"))
                .thenReturn(List.of(item));

        List<NotificationOutbox> list = notificationService.getOutboxForComplaint("CP-2026-NOTIF001");

        assertEquals(1, list.size());
        assertEquals("CP-2026-NOTIF001", list.get(0).getReferenceId());
    }

    @Test
    @DisplayName("countByStatus returns aggregate count from repository")
    void countByStatusReturnsCount() {
        when(outboxRepository.countByStatus(NotificationStatus.SENT)).thenReturn(5L);

        long count = notificationService.countByStatus(NotificationStatus.SENT);

        assertEquals(5L, count);
        verify(outboxRepository, times(1)).countByStatus(NotificationStatus.SENT);
    }
}
