package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Broker Session Service
 * 
 * Session lifecycle management with Redis caching.
 * 
 * MANDATORY: Zero Trust (Internal) - Rule #6
 * MANDATORY: Functional Programming - Rule #3
 */
@Service
@RequiredArgsConstructor 
@Slf4j
public class BrokerSessionService {
    
    private final RedisTemplate<String, BrokerSession> redisTemplate;
    
    public void saveSession(BrokerSession session) {
        String key = BrokerAuthConstants.SESSION_KEY_PREFIX + session.getSessionId();
        Duration ttl = Duration.between(LocalDateTime.now(), session.getExpiresAt());
        
        redisTemplate.opsForValue().set(key, session, ttl);
        log.debug("Session saved: {}", session.getSessionId());
    }
    
    public Optional<BrokerSession> getSession(String sessionId) {
        String key = BrokerAuthConstants.SESSION_KEY_PREFIX + sessionId;
        BrokerSession session = redisTemplate.opsForValue().get(key);
        
        return Optional.ofNullable(session)
            .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
            .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()));
    }
    
    public void revokeSession(String sessionId) {
        getSession(sessionId).ifPresent(session -> {
            BrokerSession revokedSession = session.withStatus(SessionStatus.REVOKED);
            saveSession(revokedSession);
            log.info("Session revoked: {}", sessionId);
        });
    }
    
    /**
     * Get active session count for monitoring
     *
     * MANDATORY: Rule #22 - Performance optimization with Redis SCAN
     *
     * Uses SCAN instead of KEYS for non-blocking iteration.
     * KEYS command blocks Redis and has O(N) complexity - NEVER use in production!
     * SCAN command is non-blocking and suitable for production use.
     *
     * Performance: O(N) but non-blocking, safe for production
     */
    public int getActiveSessionCount() {
        try {
            return scanSessionKeys().size();
        } catch (Exception e) {
            log.warn("Failed to get active session count", e);
            return 0;
        }
    }
    
    /**
     * Get today's session count for statistics
     */
    public int getTodaySessionCount() {
        try {
            // In a production system, this would query a database or separate counter
            // For now, return the active count as an approximation
            return getActiveSessionCount();
        } catch (Exception e) {
            log.warn("Failed to get today's session count", e);
            return 0;
        }
    }
    
    /**
     * Get user's active sessions - Rule #3 Functional Programming
     *
     * MANDATORY: Rule #22 - Performance optimization with Redis SCAN
     *
     * Uses SCAN instead of KEYS for non-blocking iteration.
     * Performance: O(N) but non-blocking, safe for production
     */
    public List<BrokerSession> getUserActiveSessions(String userId) {
        return executeWithErrorHandling(
            () -> {
                List<String> keys = scanSessionKeys();
                return extractActiveSessions(keys).stream()
                    .filter(session -> userId.equals(session.getUserId()))
                    .collect(Collectors.toList());
            },
            () -> {
                log.warn("Failed to get user active sessions for userId: {}", userId);
                return List.of();
            }
        );
    }

    /**
     * Get active session for user and broker type - Rule #3 Functional Programming
     */
    public Optional<BrokerSession> getActiveSession(String userId, com.trademaster.brokerauth.enums.BrokerType brokerType) {
        return executeWithErrorHandling(
            () -> getUserActiveSessions(userId).stream()
                .filter(session -> session.getBrokerType() == brokerType)
                .filter(BrokerSession::isActive)
                .findFirst(),
            () -> {
                log.warn("Failed to get active session for userId: {} brokerType: {}", userId, brokerType);
                return Optional.empty();
            }
        );
    }

    /**
     * Scan Redis keys using non-blocking SCAN command
     *
     * MANDATORY: Rule #22 - Performance Standards
     *
     * SCAN is production-safe, KEYS is not!
     * - KEYS: Blocks Redis, O(N) time all at once
     * - SCAN: Non-blocking, iterative O(N) time
     *
     * Performance: Safe for production, no Redis blocking
     */
    private List<String> scanSessionKeys() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
            .match(BrokerAuthConstants.SESSION_KEY_PREFIX + "*")
            .count(100) // Scan 100 keys per iteration
            .build();

        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                connection -> connection.scan(options))) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } catch (Exception e) {
            log.warn("Error scanning session keys", e);
        }

        return keys;
    }

    /**
     * Functional session extraction pipeline
     *
     * MANDATORY: Rule #22 - Performance optimization
     *
     * Efficiently extracts active sessions from Redis keys.
     * Uses Stream API for functional pipeline processing.
     */
    private List<BrokerSession> extractActiveSessions(List<String> keys) {
        return keys.stream()
            .map(key -> redisTemplate.opsForValue().get(key))
            .filter(java.util.Objects::nonNull)
            .filter(this::isActiveSession)
            .collect(Collectors.toList());
    }

    /**
     * Functional predicate for active session validation
     */
    private boolean isActiveSession(BrokerSession session) {
        return session.getStatus() == SessionStatus.ACTIVE &&
               session.getExpiresAt().isAfter(LocalDateTime.now());
    }
    
    /**
     * Update session last access time - Rule #3 Functional Programming
     */
    public boolean updateLastAccess(String sessionId) {
        return executeWithErrorHandling(
            () -> getSession(sessionId)
                .map(this::updateSessionAccess)
                .map(this::persistUpdatedSession)
                .map(session -> {
                    log.debug("Updated last access time for session: {}", sessionId);
                    return true;
                })
                .orElse(false),
            () -> {
                log.warn("Failed to update last access for session: {}", sessionId);
                return false;
            }
        );
    }

    /**
     * Functional session access update
     */
    private BrokerSession updateSessionAccess(BrokerSession session) {
        return session.withLastAccessed(LocalDateTime.now());
    }

    /**
     * Functional session persistence
     */
    private BrokerSession persistUpdatedSession(BrokerSession session) {
        saveSession(session);
        return session;
    }

    /**
     * Functional error handling wrapper - Rule #11 Error Handling
     */
    private <T> T executeWithErrorHandling(java.util.function.Supplier<T> operation, java.util.function.Supplier<T> fallback) {
        try {
            return operation.get();
        } catch (Exception e) {
            return fallback.get();
        }
    }
}