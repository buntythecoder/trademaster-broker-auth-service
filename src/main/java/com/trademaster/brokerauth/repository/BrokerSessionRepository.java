package com.trademaster.brokerauth.repository;

import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Broker Session Repository - Data access for broker authentication sessions
 * 
 * MANDATORY: Session management for security - Rule #23
 * MANDATORY: Performance optimization for high-frequency operations - Rule #22
 */
@Repository
public interface BrokerSessionRepository extends JpaRepository<BrokerSession, Long> {
    
    /**
     * Find active session for user and broker
     * MANDATORY: Primary session lookup - Rule #23
     */
    @Query("SELECT bs FROM BrokerSession bs WHERE bs.userId = :userId AND bs.brokerType = :brokerType " +
           "AND bs.status = 'ACTIVE' AND bs.expiresAt > :currentTime")
    Optional<BrokerSession> findActiveSession(@Param("userId") String userId, 
                                            @Param("brokerType") BrokerType brokerType,
                                            @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find session by session ID
     * MANDATORY: Session lookup by ID - Rule #23
     */
    Optional<BrokerSession> findBySessionId(String sessionId);

    /**
     * Find session by access token
     * MANDATORY: Token-based authentication - Rule #23
     */
    Optional<BrokerSession> findByAccessTokenAndStatus(String accessToken, SessionStatus status);
    
    /**
     * Find all active sessions for user
     * MANDATORY: User session management - Rule #23
     */
    @Query("SELECT bs FROM BrokerSession bs WHERE bs.userId = :userId AND bs.status = 'ACTIVE' " +
           "AND bs.expiresAt > :currentTime ORDER BY bs.createdAt DESC")
    List<BrokerSession> findAllActiveSessionsForUser(@Param("userId") String userId,
                                                    @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find expired sessions for cleanup
     * MANDATORY: Security cleanup operations - Rule #23
     */
    @Query("SELECT bs FROM BrokerSession bs WHERE bs.expiresAt < :currentTime " +
           "AND bs.status IN ('ACTIVE', 'REFRESHING')")
    List<BrokerSession> findExpiredSessions(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find sessions requiring token refresh
     * MANDATORY: Proactive token management - Rule #23
     */
    @Query("SELECT bs FROM BrokerSession bs WHERE bs.status = 'ACTIVE' " +
           "AND bs.expiresAt BETWEEN :currentTime AND :refreshThreshold")
    List<BrokerSession> findSessionsNeedingRefresh(@Param("currentTime") LocalDateTime currentTime,
                                                  @Param("refreshThreshold") LocalDateTime refreshThreshold);
    
    /**
     * Update session status
     * MANDATORY: Status management for session lifecycle - Rule #23
     */
    @Modifying
    @Query("UPDATE BrokerSession bs SET bs.status = :status, bs.updatedAt = :updateTime " +
           "WHERE bs.id = :sessionId")
    int updateSessionStatus(@Param("sessionId") Long sessionId, 
                           @Param("status") SessionStatus status,
                           @Param("updateTime") LocalDateTime updateTime);
    
    /**
     * Update access token and expiry
     * MANDATORY: Token refresh operations - Rule #23
     */
    @Modifying
    @Query("UPDATE BrokerSession bs SET bs.accessToken = :accessToken, bs.expiresAt = :expiresAt, " +
           "bs.status = 'ACTIVE', bs.updatedAt = :updateTime WHERE bs.id = :sessionId")
    int refreshSessionToken(@Param("sessionId") Long sessionId,
                           @Param("accessToken") String accessToken,
                           @Param("expiresAt") LocalDateTime expiresAt,
                           @Param("updateTime") LocalDateTime updateTime);
    
    /**
     * Count active sessions for user
     * MANDATORY: Session limit enforcement - Rule #23
     */
    @Query("SELECT COUNT(bs) FROM BrokerSession bs WHERE bs.userId = :userId " +
           "AND bs.status = 'ACTIVE' AND bs.expiresAt > :currentTime")
    long countActiveSessionsForUser(@Param("userId") String userId, 
                                   @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Invalidate all sessions for user and broker
     * MANDATORY: Security operations - Rule #23
     */
    @Modifying
    @Query("UPDATE BrokerSession bs SET bs.status = 'EXPIRED', bs.updatedAt = :updateTime " +
           "WHERE bs.userId = :userId AND bs.brokerType = :brokerType AND bs.status = 'ACTIVE'")
    int invalidateUserBrokerSessions(@Param("userId") String userId,
                                   @Param("brokerType") BrokerType brokerType,
                                   @Param("updateTime") LocalDateTime updateTime);
    
    /**
     * Delete old expired sessions
     * MANDATORY: Data cleanup for performance - Rule #22
     */
    @Modifying
    @Query("DELETE FROM BrokerSession bs WHERE bs.status = 'EXPIRED' " +
           "AND bs.updatedAt < :cutoffTime")
    int deleteOldExpiredSessions(@Param("cutoffTime") LocalDateTime cutoffTime);
}