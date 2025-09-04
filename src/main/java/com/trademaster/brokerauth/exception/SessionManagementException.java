package com.trademaster.brokerauth.exception;

import lombok.Getter;

/**
 * Session Management Exception
 * 
 * MANDATORY: Functional Error Handling - Rule #11
 * MANDATORY: Immutable Exception Data - Rule #9
 */
@Getter
public class SessionManagementException extends RuntimeException {
    
    private final String sessionId;
    private final String userId;
    private final SessionErrorType errorType;
    private final String correlationId;
    
    public SessionManagementException(String sessionId, String userId, 
                                    SessionErrorType errorType, String message) {
        super(message);
        this.sessionId = sessionId;
        this.userId = userId;
        this.errorType = errorType;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    public SessionManagementException(String sessionId, String userId, 
                                    SessionErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.sessionId = sessionId;
        this.userId = userId;
        this.errorType = errorType;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Session Error Types
     * 
     * MANDATORY: Pattern Matching - Rule #14
     */
    public enum SessionErrorType {
        SESSION_NOT_FOUND,
        SESSION_CREATION_FAILED,
        CONCURRENT_SESSION_LIMIT
    }
}