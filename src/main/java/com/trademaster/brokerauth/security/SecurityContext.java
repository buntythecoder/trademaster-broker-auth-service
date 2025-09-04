package com.trademaster.brokerauth.security;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Security Context - Immutable security information
 * 
 * MANDATORY: Records - Rule #9
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Zero Trust - Rule #6
 */
public record SecurityContext(
    String correlationId,
    String userId,
    String sessionId,
    String clientId,
    String ipAddress,
    String userAgent,
    LocalDateTime timestamp,
    SecurityLevel requiredLevel,
    Map<String, String> attributes
) {
    
    public SecurityContext {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or empty");
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (requiredLevel == null) {
            requiredLevel = SecurityLevel.STANDARD;
        }
        if (attributes == null) {
            attributes = Map.of();
        }
    }
    
    /**
     * Get attribute value safely
     */
    public Optional<String> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }
    
    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return userId != null && !userId.trim().isEmpty();
    }
    
    /**
     * Check if session is valid
     */
    public boolean hasValidSession() {
        return sessionId != null && !sessionId.trim().isEmpty();
    }
    
    /**
     * Create builder for complex construction
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String correlationId;
        private String userId;
        private String sessionId;
        private String clientId;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime timestamp;
        private SecurityLevel requiredLevel;
        private Map<String, String> attributes;
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }
        
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder requiredLevel(SecurityLevel requiredLevel) {
            this.requiredLevel = requiredLevel;
            return this;
        }
        
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public SecurityContext build() {
            return new SecurityContext(correlationId, userId, sessionId, clientId, 
                ipAddress, userAgent, timestamp, requiredLevel, attributes);
        }
    }
}