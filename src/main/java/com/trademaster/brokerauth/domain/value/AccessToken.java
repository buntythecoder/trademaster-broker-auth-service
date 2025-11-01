package com.trademaster.brokerauth.domain.value;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * AccessToken Value Object - Immutable Access Token with Expiry
 *
 * MANDATORY: Value Object Pattern - Rule #9
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Functional Programming - Rule #3
 *
 * This value object encapsulates an access token with expiration tracking
 * and provides functional operations. Immutable and self-validating.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
public record AccessToken(
    String token,
    LocalDateTime expiresAt
) {

    /**
     * Compact constructor with validation - Rule #9
     */
    public AccessToken {
        // Defensive validation (fail-fast)
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be null or blank");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("Token expiry time cannot be null");
        }
    }

    /**
     * Create access token from string with expiry
     *
     * @param token Token value
     * @param expiresAt Expiry timestamp
     * @return New AccessToken
     */
    public static AccessToken of(String token, LocalDateTime expiresAt) {
        return new AccessToken(token, expiresAt);
    }

    /**
     * Create access token with duration from now
     *
     * @param token Token value
     * @param durationMinutes Duration in minutes
     * @return New AccessToken
     */
    public static AccessToken withDuration(String token, long durationMinutes) {
        return new AccessToken(token, LocalDateTime.now().plusMinutes(durationMinutes));
    }

    /**
     * Check if token is currently valid (not expired)
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if token is valid
     */
    public boolean isValid() {
        return Optional.ofNullable(expiresAt)
            .map(expires -> expires.isAfter(LocalDateTime.now()))
            .orElse(false);
    }

    /**
     * Check if token is expired
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if token is expired
     */
    public boolean isExpired() {
        return !isValid();
    }

    /**
     * Check if token expires within threshold minutes
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param thresholdMinutes Minutes before expiry
     * @return true if token expires within threshold
     */
    public boolean expiresWithin(long thresholdMinutes) {
        return Optional.ofNullable(expiresAt)
            .map(expires -> expires.isBefore(LocalDateTime.now().plusMinutes(thresholdMinutes)))
            .orElse(true);
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
     * Renew token with new expiry time
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param newExpiresAt New expiry timestamp
     * @return New AccessToken with updated expiry
     */
    public AccessToken renew(LocalDateTime newExpiresAt) {
        return new AccessToken(token, newExpiresAt);
    }

    /**
     * Extend token expiry by duration
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param durationMinutes Duration to extend in minutes
     * @return New AccessToken with extended expiry
     */
    public AccessToken extend(long durationMinutes) {
        return new AccessToken(token, expiresAt.plusMinutes(durationMinutes));
    }

    /**
     * Get masked token for logging (show only last 8 chars)
     *
     * MANDATORY: Rule #23 - Security (never log full tokens)
     *
     * @return Masked token string
     */
    public String getMaskedToken() {
        return Optional.ofNullable(token)
            .filter(t -> t.length() > 8)
            .map(t -> "..." + t.substring(t.length() - 8))
            .orElse("***");
    }

    /**
     * Get string representation (masked for security)
     *
     * @return Masked token value
     */
    @Override
    public String toString() {
        return getMaskedToken();
    }
}
