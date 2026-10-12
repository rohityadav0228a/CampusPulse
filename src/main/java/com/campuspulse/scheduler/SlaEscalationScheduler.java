package com.campuspulse.scheduler;

import com.campuspulse.config.SlaProperties;
import com.campuspulse.service.SlaEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background worker that periodically detects overdue complaints,
 * triggers automated multi-stage SLA escalations, persists escalation audits,
 * and queues alerts into the notification outbox.
 */
@Component
public class SlaEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationScheduler.class);

    private final SlaEscalationService slaEscalationService;
    private final SlaProperties slaProperties;

    public SlaEscalationScheduler(
            SlaEscalationService slaEscalationService,
            SlaProperties slaProperties) {
        this.slaEscalationService = slaEscalationService;
        this.slaProperties = slaProperties;
    }

    /**
     * Periodically triggers SLA breach detection and automated escalation runs.
     * Prevents concurrent execution on the same node via fixedDelay.
     */
    @Scheduled(
            fixedDelayString = "${campuspulse.sla.escalation.interval-ms:60000}",
            initialDelayString = "${campuspulse.sla.escalation.initial-delay-ms:15000}"
    )
    public void runSlaEscalationCheck() {
        if (!slaProperties.getEscalation().isEnabled()) {
            log.trace("SLA escalation scheduler is disabled by configuration.");
            return;
        }

        try {
            int escalated = slaEscalationService.processEscalations();
            if (escalated > 0) {
                log.info("Scheduled SLA escalation worker completed: escalated {} complaint(s)", escalated);
            }
        } catch (Exception ex) {
            log.error("Unexpected error during scheduled SLA escalation check: {}", ex.getMessage(), ex);
        }
    }
}
