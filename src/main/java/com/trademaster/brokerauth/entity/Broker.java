package com.trademaster.brokerauth.entity;

import com.trademaster.brokerauth.enums.BrokerType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Broker Entity - Represents a supported trading broker
 * 
 * MANDATORY: Java 24 + Records + Immutability - Rule #9
 * MANDATORY: JPA Entity for data persistence - Rule #1
 */
@Entity
@Table(name = "brokers", 
       indexes = {
           @Index(name = "idx_broker_type", columnList = "broker_type"),
           @Index(name = "idx_broker_active", columnList = "is_active")
       })
@Data
@RequiredArgsConstructor
public class Broker {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "broker_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private BrokerType brokerType;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "api_url", nullable = false, length = 255)
    private String apiUrl;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @ElementCollection
    @CollectionTable(name = "broker_rate_limits", 
                    joinColumns = @JoinColumn(name = "broker_id"))
    @MapKeyColumn(name = "limit_type")
    @Column(name = "limit_value")
    private Map<String, Integer> rateLimits;
    
    @Column(name = "session_validity_seconds", nullable = false)
    private Integer sessionValiditySeconds;
    
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