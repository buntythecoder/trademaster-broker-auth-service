package com.trademaster.brokerauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Structured Logging Service
 * 
 * MANDATORY: Structured logging with correlation IDs - Rule #15
 * MANDATORY: Virtual Threads for async logging - Rule #12
 * MANDATORY: Functional patterns - Rule #3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StructuredLoggingService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Log business event with structured format
     * 
     * MANDATORY: Structured logging - Rule #15
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<Void> logBusinessEvent(
            String eventType, String userId, String brokerType, 
            String action, String status, Map<String, Object> details) {
        
        return CompletableFuture
            .runAsync(() -> performBusinessEventLogging(eventType, userId, brokerType,
                     action, status, details),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log performance metrics
     * 
     * MANDATORY: Performance monitoring - Rule #22
     */
    public CompletableFuture<Void> logPerformanceMetric(
            String operation, long executionTimeMs, boolean success,
            Map<String, Object> metrics) {
        
        return CompletableFuture
            .runAsync(() -> performPerformanceLogging(operation, executionTimeMs, 
                     success, metrics),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log error event with context
     * 
     * MANDATORY: Error tracking - Rule #11
     */
    public CompletableFuture<Void> logError(
            String operation, String errorType, String errorMessage,
            Throwable throwable, Map<String, Object> context) {
        
        return CompletableFuture
            .runAsync(() -> performErrorLogging(operation, errorType, errorMessage,
                     throwable, context),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Log integration event (API calls, database operations)
     * 
     * MANDATORY: Integration monitoring - Rule #22
     */
    public CompletableFuture<Void> logIntegrationEvent(
            String integrationType, String endpoint, String method,
            int statusCode, long responseTimeMs, Map<String, Object> details) {
        
        return CompletableFuture
            .runAsync(() -> performIntegrationLogging(integrationType, endpoint,
                     method, statusCode, responseTimeMs, details),
                     Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Create correlation context for request tracking
     * 
     * MANDATORY: Correlation tracking - Rule #15
     */
    public String createCorrelationContext(String userId, String operation) {
        String correlationId = UUID.randomUUID().toString();
        
        MDC.put("correlationId", correlationId);
        MDC.put("userId", sanitizeUserId(userId));
        MDC.put("operation", operation);
        MDC.put("timestamp", LocalDateTime.now().toString());
        
        return correlationId;
    }
    
    /**
     * Clear correlation context
     * 
     * MANDATORY: Context cleanup - Rule #15
     */
    public void clearCorrelationContext() {
        MDC.clear();
    }
    
    /**
     * Execute operation with correlation context
     * 
     * MANDATORY: Contextual execution - Rule #15
     */
    public <T> CompletableFuture<T> executeWithCorrelation(
            String userId, String operation, java.util.function.Supplier<T> supplier) {
        
        return CompletableFuture
            .supplyAsync(() -> {
                String correlationId = createCorrelationContext(userId, operation);
                try {
                    T result = supplier.get();
                    logBusinessEvent("OPERATION_SUCCESS", userId, null, operation, 
                        "SUCCESS", Map.of("correlationId", correlationId));
                    return result;
                } catch (Exception e) {
                    logError(operation, e.getClass().getSimpleName(), e.getMessage(), 
                        e, Map.of("correlationId", correlationId));
                    throw e;
                } finally {
                    clearCorrelationContext();
                }
            }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    private void performBusinessEventLogging(
            String eventType, String userId, String brokerType,
            String action, String status, Map<String, Object> details) {
        
        try {
            Map<String, Object> logEntry = Map.of(
                "type", "BUSINESS_EVENT",
                "eventType", eventType,
                "timestamp", LocalDateTime.now().toString(),
                "correlationId", MDC.get("correlationId"),
                "userId", sanitizeUserId(userId),
                "brokerType", brokerType != null ? brokerType : "N/A",
                "action", action,
                "status", status,
                "details", sanitizeLogDetails(details)
            );
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            
            switch (status.toUpperCase()) {
                case "SUCCESS" -> log.info("BUSINESS_EVENT: {}", logJson);
                case "FAILURE" -> log.warn("BUSINESS_EVENT: {}", logJson);
                case "ERROR" -> log.error("BUSINESS_EVENT: {}", logJson);
                default -> log.info("BUSINESS_EVENT: {}", logJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log business event: {} action: {}", eventType, action, e);
        }
    }
    
    private void performPerformanceLogging(
            String operation, long executionTimeMs, boolean success,
            Map<String, Object> metrics) {
        
        try {
            Map<String, Object> logEntry = Map.of(
                "type", "PERFORMANCE_METRIC",
                "timestamp", LocalDateTime.now().toString(),
                "correlationId", MDC.get("correlationId"),
                "operation", operation,
                "executionTimeMs", executionTimeMs,
                "success", success,
                "metrics", sanitizeLogDetails(metrics)
            );
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            
            // Log as warning if execution time is too high or operation failed
            if (executionTimeMs > 5000 || !success) {
                log.warn("PERFORMANCE_METRIC: {}", logJson);
            } else {
                log.info("PERFORMANCE_METRIC: {}", logJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log performance metric for operation: {}", operation, e);
        }
    }
    
    private void performErrorLogging(
            String operation, String errorType, String errorMessage,
            Throwable throwable, Map<String, Object> context) {
        
        try {
            Map<String, Object> logEntry = Map.of(
                "type", "ERROR_EVENT",
                "timestamp", LocalDateTime.now().toString(),
                "correlationId", MDC.get("correlationId"),
                "operation", operation,
                "errorType", errorType,
                "errorMessage", sanitizeErrorMessage(errorMessage),
                "stackTrace", throwable != null ? sanitizeStackTrace(throwable) : "N/A",
                "context", sanitizeLogDetails(context)
            );
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            log.error("ERROR_EVENT: {}", logJson);
            
        } catch (Exception e) {
            log.error("Failed to log error event for operation: {}", operation, e);
        }
    }
    
    private void performIntegrationLogging(
            String integrationType, String endpoint, String method,
            int statusCode, long responseTimeMs, Map<String, Object> details) {
        
        try {
            Map<String, Object> logEntry = Map.of(
                "type", "INTEGRATION_EVENT",
                "timestamp", LocalDateTime.now().toString(),
                "correlationId", MDC.get("correlationId"),
                "integrationType", integrationType,
                "endpoint", sanitizeEndpoint(endpoint),
                "method", method,
                "statusCode", statusCode,
                "responseTimeMs", responseTimeMs,
                "details", sanitizeLogDetails(details)
            );
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            
            // Log level based on status code and response time
            if (statusCode >= 500 || responseTimeMs > 5000) {
                log.error("INTEGRATION_EVENT: {}", logJson);
            } else if (statusCode >= 400 || responseTimeMs > 2000) {
                log.warn("INTEGRATION_EVENT: {}", logJson);
            } else {
                log.info("INTEGRATION_EVENT: {}", logJson);
            }
            
        } catch (Exception e) {
            log.error("Failed to log integration event: {} endpoint: {}", 
                integrationType, endpoint, e);
        }
    }
    
    // Sanitization methods to prevent sensitive data leakage
    
    private String sanitizeUserId(String userId) {
        if (userId == null) return "anonymous";
        return userId.length() > 50 ? userId.substring(0, 50) + "..." : userId;
    }
    
    private Map<String, Object> sanitizeLogDetails(Map<String, Object> details) {
        if (details == null) return Map.of();
        
        return details.entrySet().stream()
            .filter(entry -> !isSensitiveField(entry.getKey()))
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeValue(entry.getValue())
            ));
    }
    
    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null) return "Unknown error";
        
        // Remove potentially sensitive information from error messages
        String sanitized = errorMessage
            .replaceAll("(?i)(password|secret|token|key)=[^\\s]+", "$1=***")
            .replaceAll("(?i)(authorization|bearer)\\s+[^\\s]+", "$1 ***");
        
        return sanitized.length() > 500 ? sanitized.substring(0, 500) + "..." : sanitized;
    }
    
    private String sanitizeStackTrace(Throwable throwable) {
        if (throwable == null) return "No stack trace";
        
        // Get only the first few lines of stack trace
        String stackTrace = java.util.Arrays.stream(throwable.getStackTrace())
            .limit(5)
            .map(StackTraceElement::toString)
            .collect(java.util.stream.Collectors.joining("\n"));
        
        return sanitizeErrorMessage(stackTrace);
    }
    
    private String sanitizeEndpoint(String endpoint) {
        if (endpoint == null) return "unknown";
        
        // Remove query parameters that might contain sensitive data
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex != -1) {
            return endpoint.substring(0, queryIndex) + "?[parameters]";
        }
        
        return endpoint;
    }
    
    private Object sanitizeValue(Object value) {
        if (value == null) return null;
        
        if (value instanceof String) {
            String str = (String) value;
            return str.length() > 200 ? str.substring(0, 200) + "..." : str;
        }
        
        return value;
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
               lower.contains("otp") ||
               lower.contains("ssn") ||
               lower.contains("social");
    }
}