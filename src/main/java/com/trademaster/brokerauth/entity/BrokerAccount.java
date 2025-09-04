package com.trademaster.brokerauth.entity;

import com.trademaster.brokerauth.enums.BrokerType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;

/**
 * Broker Account Entity - Represents user's broker account credentials
 * 
 * MANDATORY: Encrypted credentials storage - Rule #23
 * MANDATORY: Audit trail for financial compliance - Rule #15
 */
@Entity
@Table(name = "broker_accounts",
       indexes = {
           @Index(name = "idx_user_broker", columnList = "user_id, broker_type", unique = true),
           @Index(name = "idx_user_id", columnList = "user_id"),
           @Index(name = "idx_broker_type", columnList = "broker_type")
       })
@Data
@RequiredArgsConstructor
public class BrokerAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "broker_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private BrokerType brokerType;
    
    @Column(name = "broker_user_id", nullable = false, length = 100)
    private String brokerUserId;
    
    @Column(name = "encrypted_password", columnDefinition = "TEXT")
    private String encryptedPassword;
    
    @Column(name = "encrypted_api_key", columnDefinition = "TEXT")
    private String encryptedApiKey;
    
    @Column(name = "encrypted_api_secret", columnDefinition = "TEXT") 
    private String encryptedApiSecret;
    
    @Column(name = "encrypted_totp_secret", columnDefinition = "TEXT")
    private String encryptedTotpSecret;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}