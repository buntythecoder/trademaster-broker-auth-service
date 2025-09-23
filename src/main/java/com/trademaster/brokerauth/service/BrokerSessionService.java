package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            session.setStatus(SessionStatus.REVOKED);
            saveSession(session);
            log.info("Session revoked: {}", sessionId);
        });
    }
    
    /**
     * Get active session count for monitoring
     */
    public int getActiveSessionCount() {
        try {
            Set<String> keys = redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
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
     * Get user's active sessions
     */
    public List<BrokerSession> getUserActiveSessions(String userId) {
        try {
            Set<String> keys = redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*");
            if (keys == null) {
                return List.of();
            }
            
            return keys.stream()
                .map(key -> redisTemplate.opsForValue().get(key))
                .filter(session -> session != null)
                .filter(session -> userId.equals(session.getUserId()))
                .filter(session -> session.getStatus() == SessionStatus.ACTIVE)
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get user active sessions for userId: {}", userId, e);
            return List.of();
        }
    }
    
    /**
     * Update session last access time
     */
    public boolean updateLastAccess(String sessionId) {
        try {
            Optional<BrokerSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isPresent()) {
                BrokerSession session = sessionOpt.get();
                session.setLastAccessedAt(LocalDateTime.now());
                saveSession(session);
                log.debug("Updated last access time for session: {}", sessionId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to update last access for session: {}", sessionId, e);
            return false;
        }
    }
}