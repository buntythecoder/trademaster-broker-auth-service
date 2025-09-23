package com.trademaster.brokerauth.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ✅ ACTUATOR REDIRECT CONTROLLER: Handles actuator endpoint redirects on main application port
 *
 * MANDATORY COMPLIANCE:
 * - SOLID principles - Single Responsibility Pattern
 * - Functional programming patterns - no if-else statements
 * - Zero Trust Security - external access patterns
 * - Cognitive complexity ≤7 per method
 *
 * PURPOSE:
 * - Eliminates "No static resource actuator/health" errors
 * - Provides helpful redirection information to users
 * - Maintains consistency with management port architecture
 * - Offers alternative health endpoints on application port
 */
@RestController
@RequestMapping("/actuator")
@Slf4j
public class ActuatorRedirectController {

    @Value("${management.server.port:9084}")
    private int managementPort;

    @Value("${server.port:8084}")
    private int applicationPort;

    /**
     * ✅ FUNCTIONAL: Handle health endpoint redirect - no if-else pattern
     * Cognitive Complexity: 1
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> redirectHealth() {
        log.debug("Health endpoint accessed on application port, providing redirect information");

        Map<String, Object> response = Map.of(
            "message", "Actuator endpoints are available on management port " + managementPort,
            "management_url", "http://localhost:" + managementPort + "/actuator/health",
            "alternative_url", "http://localhost:" + applicationPort + "/api/v2/health",
            "timestamp", LocalDateTime.now(),
            "status", "redirect_info"
        );

        return ResponseEntity.ok()
                .header("Location", "http://localhost:" + managementPort + "/actuator/health")
                .body(response);
    }

    /**
     * ✅ FUNCTIONAL: Handle info endpoint redirect - no if-else pattern
     * Cognitive Complexity: 1
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> redirectInfo() {
        log.debug("Info endpoint accessed on application port, providing redirect information");

        Map<String, Object> response = Map.of(
            "message", "Actuator endpoints are available on management port " + managementPort,
            "management_url", "http://localhost:" + managementPort + "/actuator/info",
            "timestamp", LocalDateTime.now(),
            "status", "redirect_info"
        );

        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header("Location", "http://localhost:" + managementPort + "/actuator/info")
                .body(response);
    }

    /**
     * ✅ FUNCTIONAL: Handle metrics endpoint redirect - no if-else pattern
     * Cognitive Complexity: 1
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> redirectMetrics() {
        log.debug("Metrics endpoint accessed on application port, providing redirect information");

        Map<String, Object> response = Map.of(
            "message", "Actuator endpoints are available on management port " + managementPort,
            "management_url", "http://localhost:" + managementPort + "/actuator/metrics",
            "timestamp", LocalDateTime.now(),
            "status", "redirect_info"
        );

        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header("Location", "http://localhost:" + managementPort + "/actuator/metrics")
                .body(response);
    }

    /**
     * ✅ FUNCTIONAL: Handle prometheus endpoint redirect - no if-else pattern
     * Cognitive Complexity: 1
     */
    @GetMapping("/prometheus")
    public ResponseEntity<Map<String, Object>> redirectPrometheus() {
        log.debug("Prometheus endpoint accessed on application port, providing redirect information");

        Map<String, Object> response = Map.of(
            "message", "Actuator endpoints are available on management port " + managementPort,
            "management_url", "http://localhost:" + managementPort + "/actuator/prometheus",
            "timestamp", LocalDateTime.now(),
            "status", "redirect_info"
        );

        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header("Location", "http://localhost:" + managementPort + "/actuator/prometheus")
                .body(response);
    }

    /**
     * ✅ FUNCTIONAL: Handle general actuator base path redirect - no if-else pattern
     * Cognitive Complexity: 1
     */
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> redirectBase() {
        log.debug("Actuator base path accessed on application port, providing redirect information");

        Map<String, Object> response = Map.of(
            "message", "Actuator endpoints are available on management port " + managementPort,
            "management_url", "http://localhost:" + managementPort + "/actuator",
            "available_endpoints", Map.of(
                "health", "http://localhost:" + managementPort + "/actuator/health",
                "info", "http://localhost:" + managementPort + "/actuator/info",
                "metrics", "http://localhost:" + managementPort + "/actuator/metrics",
                "prometheus", "http://localhost:" + managementPort + "/actuator/prometheus",
                "alternative_health", "http://localhost:" + applicationPort + "/api/v2/health"
            ),
            "timestamp", LocalDateTime.now(),
            "status", "redirect_info"
        );

        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header("Location", "http://localhost:" + managementPort + "/actuator")
                .body(response);
    }
}