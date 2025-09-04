package com.trademaster.brokerauth.security;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

/**
 * Authorization Validator - Validates user permissions
 * 
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Zero Trust - Rule #6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationValidator {
    
    private static final Set<String> ALLOWED_CLIENT_IDS = Set.of(
        BrokerAuthConstants.TRADEMASTER_WEB_CLIENT,
        BrokerAuthConstants.TRADEMASTER_MOBILE_CLIENT,
        BrokerAuthConstants.TRADEMASTER_API_CLIENT
    );
    
    /**
     * Authorize access asynchronously
     * 
     * MANDATORY: CompletableFuture - Rule #12
     */
    public CompletableFuture<SecurityResult<SecurityContext>> authorize(SecurityContext context) {
        return CompletableFuture.supplyAsync(() -> authorizeSync(context));
    }
    
    /**
     * Authorize access synchronously
     * 
     * MANDATORY: Functional Programming - Rule #3
     * MANDATORY: No if-else - Rule #3
     */
    public SecurityResult<SecurityContext> authorizeSync(SecurityContext context) {
        log.debug("Authorizing access for correlation: {}", context.correlationId());
        
        return validateClientId(context)
            .flatMap(this::validateSecurityLevel)
            .flatMap(this::validateUserPermissions);
    }
    
    private SecurityResult<SecurityContext> validateClientId(SecurityContext context) {
        return context.clientId() != null && ALLOWED_CLIENT_IDS.contains(context.clientId())
            ? SecurityResult.success(context, context)
            : SecurityResult.failure(SecurityError.AUTHORIZATION_FAILED, "Invalid client ID");
    }
    
    private SecurityResult<SecurityContext> validateSecurityLevel(SecurityContext context) {
        SecurityLevel userLevel = determineUserSecurityLevel(context);
        
        return userLevel.isAtLeast(context.requiredLevel())
            ? SecurityResult.success(context, context)
            : SecurityResult.failure(SecurityError.INSUFFICIENT_PRIVILEGES, 
                String.format("Required: %s, User has: %s", context.requiredLevel(), userLevel));
    }
    
    private SecurityResult<SecurityContext> validateUserPermissions(SecurityContext context) {
        // In a real implementation, this would check user roles/permissions from database
        // For now, authenticated users have STANDARD permissions
        return context.isAuthenticated()
            ? SecurityResult.success(context, context)
            : SecurityResult.failure(SecurityError.INSUFFICIENT_PRIVILEGES, "User not authenticated");
    }
    
    private SecurityLevel determineUserSecurityLevel(SecurityContext context) {
        // In a real implementation, this would check user roles from database
        // For now, return STANDARD for authenticated users
        return context.isAuthenticated() 
            ? SecurityLevel.STANDARD 
            : SecurityLevel.PUBLIC;
    }
}