package com.trademaster.brokerauth.domain.value;

import com.trademaster.brokerauth.enums.BrokerType;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * BrokerCredentials Value Object - Immutable Broker Authentication Credentials
 *
 * MANDATORY: Value Object Pattern - Rule #9
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Security - Rule #23 (encrypted storage)
 *
 * This value object encapsulates broker authentication credentials
 * including access token, refresh token, and metadata. Immutable and
 * self-validating with security considerations.
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
     * Compact constructor with validation - Rule #9
     */
    public BrokerCredentials {
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

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be null or blank");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiry timestamp cannot be null");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }
    }

    /**
     * Create credentials from components
     *
     * @param sessionId Session identifier
     * @param userId User identifier
     * @param brokerType Broker type
     * @param accessToken Access token
     * @param refreshToken Refresh token (optional)
     * @param expiresAt Expiry timestamp
     * @param createdAt Created timestamp
     * @return New BrokerCredentials
     */
    public static BrokerCredentials of(
            String sessionId,
            String userId,
            BrokerType brokerType,
            String accessToken,
            String refreshToken,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new BrokerCredentials(sessionId, userId, brokerType, accessToken, refreshToken, expiresAt, createdAt);
    }

    /**
     * Check if credentials are currently valid (not expired)
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if credentials are valid
     */
    public boolean isValid() {
        return Optional.ofNullable(expiresAt)
            .map(expires -> expires.isAfter(LocalDateTime.now()))
            .orElse(false);
    }

    /**
     * Check if credentials are expired
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if credentials are expired
     */
    public boolean isExpired() {
        return !isValid();
    }

    /**
     * Check if refresh token is available
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if refresh token is present
     */
    public boolean hasRefreshToken() {
        return Optional.ofNullable(refreshToken)
            .filter(token -> !token.isBlank())
            .isPresent();
    }

    /**
     * Check if credentials need refresh (expire within threshold)
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param thresholdMinutes Minutes before expiry to trigger refresh
     * @return true if credentials need refresh
     */
    public boolean needsRefresh(long thresholdMinutes) {
        return Optional.ofNullable(expiresAt)
            .map(expires -> expires.isBefore(LocalDateTime.now().plusMinutes(thresholdMinutes)))
            .orElse(true);
    }

    /**
     * Check if credentials need refresh with default threshold (5 minutes)
     *
     * @return true if credentials need refresh within 5 minutes
     */
    public boolean needsRefresh() {
        return needsRefresh(5);
    }

    /**
     * Get minutes until expiry
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return Optional containing minutes until expiry, or empty if expired
     */
    public Optional<Long> getMinutesUntilExpiry() {
        return Optional.ofNullable(expiresAt)
            .filter(expires -> expires.isAfter(LocalDateTime.now()))
            .map(expires -> java.time.Duration.between(LocalDateTime.now(), expires).toMinutes());
    }

    /**
     * Get age of credentials in hours
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return Hours since credentials were created
     */
    public long getAgeInHours() {
        return Optional.ofNullable(createdAt)
            .map(created -> java.time.Duration.between(created, LocalDateTime.now()).toHours())
            .orElse(0L);
    }

    /**
     * Refresh credentials with new tokens and expiry
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param newAccessToken New access token
     * @param newRefreshToken New refresh token (optional)
     * @param newExpiresAt New expiry timestamp
     * @return New BrokerCredentials with updated tokens
     */
    public BrokerCredentials refresh(String newAccessToken, String newRefreshToken, LocalDateTime newExpiresAt) {
        return new BrokerCredentials(
            sessionId,
            userId,
            brokerType,
            newAccessToken,
            newRefreshToken,
            newExpiresAt,
            LocalDateTime.now() // Update creation time to reflect refresh
        );
    }

    /**
     * Update access token only
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param newAccessToken New access token
     * @return New BrokerCredentials with updated access token
     */
    public BrokerCredentials withAccessToken(String newAccessToken) {
        return new BrokerCredentials(
            sessionId,
            userId,
            brokerType,
            newAccessToken,
            refreshToken,
            expiresAt,
            createdAt
        );
    }

    /**
     * Get masked access token for logging (show only last 8 chars)
     *
     * MANDATORY: Rule #23 - Security (never log full tokens)
     *
     * @return Masked access token
     */
    public String getMaskedAccessToken() {
        return Optional.ofNullable(accessToken)
            .filter(token -> token.length() > 8)
            .map(token -> "..." + token.substring(token.length() - 8))
            .orElse("***");
    }

    /**
     * Get masked refresh token for logging
     *
     * MANDATORY: Rule #23 - Security (never log full tokens)
     *
     * @return Masked refresh token
     */
    public String getMaskedRefreshToken() {
        return Optional.ofNullable(refreshToken)
            .filter(token -> token.length() > 8)
            .map(token -> "..." + token.substring(token.length() - 8))
            .orElse("***");
    }

    /**
     * Get string representation (masked for security)
     *
     * @return Masked credentials representation
     */
    @Override
    public String toString() {
        return String.format("BrokerCredentials[session=%s, user=%s, broker=%s, accessToken=%s, hasRefresh=%s, expiresAt=%s]",
            sessionId, userId, brokerType, getMaskedAccessToken(), hasRefreshToken(), expiresAt);
    }
}
