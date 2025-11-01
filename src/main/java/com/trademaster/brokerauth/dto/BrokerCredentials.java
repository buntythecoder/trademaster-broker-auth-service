package com.trademaster.brokerauth.dto;

import com.trademaster.brokerauth.enums.BrokerType;

import java.time.LocalDateTime;

/**
 * Broker Credentials DTO
 *
 * Contains decrypted OAuth credentials for trading operations.
 * This is the payload returned to trading-service for broker API calls.
 *
 * MANDATORY: Immutable Records - Rule #9
 * MANDATORY: Security by Default - Rule #6
 *
 * @param sessionId Unique session identifier
 * @param userId User identifier
 * @param brokerType Broker type (ZERODHA, UPSTOX, etc.)
 * @param accessToken Decrypted OAuth access token
 * @param refreshToken Decrypted OAuth refresh token (may be null)
 * @param expiresAt Token expiration timestamp
 * @param createdAt Session creation timestamp
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
public record BrokerCredentials(
    String sessionId,
    String userId,
    BrokerType brokerType,
    String accessToken,
    String refreshToken,
    LocalDateTime expiresAt,
    LocalDateTime createdAt
) {
    /**
     * Compact constructor with validation
     *
     * MANDATORY: Input validation - Rule #23
     */
    public BrokerCredentials {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        if (brokerType == null) {
            throw new IllegalArgumentException("Broker type cannot be null");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be null or blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiration timestamp cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp cannot be null");
        }
    }

    /**
     * Check if credentials are expired
     *
     * MANDATORY: Functional programming - Rule #3
     *
     * @return true if credentials are expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Check if credentials need refresh (expires within 5 minutes)
     *
     * MANDATORY: Functional programming - Rule #3
     *
     * @return true if credentials need refresh, false otherwise
     */
    public boolean needsRefresh() {
        return expiresAt.isBefore(LocalDateTime.now().plusMinutes(5));
    }

    /**
     * Check if refresh token is available
     *
     * MANDATORY: Functional programming - Rule #3
     *
     * @return true if refresh token exists, false otherwise
     */
    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }
}
