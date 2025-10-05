package com.trademaster.brokerauth.entity;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Broker Session Entity - Immutable Pattern with Builder
 *
 * MANDATORY: JPA Entity - Rule #1 (HikariCP)
 * MANDATORY: Immutability & Records Usage - Rule #9
 * MANDATORY: Builder Pattern - Rule #9
 *
 * Note: Uses compromise approach - JPA-compatible immutable-style entity
 * Updates through immutable methods returning new instances
 */
@Entity
@Table(name = "broker_sessions")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BrokerSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "broker_type", nullable = false)
    private BrokerType brokerType;
    
    @Column(name = "access_token", nullable = false)
    private String accessToken;
    
    @Column(name = "refresh_token")
    private String refreshToken;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    /**
     * Immutable update methods - Rule #9 Immutability
     * Returns new instances instead of mutating existing ones
     */

    /**
     * Update session status immutably
     */
    public BrokerSession withStatus(SessionStatus newStatus) {
        return this.toBuilder()
            .status(newStatus)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Update access token immutably
     */
    public BrokerSession withAccessToken(String newAccessToken) {
        return this.toBuilder()
            .accessToken(newAccessToken)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Update refresh token immutably
     */
    public BrokerSession withRefreshToken(String newRefreshToken) {
        return this.toBuilder()
            .refreshToken(newRefreshToken)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Update last accessed time immutably
     */
    public BrokerSession withLastAccessed(LocalDateTime lastAccessed) {
        return this.toBuilder()
            .lastAccessedAt(lastAccessed)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Update expiry time immutably
     */
    public BrokerSession withExpiresAt(LocalDateTime expiresAt) {
        return this.toBuilder()
            .expiresAt(expiresAt)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Update metadata immutably
     */
    public BrokerSession withMetadata(String newMetadata) {
        return this.toBuilder()
            .metadata(newMetadata)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Check if session is active and not expired
     */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE &&
               expiresAt != null &&
               expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Check if session needs refresh (expires within 5 minutes)
     */
    public boolean needsRefresh() {
        return expiresAt != null &&
               expiresAt.isBefore(LocalDateTime.now().plusMinutes(5));
    }
}