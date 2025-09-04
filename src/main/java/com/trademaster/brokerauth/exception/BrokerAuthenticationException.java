package com.trademaster.brokerauth.exception;

import com.trademaster.brokerauth.enums.BrokerType;
import lombok.Getter;

/**
 * Broker Authentication Exception
 * 
 * MANDATORY: Functional Error Handling - Rule #11
 * MANDATORY: Immutable Exception Data - Rule #9
 */
@Getter
public class BrokerAuthenticationException extends RuntimeException {
    
    private final String userId;
    private final BrokerType brokerType;
    private final AuthErrorType errorType;
    private final String correlationId;
    
    public BrokerAuthenticationException(String userId, BrokerType brokerType, 
                                       AuthErrorType errorType, String message) {
        super(message);
        this.userId = userId;
        this.brokerType = brokerType;
        this.errorType = errorType;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    public BrokerAuthenticationException(String userId, BrokerType brokerType, 
                                       AuthErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.userId = userId;
        this.brokerType = brokerType;
        this.errorType = errorType;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Authentication Error Types
     * 
     * MANDATORY: Pattern Matching - Rule #14
     */
    public enum AuthErrorType {
        INVALID_CREDENTIALS,
        SESSION_EXPIRED,
        RATE_LIMIT_EXCEEDED,
        BROKER_UNAVAILABLE,
        INVALID_TOTP
    }
}