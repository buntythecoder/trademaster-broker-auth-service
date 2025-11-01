package com.trademaster.brokerauth.domain.value;

import java.util.Objects;

/**
 * UserId Value Object - Immutable User Identifier
 *
 * MANDATORY: Value Object Pattern - Rule #9
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Validation - Fail-fast
 *
 * This value object encapsulates a user identifier with validation
 * and provides type-safe operations. Immutable and self-validating.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
public record UserId(String value) {

    /**
     * Compact constructor with validation - Rule #9
     */
    public UserId {
        // Defensive validation (fail-fast)
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }

        // Validate length (reasonable constraints)
        if (value.length() < 3) {
            throw new IllegalArgumentException("User ID must be at least 3 characters");
        }

        if (value.length() > 100) {
            throw new IllegalArgumentException("User ID must not exceed 100 characters");
        }
    }

    /**
     * Create from string value with validation
     *
     * @param value User ID string
     * @return New UserId
     */
    public static UserId of(String value) {
        return new UserId(value);
    }

    /**
     * Check if this user ID matches another
     *
     * @param other Other user ID to compare
     * @return true if values match
     */
    public boolean matches(UserId other) {
        return Objects.equals(this.value, other.value());
    }

    /**
     * Get string representation
     *
     * @return User ID value
     */
    @Override
    public String toString() {
        return value;
    }
}
