package com.campuspulse.scheduler;

import com.campuspulse.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite verifying NotificationRetryScheduler behavior,
 * configuration enablement flag, and exception isolation.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private NotificationService notificationService;

    @Test
    @DisplayName("runNotificationRetry invokes notificationService when retry is enabled")
    void runNotificationRetryExecutesWhenEnabled() {
        NotificationRetryScheduler scheduler = new NotificationRetryScheduler(notificationService, true);
        assertTrue(scheduler.isRetryEnabled());

        when(notificationService.processPendingAndFailedRetries()).thenReturn(2);

        scheduler.runNotificationRetry();

        verify(notificationService, times(1)).processPendingAndFailedRetries();
    }

    @Test
    @DisplayName("runNotificationRetry does nothing when retry is disabled via configuration")
    void runNotificationRetrySkipsWhenDisabled() {
        NotificationRetryScheduler scheduler = new NotificationRetryScheduler(notificationService, false);
        assertFalse(scheduler.isRetryEnabled());

        scheduler.runNotificationRetry();

        verify(notificationService, never()).processPendingAndFailedRetries();
    }

    @Test
    @DisplayName("runNotificationRetry catches unexpected exceptions to protect scheduler thread")
    void runNotificationRetryCatchesExceptionsGracefully() {
        NotificationRetryScheduler scheduler = new NotificationRetryScheduler(notificationService, true);

        doThrow(new RuntimeException("Simulated unexpected database failure"))
                .when(notificationService).processPendingAndFailedRetries();

        assertDoesNotThrow(scheduler::runNotificationRetry);
        verify(notificationService, times(1)).processPendingAndFailedRetries();
    }
}
