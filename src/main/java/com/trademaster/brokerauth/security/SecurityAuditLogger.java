package com.trademaster.brokerauth.security;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Security Audit Logger - Logs all security events
 * 
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Structured Logging - Rule #15
 * MANDATORY: Zero Trust - Rule #6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditLogger {
    
    // Using constants from BrokerAuthConstants
    
    /**
     * Log security access result asynchronously
     * 
     * MANDATORY: Structured logging with correlation IDs - Rule #15
     */
    public <T> void logAccess(SecurityResult<T> result) {
        logAccessSync(result);
    }
    
    /**
     * Log security access result synchronously
     * 
     * MANDATORY: Pattern matching - Rule #14
     * MANDATORY: Structured logging - Rule #15
     */
    public <T> void logAccessSync(SecurityResult<T> result) {
        switch (result) {
            case SecurityResult.Success<T> success -> logSuccessfulAccess(success);
            case SecurityResult.Failure<T> failure -> logFailedAccess(failure);
        }
    }
    
    private <T> void logSuccessfulAccess(SecurityResult.Success<T> success) {
        SecurityContext context = success.context();
        
        log.info("[{}] Successful security access - correlation: {}, user: {}, client: {}, ip: {}, timestamp: {}", 
            BrokerAuthConstants.SECURITY_AUDIT_MARKER,
            context.correlationId(),
            maskUserId(context.userId()),
            context.clientId(),
            maskIpAddress(context.ipAddress()),
            context.timestamp()
        );
        
        // Log additional context for audit trail
        logSecurityContext(context);
    }
    
    private <T> void logFailedAccess(SecurityResult.Failure<T> failure) {
        log.warn("[{}] Failed security access - error: {}, message: {}", 
            BrokerAuthConstants.SECURITY_AUDIT_MARKER,
            failure.error().getCode(),
            failure.message()
        );
    }
    
    private void logSecurityContext(SecurityContext context) {
        Map<String, Object> auditData = Map.of(
            "correlationId", context.correlationId(),
            "userId", maskUserId(context.userId()),
            "sessionId", maskSessionId(context.sessionId()),
            "clientId", context.clientId(),
            "ipAddress", maskIpAddress(context.ipAddress()),
            "requiredLevel", context.requiredLevel().name(),
            "timestamp", context.timestamp(),
            "attributeCount", context.attributes().size()
        );
        
        log.debug("[{}] Security context details: {}", BrokerAuthConstants.SECURITY_AUDIT_MARKER, auditData);
    }
    
    /**
     * Log authentication attempt
     */
    public void logAuthenticationAttempt(String correlationId, String userId, boolean successful) {
        if (successful) {
            log.info("[{}] Authentication successful - correlation: {}, user: {}, timestamp: {}", 
                BrokerAuthConstants.SECURITY_AUDIT_MARKER,
                correlationId,
                maskUserId(userId),
                LocalDateTime.now()
            );
        } else {
            log.warn("[{}] Authentication failed - correlation: {}, user: {}, timestamp: {}", 
                BrokerAuthConstants.SECURITY_AUDIT_MARKER,
                correlationId,
                maskUserId(userId),
                LocalDateTime.now()
            );
        }
    }
    
    /**
     * Log authorization decision
     */
    public void logAuthorizationDecision(String correlationId, String userId, 
                                       String resource, boolean authorized) {
        log.info("[{}] Authorization {} - correlation: {}, user: {}, resource: {}, timestamp: {}", 
            BrokerAuthConstants.SECURITY_AUDIT_MARKER,
            authorized ? "granted" : "denied",
            correlationId,
            maskUserId(userId),
            resource,
            LocalDateTime.now()
        );
    }
    
    /**
     * Log risk assessment result
     */
    public void logRiskAssessment(String correlationId, String userId, int riskScore, boolean blocked) {
        log.info("[{}] Risk assessment - correlation: {}, user: {}, score: {}, blocked: {}, timestamp: {}", 
            BrokerAuthConstants.SECURITY_AUDIT_MARKER,
            correlationId,
            maskUserId(userId),
            riskScore,
            blocked,
            LocalDateTime.now()
        );
    }
    
    // Security: Never log full user IDs or IP addresses
    private String maskUserId(String userId) {
        if (userId == null || userId.length() < BrokerAuthConstants.MIN_MASK_LENGTH) {
            return BrokerAuthConstants.USER_ID_MASK;
        }
        return userId.substring(0, BrokerAuthConstants.USER_ID_VISIBLE_CHARS) + 
               "*".repeat(userId.length() - BrokerAuthConstants.USER_ID_VISIBLE_CHARS);
    }
    
    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() < BrokerAuthConstants.MIN_SESSION_ID_LENGTH) {
            return BrokerAuthConstants.SESSION_ID_MASK;
        }
        return sessionId.substring(0, BrokerAuthConstants.SESSION_ID_VISIBLE_CHARS) + 
               "*".repeat(sessionId.length() - BrokerAuthConstants.SESSION_ID_VISIBLE_CHARS);
    }
    
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return BrokerAuthConstants.UNKNOWN_IP;
        String[] parts = ipAddress.split("\\.");
        return parts.length == 4 
            ? String.format("%s.%s.*.***", parts[0], parts[1])
            : BrokerAuthConstants.IP_MASK;
    }
}