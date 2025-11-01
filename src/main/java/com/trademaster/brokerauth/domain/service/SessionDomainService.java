package com.trademaster.brokerauth.domain.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.domain.model.SessionDomain;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Session Domain Service - Domain-Driven Session Management
 *
 * MANDATORY: Domain-Driven Design - Pure business logic
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Clean Architecture - Domain layer service
 *
 * This service provides domain-level session operations working exclusively
 * with SessionDomain models. All persistence concerns are handled through
 * the mapper layer, maintaining clean separation of concerns.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionDomainService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionMapper sessionMapper;

    /**
     * Save session domain model to Redis
     *
     * MANDATORY: Rule #3 - Functional Programming
     * MANDATORY: Rule #12 - Redis operations
     *
     * @param domain SessionDomain to save
     */
    public void save(SessionDomain domain) {
        Optional.ofNullable(domain)
            .map(this::buildRedisKey)
            .ifPresent(key -> persistToRedis(key, domain));
    }

    /**
     * Retrieve session by ID
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param sessionId Session identifier
     * @return Optional SessionDomain
     */
    public Optional<SessionDomain> findById(String sessionId) {
        return Optional.ofNullable(sessionId)
            .map(this::buildRedisKeyFromId)
            .flatMap(this::retrieveFromRedis)
            .filter(SessionDomain::isActive);
    }

    /**
     * Find active session for user and broker
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param userId User identifier
     * @param brokerType Broker type
     * @return Optional SessionDomain
     */
    public Optional<SessionDomain> findActiveSession(String userId, BrokerType brokerType) {
        return findUserSessions(userId).stream()
            .filter(session -> session.brokerType() == brokerType)
            .filter(SessionDomain::isActive)
            .findFirst();
    }

    /**
     * Find all sessions for user
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param userId User identifier
     * @return List of SessionDomain
     */
    public List<SessionDomain> findUserSessions(String userId) {
        return getAllSessionKeys().stream()
            .map(this::retrieveFromRedis)
            .flatMap(Optional::stream)
            .filter(session -> userId.equals(session.userId()))
            .filter(SessionDomain::isActive)
            .collect(Collectors.toList());
    }

    /**
     * Revoke session
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param sessionId Session identifier
     * @return true if revoked successfully
     */
    public boolean revoke(String sessionId) {
        return findById(sessionId)
            .map(SessionDomain::revoke)
            .map(this::saveAndReturnTrue)
            .orElse(false);
    }

    /**
     * Touch session - update last accessed time
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param sessionId Session identifier
     * @return true if updated successfully
     */
    public boolean touch(String sessionId) {
        return findById(sessionId)
            .map(SessionDomain::touch)
            .map(this::saveAndReturnTrue)
            .orElse(false);
    }

    /**
     * Update session status
     *
     * MANDATORY: Rule #9 - Immutability
     *
     * @param sessionId Session identifier
     * @param domain Updated session domain
     * @return true if updated successfully
     */
    public boolean update(String sessionId, SessionDomain domain) {
        return findById(sessionId)
            .map(existing -> domain)
            .map(this::saveAndReturnTrue)
            .orElse(false);
    }

    /**
     * Count active sessions
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return Number of active sessions
     */
    public int countActiveSessions() {
        return (int) getAllSessionKeys().stream()
            .map(this::retrieveFromRedis)
            .flatMap(Optional::stream)
            .filter(SessionDomain::isActive)
            .count();
    }

    /**
     * Find sessions expiring within minutes
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param minutes Minutes threshold
     * @return List of expiring sessions
     */
    public List<SessionDomain> findExpiringWithin(int minutes) {
        return getAllSessionKeys().stream()
            .map(this::retrieveFromRedis)
            .flatMap(Optional::stream)
            .filter(SessionDomain::isActive)
            .filter(session -> session.needsRefresh(minutes))
            .collect(Collectors.toList());
    }

    // ==================== Private Helper Methods ====================

    /**
     * Build Redis key from session domain
     */
    private String buildRedisKey(SessionDomain domain) {
        return BrokerAuthConstants.SESSION_KEY_PREFIX + domain.sessionId();
    }

    /**
     * Build Redis key from session ID
     */
    private String buildRedisKeyFromId(String sessionId) {
        return BrokerAuthConstants.SESSION_KEY_PREFIX + sessionId;
    }

    /**
     * Persist domain to Redis with TTL
     */
    private void persistToRedis(String key, SessionDomain domain) {
        try {
            // Convert domain to entity for Redis storage
            var entity = sessionMapper.toEntity(domain);

            // Calculate TTL from expiry
            Duration ttl = Duration.between(LocalDateTime.now(), domain.expiresAt());

            // Store in Redis
            redisTemplate.opsForValue().set(key, entity, ttl);

            log.debug("Session saved to Redis: {}", domain.sessionId());
        } catch (Exception e) {
            log.error("Failed to persist session to Redis: {}", domain.sessionId(), e);
            throw new RuntimeException("Session persistence failed", e);
        }
    }

    /**
     * Retrieve domain from Redis
     */
    private Optional<SessionDomain> retrieveFromRedis(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key))
                .map(obj -> (com.trademaster.brokerauth.entity.BrokerSession) obj)
                .map(sessionMapper::toDomain);
        } catch (Exception e) {
            log.warn("Failed to retrieve session from Redis: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Get all session keys from Redis
     */
    private Set<String> getAllSessionKeys() {
        try {
            return Optional.ofNullable(redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*"))
                .orElse(Set.of());
        } catch (Exception e) {
            log.warn("Failed to retrieve session keys from Redis", e);
            return Set.of();
        }
    }

    /**
     * Save domain and return true
     */
    private boolean saveAndReturnTrue(SessionDomain domain) {
        save(domain);
        log.info("Session updated: {}", domain.sessionId());
        return true;
    }
}
