package com.trademaster.brokerauth.domain.model;

import com.trademaster.brokerauth.enums.BrokerType;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Broker Account Domain Model - Pure Immutable Business Logic
 *
 * MANDATORY: Immutable Records - Rule #9
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Domain-Driven Design - Separation of domain and persistence
 *
 * This record represents the business domain concept of a broker account,
 * completely decoupled from persistence concerns. All operations are
 * functional and immutable.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
public record BrokerAccountDomain(
    Long id,
    String userId,
    BrokerType brokerType,
    String brokerUserId,
    String encryptedPassword,
    String encryptedApiKey,
    String encryptedApiSecret,
    String encryptedTotpSecret,
    Boolean isActive,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    /**
     * Compact constructor with validation - Rule #9
     */
    public BrokerAccountDomain {
        // Defensive validation (fail-fast)
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        if (brokerType == null) {
            throw new IllegalArgumentException("Broker type cannot be null");
        }
        if (brokerUserId == null || brokerUserId.isBlank()) {
            throw new IllegalArgumentException("Broker user ID cannot be null or blank");
        }
        if (isActive == null) {
            throw new IllegalArgumentException("Active status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }
    }

    /**
     * Check if account is active
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if account is active
     */
    public boolean isAccountActive() {
        return Optional.ofNullable(isActive)
            .orElse(false);
    }

    /**
     * Check if account has password authentication
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if password is configured
     */
    public boolean hasPasswordAuth() {
        return Optional.ofNullable(encryptedPassword)
            .filter(pwd -> !pwd.isBlank())
            .isPresent();
    }

    /**
     * Check if account has API key authentication
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if API key and secret are configured
     */
    public boolean hasApiKeyAuth() {
        return Optional.ofNullable(encryptedApiKey)
            .filter(key -> !key.isBlank())
            .flatMap(key -> Optional.ofNullable(encryptedApiSecret)
                .filter(secret -> !secret.isBlank()))
            .isPresent();
    }

    /**
     * Check if account has TOTP authentication
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if TOTP secret is configured
     */
    public boolean hasTotpAuth() {
        return Optional.ofNullable(encryptedTotpSecret)
            .filter(totp -> !totp.isBlank())
            .isPresent();
    }

    /**
     * Check if account has never logged in
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if account has never been used
     */
    public boolean isNewAccount() {
        return Optional.ofNullable(lastLoginAt)
            .isEmpty();
    }

    /**
     * Check if account was used recently (within hours)
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param hoursThreshold Hours to consider as recent
     * @return true if last login within threshold
     */
    public boolean isRecentlyActive(int hoursThreshold) {
        return Optional.ofNullable(lastLoginAt)
            .map(lastLogin -> lastLogin.isAfter(LocalDateTime.now().minusHours(hoursThreshold)))
            .orElse(false);
    }

    /**
     * Check if account was used recently (default 24 hours)
     *
     * @return true if last login within 24 hours
     */
    public boolean isRecentlyActive() {
        return isRecentlyActive(24);
    }

    /**
     * Get hours since last login
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return Optional containing hours since last login, or empty if never logged in
     */
    public Optional<Long> getHoursSinceLastLogin() {
        return Optional.ofNullable(lastLoginAt)
            .map(lastLogin -> java.time.Duration.between(lastLogin, LocalDateTime.now()).toHours());
    }

    /**
     * Get days since account creation
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return Days since account was created
     */
    public long getDaysSinceCreation() {
        return Optional.ofNullable(createdAt)
            .map(created -> java.time.Duration.between(created, LocalDateTime.now()).toDays())
            .orElse(0L);
    }

    /**
     * Immutable update - activate account
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @return New BrokerAccountDomain with active status
     */
    public BrokerAccountDomain activate() {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            encryptedPassword,
            encryptedApiKey,
            encryptedApiSecret,
            encryptedTotpSecret,
            true,
            lastLoginAt,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Immutable update - deactivate account
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @return New BrokerAccountDomain with inactive status
     */
    public BrokerAccountDomain deactivate() {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            encryptedPassword,
            encryptedApiKey,
            encryptedApiSecret,
            encryptedTotpSecret,
            false,
            lastLoginAt,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Immutable update - update encrypted password
     *
     * @param newEncryptedPassword New encrypted password
     * @return New BrokerAccountDomain with updated password
     */
    public BrokerAccountDomain withEncryptedPassword(String newEncryptedPassword) {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            newEncryptedPassword,
            encryptedApiKey,
            encryptedApiSecret,
            encryptedTotpSecret,
            isActive,
            lastLoginAt,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Immutable update - update encrypted API credentials
     *
     * @param newApiKey New encrypted API key
     * @param newApiSecret New encrypted API secret
     * @return New BrokerAccountDomain with updated API credentials
     */
    public BrokerAccountDomain withApiCredentials(String newApiKey, String newApiSecret) {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            encryptedPassword,
            newApiKey,
            newApiSecret,
            encryptedTotpSecret,
            isActive,
            lastLoginAt,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Immutable update - update encrypted TOTP secret
     *
     * @param newTotpSecret New encrypted TOTP secret
     * @return New BrokerAccountDomain with updated TOTP
     */
    public BrokerAccountDomain withTotpSecret(String newTotpSecret) {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            encryptedPassword,
            encryptedApiKey,
            encryptedApiSecret,
            newTotpSecret,
            isActive,
            lastLoginAt,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Immutable update - record login
     *
     * @return New BrokerAccountDomain with updated last login time
     */
    public BrokerAccountDomain recordLogin() {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            encryptedPassword,
            encryptedApiKey,
            encryptedApiSecret,
            encryptedTotpSecret,
            isActive,
            LocalDateTime.now(),
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Immutable update - update last login time
     *
     * @param loginTime Login timestamp
     * @return New BrokerAccountDomain with specified login time
     */
    public BrokerAccountDomain withLastLogin(LocalDateTime loginTime) {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            encryptedPassword,
            encryptedApiKey,
            encryptedApiSecret,
            encryptedTotpSecret,
            isActive,
            loginTime,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Clear all encrypted credentials (for security)
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @return New BrokerAccountDomain with cleared credentials
     */
    public BrokerAccountDomain clearCredentials() {
        return new BrokerAccountDomain(
            id,
            userId,
            brokerType,
            brokerUserId,
            null,
            null,
            null,
            null,
            isActive,
            lastLoginAt,
            createdAt,
            LocalDateTime.now()
        );
    }

    /**
     * Get client ID - alias for broker user ID
     *
     * @return Broker user ID
     */
    public String getClientId() {
        return brokerUserId;
    }
}
