package com.trademaster.brokerauth.health;

import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.service.BrokerSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Session Health Indicator
 *
 * Monitors session health and lifecycle metrics for Spring Boot Actuator.
 *
 * MANDATORY: Rule #12 - Virtual Threads for async operations
 * MANDATORY: Rule #3 - Functional Programming patterns
 * MANDATORY: Task 2.2 - Session Management Enhancement
 *
 * Features:
 * - Active session count monitoring
 * - Sessions needing refresh tracking
 * - Average session lifetime calculation
 * - Health status based on thresholds
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionHealthIndicator implements HealthIndicator {

    private final BrokerSessionService sessionService;

    private static final int CRITICAL_REFRESH_THRESHOLD = 50; // 50% of sessions need refresh
    private static final int WARNING_REFRESH_THRESHOLD = 30;  // 30% of sessions need refresh
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    /**
     * Perform health check with timeout protection
     *
     * MANDATORY: Rule #12 - Virtual Threads for async operations
     */
    @Override
    public Health health() {
        return CompletableFuture
            .supplyAsync(this::performHealthCheck, Executors.newVirtualThreadPerTaskExecutor())
            .orTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(this::handleHealthCheckError)
            .join();
    }

    /**
     * Perform comprehensive session health assessment
     *
     * MANDATORY: Rule #3 - Functional Programming
     */
    private Health performHealthCheck() {
        try {
            List<BrokerSession> allSessions = getAllSessions();

            if (allSessions.isEmpty()) {
                return buildHealthStatus(HealthStatus.NO_SESSIONS, Map.of(
                    "activeSessions", 0,
                    "message", "No active sessions"
                ));
            }

            SessionMetrics metrics = calculateSessionMetrics(allSessions);
            HealthStatus status = determineHealthStatus(metrics);

            return buildHealthStatus(status, buildDetailsMap(metrics));

        } catch (Exception e) {
            log.error("Session health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    /**
     * Get all sessions with error handling
     *
     * MANDATORY: Rule #11 - Functional error handling
     */
    private List<BrokerSession> getAllSessions() {
        try {
            return sessionService.getUserActiveSessions("*");
        } catch (Exception e) {
            log.error("Failed to retrieve sessions for health check", e);
            return List.of();
        }
    }

    /**
     * Calculate session metrics using functional streams
     *
     * MANDATORY: Rule #3 - Functional Programming with streams
     */
    private SessionMetrics calculateSessionMetrics(List<BrokerSession> sessions) {
        long activeCount = countActiveSessions(sessions);
        long needsRefreshCount = countSessionsNeedingRefresh(sessions);
        long expiredCount = countExpiredSessions(sessions);
        double avgLifetimeMinutes = calculateAverageLifetime(sessions);
        double refreshPercentage = calculateRefreshPercentage(activeCount, needsRefreshCount);

        return new SessionMetrics(
            sessions.size(),
            activeCount,
            needsRefreshCount,
            expiredCount,
            avgLifetimeMinutes,
            refreshPercentage
        );
    }

    /**
     * Count active sessions
     */
    private long countActiveSessions(List<BrokerSession> sessions) {
        return sessions.stream()
            .filter(BrokerSession::isActive)
            .count();
    }

    /**
     * Count sessions needing refresh
     */
    private long countSessionsNeedingRefresh(List<BrokerSession> sessions) {
        return sessions.stream()
            .filter(BrokerSession::isActive)
            .filter(BrokerSession::needsRefresh)
            .count();
    }

    /**
     * Count expired sessions
     */
    private long countExpiredSessions(List<BrokerSession> sessions) {
        return sessions.stream()
            .filter(session -> session.getStatus() == SessionStatus.EXPIRED)
            .count();
    }

    /**
     * Calculate average session lifetime in minutes
     */
    private double calculateAverageLifetime(List<BrokerSession> sessions) {
        return sessions.stream()
            .filter(session -> session.getCreatedAt() != null)
            .mapToLong(this::calculateSessionAge)
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate session age in minutes
     */
    private long calculateSessionAge(BrokerSession session) {
        return Duration.between(session.getCreatedAt(), LocalDateTime.now()).toMinutes();
    }

    /**
     * Calculate refresh percentage
     */
    private double calculateRefreshPercentage(long active, long needsRefresh) {
        return active > 0 ? (needsRefresh * 100.0) / active : 0.0;
    }

    /**
     * Determine overall health status based on metrics
     *
     * MANDATORY: Rule #14 - Pattern matching for conditionals
     */
    private HealthStatus determineHealthStatus(SessionMetrics metrics) {
        return switch ((int) metrics.refreshPercentage()) {
            case int pct when pct >= CRITICAL_REFRESH_THRESHOLD -> HealthStatus.CRITICAL;
            case int pct when pct >= WARNING_REFRESH_THRESHOLD -> HealthStatus.WARNING;
            case int pct when metrics.activeCount() == 0 -> HealthStatus.NO_SESSIONS;
            default -> HealthStatus.HEALTHY;
        };
    }

    /**
     * Build health status with appropriate Spring Boot Health status
     */
    private Health buildHealthStatus(HealthStatus status, Map<String, Object> details) {
        return switch (status) {
            case HEALTHY -> Health.up().withDetails(details).build();
            case WARNING -> Health.up().withDetail("warning", "High refresh rate").withDetails(details).build();
            case CRITICAL -> Health.down().withDetail("reason", "Critical refresh threshold exceeded").withDetails(details).build();
            case NO_SESSIONS -> Health.up().withDetails(details).build();
        };
    }

    /**
     * Build details map for health response
     *
     * MANDATORY: Rule #9 - Immutable collections
     */
    private Map<String, Object> buildDetailsMap(SessionMetrics metrics) {
        return Map.of(
            "totalSessions", metrics.totalCount(),
            "activeSessions", metrics.activeCount(),
            "sessionsNeedingRefresh", metrics.needsRefreshCount(),
            "expiredSessions", metrics.expiredCount(),
            "refreshPercentage", String.format("%.2f%%", metrics.refreshPercentage()),
            "averageLifetimeMinutes", String.format("%.2f", metrics.avgLifetimeMinutes())
        );
    }

    /**
     * Handle health check errors
     */
    private Health handleHealthCheckError(Throwable error) {
        log.error("Health check timeout or error", error);
        return Health.down()
            .withDetail("error", "Health check timeout")
            .withDetail("message", error.getMessage())
            .build();
    }

    /**
     * Session metrics record
     *
     * MANDATORY: Rule #9 - Records for DTOs
     */
    private record SessionMetrics(
        long totalCount,
        long activeCount,
        long needsRefreshCount,
        long expiredCount,
        double avgLifetimeMinutes,
        double refreshPercentage
    ) {}

    /**
     * Health status enum for pattern matching
     */
    private enum HealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL,
        NO_SESSIONS
    }
}
