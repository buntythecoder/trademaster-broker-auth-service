package com.trademaster.brokerauth.repository;

import com.trademaster.brokerauth.entity.BrokerAccount;
import com.trademaster.brokerauth.enums.BrokerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Broker Account Repository - Data access for user broker accounts
 * 
 * MANDATORY: Security-focused data access - Rule #23
 * MANDATORY: Audit trail support - Rule #15
 */
@Repository
public interface BrokerAccountRepository extends JpaRepository<BrokerAccount, Long> {
    
    /**
     * Find user's account for specific broker
     * MANDATORY: Unique constraint enforcement - Rule #23
     */
    Optional<BrokerAccount> findByUserIdAndBrokerType(String userId, BrokerType brokerType);
    
    /**
     * Find all active accounts for user
     * MANDATORY: User-centric data access - Rule #23
     */
    @Query("SELECT ba FROM BrokerAccount ba WHERE ba.userId = :userId AND ba.isActive = true " +
           "ORDER BY ba.brokerType")
    List<BrokerAccount> findAllActiveByUserId(@Param("userId") String userId);
    
    /**
     * Find all accounts for specific broker type
     * MANDATORY: Broker management operations - Rule #22
     */
    List<BrokerAccount> findByBrokerTypeAndIsActive(BrokerType brokerType, Boolean isActive);
    
    /**
     * Check if user has active account with broker
     * MANDATORY: Quick validation query - Rule #22
     */
    boolean existsByUserIdAndBrokerTypeAndIsActive(String userId, BrokerType brokerType, Boolean isActive);
    
    /**
     * Update last login timestamp
     * MANDATORY: Audit trail maintenance - Rule #15
     */
    @Modifying
    @Query("UPDATE BrokerAccount ba SET ba.lastLoginAt = :loginTime, ba.updatedAt = :updateTime " +
           "WHERE ba.userId = :userId AND ba.brokerType = :brokerType")
    int updateLastLoginTime(@Param("userId") String userId, 
                           @Param("brokerType") BrokerType brokerType,
                           @Param("loginTime") LocalDateTime loginTime,
                           @Param("updateTime") LocalDateTime updateTime);
    
    /**
     * Find accounts that haven't been used recently
     * MANDATORY: Security cleanup operations - Rule #23
     */
    @Query("SELECT ba FROM BrokerAccount ba WHERE ba.lastLoginAt < :cutoffTime AND ba.isActive = true")
    List<BrokerAccount> findInactiveAccounts(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Deactivate account
     * MANDATORY: Security operations - Rule #23
     */
    @Modifying
    @Query("UPDATE BrokerAccount ba SET ba.isActive = false, ba.updatedAt = :updateTime " +
           "WHERE ba.userId = :userId AND ba.brokerType = :brokerType")
    int deactivateAccount(@Param("userId") String userId, 
                         @Param("brokerType") BrokerType brokerType,
                         @Param("updateTime") LocalDateTime updateTime);
}