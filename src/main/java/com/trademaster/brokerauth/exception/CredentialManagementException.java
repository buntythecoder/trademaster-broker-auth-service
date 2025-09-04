package com.trademaster.brokerauth.exception;

import lombok.Getter;

/**
 * Credential Management Exception
 * 
 * MANDATORY: Functional Error Handling - Rule #11
 * MANDATORY: Immutable Exception Data - Rule #9
 */
@Getter
public class CredentialManagementException extends RuntimeException {
    
    private final String userId;
    private final CredentialErrorType errorType;
    private final String correlationId;
    
    public CredentialManagementException(String userId, CredentialErrorType errorType, String message) {
        super(message);
        this.userId = userId;
        this.errorType = errorType;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    public CredentialManagementException(String userId, CredentialErrorType errorType, 
                                       String message, Throwable cause) {
        super(message, cause);
        this.userId = userId;
        this.errorType = errorType;
        this.correlationId = java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Credential Error Types
     * 
     * MANDATORY: Pattern Matching - Rule #14
     */
    public enum CredentialErrorType {
        ENCRYPTION_FAILED,
        DECRYPTION_FAILED,
        INVALID_CREDENTIAL_FORMAT
    }
}