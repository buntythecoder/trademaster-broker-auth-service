package com.trademaster.brokerauth.controller;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.security.SecurityFacade;
import com.trademaster.brokerauth.security.SecurityContext;
import com.trademaster.brokerauth.security.SecurityLevel;
import com.trademaster.brokerauth.security.SecurityResult;
import com.trademaster.brokerauth.service.BrokerAuthenticationService;
import com.trademaster.brokerauth.service.BrokerSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Broker Authentication REST Controller
 * 
 * MANDATORY: Zero Trust Security (External Access) - Rule #6
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Virtual Threads - Rule #12
 */
@RestController
@RequestMapping("/api/v1/broker-auth")
@RequiredArgsConstructor
@Slf4j
public class BrokerAuthController {
    
    // MANDATORY: SecurityFacade for external access - Rule #6
    private final SecurityFacade securityFacade;
    
    // MANDATORY: Internal service access (lightweight) - Rule #6
    private final BrokerAuthenticationService authService;
    private final BrokerSessionService sessionService;
    
    /**
     * Authenticate with broker - EXTERNAL ACCESS via SecurityFacade
     * 
     * MANDATORY: Zero Trust Security - Rule #6
     * MANDATORY: Functional Programming - Rule #3
     * MANDATORY: Virtual Threads - Rule #12
     */
    @PostMapping("/authenticate")
    public CompletableFuture<ResponseEntity<AuthResponse>> authenticate(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {
        
        SecurityContext context = createSecurityContext(request, httpRequest);
        
        return securityFacade.secureExecute(context, () -> {
            log.info("Secure authentication request for broker: {}", request.brokerType());
            return authService.authenticate(request);
        }).thenApply(securityResult -> convertToHttpResponse(securityResult));
    }
    
    /**
     * Get session information - EXTERNAL ACCESS via SecurityFacade
     * 
     * MANDATORY: Zero Trust Security - Rule #6
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<BrokerSession> getSession(@PathVariable String sessionId,
                                                   HttpServletRequest httpRequest) {
        
        SecurityContext context = createSecurityContext(sessionId, httpRequest);
        
        SecurityResult<BrokerSession> result = securityFacade.secureExecuteSync(context, 
            ctx -> sessionService.getSession(sessionId).orElse(null));
        
        return result.isSuccess() && result.getValue().isPresent()
            ? ResponseEntity.ok(result.getValue().get())
            : ResponseEntity.notFound().build();
    }
    
    /**
     * Revoke session - EXTERNAL ACCESS via SecurityFacade
     * 
     * MANDATORY: Zero Trust Security - Rule #6
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId,
                                            HttpServletRequest httpRequest) {
        
        SecurityContext context = createSecurityContext(sessionId, httpRequest);
        
        SecurityResult<Void> result = securityFacade.secureExecuteSync(context, 
            ctx -> {
                sessionService.revokeSession(sessionId);
                return null;
            });
        
        return result.isSuccess() 
            ? ResponseEntity.noContent().build()
            : ResponseEntity.badRequest().build();
    }
    
    /**
     * Create security context from request
     * 
     * MANDATORY: Builder pattern - Rule #4
     * MANDATORY: Zero Trust - Rule #6
     */
    private SecurityContext createSecurityContext(AuthRequest request, HttpServletRequest httpRequest) {
        return SecurityContext.builder()
            .correlationId(UUID.randomUUID().toString())
            .userId(request.userId())
            .clientId(extractClientId(httpRequest))
            .ipAddress(extractIpAddress(httpRequest))
            .userAgent(httpRequest.getHeader(BrokerAuthConstants.USER_AGENT_HEADER))
            .timestamp(LocalDateTime.now())
            .requiredLevel(SecurityLevel.STANDARD)
            .attributes(Map.of(
                "brokerType", request.brokerType().name(),
                "endpoint", "authenticate"
            ))
            .build();
    }
    
    private SecurityContext createSecurityContext(String sessionId, HttpServletRequest httpRequest) {
        return SecurityContext.builder()
            .correlationId(UUID.randomUUID().toString())
            .sessionId(sessionId)
            .clientId(extractClientId(httpRequest))
            .ipAddress(extractIpAddress(httpRequest))
            .userAgent(httpRequest.getHeader(BrokerAuthConstants.USER_AGENT_HEADER))
            .timestamp(LocalDateTime.now())
            .requiredLevel(SecurityLevel.STANDARD)
            .attributes(Map.of(
                "sessionId", sessionId,
                "endpoint", "session-management"
            ))
            .build();
    }
    
    private String extractClientId(HttpServletRequest request) {
        String clientId = request.getHeader(BrokerAuthConstants.CLIENT_ID_HEADER);
        return clientId != null ? clientId : BrokerAuthConstants.UNKNOWN_CLIENT;
    }
    
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(BrokerAuthConstants.FORWARDED_FOR_HEADER);
        return xForwardedFor != null ? xForwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }
    
    /**
     * Convert SecurityResult to HTTP Response
     * 
     * MANDATORY: Pattern matching - Rule #14
     */
    private ResponseEntity<AuthResponse> convertToHttpResponse(SecurityResult<AuthResponse> securityResult) {
        return switch (securityResult) {
            case SecurityResult.Success<AuthResponse> success -> {
                AuthResponse response = success.getValue().orElse(null);
                yield response != null && response.success()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
            }
            case SecurityResult.Failure<AuthResponse> failure -> ResponseEntity.status(401)
                .body(new AuthResponse(null, null, null, null, null, false, failure.getMessage().orElse("Security check failed")));
        };
    }
}