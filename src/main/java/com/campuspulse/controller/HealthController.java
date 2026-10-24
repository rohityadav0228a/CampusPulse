package com.campuspulse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight health check endpoint for container orchestrators, cloud platform probes,
 * Docker healthchecks, and uptime monitors.
 *
 * Distinguishes application liveness from database readiness:
 * - GET /health/live: Lightweight liveness probe verifying Tomcat and Spring ApplicationContext.
 * - GET /health: Readiness probe verifying both application liveness and active database connectivity.
 *   Returns HTTP 200 with status=UP if healthy, or HTTP 503 with status=DEGRADED if database is unreachable.
 *
 * Security notice: Does not disclose environment variables, passwords, or diagnostic internals.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    @Autowired
    public HealthController(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Liveness probe: returns HTTP 200 as long as the web server is responsive.
     */
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }

    /**
     * Readiness probe: checks database connectivity using a short-timeout validation query.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "CampusPulse");

        boolean dbHealthy = isDatabaseReachable();
        response.put("database", dbHealthy ? "UP" : "DOWN");

        if (!dbHealthy) {
            response.put("status", "DEGRADED");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        return ResponseEntity.ok(response);
    }

    private boolean isDatabaseReachable() {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(3);
            return statement.execute("SELECT 1");
        } catch (Exception e) {
            return false;
        }
    }
}
