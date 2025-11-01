package com.trademaster.brokerauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health Check Response DTO
 *
 * MANDATORY: Records Usage - Rule #9
 * MANDATORY: Immutable Data - Rule #9
 *
 * @param status Overall health status (UP, DOWN, DEGRADED)
 * @param service Service name
 * @param version Service version
 * @param timestamp Check timestamp
 * @param checks Map of component health checks
 */
@Schema(description = "Comprehensive health check response for Kong Gateway")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthCheckResponse(
    @Schema(description = "Overall health status", example = "UP")
    String status,

    @Schema(description = "Service name", example = "broker-auth-service")
    String service,

    @Schema(description = "Service version", example = "1.0.0")
    String version,

    @Schema(description = "Health check timestamp")
    LocalDateTime timestamp,

    @Schema(description = "Component-level health checks")
    Map<String, Object> checks
) {
    /**
     * Factory method for UP status
     */
    public static HealthCheckResponse up(
            String service,
            String version,
            Map<String, Object> checks) {
        return new HealthCheckResponse("UP", service, version, LocalDateTime.now(), checks);
    }

    /**
     * Factory method for DOWN status
     */
    public static HealthCheckResponse down(
            String service,
            String version,
            Map<String, Object> checks) {
        return new HealthCheckResponse("DOWN", service, version, LocalDateTime.now(), checks);
    }

    /**
     * Factory method for DEGRADED status
     */
    public static HealthCheckResponse degraded(
            String service,
            String version,
            Map<String, Object> checks) {
        return new HealthCheckResponse("DEGRADED", service, version, LocalDateTime.now(), checks);
    }

    /**
     * Functional predicate - is system healthy?
     */
    public boolean isHealthy() {
        return "UP".equals(status);
    }

    /**
     * Functional predicate - is system degraded?
     */
    public boolean isDegraded() {
        return "DEGRADED".equals(status);
    }
}
