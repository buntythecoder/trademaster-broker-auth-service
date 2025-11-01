package com.trademaster.brokerauth.dto;

import com.trademaster.brokerauth.enums.BrokerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Session Validation Result DTO
 *
 * Immutable response record for internal session validation.
 *
 * MANDATORY: Records Usage - Rule #9
 * MANDATORY: Immutability - Rule #9
 *
 * @param valid Whether the session is valid and active
 * @param sessionId The session identifier
 * @param userId The user identifier
 * @param brokerType The broker type
 * @param expiresAt Session expiration timestamp
 * @param message Validation message
 */
@Schema(description = "Session validation result for internal API")
public record SessionValidationResult(
    @Schema(description = "Whether the session is valid", example = "true")
    boolean valid,

    @Schema(description = "Session identifier", example = "sess_123456")
    String sessionId,

    @Schema(description = "User identifier", example = "user_789")
    String userId,

    @Schema(description = "Broker type", example = "ZERODHA")
    BrokerType brokerType,

    @Schema(description = "Session expiration time")
    LocalDateTime expiresAt,

    @Schema(description = "Validation message", example = "Session valid")
    String message
) {
    /**
     * Compact constructor for validation - Rule #9
     */
    public SessionValidationResult {
        java.util.Objects.requireNonNull(sessionId, "Session ID cannot be null");
        java.util.Objects.requireNonNull(userId, "User ID cannot be null");
        java.util.Objects.requireNonNull(brokerType, "Broker type cannot be null");
        java.util.Objects.requireNonNull(expiresAt, "Expires at cannot be null");
        java.util.Objects.requireNonNull(message, "Message cannot be null");
    }

    /**
     * Factory method for invalid session - Rule #3: Functional pattern
     */
    public static SessionValidationResult invalid(String sessionId, String message) {
        return new SessionValidationResult(
            false,
            sessionId,
            "unknown",
            BrokerType.ZERODHA, // Default broker type for invalid sessions
            LocalDateTime.now(),
            message
        );
    }

    /**
     * Factory method for valid session - Rule #3: Functional pattern
     */
    public static SessionValidationResult valid(
            String sessionId,
            String userId,
            BrokerType brokerType,
            LocalDateTime expiresAt) {
        return new SessionValidationResult(
            true,
            sessionId,
            userId,
            brokerType,
            expiresAt,
            "Session valid"
        );
    }
}
