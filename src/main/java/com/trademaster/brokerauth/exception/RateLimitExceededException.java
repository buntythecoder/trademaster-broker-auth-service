package com.trademaster.brokerauth.exception;

import lombok.Getter;

/**
 * Rate Limit Exceeded Exception
 * 
 * MANDATORY: Functional Error Handling - Rule #11
 * MANDATORY: Immutable Exception Data - Rule #9
 */
@Getter
public class RateLimitExceededException extends RuntimeException {
    
    private final String userId;
    private final String endpoint;
    private final int retryAfterSeconds;
    private final String correlationId;
    
    public RateLimitExceededException(String userId, String endpoint, 
                                    int retryAfterSeconds, String message) {
        super(message);
        this.userId = userId;
        this.endpoint = endpoint;
        this.retryAfterSeconds = retryAfterSeconds;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    public RateLimitExceededException(String userId, String endpoint, 
                                    int retryAfterSeconds, String message, Throwable cause) {
        super(message, cause);
        this.userId = userId;
        this.endpoint = endpoint;
        this.retryAfterSeconds = retryAfterSeconds;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
}