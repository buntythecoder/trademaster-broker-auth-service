package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.service.broker.BrokerApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Session Refresh Service
 *
 * Automatic token refresh for expiring sessions.
 *
 * MANDATORY: Rule #12 - Virtual Threads for async operations
 * MANDATORY: Rule #3 - Functional Programming patterns
 * MANDATORY: Task 2.2 - Session Management Enhancement
 *
 * Features:
 * - Scheduled refresh checks every 2 minutes
 * - Automatic token refresh for sessions expiring within 5 minutes
 * - Kafka notifications for refresh success/failure
 * - Prometheus metrics for refresh operations
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionRefreshService {

    private final BrokerSessionService sessionService;
    private final List<BrokerApiService> brokerApiServices;
    private final VaultSecretService vaultSecretService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.trademaster.brokerauth.metrics.SessionMetricsService metricsService;

    private static final String TOPIC_SESSION_EVENTS = "session-events";

    /**
     * Scheduled session refresh check - runs every 2 minutes
     *
     * MANDATORY: Rule #12 - Virtual Threads
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void checkAndRefreshSessions() {
        log.info("Starting scheduled session refresh check");

        CompletableFuture.runAsync(
            this::executeRefreshCheck,
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * Public API for manual session refresh (for internal API)
     *
     * MANDATORY: Rule #11 - Railway Programming for error handling
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param sessionId Session to refresh
     * @param refreshToken Refresh token for broker API
     * @return Optional with refreshed session if successful
     */
    public Optional<BrokerSession> refreshSession(String sessionId, String refreshToken) {
        log.info("Manual refresh requested for session: {}", sessionId);

        return sessionService.getSession(sessionId)
            .map(session -> performManualRefresh(session, refreshToken))
            .map(this::handleManualRefreshResult)
            .orElseGet(() -> {
                log.warn("Session not found for manual refresh: {}", sessionId);
                return Optional.empty();
            });
    }

    /**
     * Perform manual refresh operation - Rule #3 Functional
     */
    private RefreshResult performManualRefresh(BrokerSession session, String refreshToken) {
        return findBrokerService(session)
            .map(service -> callBrokerRefreshApi(service, session, refreshToken))
            .orElse(new RefreshResult.Failure(session, "Broker service not found"));
    }

    /**
     * Handle manual refresh result - Rule #14 Pattern Matching
     */
    private Optional<BrokerSession> handleManualRefreshResult(RefreshResult result) {
        return switch (result) {
            case RefreshResult.Success(BrokerSession session) -> {
                publishSuccessEvent(session);
                yield Optional.of(session);
            }
            case RefreshResult.Failure(BrokerSession session, String error) -> {
                publishFailureEvent(session, error);
                log.warn("Manual refresh failed for session: {} error: {}", session.getSessionId(), error);
                yield Optional.empty();
            }
        };
    }

    /**
     * Execute refresh check for all active sessions
     *
     * MANDATORY: Rule #3 - Functional Programming
     */
    private void executeRefreshCheck() {
        switch (getExpiringSessionsResult()) {
            case SessionResult.Success(List<BrokerSession> sessions) ->
                processExpiringSessions(sessions);
            case SessionResult.Failure(String error) ->
                log.error("Failed to retrieve expiring sessions: {}", error);
        }
    }

    /**
     * Functional result type for session retrieval
     *
     * MANDATORY: Rule #11 - Functional error handling
     */
    private sealed interface SessionResult {
        record Success(List<BrokerSession> sessions) implements SessionResult {}
        record Failure(String error) implements SessionResult {}
    }

    /**
     * Get expiring sessions with functional error handling
     */
    private SessionResult getExpiringSessionsResult() {
        return switch (retrieveExpiringSessions()) {
            case List<BrokerSession> sessions -> new SessionResult.Success(sessions);
            case null -> new SessionResult.Failure("Failed to retrieve sessions");
        };
    }

    /**
     * Retrieve all expiring sessions (expire within 5 minutes)
     */
    private List<BrokerSession> retrieveExpiringSessions() {
        try {
            return sessionService.getUserActiveSessions("*").stream()
                .filter(BrokerSession::needsRefresh)
                .filter(BrokerSession::isActive)
                .toList();
        } catch (Exception e) {
            log.error("Error retrieving expiring sessions", e);
            return null;
        }
    }

    /**
     * Process expiring sessions for refresh
     *
     * MANDATORY: Rule #3 - Functional Programming with streams
     */
    private void processExpiringSessions(List<BrokerSession> sessions) {
        log.info("Found {} sessions needing refresh", sessions.size());

        sessions.stream()
            .map(this::refreshSessionAsync)
            .forEach(future -> future.thenAccept(this::handleRefreshResult));
    }

    /**
     * Refresh single session asynchronously
     *
     * MANDATORY: Rule #12 - Virtual Threads
     */
    private CompletableFuture<RefreshResult> refreshSessionAsync(BrokerSession session) {
        return CompletableFuture.supplyAsync(
            () -> performSessionRefresh(session),
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * Perform actual session refresh
     *
     * MANDATORY: Rule #3 - Functional Programming
     */
    private RefreshResult performSessionRefresh(BrokerSession session) {
        log.debug("Refreshing session: {} for user: {}", session.getSessionId(), session.getUserId());

        return switch (getRefreshToken(session)) {
            case Optional<?> opt when opt.isPresent() ->
                executeTokenRefresh(session, (String) opt.get());
            default ->
                new RefreshResult.Failure(session, "No refresh token found in Vault");
        };
    }

    /**
     * Get refresh token from Vault
     */
    private Optional<String> getRefreshToken(BrokerSession session) {
        return vaultSecretService
            .getSecret(session.getVaultPath(), "refresh_token")
            .join();
    }

    /**
     * Execute token refresh with broker API
     */
    private RefreshResult executeTokenRefresh(BrokerSession session, String refreshToken) {
        return findBrokerService(session)
            .map(service -> callBrokerRefreshApi(service, session, refreshToken))
            .orElse(new RefreshResult.Failure(session, "Broker service not found"));
    }

    /**
     * Find appropriate broker API service
     *
     * MANDATORY: Rule #3 - Functional Programming
     */
    private Optional<BrokerApiService> findBrokerService(BrokerSession session) {
        return brokerApiServices.stream()
            .filter(service -> service.supports(session.getBrokerType()))
            .findFirst();
    }

    /**
     * Call broker refresh API and handle result
     */
    private RefreshResult callBrokerRefreshApi(
            BrokerApiService service,
            BrokerSession session,
            String refreshToken) {
        try {
            AuthResponse response = service.refreshToken(refreshToken).join();

            return response.success()
                ? updateSessionWithNewToken(session, response)
                : new RefreshResult.Failure(session, response.message());

        } catch (Exception e) {
            log.error("Token refresh failed for session: {}", session.getSessionId(), e);
            return new RefreshResult.Failure(session, e.getMessage());
        }
    }

    /**
     * Update session with new token and expiry
     *
     * MANDATORY: Rule #9 - Immutability
     */
    private RefreshResult updateSessionWithNewToken(BrokerSession session, AuthResponse response) {
        BrokerSession updatedSession = session
            .withExpiresAt(response.expiresAt())
            .withLastAccessed(LocalDateTime.now());

        sessionService.saveSession(updatedSession);

        // Update tokens in Vault
        vaultSecretService.storeSecret(
            session.getVaultPath(),
            "access_token",
            response.accessToken()
        );

        if (response.refreshToken() != null) {
            vaultSecretService.storeSecret(
                session.getVaultPath(),
                "refresh_token",
                response.refreshToken()
            );
        }

        log.info("Successfully refreshed session: {} for user: {}",
            session.getSessionId(), session.getUserId());

        return new RefreshResult.Success(updatedSession);
    }

    /**
     * Handle refresh result and publish events
     */
    private void handleRefreshResult(RefreshResult result) {
        switch (result) {
            case RefreshResult.Success(BrokerSession session) ->
                publishSuccessEvent(session);
            case RefreshResult.Failure(BrokerSession session, String error) ->
                publishFailureEvent(session, error);
        }
    }

    /**
     * Publish success event to Kafka and record metrics
     */
    private void publishSuccessEvent(BrokerSession session) {
        SessionEvent event = new SessionEvent(
            session.getSessionId(),
            session.getUserId(),
            session.getBrokerType().name(),
            "REFRESH_SUCCESS",
            LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC_SESSION_EVENTS, event);
        metricsService.recordRefreshSuccess(session.getBrokerType());
        log.debug("Published refresh success event for session: {}", session.getSessionId());
    }

    /**
     * Publish failure event to Kafka and record metrics
     */
    private void publishFailureEvent(BrokerSession session, String error) {
        SessionEvent event = new SessionEvent(
            session.getSessionId(),
            session.getUserId(),
            session.getBrokerType().name(),
            "REFRESH_FAILURE: " + error,
            LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC_SESSION_EVENTS, event);
        metricsService.recordRefreshFailure(session.getBrokerType(), error);
        metricsService.recordSessionExpired(session.getBrokerType());
        log.warn("Published refresh failure event for session: {}", session.getSessionId());

        // Mark session as EXPIRED if refresh failed
        BrokerSession expiredSession = session.withStatus(SessionStatus.EXPIRED);
        sessionService.saveSession(expiredSession);
    }

    /**
     * Refresh result sealed interface
     *
     * MANDATORY: Rule #11 - Functional error handling
     */
    private sealed interface RefreshResult {
        record Success(BrokerSession session) implements RefreshResult {}
        record Failure(BrokerSession session, String error) implements RefreshResult {}
    }

    /**
     * Session event record for Kafka
     *
     * MANDATORY: Rule #9 - Records for DTOs
     */
    private record SessionEvent(
        String sessionId,
        String userId,
        String brokerType,
        String eventType,
        LocalDateTime timestamp
    ) {}
}
