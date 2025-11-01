package com.trademaster.brokerauth.dto;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Internal Session Response DTO
 *
 * Immutable response record for internal session details.
 * Used by other services for session information.
 *
 * MANDATORY: Records Usage - Rule #9
 * MANDATORY: Immutability - Rule #9
 *
 * @param sessionId The session identifier
 * @param userId The user identifier
 * @param brokerType The broker type
 * @param status The session status
 * @param createdAt Session creation timestamp
 * @param expiresAt Session expiration timestamp
 * @param lastAccessedAt Last access timestamp
 */
@Schema(description = "Internal session details for service-to-service communication")
public record InternalSessionResponse(
    @Schema(description = "Session identifier", example = "sess_123456")
    String sessionId,

    @Schema(description = "User identifier", example = "user_789")
    String userId,

    @Schema(description = "Broker type", example = "ZERODHA")
    BrokerType brokerType,

    @Schema(description = "Session status", example = "ACTIVE")
    SessionStatus status,

    @Schema(description = "Session creation time")
    LocalDateTime createdAt,

    @Schema(description = "Session expiration time")
    LocalDateTime expiresAt,

    @Schema(description = "Last access time")
    LocalDateTime lastAccessedAt
) {
    /**
     * Compact constructor for validation - Rule #9
     */
    public InternalSessionResponse {
        java.util.Objects.requireNonNull(sessionId, "Session ID cannot be null");
        java.util.Objects.requireNonNull(userId, "User ID cannot be null");
        java.util.Objects.requireNonNull(brokerType, "Broker type cannot be null");
        java.util.Objects.requireNonNull(status, "Status cannot be null");
        java.util.Objects.requireNonNull(createdAt, "Created at cannot be null");
        java.util.Objects.requireNonNull(expiresAt, "Expires at cannot be null");
    }

    /**
     * Check if session is expired - Rule #3: Functional predicate
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Check if session is active - Rule #3: Functional predicate
     */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE && !isExpired();
    }
}
