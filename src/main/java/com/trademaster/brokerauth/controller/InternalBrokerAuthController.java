package com.trademaster.brokerauth.controller;

import com.trademaster.brokerauth.dto.InternalSessionResponse;
import com.trademaster.brokerauth.dto.SessionRefreshRequest;
import com.trademaster.brokerauth.dto.SessionValidationResult;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.service.BrokerSessionService;
import com.trademaster.brokerauth.service.SessionRefreshService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Internal Broker Auth API Controller - Service-to-Service Communication
 *
 * Provides internal endpoints for service-to-service communication using Kong API key authentication.
 * These endpoints are used by other TradeMaster services to access broker authentication data.
 *
 * MANDATORY: Zero Trust (Internal) - Rule #6 - Simple injection pattern
 * MANDATORY: Functional Programming - Rule #3 - No if-else, no loops
 * MANDATORY: Virtual Threads - Rule #1, #12 - CompletableFuture with virtual threads
 * MANDATORY: Records Usage - Rule #9 - DTOs as Records
 * MANDATORY: OpenAPI Documentation - Golden Spec Section 7
 *
 * Security:
 * - Kong API key authentication required via @PreAuthorize
 * - Role-based access control (ROLE_SERVICE)
 * - Internal network access only
 * - Audit logging for all operations
 *
 * Service-to-Service Use Cases:
 * - Trading Service: Validate broker sessions for order execution
 * - Portfolio Service: Get account information for position tracking
 * - Risk Service: Access session data for risk calculations
 * - Notification Service: Get session status for alerts
 *
 * SLA Targets:
 * - Session validation: ≤25ms (cached)
 * - Session refresh: ≤50ms (high priority)
 * - User sessions: ≤100ms (standard)
 * - Health check: ≤10ms (critical)
 *
 * @author TradeMaster Development Team
 * @version 3.0.0 (Functional Programming + Virtual Threads)
 */
