package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

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
}