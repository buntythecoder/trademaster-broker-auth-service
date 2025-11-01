package com.trademaster.brokerauth.mapper;

import com.trademaster.brokerauth.domain.model.BrokerAccountDomain;
import com.trademaster.brokerauth.entity.BrokerAccount;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * BrokerAccountMapper - Bidirectional Transformation between Entity and Domain
 *
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Clean Architecture - Separation of concerns
 * MANDATORY: Immutability - Rule #9
 *
 * This mapper provides functional transformation between persistence entities
 * and domain models, maintaining clean separation of concerns. All transformations
 * are pure functions without side effects.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Component
public class BrokerAccountMapper {

    /**
     * Convert domain model to persistence entity
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Rule #9 - Builder pattern for entity construction
     *
     * @param domain BrokerAccountDomain to convert
     * @return BrokerAccount entity
     */
    public BrokerAccount toEntity(BrokerAccountDomain domain) {
        return Optional.ofNullable(domain)
            .map(this::buildEntity)
            .orElseThrow(() -> new IllegalArgumentException("BrokerAccountDomain cannot be null"));
    }

    /**
     * Convert persistence entity to domain model
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Rule #9 - Immutable record construction
     *
     * @param entity BrokerAccount entity to convert
     * @return BrokerAccountDomain
     */
    public BrokerAccountDomain toDomain(BrokerAccount entity) {
        return Optional.ofNullable(entity)
            .map(this::buildDomain)
            .orElseThrow(() -> new IllegalArgumentException("BrokerAccount entity cannot be null"));
    }

    /**
     * Convert optional entity to optional domain
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param entityOpt Optional BrokerAccount entity
     * @return Optional BrokerAccountDomain
     */
    public Optional<BrokerAccountDomain> toDomain(Optional<BrokerAccount> entityOpt) {
        return entityOpt.map(this::toDomain);
    }

    /**
     * Update entity with domain state (for JPA persistence)
     *
     * MANDATORY: Rule #3 - Functional Programming
     * Note: Returns new entity instance for immutability
     *
     * @param entity Existing entity to update
     * @param domain Domain with new state
     * @return Updated entity (new instance)
     */
    public BrokerAccount updateEntityFromDomain(BrokerAccount entity, BrokerAccountDomain domain) {
        return Optional.ofNullable(entity)
            .map(e -> Optional.ofNullable(domain)
                .map(d -> buildUpdatedEntity(e, d))
                .orElse(e))
            .orElseThrow(() -> new IllegalArgumentException("Entity cannot be null"));
    }

    /**
     * Build entity from domain using builder pattern
     *
     * MANDATORY: Rule #9 - Builder pattern
     * Private method - functional building block
     *
     * @param domain BrokerAccountDomain
     * @return BrokerAccount entity
     */
    private BrokerAccount buildEntity(BrokerAccountDomain domain) {
        return BrokerAccount.builder()
            .id(domain.id())
            .userId(domain.userId())
            .brokerType(domain.brokerType())
            .brokerUserId(domain.brokerUserId())
            .encryptedPassword(domain.encryptedPassword())
            .encryptedApiKey(domain.encryptedApiKey())
            .encryptedApiSecret(domain.encryptedApiSecret())
            .encryptedTotpSecret(domain.encryptedTotpSecret())
            .isActive(domain.isActive())
            .lastLoginAt(domain.lastLoginAt())
            .createdAt(domain.createdAt())
            .updatedAt(domain.updatedAt())
            .build();
    }

    /**
     * Build domain from entity using record constructor
     *
     * MANDATORY: Rule #9 - Immutable records
     * Private method - functional building block
     *
     * @param entity BrokerAccount entity
     * @return BrokerAccountDomain
     */
    private BrokerAccountDomain buildDomain(BrokerAccount entity) {
        return new BrokerAccountDomain(
            entity.getId(),
            entity.getUserId(),
            entity.getBrokerType(),
            entity.getBrokerUserId(),
            entity.getEncryptedPassword(),
            entity.getEncryptedApiKey(),
            entity.getEncryptedApiSecret(),
            entity.getEncryptedTotpSecret(),
            entity.getIsActive(),
            entity.getLastLoginAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Build updated entity from existing entity and domain state
     *
     * MANDATORY: Rule #9 - Immutable-style updates
     * Private method - functional building block
     *
     * @param entity Existing entity
     * @param domain Domain with new state
     * @return Updated entity (new instance)
     */
    private BrokerAccount buildUpdatedEntity(BrokerAccount entity, BrokerAccountDomain domain) {
        return BrokerAccount.builder()
            .id(entity.getId()) // Preserve database ID
            .userId(domain.userId())
            .brokerType(domain.brokerType())
            .brokerUserId(domain.brokerUserId())
            .encryptedPassword(domain.encryptedPassword())
            .encryptedApiKey(domain.encryptedApiKey())
            .encryptedApiSecret(domain.encryptedApiSecret())
            .encryptedTotpSecret(domain.encryptedTotpSecret())
            .isActive(domain.isActive())
            .lastLoginAt(domain.lastLoginAt())
            .createdAt(domain.createdAt())
            .updatedAt(domain.updatedAt())
            .build();
    }
}
