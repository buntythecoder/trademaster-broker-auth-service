package com.trademaster.brokerauth.repository;

import com.trademaster.brokerauth.entity.Broker;
import com.trademaster.brokerauth.enums.BrokerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Broker Repository - Data access layer for broker entities
 * 
 * MANDATORY: Spring Data JPA with performance optimization - Rule #1
 * MANDATORY: Custom queries for complex operations - Rule #22
 */
@Repository
public interface BrokerRepository extends JpaRepository<Broker, Long> {
    
    /**
     * Find broker by type
     * MANDATORY: Indexed query for performance - Rule #22
     */
    Optional<Broker> findByBrokerType(BrokerType brokerType);
    
    /**
     * Find active brokers only
     * MANDATORY: Business logic optimization - Rule #22
     */
    @Query("SELECT b FROM Broker b WHERE b.isActive = true ORDER BY b.name")
    List<Broker> findAllActiveBrokers();
    
    /**
     * Find broker by type and active status
     * MANDATORY: Composite filtering for performance - Rule #22
     */
    Optional<Broker> findByBrokerTypeAndIsActive(BrokerType brokerType, Boolean isActive);
    
    /**
     * Check if broker type is supported and active
     * MANDATORY: Validation query for authentication flow - Rule #22
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
           "FROM Broker b WHERE b.brokerType = :brokerType AND b.isActive = true")
    boolean existsByBrokerTypeAndIsActive(@Param("brokerType") BrokerType brokerType);
    
    /**
     * Get broker rate limits for specific broker
     * MANDATORY: Performance optimization for rate limiting - Rule #22
     */
    @Query("SELECT b.rateLimits FROM Broker b WHERE b.brokerType = :brokerType AND b.isActive = true")
    Optional<java.util.Map<String, Integer>> findRateLimitsByBrokerType(@Param("brokerType") BrokerType brokerType);
}