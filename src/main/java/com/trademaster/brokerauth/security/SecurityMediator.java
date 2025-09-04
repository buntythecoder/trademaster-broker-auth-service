package com.trademaster.brokerauth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Security Mediator - Coordinates all security components
 * 
 * MANDATORY: Zero Trust Security - Rule #6
 * MANDATORY: Mediator Pattern - Rule #4
 * MANDATORY: Virtual Threads - Rule #12
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityMediator {
    
    private final AuthenticationValidator authenticationValidator;
    private final AuthorizationValidator authorizationValidator;
    private final RiskAssessmentService riskAssessmentService;
    private final SecurityAuditLogger securityAuditLogger;
    
    /**
     * Mediate external access with full security pipeline
     * 
     * MANDATORY: Railway Programming - Rule #11
     * MANDATORY: No if-else - Rule #3
     * MANDATORY: Low cognitive complexity - Rule #5
     */
    public <T> CompletableFuture<SecurityResult<T>> mediateAccess(
            SecurityContext context,
            Supplier<CompletableFuture<T>> operation) {
        
        return processSecurityPipeline(context, operation);
    }
    
    /**
     * Synchronous mediation for simpler operations
     */
    public <T> SecurityResult<T> mediateAccessSync(
            SecurityContext context,
            Function<SecurityContext, T> operation) {
        
        return processSyncSecurityPipeline(context, operation);
    }
    
    /**
     * Process async security pipeline
     * 
     * MANDATORY: Low cognitive complexity - Rule #5
     */
    private <T> CompletableFuture<SecurityResult<T>> processSecurityPipeline(
            SecurityContext context,
            Supplier<CompletableFuture<T>> operation) {
        
        return CompletableFuture
            .supplyAsync(() -> validateAuthentication(context))
            .thenCompose(result -> continueIfValid(result, this::authorizeAccess))
            .thenCompose(result -> continueIfValid(result, this::assessRisk))
            .thenCompose(result -> executeIfValid(result, operation))
            .thenApply(this::auditResult);
    }
    
    /**
     * Process sync security pipeline
     */
    private <T> SecurityResult<T> processSyncSecurityPipeline(
            SecurityContext context,
            Function<SecurityContext, T> operation) {
        
        return validateAuthentication(context)
            .flatMap(this::authorizeAccessSync)
            .flatMap(this::assessRiskSync)
            .flatMap(ctx -> executeSecureOperationSync(ctx, operation));
    }
    
    private <T> CompletableFuture<SecurityResult<SecurityContext>> continueIfValid(
            SecurityResult<SecurityContext> result,
            Function<SecurityContext, CompletableFuture<SecurityResult<SecurityContext>>> nextStep) {
        
        return result.isSuccess() 
            ? nextStep.apply(result.getValue().orElseThrow())
            : CompletableFuture.completedFuture(result);
    }
    
    private <T> CompletableFuture<SecurityResult<T>> executeIfValid(
            SecurityResult<SecurityContext> result,
            Supplier<CompletableFuture<T>> operation) {
        
        return result.isSuccess()
            ? executeSecureOperation(result.getValue().orElseThrow(), operation)
            : CompletableFuture.completedFuture(SecurityResult.failure(
                result.getError().orElse(SecurityError.SYSTEM_ERROR),
                result.getMessage().orElse("Security check failed")));
    }
    
    private SecurityResult<SecurityContext> validateAuthentication(SecurityContext context) {
        log.debug("Validating authentication: {}", context.correlationId());
        return authenticationValidator.validate(context);
    }
    
    private CompletableFuture<SecurityResult<SecurityContext>> authorizeAccess(SecurityContext context) {
        log.debug("Authorizing access: {}", context.correlationId());
        return authorizationValidator.authorize(context);
    }
    
    private SecurityResult<SecurityContext> authorizeAccessSync(SecurityContext context) {
        return authorizationValidator.authorizeSync(context);
    }
    
    private CompletableFuture<SecurityResult<SecurityContext>> assessRisk(SecurityContext context) {
        log.debug("Assessing risk: {}", context.correlationId());
        return riskAssessmentService.assess(context);
    }
    
    private SecurityResult<SecurityContext> assessRiskSync(SecurityContext context) {
        return riskAssessmentService.assessSync(context);
    }
    
    private <T> CompletableFuture<SecurityResult<T>> executeSecureOperation(
            SecurityContext context,
            Supplier<CompletableFuture<T>> operation) {
        
        log.debug("Executing secure operation: {}", context.correlationId());
        
        try {
            return operation.get()
                .thenApply(result -> SecurityResult.success(result, context))
                .exceptionally(throwable -> {
                    log.error("Secure operation failed: correlation={}, error={}", 
                        context.correlationId(), throwable.getMessage());
                    return SecurityResult.failure(SecurityError.OPERATION_FAILED, throwable.getMessage());
                });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                SecurityResult.failure(SecurityError.OPERATION_FAILED, e.getMessage()));
        }
    }
    
    private <T> SecurityResult<T> executeSecureOperationSync(
            SecurityContext context,
            Function<SecurityContext, T> operation) {
        
        try {
            T result = operation.apply(context);
            return SecurityResult.success(result, context);
        } catch (Exception e) {
            log.error("Synchronous secure operation failed: correlation={}, error={}", 
                context.correlationId(), e.getMessage());
            return SecurityResult.failure(SecurityError.OPERATION_FAILED, e.getMessage());
        }
    }
    
    private <T> SecurityResult<T> auditResult(SecurityResult<T> result) {
        securityAuditLogger.logAccess(result);
        return result;
    }
    
    private <T> SecurityResult<T> auditResultSync(SecurityResult<T> result) {
        securityAuditLogger.logAccessSync(result);
        return result;
    }
}