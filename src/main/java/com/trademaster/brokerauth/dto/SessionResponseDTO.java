package com.trademaster.brokerauth.dto;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Session Response DTO - API Response Model
 *
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Clean Architecture - Separation of domain and API layers
 *
 * This DTO represents broker session information in API responses,
 * decoupling the API contract from domain models and persistence entities.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Schema(
    description = "Broker session information including authentication status, tokens, and expiration details",
    example = """
        {
          "id": 1,
          "sessionId": "550e8400-e29b-41d4-a716-446655440000",
          "userId": "USER123",
          "brokerType": "ZERODHA",
          "status": "ACTIVE",
          "createdAt": "2024-01-15T10:00:00",
          "expiresAt": "2024-01-15T14:30:00",
          "lastAccessedAt": "2024-01-15T12:15:00"
        }
        """
)
public record SessionResponseDTO(
    @Schema(description = "Database ID of the session", example = "1")
    Long id,

    @Schema(description = "Unique session identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    String sessionId,

    @Schema(description = "User ID owning this session", example = "USER123")
    String userId,

    @Schema(description = "Broker type for this session", example = "ZERODHA")
    BrokerType brokerType,

    @Schema(description = "Current session status", example = "ACTIVE")
    SessionStatus status,

    @Schema(description = "Session creation timestamp", example = "2024-01-15T10:00:00")
    LocalDateTime createdAt,

    @Schema(description = "Session expiration timestamp", example = "2024-01-15T14:30:00")
    LocalDateTime expiresAt,

    @Schema(description = "Last access timestamp", example = "2024-01-15T12:15:00")
    LocalDateTime lastAccessedAt,

    @Schema(description = "Session metadata (JSON)", example = "{\"loginMethod\": \"oauth\", \"ipAddress\": \"192.168.1.1\"}")
    String metadata,

    @Schema(description = "Vault path for secure credential storage", example = "secret/data/trademaster/broker-sessions/USER123/zerodha")
    String vaultPath
) {
    /**
     * Compact constructor with validation
     *
     * MANDATORY: Rule #9 - Immutability with validation
     */
    public SessionResponseDTO {
        // Defensive validation (fail-fast)
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be null or blank");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }

        if (brokerType == null) {
            throw new IllegalArgumentException("Broker type cannot be null");
        }

        if (status == null) {
            throw new IllegalArgumentException("Session status cannot be null");
        }
    }
}
