package com.campuspulse.scheduler;

import com.campuspulse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background worker that periodically triggers retry processing
 * for eligible PENDING and FAILED notifications in the outbox.
 */
@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationService notificationService;
    private final boolean retryEnabled;

    public NotificationRetryScheduler(
            NotificationService notificationService,
            @Value("${campuspulse.notifications.retry.enabled:true}") boolean retryEnabled) {
        this.notificationService = notificationService;
        this.retryEnabled = retryEnabled;
    }

    /**
     * Periodically triggered scheduled task that inspects the notification outbox
     * and re-attempts delivery for failed or pending notifications whose backoff interval has passed.
     * Prevents concurrent execution within the same instance via fixedDelay.
     */
    @Scheduled(
            fixedDelayString = "${campuspulse.notifications.retry.interval-ms:60000}",
            initialDelayString = "${campuspulse.notifications.retry.initial-delay-ms:10000}"
    )
    public void runNotificationRetry() {
        if (!retryEnabled) {
            log.trace("Notification retry scheduler is disabled by configuration.");
            return;
        }

        try {
            int delivered = notificationService.processPendingAndFailedRetries();
            if (delivered > 0) {
                log.info("Scheduled notification retry worker successfully delivered {} notification(s)", delivered);
            }
        } catch (Exception ex) {
            log.error("Unexpected error during scheduled notification retry run: {}", ex.getMessage());
        }
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }
}
