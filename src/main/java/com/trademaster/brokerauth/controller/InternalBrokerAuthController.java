package com.trademaster.brokerauth.controller;

import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.service.BrokerAuthenticationService;
import com.trademaster.brokerauth.service.BrokerSessionService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Internal Broker Auth API Controller - Service-to-Service Communication
 * 
 * Provides internal endpoints for service-to-service communication using Kong API key authentication.
 * These endpoints are used by other TradeMaster services to access broker authentication data.
 * 
 * Security:
 * - Kong API key authentication required via ServiceApiKeyFilter
 * - Role-based access control (ROLE_SERVICE, ROLE_INTERNAL)
 * - Internal network access only
 * - Audit logging for all operations
 * 
 * Service-to-Service Use Cases:
 * - Trading Service: Validate broker sessions for order execution
 * - Portfolio Service: Get account information for position tracking
 * - Risk Service: Access session data for risk calculations
 * - Notification Service: Get session status for alerts
 * 
 * @author TradeMaster Development Team
 * @version 2.0.0 (Kong Integration)
 */
@RestController
@RequestMapping("/api/internal/v1/broker-auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Broker Auth API", description = "Service-to-service broker authentication operations")
@SecurityRequirement(name = "API Key Authentication")
public class InternalBrokerAuthController {

    // ✅ INTERNAL ACCESS: Direct service injection (lightweight) - Golden Spec Pattern
    private final BrokerSessionService sessionService;
    private final BrokerAuthenticationService authService;

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
     */
    @GetMapping("/sessions/{sessionId}/validate")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Validate broker session",
        description = "Validates if a broker session is active and valid for trading operations"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session validation result"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<Map<String, Object>> validateSession(
            @Parameter(description = "Session ID to validate", required = true)
            @PathVariable String sessionId) {
        
        log.info("Internal session validation request for sessionId: {}", sessionId);
        
        Optional<BrokerSession> sessionOpt = sessionService.getSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        BrokerSession session = sessionOpt.get();
        boolean isValid = session.getStatus() == SessionStatus.ACTIVE && 
                         session.getExpiresAt().isAfter(LocalDateTime.now());
        
        Map<String, Object> response = Map.of(
            "sessionId", sessionId,
            "valid", isValid,
            "status", session.getStatus().name(),
            "brokerType", session.getBrokerType().name(),
            "userId", session.getUserId(),
            "expiresAt", session.getExpiresAt(),
            "lastAccessAt", session.getLastAccessedAt()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get user's active broker sessions
     * Used by portfolio-service to get account information
     */
    @GetMapping("/users/{userId}/sessions")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Get user's broker sessions",
        description = "Retrieves all active broker sessions for a specific user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User sessions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
    })
    public ResponseEntity<List<Map<String, Object>>> getUserSessions(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId) {
        
        log.info("Internal request for user sessions: userId={}", userId);
        
        List<BrokerSession> sessions = sessionService.getUserActiveSessions(userId);
        
        List<Map<String, Object>> response = sessions.stream()
            .map(session -> Map.<String, Object>of(
                "sessionId", session.getSessionId(),
                "brokerType", session.getBrokerType().name(),
                "status", session.getStatus().name(),
                "createdAt", session.getCreatedAt(),
                "expiresAt", session.getExpiresAt(),
                "lastAccessAt", session.getLastAccessedAt(),
                "metadata", session.getMetadata() != null ? session.getMetadata() : ""
            ))
            .toList();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get broker session details
     * Used by risk-service for risk calculations
     */
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Get broker session details",
        description = "Retrieves detailed information about a specific broker session"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session details retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<Map<String, Object>> getSessionDetails(
            @Parameter(description = "Session ID", required = true)
            @PathVariable String sessionId) {
        
        log.info("Internal request for session details: sessionId={}", sessionId);
        
        Optional<BrokerSession> sessionOpt = sessionService.getSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        BrokerSession session = sessionOpt.get();
        
        Map<String, Object> response = Map.of(
            "sessionId", session.getSessionId(),
            "userId", session.getUserId(),
            "brokerType", session.getBrokerType().name(),
            "status", session.getStatus().name(),
            "createdAt", session.getCreatedAt(),
            "expiresAt", session.getExpiresAt(),
            "lastAccessAt", session.getLastAccessedAt(),
            "metadata", session.getMetadata() != null ? session.getMetadata() : "",
            "isExpired", session.getExpiresAt().isBefore(LocalDateTime.now())
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update session last access time
     * Used by trading-service to keep sessions active during trading
     */
    @PutMapping("/sessions/{sessionId}/touch")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
        summary = "Update session last access time",
        description = "Updates the last access time of a session to keep it active"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session touched successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<Map<String, Object>> touchSession(
            @Parameter(description = "Session ID", required = true)
            @PathVariable String sessionId) {
        
        log.info("Internal request to touch session: sessionId={}", sessionId);
        
        boolean updated = sessionService.updateLastAccess(sessionId);
        
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = Map.of(
            "sessionId", sessionId,
            "lastAccessAt", LocalDateTime.now(),
            "status", "updated"
        );
        
        return ResponseEntity.ok(response);
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
}