@RestController
@RequestMapping("/api/internal/v1/broker-auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Broker Auth API", description = "Service-to-service broker authentication operations")
@SecurityRequirement(name = "API Key Authentication")
public class InternalBrokerAuthController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    // ✅ INTERNAL ACCESS: Direct service injection (lightweight) - Golden Spec Pattern
    private final BrokerSessionService sessionService;
    private final SessionRefreshService refreshService;

    /**
     * Health check for internal services (no authentication required)
     * Available at: /api/internal/v1/broker-auth/health
     */
    @GetMapping("/health")
    @Hidden // Hide from public OpenAPI documentation
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "service", "broker-auth-service",
            "status", "UP",
            "internal_api", "available",
            "timestamp", LocalDateTime.now(),
            "version", "1.0.0",
            "authentication", "service-api-key-enabled",
            "capabilities", List.of("session-validation", "broker-integration", "credential-management")
        ));
    }

    /**
     * Internal greeting endpoint for API key connectivity testing
     * Used to validate Kong API key authentication is working correctly
     */
    @GetMapping("/greeting")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Test API key connectivity",
        description = "Simple greeting endpoint to validate Kong API key authentication is working correctly"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "API key authentication successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
    })
    public Map<String, Object> getGreeting() {
        log.info("Internal greeting endpoint accessed - API key authentication successful");
        
        return Map.of(
            "message", "Hello from Broker Auth Service Internal API!",
            "timestamp", LocalDateTime.now(),
            "service", "broker-auth-service",
            "authenticated", true,
            "role", "SERVICE",
            "kong_integration", "working"
        );
    }

    /**
     * Internal status with authentication required
     * Used by other services to verify broker-auth-service availability
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Get internal service status",
        description = "Returns detailed status information for service-to-service monitoring"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service status retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
    })
    public Map<String, Object> getStatus() {
        log.info("Internal status endpoint accessed by service");
        
        return Map.of(
            "status", "UP",
            "service", "broker-auth-service",
            "timestamp", LocalDateTime.now(),
            "authenticated", true,
            "message", "Broker Auth service is running and authenticated",
            "active_sessions", sessionService.getActiveSessionCount(),
            "supported_brokers", List.of("ZERODHA", "UPSTOX", "ANGEL_ONE", "ICICI_DIRECT")
        );
    }

    /**
     * Validate broker session for service-to-service calls
     * Used by trading-service before executing orders
     *
     * SLA: ≤25ms (cached sessions)
     * Rule #3: Functional pattern matching, no if-else
     * Rule #12: Virtual threads for async execution
     */
    @GetMapping("/sessions/{sessionId}/validate")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Validate broker session",
        description = "Validates if a broker session is active and valid for trading operations. SLA: ≤25ms"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session validation result"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public CompletableFuture<ResponseEntity<SessionValidationResult>> validateSession(
            @Parameter(description = "Session ID to validate", required = true)
            @PathVariable String sessionId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId) {

        return executeWithCorrelation(
            correlationId,
            () -> processSessionValidation(sessionId),
            result -> convertToValidationResponse(result)
        );
    }

    /**
     * Get user's active broker sessions
     * Used by portfolio-service to get account information
     *
     * SLA: ≤100ms (standard priority)
     * Rule #13: Stream API for collection processing
     * Rule #12: Virtual threads for async execution
     */
    @GetMapping("/users/{userId}/sessions")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Get user's broker sessions",
        description = "Retrieves all active broker sessions for a specific user. SLA: ≤100ms"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User sessions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
    })
    public CompletableFuture<ResponseEntity<List<InternalSessionResponse>>> getUserSessions(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId) {

        return executeWithCorrelation(
            correlationId,
            () -> retrieveUserSessions(userId),
            sessions -> ResponseEntity.ok(convertSessionsToResponse(sessions))
        );
    }

    /**
     * Get broker session details
     * Used by risk-service for risk calculations
     *
     * SLA: ≤100ms (standard priority)
     * Rule #3: Functional pattern matching
     * Rule #12: Virtual threads for async execution
     */
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Get broker session details",
        description = "Retrieves detailed information about a specific broker session. SLA: ≤100ms"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session details retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public CompletableFuture<ResponseEntity<InternalSessionResponse>> getSessionDetails(
            @Parameter(description = "Session ID", required = true)
            @PathVariable String sessionId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId) {

        return executeWithCorrelation(
            correlationId,
            () -> processSessionValidation(sessionId),
            result -> convertToSessionResponse(result)
        );
    }

    /**
     * Update session last access time
     * Used by trading-service to keep sessions active during trading
     *
     * SLA: ≤50ms (high priority)
     * Rule #3: Functional boolean handling
     * Rule #12: Virtual threads for async execution
     */
    @PutMapping("/sessions/{sessionId}/touch")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Update session last access time",
        description = "Updates the last access time of a session to keep it active. SLA: ≤50ms"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Session touched successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public CompletableFuture<ResponseEntity<Void>> touchSession(
            @Parameter(description = "Session ID", required = true)
            @PathVariable String sessionId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId) {

        return executeWithCorrelation(
            correlationId,
            () -> processSessionTouch(sessionId),
            result -> convertBooleanToResponse(result)
        );
    }

    /**
     * Revoke broker session
     *
     * SLA: ≤100ms (standard priority)
     * Rule #3: Functional void operation handling
     * Rule #12: Virtual threads for async execution
     */
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Revoke session",
        description = "Internal endpoint for session revocation. SLA: ≤100ms"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Session revoked successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key")
    })
    public CompletableFuture<ResponseEntity<Void>> revokeSession(
            @Parameter(description = "Session ID to revoke")
            @PathVariable String sessionId,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId) {

        return executeWithCorrelation(
            correlationId,
            () -> processSessionRevocation(sessionId),
            result -> ResponseEntity.noContent().<Void>build()
        );
    }

    /**
     * Refresh broker session token
     * Used by services to refresh expiring broker sessions
     *
     * SLA: ≤50ms (high priority)
     * Rule #3: Functional pattern with Optional
     * Rule #12: Virtual threads for async execution
     */
    @PostMapping("/sessions/{sessionId}/refresh")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Refresh broker session",
        description = "Refreshes an expiring broker session token. SLA: ≤50ms"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found"),
        @ApiResponse(responseCode = "400", description = "Refresh failed - Invalid token")
    })
    public CompletableFuture<ResponseEntity<InternalSessionResponse>> refreshSession(
            @Parameter(description = "Session ID to refresh", required = true)
            @PathVariable String sessionId,
            @Valid @RequestBody SessionRefreshRequest request,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationId) {

        return executeWithCorrelation(
            correlationId,
            () -> processSessionRefresh(sessionId, request),
            session -> convertToSessionResponse(session)
        );
    }

    /**
     * Get service statistics
     * Used by monitoring services for health dashboards
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Get service statistics",
        description = "Returns statistical information about broker authentication service"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key")
    })
    public Map<String, Object> getStatistics() {
        log.info("Internal statistics request");

        return Map.of(
            "active_sessions", sessionService.getActiveSessionCount(),
            "total_sessions_today", sessionService.getTodaySessionCount(),
            "supported_brokers", List.of("ZERODHA", "UPSTOX", "ANGEL_ONE", "ICICI_DIRECT"),
            "broker_status", Map.of(
                "ZERODHA", "OPERATIONAL",
                "UPSTOX", "OPERATIONAL",
                "ANGEL_ONE", "OPERATIONAL",
                "ICICI_DIRECT", "OPERATIONAL"
            ),
            "timestamp", LocalDateTime.now()
        );
    }

    // ========== Functional Helper Methods - Rule #3 ==========

    /**
     * Functional session validation pipeline - Rule #3
     */
    private Optional<BrokerSession> processSessionValidation(String sessionId) {
        log.debug("Processing session validation: {}", sessionId);
        return sessionService.getSession(sessionId);
    }

    /**
     * Functional user sessions retrieval - Rule #13: Stream API
     */
    private List<BrokerSession> retrieveUserSessions(String userId) {
        log.debug("Retrieving user sessions: {}", userId);
        return sessionService.getUserActiveSessions(userId);
    }

    /**
     * Functional session touch operation - Rule #3
     */
    private Boolean processSessionTouch(String sessionId) {
        log.debug("Processing session touch: {}", sessionId);
        return sessionService.updateLastAccess(sessionId);
    }

    /**
     * Functional session revocation - Rule #3: Functional void handling
     */
    private Void processSessionRevocation(String sessionId) {
        log.info("Processing session revocation: {}", sessionId);
        sessionService.revokeSession(sessionId);
        return null;
    }

    /**
     * Functional session refresh - Rule #3: Functional with Optional
     */
    private Optional<BrokerSession> processSessionRefresh(String sessionId, SessionRefreshRequest request) {
        log.debug("Processing session refresh: {}", sessionId);
        return refreshService.refreshSession(sessionId, request.refreshToken());
    }

    // ========== Response Converters - Rule #14: Pattern Matching ==========

    /**
     * Functional validation response converter - Rule #14: Pattern matching
     */
    private ResponseEntity<SessionValidationResult> convertToValidationResponse(Optional<BrokerSession> sessionOpt) {
        return sessionOpt
            .map(this::createValidationSuccess)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Functional session response converter - Rule #14: Pattern matching
     */
    private ResponseEntity<InternalSessionResponse> convertToSessionResponse(Optional<BrokerSession> sessionOpt) {
        return sessionOpt
            .map(this::createSessionResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Functional sessions list converter - Rule #13: Stream API
     */
    private List<InternalSessionResponse> convertSessionsToResponse(List<BrokerSession> sessions) {
        return sessions.stream()
            .map(this::createSessionResponse)
            .toList();
    }

    /**
     * Functional boolean to response converter - Rule #3
     */
    private ResponseEntity<Void> convertBooleanToResponse(Boolean updated) {
        return updated
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    // ========== Response Builders - Rule #9: Records ==========

    /**
     * Functional validation result builder - Rule #9: Records
     */
    private SessionValidationResult createValidationSuccess(BrokerSession session) {
        return SessionValidationResult.valid(
            session.getSessionId(),
            session.getUserId(),
            session.getBrokerType(),
            session.getExpiresAt()
        );
    }

    /**
     * Functional session response builder - Rule #9: Records
     */
    private InternalSessionResponse createSessionResponse(BrokerSession session) {
        return new InternalSessionResponse(
            session.getSessionId(),
            session.getUserId(),
            session.getBrokerType(),
            session.getStatus(),
            session.getCreatedAt(),
            session.getExpiresAt(),
            session.getLastAccessedAt()
        );
    }

    // ========== Correlation Wrapper - Rule #12: Virtual Threads ==========

    /**
     * Functional correlation wrapper - Rule #12: Virtual Threads
     *
     * Executes operation with correlation ID logging and virtual thread executor.
     * Uses CompletableFuture for async execution with virtual threads.
     */
    private <T, R> CompletableFuture<R> executeWithCorrelation(
            String correlationId,
            java.util.function.Supplier<T> operation,
            Function<T, R> responseMapper) {

        return CompletableFuture
            .supplyAsync(() -> {
                Optional.ofNullable(correlationId)
                    .ifPresent(id -> log.debug("Correlation ID: {}", id));
                return operation.get();
            }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
            .thenApply(responseMapper)
            .exceptionally(throwable -> {
                log.error("Internal operation failed: correlation={}, error={}",
                    correlationId, throwable.getMessage());
                throw new RuntimeException("Internal operation failed", throwable);
            });
    }
}