package com.trademaster.brokerauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Audit Event Publisher
 * 
 * MANDATORY: Event-driven audit trail - Rule #15
 * MANDATORY: Virtual Threads for performance - Rule #12
 * MANDATORY: Kafka for event streaming - Rule #26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${app.kafka.topics.broker-auth-events:broker-auth-events}")
    private String brokerAuthEventsTopic;
    
    @Value("${app.kafka.topics.session-events:session-events}")
    private String sessionEventsTopic;
    
    @Value("${app.kafka.topics.rate-limit-events:rate-limit-events}")
    private String rateLimitEventsTopic;
    
    /**
     * Publish broker authentication event
     * 
     * MANDATORY: Audit trail for compliance - Rule #15
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<Boolean> publishAuthenticationEvent(
            String userId, String brokerType, String action, String status,
            String ipAddress, long executionTimeMs, Map<String, Object> details) {
        
        return CompletableFuture
            .supplyAsync(() -> createAuthenticationEvent(userId, brokerType, action,
                        status, ipAddress, executionTimeMs, details),
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(event -> publishEvent(brokerAuthEventsTopic, userId, event))
            .handle(this::handlePublishResult);
    }
    
    /**
     * Publish session lifecycle event
     * 
     * MANDATORY: Session audit trail - Rule #15
     */
    public CompletableFuture<Boolean> publishSessionEvent(
            String sessionId, String userId, String brokerType, String action,
            String status, Map<String, Object> sessionDetails) {
        
        return CompletableFuture
            .supplyAsync(() -> createSessionEvent(sessionId, userId, brokerType,
                        action, status, sessionDetails),
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(event -> publishEvent(sessionEventsTopic, sessionId, event))
            .handle(this::handlePublishResult);
    }
    
    /**
     * Publish rate limit event
     * 
     * MANDATORY: Rate limiting audit - Rule #15
     */
    public CompletableFuture<Boolean> publishRateLimitEvent(
            String userId, String brokerType, String endpoint, String action,
            int currentCount, int limitValue, boolean limitExceeded) {
        
        return CompletableFuture
            .supplyAsync(() -> createRateLimitEvent(userId, brokerType, endpoint,
                        action, currentCount, limitValue, limitExceeded),
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(event -> publishEvent(rateLimitEventsTopic, userId, event))
            .handle(this::handlePublishResult);
    }
    
    /**
     * Publish security event
     * 
     * MANDATORY: Security incident tracking - Rule #23
     */
    public CompletableFuture<Boolean> publishSecurityEvent(
            String userId, String eventType, String severity, String description,
            Map<String, Object> eventDetails) {
        
        return CompletableFuture
            .supplyAsync(() -> createSecurityEvent(userId, eventType, severity,
                        description, eventDetails),
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(event -> publishEvent(brokerAuthEventsTopic, userId, event))
            .handle(this::handlePublishResult);
    }
    
    /**
     * Publish batch audit events
     * 
     * MANDATORY: Batch processing for efficiency - Rule #22
     */
    public CompletableFuture<Integer> publishBatchEvents(
            String topic, Map<String, String> events) {
        
        return CompletableFuture
            .supplyAsync(() -> processBatchEvents(topic, events),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle((successCount, throwable) -> {
                if (throwable != null) {
                    log.error("Batch event publishing failed for topic: {}", topic, throwable);
                    return 0;
                }
                return successCount;
            });
    }
    
    private String createAuthenticationEvent(
            String userId, String brokerType, String action, String status,
            String ipAddress, long executionTimeMs, Map<String, Object> details) {
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "BROKER_AUTHENTICATION",
                "eventId", UUID.randomUUID().toString(),
                "timestamp", LocalDateTime.now().toString(),
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType,
                "action", action,
                "status", status,
                "ipAddress", sanitizeIpAddress(ipAddress),
                "executionTimeMs", executionTimeMs,
                "details", sanitizeEventDetails(details)
            );
            
            return objectMapper.writeValueAsString(event);
            
        } catch (Exception e) {
            log.error("Failed to create authentication event", e);
            throw new RuntimeException("Event creation failed", e);
        }
    }
    
    private String createSessionEvent(
            String sessionId, String userId, String brokerType, String action,
            String status, Map<String, Object> sessionDetails) {
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "BROKER_SESSION",
                "eventId", UUID.randomUUID().toString(),
                "timestamp", LocalDateTime.now().toString(),
                "sessionId", sessionId,
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType,
                "action", action,
                "status", status,
                "sessionDetails", sanitizeEventDetails(sessionDetails)
            );
            
            return objectMapper.writeValueAsString(event);
            
        } catch (Exception e) {
            log.error("Failed to create session event", e);
            throw new RuntimeException("Event creation failed", e);
        }
    }
    
    private String createRateLimitEvent(
            String userId, String brokerType, String endpoint, String action,
            int currentCount, int limitValue, boolean limitExceeded) {
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "RATE_LIMIT",
                "eventId", UUID.randomUUID().toString(),
                "timestamp", LocalDateTime.now().toString(),
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType,
                "endpoint", endpoint,
                "action", action,
                "currentCount", currentCount,
                "limitValue", limitValue,
                "limitExceeded", limitExceeded
            );
            
            return objectMapper.writeValueAsString(event);
            
        } catch (Exception e) {
            log.error("Failed to create rate limit event", e);
            throw new RuntimeException("Event creation failed", e);
        }
    }
    
    private String createSecurityEvent(
            String userId, String eventType, String severity, String description,
            Map<String, Object> eventDetails) {
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "SECURITY_EVENT",
                "eventId", UUID.randomUUID().toString(),
                "timestamp", LocalDateTime.now().toString(),
                "userId", sanitizeUserId(userId),
                "securityEventType", eventType,
                "severity", severity,
                "description", description,
                "eventDetails", sanitizeEventDetails(eventDetails)
            );
            
            return objectMapper.writeValueAsString(event);
            
        } catch (Exception e) {
            log.error("Failed to create security event", e);
            throw new RuntimeException("Event creation failed", e);
        }
    }
    
    private CompletableFuture<Boolean> publishEvent(String topic, String key, String eventJson) {
        return kafkaTemplate.send(topic, key, eventJson)
            .thenApply(this::handleKafkaResult)
            .exceptionally(this::handleKafkaException);
    }
    
    private Integer processBatchEvents(String topic, Map<String, String> events) {
        int successCount = 0;
        
        for (Map.Entry<String, String> entry : events.entrySet()) {
            try {
                kafkaTemplate.send(topic, entry.getKey(), entry.getValue()).get();
                successCount++;
            } catch (Exception e) {
                log.error("Failed to publish batch event with key: {}", entry.getKey(), e);
            }
        }
        
        log.info("Published {} out of {} batch events to topic: {}", 
            successCount, events.size(), topic);
        
        return successCount;
    }
    
    private Boolean handleKafkaResult(SendResult<String, String> result) {
        log.debug("Event published successfully to topic: {} partition: {} offset: {}",
            result.getRecordMetadata().topic(),
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset());
        return true;
    }
    
    private Boolean handleKafkaException(Throwable throwable) {
        log.error("Failed to publish event to Kafka", throwable);
        return false;
    }
    
    private Boolean handlePublishResult(Boolean result, Throwable throwable) {
        if (throwable != null) {
            log.error("Event publishing failed", throwable);
            return false;
        }
        return result;
    }
    
    // Sanitization methods to prevent sensitive data leakage
    
    private String sanitizeUserId(String userId) {
        if (userId == null) return "anonymous";
        return userId.length() > 50 ? userId.substring(0, 50) + "..." : userId;
    }
    
    private String sanitizeIpAddress(String ipAddress) {
        if (ipAddress == null) return "unknown";
        
        // Mask the last octet for IPv4 addresses
        if (ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            String[] parts = ipAddress.split("\\.");
            return parts[0] + "." + parts[1] + "." + parts[2] + ".***";
        }
        
        return "masked";
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
}