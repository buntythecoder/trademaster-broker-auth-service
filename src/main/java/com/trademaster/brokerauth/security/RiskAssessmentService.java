package com.trademaster.brokerauth.security;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Risk Assessment Service - Evaluates security risks
 * 
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Functional Programming - Rule #3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskAssessmentService {
    
    // Using constants from BrokerAuthConstants
    
    // Thread-safe rate limiting
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastRequestTime = new ConcurrentHashMap<>();
    
    /**
     * Assess risk asynchronously
     * 
     * MANDATORY: CompletableFuture - Rule #12
     */
    public CompletableFuture<SecurityResult<SecurityContext>> assess(SecurityContext context) {
        return CompletableFuture.supplyAsync(() -> assessSync(context));
    }
    
    /**
     * Assess risk synchronously
     * 
     * MANDATORY: No if-else - Rule #3
     * MANDATORY: Pattern matching - Rule #14
     */
    public SecurityResult<SecurityContext> assessSync(SecurityContext context) {
        log.debug("Assessing risk for correlation: {}", context.correlationId());
        
        int riskScore = calculateRiskScore(context);
        
        return switch (classifyRiskLevel(riskScore)) {
            case LOW -> SecurityResult.success(context, context);
            case MEDIUM -> {
                log.warn("Medium risk detected: correlation={}, score={}", 
                    context.correlationId(), riskScore);
                yield SecurityResult.success(context, context);
            }
            case HIGH -> {
                log.error("High risk detected: correlation={}, score={}", 
                    context.correlationId(), riskScore);
                yield SecurityResult.failure(SecurityError.RISK_TOO_HIGH, 
                    String.format("Risk score %d exceeds threshold", riskScore));
            }
            case RATE_LIMITED -> SecurityResult.failure(SecurityError.RATE_LIMIT_EXCEEDED, 
                "Rate limit exceeded");
        };
    }
    
    private int calculateRiskScore(SecurityContext context) {
        int score = 0;
        
        // IP-based risk factors
        score += assessIpRisk(context.ipAddress());
        
        // Rate limiting risk
        score += assessRateLimitRisk(context.userId());
        
        // Time-based risk
        score += assessTimingRisk(context.timestamp());
        
        // User agent risk
        score += assessUserAgentRisk(context.userAgent());
        
        return Math.min(score, BrokerAuthConstants.MAX_RISK_SCORE);
    }
    
    private int assessIpRisk(String ipAddress) {
        // Simplified IP risk assessment
        return ipAddress != null && isPrivateIp(ipAddress) 
            ? BrokerAuthConstants.PRIVATE_IP_RISK_SCORE 
            : BrokerAuthConstants.PUBLIC_IP_RISK_SCORE;
    }
    
    private int assessRateLimitRisk(String userId) {
        if (userId == null) return BrokerAuthConstants.MISSING_USER_RISK_SCORE;
        
        String key = BrokerAuthConstants.RATE_LIMIT_KEY_PREFIX + userId;
        AtomicInteger count = requestCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        LocalDateTime lastRequest = lastRequestTime.get(key);
        LocalDateTime now = LocalDateTime.now();
        
        // Functional reset counter logic - Rule #3 Functional Programming
        Optional.ofNullable(lastRequest)
            .filter(request -> request.isAfter(now.minusMinutes(1)))
            .or(() -> {
                count.set(0);
                lastRequestTime.put(key, now);
                return Optional.of(now);
            });

        int currentCount = count.incrementAndGet();
        return currentCount > BrokerAuthConstants.MAX_REQUESTS_PER_MINUTE
            ? BrokerAuthConstants.MAX_RISK_SCORE
            : (currentCount * BrokerAuthConstants.MAX_RISK_SCORE / BrokerAuthConstants.MAX_REQUESTS_PER_MINUTE);
    }
    
    private int assessTimingRisk(LocalDateTime timestamp) {
        return Optional.ofNullable(timestamp)
            .map(ts -> {
                LocalDateTime now = LocalDateTime.now();
                long minutesAgo = java.time.Duration.between(ts, now).toMinutes();
                return calculateTimingRiskScore(minutesAgo);
            })
            .orElse(10);
    }

    /**
     * Functional timing risk calculation - Rule #3 Functional Programming
     */
    private int calculateTimingRiskScore(long minutesAgo) {
        // Pattern matching for timing risk assessment - Rule #14
        return switch (true) {
            case boolean b when minutesAgo > BrokerAuthConstants.TIMING_RISK_THRESHOLD_MINUTES ->
                BrokerAuthConstants.OLD_REQUEST_RISK_SCORE;
            case boolean b when minutesAgo < 0 ->
                BrokerAuthConstants.OLD_REQUEST_RISK_SCORE;
            default -> 0;
        };
    }
    
    private int assessUserAgentRisk(String userAgent) {
        // Functional user agent risk assessment - Rule #3
        return Optional.ofNullable(userAgent)
            .map(String::trim)
            .filter(ua -> !ua.isEmpty())
            .map(ua -> 0)
            .orElse(BrokerAuthConstants.MISSING_USER_AGENT_RISK_SCORE);
    }
    
    private boolean isPrivateIp(String ip) {
        // Simplified private IP check
        return ip != null && (
            ip.startsWith("192.168.") ||
            ip.startsWith("10.") ||
            ip.startsWith("172.") ||
            ip.equals("127.0.0.1")
        );
    }
    
    private RiskLevel classifyRiskLevel(int riskScore) {
        if (riskScore >= BrokerAuthConstants.MAX_RISK_SCORE) return RiskLevel.RATE_LIMITED;
        if (riskScore >= BrokerAuthConstants.HIGH_RISK_THRESHOLD) return RiskLevel.HIGH;
        if (riskScore >= BrokerAuthConstants.MEDIUM_RISK_THRESHOLD) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
    
    private enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        RATE_LIMITED
    }
}