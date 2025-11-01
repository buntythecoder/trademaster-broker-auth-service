package com.trademaster.brokerauth.domain.value;

import java.util.Objects;
import java.util.UUID;

/**
 * SessionId Value Object - Immutable Session Identifier
 *
 * MANDATORY: Value Object Pattern - Rule #9
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Validation - Fail-fast
 *
 * This value object encapsulates a session identifier with validation
 * and provides type-safe operations. Immutable and self-validating.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
public record SessionId(String value) {

    /**
     * Compact constructor with validation - Rule #9
     */
    public SessionId {
        // Defensive validation (fail-fast)
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be null or blank");
        }

        // Validate UUID format (optional - can be relaxed if needed)
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Session ID must be a valid UUID format", e);
        }
    }

    /**
     * Create new random session ID
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return New SessionId with random UUID
     */
    public static SessionId generate() {
        return new SessionId(UUID.randomUUID().toString());
    }

    /**
     * Create from string value with validation
     *
     * @param value Session ID string
     * @return New SessionId
     */
    public static SessionId of(String value) {
        return new SessionId(value);
    }

    /**
     * Check if this session ID matches another
     *
     * @param other Other session ID to compare
     * @return true if values match
     */
    public boolean matches(SessionId other) {
        return Objects.equals(this.value, other.value());
    }

    /**
     * Get string representation
     *
     * @return Session ID value
     */
    @Override
    public String toString() {
        return value;
    }
}
