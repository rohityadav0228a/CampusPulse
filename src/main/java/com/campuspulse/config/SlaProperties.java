package com.campuspulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * Configuration properties governing SLA response/resolution targets,
 * timezone calculation policy, and automated escalation scheduling.
 */
@Configuration
@ConfigurationProperties(prefix = "campuspulse.sla")
public class SlaProperties {

    /**
     * Default allowed time window (in hours) between complaint submission and initial administrative response.
     * Default: 24 hours.
     */
    private int defaultResponseHours = 24;

    /**
     * Default allowed time window (in hours) between complaint submission and final closure/resolution.
     * Default: 72 hours.
     */
    private int defaultResolutionHours = 72;

    /**
     * Proximity threshold (in hours) prior to deadline expiry at which a complaint is flagged as approaching deadline.
     * Default: 6 hours.
     */
    private int approachingDeadlineHours = 6;

    /**
     * Documented timezone ID used to anchor deadline calculations and SLA evaluations consistently.
     * Default: Asia/Kolkata.
     */
    private String timezone = "Asia/Kolkata";

    /**
     * Administrator or escalation team email address receiving SLA breach alert notifications.
     */
    private String adminEscalationEmail = "admin@campuspulse.edu";

    /**
     * SLA Escalation sub-properties for background scheduling.
     */
    private Escalation escalation = new Escalation();

    public static class Escalation {
        private boolean enabled = true;
        private long intervalMs = 60000L;
        private long initialDelayMs = 15000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }
    }

    public int getDefaultResponseHours() {
        return defaultResponseHours;
    }

    public void setDefaultResponseHours(int defaultResponseHours) {
        this.defaultResponseHours = defaultResponseHours;
    }

    public int getDefaultResolutionHours() {
        return defaultResolutionHours;
    }

    public void setDefaultResolutionHours(int defaultResolutionHours) {
        this.defaultResolutionHours = defaultResolutionHours;
    }

    public int getApproachingDeadlineHours() {
        return approachingDeadlineHours;
    }

    public void setApproachingDeadlineHours(int approachingDeadlineHours) {
        this.approachingDeadlineHours = approachingDeadlineHours;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public ZoneId getZoneId() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    public String getAdminEscalationEmail() {
        return adminEscalationEmail;
    }

    public void setAdminEscalationEmail(String adminEscalationEmail) {
        this.adminEscalationEmail = adminEscalationEmail;
    }

    public Escalation getEscalation() {
        return escalation;
    }

    public void setEscalation(Escalation escalation) {
        this.escalation = escalation;
    }
}
