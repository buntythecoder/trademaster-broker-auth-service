package com.trademaster.brokerauth.entity;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Broker Session Entity
 * 
 * MANDATORY: JPA Entity - Rule #1 (HikariCP)
 * MANDATORY: Lombok - Rule #10
 */
@Entity
@Table(name = "broker_sessions")
@Data
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
}