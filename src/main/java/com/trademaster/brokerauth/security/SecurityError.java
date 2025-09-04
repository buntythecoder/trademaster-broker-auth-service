package com.trademaster.brokerauth.security;

import lombok.Getter;

/**
 * Security Errors - Defines all possible security error types
 * 
 * MANDATORY: Enums for type safety - Rule #14
 * MANDATORY: Error Handling - Rule #11
 */
@Getter
public enum SecurityError {
    
    // Authentication Errors
    AUTHENTICATION_FAILED("AUTH_001", "Authentication failed"),
    INVALID_CREDENTIALS("AUTH_002", "Invalid credentials provided"),
    EXPIRED_CREDENTIALS("AUTH_003", "Credentials have expired"),
    ACCOUNT_LOCKED("AUTH_004", "Account is locked"),
    
    // Authorization Errors
    AUTHORIZATION_FAILED("AUTHZ_001", "Authorization failed"),
    INSUFFICIENT_PRIVILEGES("AUTHZ_002", "Insufficient privileges"),
    RESOURCE_FORBIDDEN("AUTHZ_003", "Resource access forbidden"),
    
    // Risk Assessment Errors
    RISK_TOO_HIGH("RISK_001", "Risk level too high for operation"),
    SUSPICIOUS_ACTIVITY("RISK_002", "Suspicious activity detected"),
    RATE_LIMIT_EXCEEDED("RISK_003", "Rate limit exceeded"),
    
    // System Errors
    SYSTEM_ERROR("SYS_001", "System error occurred"),
    OPERATION_FAILED("SYS_002", "Operation failed"),
    MAPPING_ERROR("SYS_003", "Data mapping error"),
    SIDE_EFFECT_ERROR("SYS_004", "Side effect execution error"),
    
    // Validation Errors
    INVALID_INPUT("VAL_001", "Invalid input provided"),
    CONTEXT_INVALID("VAL_002", "Security context invalid"),
    CONFIGURATION_ERROR("VAL_003", "Security configuration error");
    
    private final String code;
    private final String message;
    
    SecurityError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", code, message);
    }
}