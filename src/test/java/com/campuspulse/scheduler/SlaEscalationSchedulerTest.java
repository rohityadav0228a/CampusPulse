package com.campuspulse.scheduler;

import com.campuspulse.config.SlaProperties;
import com.campuspulse.service.SlaEscalationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying SlaEscalationScheduler execution, enabled/disabled configuration,
 * and exception containment.
 */
@ExtendWith(MockitoExtension.class)
class SlaEscalationSchedulerTest {

    @Mock
    private SlaEscalationService slaEscalationService;

    @Test
    @DisplayName("runSlaEscalationCheck invokes slaEscalationService when enabled")
    void shouldExecuteEscalationWhenEnabled() {
        SlaProperties properties = new SlaProperties();
        properties.getEscalation().setEnabled(true);

        when(slaEscalationService.processEscalations()).thenReturn(2);

        SlaEscalationScheduler scheduler = new SlaEscalationScheduler(slaEscalationService, properties);
        scheduler.runSlaEscalationCheck();

        verify(slaEscalationService, times(1)).processEscalations();
    }

    @Test
    @DisplayName("runSlaEscalationCheck skips processing when disabled via configuration")
    void shouldSkipEscalationWhenDisabled() {
        SlaProperties properties = new SlaProperties();
        properties.getEscalation().setEnabled(false);

        SlaEscalationScheduler scheduler = new SlaEscalationScheduler(slaEscalationService, properties);
        scheduler.runSlaEscalationCheck();

        verify(slaEscalationService, never()).processEscalations();
    }

    @Test
    @DisplayName("runSlaEscalationCheck catches unexpected exceptions to protect scheduler thread")
    void shouldHandleUnexpectedExceptionsGracefully() {
        SlaProperties properties = new SlaProperties();
        properties.getEscalation().setEnabled(true);

        doThrow(new RuntimeException("Simulated unexpected database failure"))
                .when(slaEscalationService).processEscalations();

        SlaEscalationScheduler scheduler = new SlaEscalationScheduler(slaEscalationService, properties);

        assertDoesNotThrow(scheduler::runSlaEscalationCheck);
        verify(slaEscalationService, times(1)).processEscalations();
    }
}
