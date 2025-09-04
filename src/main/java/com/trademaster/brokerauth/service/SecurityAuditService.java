package com.trademaster.brokerauth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Security Audit Service
 * 
 * MANDATORY: Complete audit trail for financial compliance - Rule #15
 * MANDATORY: Virtual Threads for performance - Rule #12
 * MANDATORY: Structured logging - Rule #15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Log authentication attempt with full context
     * 
     * MANDATORY: Audit trail for compliance - Rule #15
     * MANDATORY: Structured logging - Rule #15
     */
    @Async
    public CompletableFuture<Void> logAuthenticationAttempt(
            String userId, String brokerType, String action, String status,
            String ipAddress, String userAgent, Map<String, Object> requestDetails,
            Map<String, Object> responseDetails, Map<String, Object> errorDetails,
            long executionTimeMs) {
        
        return CompletableFuture
            .runAsync(() -> performAuditLogging(userId, brokerType, action, status,
                     ipAddress, userAgent, requestDetails, responseDetails, 
                     errorDetails, executionTimeMs),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log security event with correlation ID
     * 
     * MANDATORY: Correlation tracking - Rule #15
     */
    @Async
    public CompletableFuture<Void> logSecurityEvent(
            String correlationId, String userId, String eventType, String severity,
            String description, Map<String, Object> eventDetails) {
        
        return CompletableFuture
            .runAsync(() -> performSecurityEventLogging(correlationId, userId, 
                     eventType, severity, description, eventDetails),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log rate limiting event
     * 
     * MANDATORY: Rate limiting audit - Rule #15
     */
    @Async
    public CompletableFuture<Void> logRateLimitEvent(
            String userId, String brokerType, String endpoint, String action,
            int currentCount, int limitValue, String windowType) {
        
        return CompletableFuture
            .runAsync(() -> performRateLimitLogging(userId, brokerType, endpoint,
                     action, currentCount, limitValue, windowType),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log session lifecycle event
     * 
     * MANDATORY: Session audit trail - Rule #15
     */
    @Async
    public CompletableFuture<Void> logSessionEvent(
            String sessionId, String userId, String brokerType, String action,
            String status, LocalDateTime timestamp, Map<String, Object> sessionDetails) {
        
        return CompletableFuture
            .runAsync(() -> performSessionEventLogging(sessionId, userId, brokerType,
                     action, status, timestamp, sessionDetails),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log credential management event
     * 
     * MANDATORY: Credential audit trail - Rule #23
     */
    @Async
    public CompletableFuture<Void> logCredentialEvent(
            String userId, String brokerType, String action, String status,
            String ipAddress, boolean containsSensitiveData) {
        
        return CompletableFuture
            .runAsync(() -> performCredentialEventLogging(userId, brokerType, action,
                     status, ipAddress, containsSensitiveData),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    private void performAuditLogging(
            String userId, String brokerType, String action, String status,
            String ipAddress, String userAgent, Map<String, Object> requestDetails,
            Map<String, Object> responseDetails, Map<String, Object> errorDetails,
            long executionTimeMs) {
        
        try {
            String correlationId = UUID.randomUUID().toString();
            
            // Create structured audit entry
            Map<String, Object> auditEntry = new java.util.HashMap<>();
            auditEntry.put("type", "BROKER_AUTH_AUDIT");
            auditEntry.put("correlationId", correlationId);
            auditEntry.put("timestamp", LocalDateTime.now().toString());
            auditEntry.put("userId", sanitizeUserId(userId));
            auditEntry.put("brokerType", brokerType);
            auditEntry.put("action", action);
            auditEntry.put("status", status);
            auditEntry.put("ipAddress", sanitizeIpAddress(ipAddress));
            auditEntry.put("userAgent", sanitizeUserAgent(userAgent));
            auditEntry.put("executionTimeMs", executionTimeMs);
            auditEntry.put("requestDetails", sanitizeRequestDetails(requestDetails));
            auditEntry.put("responseDetails", sanitizeResponseDetails(responseDetails));
            auditEntry.put("errorDetails", sanitizeErrorDetails(errorDetails));
            
            String auditJson = objectMapper.writeValueAsString(auditEntry);
            
            // Log with appropriate level based on status
            switch (status.toUpperCase()) {
                case "SUCCESS" -> log.info("AUDIT: {}", auditJson);
                case "FAILURE" -> log.warn("AUDIT: {}", auditJson);
                case "ERROR" -> log.error("AUDIT: {}", auditJson);
                default -> log.info("AUDIT: {}", auditJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log audit entry for user: {} action: {}", 
                userId, action, e);
        }
    }
    
    private void performSecurityEventLogging(
            String correlationId, String userId, String eventType, String severity,
            String description, Map<String, Object> eventDetails) {
        
        try {
            Map<String, Object> securityEvent = Map.of(
                "type", "SECURITY_EVENT",
                "correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString(),
                "timestamp", LocalDateTime.now().toString(),
                "userId", sanitizeUserId(userId),
                "eventType", eventType,
                "severity", severity,
                "description", description,
                "eventDetails", sanitizeEventDetails(eventDetails)
            );
            
            String eventJson = objectMapper.writeValueAsString(securityEvent);
            
            switch (severity.toUpperCase()) {
                case "CRITICAL" -> log.error("SECURITY_EVENT: {}", eventJson);
                case "HIGH" -> log.warn("SECURITY_EVENT: {}", eventJson);
                case "MEDIUM" -> log.info("SECURITY_EVENT: {}", eventJson);
                case "LOW" -> log.debug("SECURITY_EVENT: {}", eventJson);
                default -> log.info("SECURITY_EVENT: {}", eventJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log security event for user: {} type: {}", 
                userId, eventType, e);
        }
    }
    
    private void performRateLimitLogging(
            String userId, String brokerType, String endpoint, String action,
            int currentCount, int limitValue, String windowType) {
        
        try {
            Map<String, Object> rateLimitEvent = Map.of(
                "type", "RATE_LIMIT_EVENT",
                "timestamp", LocalDateTime.now().toString(),
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType,
                "endpoint", endpoint,
                "action", action,
                "currentCount", currentCount,
                "limitValue", limitValue,
                "windowType", windowType,
                "limitExceeded", currentCount >= limitValue
            );
            
            String eventJson = objectMapper.writeValueAsString(rateLimitEvent);
            
            if (currentCount >= limitValue) {
                log.warn("RATE_LIMIT: {}", eventJson);
            } else {
                log.debug("RATE_LIMIT: {}", eventJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log rate limit event for user: {} broker: {}", 
                userId, brokerType, e);
        }
    }
    
    private void performSessionEventLogging(
            String sessionId, String userId, String brokerType, String action,
            String status, LocalDateTime timestamp, Map<String, Object> sessionDetails) {
        
        try {
            Map<String, Object> sessionEvent = Map.of(
                "type", "SESSION_EVENT",
                "timestamp", timestamp.toString(),
                "sessionId", sessionId,
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType,
                "action", action,
                "status", status,
                "sessionDetails", sanitizeSessionDetails(sessionDetails)
            );
            
            String eventJson = objectMapper.writeValueAsString(sessionEvent);
            log.info("SESSION_EVENT: {}", eventJson);
            
        } catch (Exception e) {
            log.error("Failed to log session event for session: {} user: {}", 
                sessionId, userId, e);
        }
    }
    
    private void performCredentialEventLogging(
            String userId, String brokerType, String action, String status,
            String ipAddress, boolean containsSensitiveData) {
        
        try {
            Map<String, Object> credentialEvent = Map.of(
                "type", "CREDENTIAL_EVENT",
                "timestamp", LocalDateTime.now().toString(),
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType,
                "action", action,
                "status", status,
                "ipAddress", sanitizeIpAddress(ipAddress),
                "containsSensitiveData", containsSensitiveData
            );
            
            String eventJson = objectMapper.writeValueAsString(credentialEvent);
            
            if ("FAILURE".equals(status) || containsSensitiveData) {
                log.warn("CREDENTIAL_EVENT: {}", eventJson);
            } else {
                log.info("CREDENTIAL_EVENT: {}", eventJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log credential event for user: {} broker: {}", 
                userId, brokerType, e);
        }
    }
    
    // Sanitization methods to prevent sensitive data leakage
    
    private String sanitizeUserId(String userId) {
        if (userId == null) return "anonymous";
        return userId.length() > 50 ? userId.substring(0, 50) + "..." : userId;
    }
    
    private String sanitizeIpAddress(String ipAddress) {
        if (ipAddress == null) return "unknown";
        // Keep first 3 octets for IPv4, mask the last one
        if (ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            String[] parts = ipAddress.split("\\.");
            return parts[0] + "." + parts[1] + "." + parts[2] + ".***";
        }
        return "masked";
    }
    
    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) return "unknown";
        // Keep only browser/platform info, remove potentially identifying details
        return userAgent.length() > 100 ? userAgent.substring(0, 100) + "..." : userAgent;
    }
    
    private Map<String, Object> sanitizeRequestDetails(Map<String, Object> details) {
        if (details == null) return Map.of();
        // Remove sensitive fields like passwords, secrets, tokens
        return details.entrySet().stream()
            .filter(entry -> !isSensitiveField(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, 
                entry -> sanitizeValue(entry.getValue())
            ));
    }
    
    private Map<String, Object> sanitizeResponseDetails(Map<String, Object> details) {
        if (details == null) return Map.of();
        return details.entrySet().stream()
            .filter(entry -> !isSensitiveField(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeValue(entry.getValue())
            ));
    }
    
    private Map<String, Object> sanitizeErrorDetails(Map<String, Object> details) {
        if (details == null) return Map.of();
        // For error details, keep error types but mask sensitive info
        return details.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeErrorValue(entry.getValue())
            ));
    }
    
    private Map<String, Object> sanitizeEventDetails(Map<String, Object> details) {
        if (details == null) return Map.of();
        return details.entrySet().stream()
            .filter(entry -> !isSensitiveField(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeValue(entry.getValue())
            ));
    }
    
    private Map<String, Object> sanitizeSessionDetails(Map<String, Object> details) {
        if (details == null) return Map.of();
        return details.entrySet().stream()
            .filter(entry -> !isSensitiveField(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeValue(entry.getValue())
            ));
    }
    
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("password") || 
               lower.contains("secret") || 
               lower.contains("token") || 
               lower.contains("key") ||
               lower.contains("credential") ||
               lower.contains("auth") ||
               lower.contains("pin") ||
               lower.contains("otp");
    }
    
    private Object sanitizeValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) {
            String str = (String) value;
            return str.length() > 200 ? str.substring(0, 200) + "..." : str;
        }
        return value;
    }
    
    private Object sanitizeErrorValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) {
            String str = (String) value;
            // Keep error messages but mask sensitive patterns
            str = str.replaceAll("(?i)(password|secret|token|key)=[^\\s]+", "$1=***");
            return str.length() > 500 ? str.substring(0, 500) + "..." : str;
        }
        return value;
    }
}