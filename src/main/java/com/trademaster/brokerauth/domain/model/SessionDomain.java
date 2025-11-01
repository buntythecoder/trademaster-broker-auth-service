package com.trademaster.brokerauth.domain.model;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Session Domain Model - Pure Immutable Business Logic
 *
 * MANDATORY: Immutable Records - Rule #9
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Domain-Driven Design - Separation of domain and persistence
 *
 * This record represents the business domain concept of a broker session,
 * completely decoupled from persistence concerns. All operations are
 * functional and immutable.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
public record SessionDomain(
    Long id,
    String sessionId,
    String userId,
    BrokerType brokerType,
    String accessToken,
    String refreshToken,
    SessionStatus status,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime lastAccessedAt,
    LocalDateTime updatedAt,
    String metadata,
    String vaultPath
) {

    /**
     * Compact constructor with validation - Rule #9
     */
    public SessionDomain {
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
        if (createdAt == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiry timestamp cannot be null");
        }
    }

    /**
     * Check if session is active and not expired
     *
     * MANDATORY: Rule #3 - Functional Programming (no if-else)
     * MANDATORY: Rule #14 - Pattern Matching
     *
     * @return true if session is active and not expired
     */
    public boolean isActive() {
        return switch (status) {
            case ACTIVE -> Optional.ofNullable(expiresAt)
                .map(expires -> expires.isAfter(LocalDateTime.now()))
                .orElse(false);
            case EXPIRED, REVOKED, INVALID -> false;
        };
    }

    /**
     * Check if session needs refresh (expires within threshold)
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Rule #16 - Configurable threshold (default 5 minutes)
     *
     * @param thresholdMinutes Minutes before expiry to trigger refresh
     * @return true if session needs refresh
     */
    public boolean needsRefresh(int thresholdMinutes) {
        return Optional.ofNullable(expiresAt)
            .map(expires -> expires.isBefore(LocalDateTime.now().plusMinutes(thresholdMinutes)))
            .orElse(false);
    }

    /**
     * Check if session needs refresh with default threshold (5 minutes)
     *
     * @return true if session needs refresh within 5 minutes
     */
    public boolean needsRefresh() {
        return needsRefresh(5);
    }

    /**
     * Check if session is expired
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return true if session is expired
     */
    public boolean isExpired() {
        return switch (status) {
            case EXPIRED -> true;
            case ACTIVE -> Optional.ofNullable(expiresAt)
                .map(expires -> expires.isBefore(LocalDateTime.now()))
                .orElse(true);
            case REVOKED, INVALID -> false; // Not expired, but inactive for other reasons
        };
    }

    /**
     * Check if session is revoked
     *
     * @return true if session is revoked
     */
    public boolean isRevoked() {
        return status == SessionStatus.REVOKED;
    }

    /**
     * Check if session has refresh token
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
     * Get time remaining until expiry
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
     * Immutable update - change session status
     *
     * MANDATORY: Rule #9 - Immutability (returns new instance)
     *
     * @param newStatus New session status
     * @return New SessionDomain with updated status
     */
    public SessionDomain withStatus(SessionStatus newStatus) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            accessToken,
            refreshToken,
            newStatus,
            createdAt,
            expiresAt,
            lastAccessedAt,
            LocalDateTime.now(), // Update timestamp
            metadata,
            vaultPath
        );
    }

    /**
     * Immutable update - refresh access token
     *
     * @param newAccessToken New access token
     * @return New SessionDomain with updated token
     */
    public SessionDomain withAccessToken(String newAccessToken) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            newAccessToken,
            refreshToken,
            status,
            createdAt,
            expiresAt,
            lastAccessedAt,
            LocalDateTime.now(),
            metadata,
            vaultPath
        );
    }

    /**
     * Immutable update - refresh both tokens
     *
     * @param newAccessToken New access token
     * @param newRefreshToken New refresh token
     * @return New SessionDomain with updated tokens
     */
    public SessionDomain withTokens(String newAccessToken, String newRefreshToken) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            newAccessToken,
            newRefreshToken,
            status,
            createdAt,
            expiresAt,
            lastAccessedAt,
            LocalDateTime.now(),
            metadata,
            vaultPath
        );
    }

    /**
     * Immutable update - update last accessed time
     *
     * @param accessTime New last accessed time
     * @return New SessionDomain with updated access time
     */
    public SessionDomain withLastAccessed(LocalDateTime accessTime) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            accessToken,
            refreshToken,
            status,
            createdAt,
            expiresAt,
            accessTime,
            LocalDateTime.now(),
            metadata,
            vaultPath
        );
    }

    /**
     * Immutable update - extend expiry time
     *
     * @param newExpiresAt New expiry timestamp
     * @return New SessionDomain with updated expiry
     */
    public SessionDomain withExpiresAt(LocalDateTime newExpiresAt) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            accessToken,
            refreshToken,
            status,
            createdAt,
            newExpiresAt,
            lastAccessedAt,
            LocalDateTime.now(),
            metadata,
            vaultPath
        );
    }

    /**
     * Immutable update - update metadata
     *
     * @param newMetadata New metadata string
     * @return New SessionDomain with updated metadata
     */
    public SessionDomain withMetadata(String newMetadata) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            accessToken,
            refreshToken,
            status,
            createdAt,
            expiresAt,
            lastAccessedAt,
            LocalDateTime.now(),
            newMetadata,
            vaultPath
        );
    }

    /**
     * Immutable update - update vault path
     *
     * @param newVaultPath New vault path
     * @return New SessionDomain with updated vault path
     */
    public SessionDomain withVaultPath(String newVaultPath) {
        return new SessionDomain(
            id,
            sessionId,
            userId,
            brokerType,
            accessToken,
            refreshToken,
            status,
            createdAt,
            expiresAt,
            lastAccessedAt,
            LocalDateTime.now(),
            metadata,
            newVaultPath
        );
    }

    /**
     * Touch session - update last accessed time to now
     *
     * @return New SessionDomain with current time as last accessed
     */
    public SessionDomain touch() {
        return withLastAccessed(LocalDateTime.now());
    }

    /**
     * Revoke session immutably
     *
     * @return New SessionDomain with REVOKED status
     */
    public SessionDomain revoke() {
        return withStatus(SessionStatus.REVOKED);
    }

    /**
     * Expire session immutably
     *
     * @return New SessionDomain with EXPIRED status
     */
    public SessionDomain expire() {
        return withStatus(SessionStatus.EXPIRED);
    }

    /**
     * Mark session as invalid
     *
     * @return New SessionDomain with INVALID status
     */
    public SessionDomain invalidate() {
        return withStatus(SessionStatus.INVALID);
    }
}
