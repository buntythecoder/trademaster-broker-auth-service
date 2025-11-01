package com.trademaster.brokerauth.mapper;

import com.trademaster.brokerauth.domain.model.SessionDomain;
import com.trademaster.brokerauth.dto.SessionResponseDTO;
import com.trademaster.brokerauth.entity.BrokerSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SessionMapper - Bidirectional Transformation between Entity and Domain
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
public class SessionMapper {

    /**
     * Convert domain model to persistence entity
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Rule #9 - Builder pattern for entity construction
     *
     * @param domain SessionDomain to convert
     * @return BrokerSession entity
     */
    public BrokerSession toEntity(SessionDomain domain) {
        return Optional.ofNullable(domain)
            .map(this::buildEntity)
            .orElseThrow(() -> new IllegalArgumentException("SessionDomain cannot be null"));
    }

    /**
     * Convert persistence entity to domain model
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Rule #9 - Immutable record construction
     *
     * @param entity BrokerSession entity to convert
     * @return SessionDomain
     */
    public SessionDomain toDomain(BrokerSession entity) {
        return Optional.ofNullable(entity)
            .map(this::buildDomain)
            .orElseThrow(() -> new IllegalArgumentException("BrokerSession entity cannot be null"));
    }

    /**
     * Convert optional entity to optional domain
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param entityOpt Optional BrokerSession entity
     * @return Optional SessionDomain
     */
    public Optional<SessionDomain> toDomain(Optional<BrokerSession> entityOpt) {
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
    public BrokerSession updateEntityFromDomain(BrokerSession entity, SessionDomain domain) {
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
     * @param domain SessionDomain
     * @return BrokerSession entity
     */
    private BrokerSession buildEntity(SessionDomain domain) {
        return BrokerSession.builder()
            .id(domain.id())
            .sessionId(domain.sessionId())
            .userId(domain.userId())
            .brokerType(domain.brokerType())
            .accessToken(domain.accessToken())
            .refreshToken(domain.refreshToken())
            .status(domain.status())
            .createdAt(domain.createdAt())
            .expiresAt(domain.expiresAt())
            .lastAccessedAt(domain.lastAccessedAt())
            .updatedAt(domain.updatedAt())
            .metadata(domain.metadata())
            .vaultPath(domain.vaultPath())
            .build();
    }

    /**
     * Build domain from entity using record constructor
     *
     * MANDATORY: Rule #9 - Immutable records
     * Private method - functional building block
     *
     * @param entity BrokerSession entity
     * @return SessionDomain
     */
    private SessionDomain buildDomain(BrokerSession entity) {
        return new SessionDomain(
            entity.getId(),
            entity.getSessionId(),
            entity.getUserId(),
            entity.getBrokerType(),
            entity.getAccessToken(),
            entity.getRefreshToken(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getExpiresAt(),
            entity.getLastAccessedAt(),
            entity.getUpdatedAt(),
            entity.getMetadata(),
            entity.getVaultPath()
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
    private BrokerSession buildUpdatedEntity(BrokerSession entity, SessionDomain domain) {
        return BrokerSession.builder()
            .id(entity.getId()) // Preserve database ID
            .sessionId(domain.sessionId())
            .userId(domain.userId())
            .brokerType(domain.brokerType())
            .accessToken(domain.accessToken())
            .refreshToken(domain.refreshToken())
            .status(domain.status())
            .createdAt(domain.createdAt())
            .expiresAt(domain.expiresAt())
            .lastAccessedAt(domain.lastAccessedAt())
            .updatedAt(domain.updatedAt())
            .metadata(domain.metadata())
            .vaultPath(domain.vaultPath())
            .build();
    }

    /**
     * Convert domain model to DTO for API responses
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Clean Architecture - API layer separation
     *
     * @param domain SessionDomain to convert
     * @return SessionResponseDTO for API
     */
    public SessionResponseDTO toDTO(SessionDomain domain) {
        return Optional.ofNullable(domain)
            .map(this::buildDTO)
            .orElseThrow(() -> new IllegalArgumentException("SessionDomain cannot be null"));
    }

    /**
     * Convert optional domain to optional DTO
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param domainOpt Optional SessionDomain
     * @return Optional SessionResponseDTO
     */
    public Optional<SessionResponseDTO> toDTO(Optional<SessionDomain> domainOpt) {
        return domainOpt.map(this::toDTO);
    }

    /**
     * Build DTO from domain using record constructor
     *
     * MANDATORY: Rule #9 - Immutable records
     * Private method - functional building block
     *
     * @param domain SessionDomain
     * @return SessionResponseDTO
     */
    private SessionResponseDTO buildDTO(SessionDomain domain) {
        return new SessionResponseDTO(
            domain.id(),
            domain.sessionId(),
            domain.userId(),
            domain.brokerType(),
            domain.status(),
            domain.createdAt(),
            domain.expiresAt(),
            domain.lastAccessedAt(),
            domain.metadata(),
            domain.vaultPath()
        );
    }
}
