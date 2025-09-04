package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.repository.BrokerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Broker Rate Limiting Service
 * 
 * MANDATORY: Rate limiting for broker APIs - Rule #22
 * MANDATORY: Virtual Threads for performance - Rule #12
 * MANDATORY: Redis for distributed rate limiting - Rule #22
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerRateLimitService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final BrokerRepository brokerRepository;
    private final SecurityAuditService auditService;
    
    /**
     * Check if API call is allowed for user and broker
     * 
     * MANDATORY: Pattern matching - Rule #14
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<Boolean> isAllowed(String userId, BrokerType brokerType, String endpoint) {
        return CompletableFuture
            .supplyAsync(() -> performRateLimitCheck(userId, brokerType, endpoint),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleRateLimitResult);
    }
    
    /**
     * Record API call for rate limiting
     * 
     * MANDATORY: Atomic operations for consistency - Rule #22
     */
    public CompletableFuture<Void> recordApiCall(String userId, BrokerType brokerType, String endpoint) {
        return CompletableFuture
            .runAsync(() -> performApiCallRecording(userId, brokerType, endpoint),
                     Executors.newVirtualThreadPerTaskExecutor())
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to record API call for user: {} broker: {} endpoint: {}", 
                        userId, brokerType, endpoint, throwable);
                }
                return null;
            });
    }
    
    /**
     * Get current rate limit status for user
     * 
     * MANDATORY: User visibility into rate limits - Rule #22
     */
    public CompletableFuture<Map<String, Integer>> getRateLimitStatus(
            String userId, BrokerType brokerType) {
        
        return CompletableFuture
            .supplyAsync(() -> retrieveRateLimitStatus(userId, brokerType),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle((status, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to get rate limit status for user: {} broker: {}", 
                        userId, brokerType, throwable);
                    return Map.of();
                }
                return status;
            });
    }
    
    /**
     * Reset rate limits for user (admin operation)
     * 
     * MANDATORY: Administrative controls - Rule #23
     */
    public CompletableFuture<Boolean> resetUserRateLimits(String userId, BrokerType brokerType) {
        return CompletableFuture
            .supplyAsync(() -> performRateLimitReset(userId, brokerType),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to reset rate limits for user: {} broker: {}", 
                        userId, brokerType, throwable);
                    return false;
                }
                return result;
            });
    }
    
    private boolean performRateLimitCheck(String userId, BrokerType brokerType, String endpoint) {
        try {
            // Get broker rate limits from database
            Optional<Map<String, Integer>> rateLimitsOpt = 
                brokerRepository.findRateLimitsByBrokerType(brokerType);
            
            if (rateLimitsOpt.isEmpty()) {
                log.warn("No rate limits found for broker: {}", brokerType);
                return true; // Allow if no limits configured
            }
            
            Map<String, Integer> rateLimits = rateLimitsOpt.get();
            
            // Check per-second limit
            boolean perSecondAllowed = checkRateLimit(
                userId, brokerType, endpoint, "per_second", 
                rateLimits.getOrDefault("per_second", Integer.MAX_VALUE), 1);
            
            // Check per-minute limit
            boolean perMinuteAllowed = checkRateLimit(
                userId, brokerType, endpoint, "per_minute",
                rateLimits.getOrDefault("per_minute", Integer.MAX_VALUE), 60);
            
            // Check per-day limit
            boolean perDayAllowed = checkRateLimit(
                userId, brokerType, endpoint, "per_day",
                rateLimits.getOrDefault("per_day", Integer.MAX_VALUE), 24 * 60);
            
            boolean allowed = perSecondAllowed && perMinuteAllowed && perDayAllowed;
            
            if (!allowed) {
                auditService.logRateLimitEvent(userId, brokerType.name(), endpoint, 
                    "CHECK", getCurrentCount(userId, brokerType, endpoint, "per_second"), 
                    rateLimits.getOrDefault("per_second", 0), "per_second");
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Rate limit check failed for user: {} broker: {} endpoint: {}", 
                userId, brokerType, endpoint, e);
            return false; // Fail closed for security
        }
    }
    
    private boolean checkRateLimit(String userId, BrokerType brokerType, String endpoint, 
                                  String windowType, int limit, int windowMinutes) {
        
        String key = buildRedisKey(userId, brokerType, endpoint, windowType);
        
        try {
            // Get current count
            String countStr = (String) redisTemplate.opsForValue().get(key);
            int currentCount = countStr != null ? Integer.parseInt(countStr) : 0;
            
            return currentCount < limit;
            
        } catch (Exception e) {
            log.error("Failed to check rate limit for key: {}", key, e);
            return false; // Fail closed
        }
    }
    
    private void performApiCallRecording(String userId, BrokerType brokerType, String endpoint) {
        try {
            // Increment counters for different time windows
            incrementCounter(userId, brokerType, endpoint, "per_second", 1);
            incrementCounter(userId, brokerType, endpoint, "per_minute", 60);  
            incrementCounter(userId, brokerType, endpoint, "per_day", 24 * 60);
            
            log.debug("API call recorded for user: {} broker: {} endpoint: {}", 
                userId, brokerType, endpoint);
                
        } catch (Exception e) {
            log.error("Failed to record API call for user: {} broker: {} endpoint: {}", 
                userId, brokerType, endpoint, e);
        }
    }
    
    private void incrementCounter(String userId, BrokerType brokerType, String endpoint, 
                                 String windowType, int windowMinutes) {
        
        String key = buildRedisKey(userId, brokerType, endpoint, windowType);
        
        try {
            // Increment the counter
            Long newCount = redisTemplate.opsForValue().increment(key, 1);
            
            // Set expiry if this is the first increment
            if (newCount != null && newCount == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
            }
            
        } catch (Exception e) {
            log.error("Failed to increment counter for key: {}", key, e);
        }
    }
    
    private int getCurrentCount(String userId, BrokerType brokerType, String endpoint, String windowType) {
        String key = buildRedisKey(userId, brokerType, endpoint, windowType);
        
        try {
            String countStr = (String) redisTemplate.opsForValue().get(key);
            return countStr != null ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("Failed to get current count for key: {}", key, e);
            return 0;
        }
    }
    
    private Map<String, Integer> retrieveRateLimitStatus(String userId, BrokerType brokerType) {
        try {
            return Map.of(
                "per_second", getCurrentCount(userId, brokerType, "default", "per_second"),
                "per_minute", getCurrentCount(userId, brokerType, "default", "per_minute"),
                "per_day", getCurrentCount(userId, brokerType, "default", "per_day")
            );
        } catch (Exception e) {
            log.error("Failed to retrieve rate limit status for user: {} broker: {}", 
                userId, brokerType, e);
            return Map.of();
        }
    }
    
    private boolean performRateLimitReset(String userId, BrokerType brokerType) {
        try {
            String pattern = String.format("rate_limit:%s:%s:*", 
                sanitizeKeyComponent(userId), brokerType.name());
            
            // Find all keys for this user and broker
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Reset {} rate limit keys for user: {} broker: {}", 
                    keys.size(), userId, brokerType);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to reset rate limits for user: {} broker: {}", 
                userId, brokerType, e);
            return false;
        }
    }
    
    private String buildRedisKey(String userId, BrokerType brokerType, String endpoint, String windowType) {
        return String.format("rate_limit:%s:%s:%s:%s", 
            sanitizeKeyComponent(userId), 
            brokerType.name(), 
            sanitizeKeyComponent(endpoint), 
            windowType);
    }
    
    private String sanitizeKeyComponent(String component) {
        if (component == null) return "null";
        // Remove or replace characters that might cause issues in Redis keys
        return component.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    
    private Boolean handleRateLimitResult(Boolean result, Throwable throwable) {
        if (throwable != null) {
            log.error("Rate limit check failed", throwable);
            return false; // Fail closed for security
        }
        return result;
    }
}