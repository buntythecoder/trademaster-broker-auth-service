package com.trademaster.brokerauth.controller;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.domain.service.SessionDomainService;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.dto.SessionResponseDTO;
import com.trademaster.brokerauth.mapper.SessionMapper;
import com.trademaster.brokerauth.security.SecurityFacade;
import com.trademaster.brokerauth.security.SecurityContext;
import com.trademaster.brokerauth.security.SecurityLevel;
import com.trademaster.brokerauth.security.SecurityResult;
import com.trademaster.brokerauth.service.BrokerAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name = "Broker Authentication",
    description = "External broker authentication endpoints for establishing and managing broker sessions. " +
                  "Provides OAuth-based authentication, session management, and secure broker credential handling. " +
                  "All endpoints require JWT authentication and implement zero-trust security patterns."
)
@RestController
@RequestMapping("/api/v1/broker-auth")
@RequiredArgsConstructor
@Slf4j
public class BrokerAuthController {
    
    // MANDATORY: SecurityFacade for external access - Rule #6
    private final SecurityFacade securityFacade;

    // MANDATORY: Internal service access (lightweight) - Rule #6
    private final BrokerAuthenticationService authService;
    private final SessionDomainService sessionDomainService;
    private final SessionMapper sessionMapper;
    
    /**
     * Authenticate with broker - EXTERNAL ACCESS via SecurityFacade
     *
     * MANDATORY: Zero Trust Security - Rule #6
     * MANDATORY: Functional Programming - Rule #3
     * MANDATORY: Virtual Threads - Rule #12
     */
    @Operation(
        summary = "Authenticate with broker",
        description = "Initiates OAuth authentication flow with the specified broker. " +
                      "Creates a new broker session upon successful authentication. " +
                      "Implements zero-trust security with comprehensive validation, risk assessment, and audit logging. " +
                      "Supports multiple broker types (ZERODHA, UPSTOX, ANGEL_ONE, FYERS, DHAN) with broker-specific OAuth flows.",
        security = @SecurityRequirement(name = "jwtAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Authentication initiated successfully. Returns session ID and OAuth authorization URL.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthResponse.class),
                examples = @ExampleObject(
                    name = "Success Response",
                    value = """
                        {
                          "sessionId": "550e8400-e29b-41d4-a716-446655440000",
                          "userId": "USER123",
                          "brokerType": "ZERODHA",
                          "authorizationUrl": "https://kite.zerodha.com/connect/login?api_key=xxx&v=3",
                          "expiresAt": "2024-01-15T14:30:00",
                          "success": true,
                          "message": "Authentication initiated. Please complete OAuth flow."
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - validation failed or unsupported broker type",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Validation Error",
                    value = """
                        {
                          "sessionId": null,
                          "userId": null,
                          "brokerType": null,
                          "authorizationUrl": null,
                          "expiresAt": null,
                          "success": false,
                          "message": "Invalid broker type or missing required fields"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Security validation failed - JWT invalid, risk too high, or unauthorized access",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Security Failure",
                    value = """
                        {
                          "sessionId": null,
                          "userId": null,
                          "brokerType": null,
                          "authorizationUrl": null,
                          "expiresAt": null,
                          "success": false,
                          "message": "Security check failed: Risk level too high"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error - broker OAuth service unavailable or system failure",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "System Error",
                    value = """
                        {
                          "sessionId": null,
                          "userId": null,
                          "brokerType": null,
                          "authorizationUrl": null,
                          "expiresAt": null,
                          "success": false,
                          "message": "Broker authentication service temporarily unavailable"
                        }
                        """
                )
            )
        )
    })
    @PostMapping("/authenticate")
    public CompletableFuture<ResponseEntity<AuthResponse>> authenticate(
            @Parameter(
                description = "Authentication request containing user ID, broker type, and credentials",
                required = true,
                schema = @Schema(implementation = AuthRequest.class)
            )
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
    @Operation(
        summary = "Get broker session",
        description = "Retrieves details of an active broker session including status, expiration, and broker type. " +
                      "Validates session ownership and implements zero-trust security checks. " +
                      "Returns session metadata without exposing sensitive credentials.",
        security = @SecurityRequirement(name = "jwtAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Session found and returned successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SessionResponseDTO.class),
                examples = @ExampleObject(
                    name = "Active Session",
                    value = """
                        {
                          "id": 1,
                          "sessionId": "550e8400-e29b-41d4-a716-446655440000",
                          "userId": "USER123",
                          "brokerType": "ZERODHA",
                          "status": "ACTIVE",
                          "createdAt": "2024-01-15T10:00:00",
                          "expiresAt": "2024-01-15T14:30:00",
                          "lastAccessedAt": "2024-01-15T12:15:00"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found or expired",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Security validation failed - JWT invalid or session ownership validation failed",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json")
        )
    })
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponseDTO> getSession(
            @Parameter(
                description = "Unique identifier of the broker session to retrieve",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable String sessionId,
            HttpServletRequest httpRequest) {

        SecurityContext context = createSecurityContext(sessionId, httpRequest);

        SecurityResult<SessionResponseDTO> result = securityFacade.secureExecuteSync(context,
            ctx -> sessionMapper.toDTO(sessionDomainService.findById(sessionId)).orElse(null));

        return result.isSuccess() && result.getValue().isPresent()
            ? ResponseEntity.ok(result.getValue().get())
            : ResponseEntity.notFound().build();
    }
    
    /**
     * Revoke session - EXTERNAL ACCESS via SecurityFacade
     *
     * MANDATORY: Zero Trust Security - Rule #6
     */
    @Operation(
        summary = "Revoke broker session",
        description = "Revokes an active broker session, invalidating all associated credentials and tokens. " +
                      "Implements secure logout with complete session cleanup, credential revocation in Vault, " +
                      "and audit trail logging. Validates session ownership before revocation. " +
                      "This is an irreversible operation requiring re-authentication to restore access.",
        security = @SecurityRequirement(name = "jwtAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Session revoked successfully. No content returned.",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid session ID or revocation failed due to validation errors",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Security validation failed - JWT invalid or session ownership validation failed",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found or already revoked",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during session revocation",
            content = @Content(mediaType = "application/json")
        )
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @Parameter(
                description = "Unique identifier of the broker session to revoke",
                required = true,
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable String sessionId,
            HttpServletRequest httpRequest) {

        SecurityContext context = createSecurityContext(sessionId, httpRequest);

        SecurityResult<Boolean> result = securityFacade.secureExecuteSync(context,
            ctx -> sessionDomainService.revoke(sessionId));

        return result.isSuccess() && result.getValue().orElse(false)
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