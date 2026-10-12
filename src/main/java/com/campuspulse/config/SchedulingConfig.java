package com.campuspulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration enabling Spring Boot scheduled task execution
 * for background jobs such as the notification outbox retry worker.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
