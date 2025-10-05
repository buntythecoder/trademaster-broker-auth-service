package com.trademaster.brokerauth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Security Facade - Zero Trust External Access Control
 * 
 * MANDATORY: Zero Trust Security - Rule #6
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Virtual Threads - Rule #12
 * 
 * All external access MUST go through this facade
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityFacade {
    
    private final SecurityMediator mediator;
    
    /**
     * Secure execution for external access with comprehensive security controls
     * 
     * MANDATORY: Functional Programming - Rule #3
     * MANDATORY: Zero Trust - All external access denied by default
     */
    public <T> CompletableFuture<SecurityResult<T>> secureExecute(
            SecurityContext context,
            Supplier<CompletableFuture<T>> operation) {
        
        log.debug("Security facade processing external access request: {}", context.correlationId());
        
        return mediator.mediateAccess(context, operation)
            .thenApply(result -> {
                log.info("External access completed: correlation={}, success={}", 
                    context.correlationId(), result.isSuccess());
                return result;
            })
            .exceptionally(throwable -> {
                log.error("Security facade error: correlation={}, error={}", 
                    context.correlationId(), throwable.getMessage(), throwable);
                return SecurityResult.failure(SecurityError.SYSTEM_ERROR, throwable.getMessage());
            });
    }
    
    /**
     * Synchronous secure execution for simpler operations
     */
    public <T> SecurityResult<T> secureExecuteSync(
            SecurityContext context,
            Function<SecurityContext, T> operation) {

        try {
            log.debug("Synchronous security facade processing: {}", context.correlationId());
            return mediator.mediateAccessSync(context, operation);
        } catch (Exception e) {
            log.error("Synchronous security facade error: correlation={}, error={}",
                context.correlationId(), e.getMessage(), e);
            return SecurityResult.failure(SecurityError.SYSTEM_ERROR, e.getMessage());
        }
    }

    /**
     * Secure access method for test compatibility
     * MANDATORY: Functional Programming - Rule #3
     */
    public <T> SecurityResult<T> secureAccess(
            SecurityContext context,
            Supplier<T> operation) {

        try {
            log.debug("Security facade secure access: {}", context.correlationId());
            return mediator.mediateAccessSync(context, ctx -> operation.get());
        } catch (Exception e) {
            log.error("Security facade secure access error: correlation={}, error={}",
                context.correlationId(), e.getMessage(), e);
            return SecurityResult.failure(SecurityError.SYSTEM_ERROR, e.getMessage());
        }
    }
}