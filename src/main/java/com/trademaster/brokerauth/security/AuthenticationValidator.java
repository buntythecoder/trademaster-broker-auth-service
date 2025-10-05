package com.trademaster.brokerauth.security;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication Validator - Validates authentication credentials
 * 
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Zero Trust - Rule #6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationValidator {
    
    // Using constants from BrokerAuthConstants
    
    /**
     * Validate authentication context
     * 
     * MANDATORY: No if-else - Rule #3
     * MANDATORY: Result pattern - Rule #11
     */
    public SecurityResult<SecurityContext> validate(SecurityContext context) {
        log.debug("Validating authentication for correlation: {}", context.correlationId());
        
        return validateBasicContext(context)
            .flatMap(this::validateUserAuthentication)
            .flatMap(this::validateSessionValidity)
            .flatMap(this::validateTimestamp);
    }
    
    private SecurityResult<SecurityContext> validateBasicContext(SecurityContext context) {
        return context.correlationId() == null || context.correlationId().trim().isEmpty()
            ? SecurityResult.failure(SecurityError.CONTEXT_INVALID, "Missing correlation ID")
            : SecurityResult.success(context, context);
    }
    
    private SecurityResult<SecurityContext> validateUserAuthentication(SecurityContext context) {
        return switch (evaluateAuthenticationStatus(context)) {
            case VALID -> SecurityResult.success(context, context);
            case MISSING_USER_ID -> SecurityResult.failure(SecurityError.AUTHENTICATION_FAILED, "User ID missing");
            case INVALID_USER_ID -> SecurityResult.failure(SecurityError.INVALID_CREDENTIALS, "User ID invalid");
            case NO_SESSION -> SecurityResult.failure(SecurityError.AUTHENTICATION_FAILED, "No valid session");
        };
    }
    
    private SecurityResult<SecurityContext> validateSessionValidity(SecurityContext context) {
        return context.hasValidSession()
            ? SecurityResult.success(context, context)
            : SecurityResult.failure(SecurityError.AUTHENTICATION_FAILED, "Invalid session");
    }
    
    private SecurityResult<SecurityContext> validateTimestamp(SecurityContext context) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(BrokerAuthConstants.MAX_SESSION_AGE_HOURS);
        
        return context.timestamp() != null && context.timestamp().isAfter(cutoff)
            ? SecurityResult.success(context, context)
            : SecurityResult.failure(SecurityError.EXPIRED_CREDENTIALS, "Session expired");
    }
    
    private AuthenticationStatus evaluateAuthenticationStatus(SecurityContext context) {
        // Functional pattern matching with switch expression - Rule #14
        return switch (true) {
            case boolean b when !context.isAuthenticated() ->
                AuthenticationStatus.MISSING_USER_ID;
            case boolean b when context.userId().length() < BrokerAuthConstants.MIN_USER_ID_LENGTH ->
                AuthenticationStatus.INVALID_USER_ID;
            case boolean b when !context.hasValidSession() ->
                AuthenticationStatus.NO_SESSION;
            default ->
                AuthenticationStatus.VALID;
        };
    }
    
    private enum AuthenticationStatus {
        VALID,
        MISSING_USER_ID,
        INVALID_USER_ID,
        NO_SESSION
    }
}