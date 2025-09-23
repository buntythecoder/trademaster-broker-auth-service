package com.trademaster.brokerauth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Global Exception Handler for Broker Auth Service
 * 
 * MANDATORY: Error Handling Patterns - Rule #11
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Structured Logging - Rule #15
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle broker authentication exceptions
     * 
     * MANDATORY: Pattern matching - Rule #14
     */
    @ExceptionHandler(BrokerAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleBrokerAuthenticationException(
            BrokerAuthenticationException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        log.error("Broker authentication failed - CorrelationId: {}, Broker: {}, UserId: {}, Error: {}", 
            correlationId, ex.getBrokerType(), maskUserId(ex.getUserId()), ex.getMessage());
        
        return switch (ex.getErrorType()) {
            case INVALID_CREDENTIALS -> createErrorResponse(
                correlationId, 
                "INVALID_CREDENTIALS", 
                "Invalid broker credentials provided",
                HttpStatus.UNAUTHORIZED,
                request.getDescription(false)
            );
            case SESSION_EXPIRED -> createErrorResponse(
                correlationId,
                "SESSION_EXPIRED", 
                "Broker session has expired. Please re-authenticate",
                HttpStatus.UNAUTHORIZED,
                request.getDescription(false)
            );
            case RATE_LIMIT_EXCEEDED -> createErrorResponse(
                correlationId,
                "RATE_LIMIT_EXCEEDED",
                "API rate limit exceeded. Please retry after some time",
                HttpStatus.TOO_MANY_REQUESTS,
                request.getDescription(false)
            );
            case BROKER_UNAVAILABLE -> createErrorResponse(
                correlationId,
                "BROKER_UNAVAILABLE",
                "Broker API is currently unavailable. Please try again later",
                HttpStatus.SERVICE_UNAVAILABLE,
                request.getDescription(false)
            );
            case INVALID_TOTP -> createErrorResponse(
                correlationId,
                "INVALID_TOTP",
                "Invalid TOTP code provided",
                HttpStatus.BAD_REQUEST,
                request.getDescription(false)
            );
        };
    }

    /**
     * Handle session management exceptions
     */
    @ExceptionHandler(SessionManagementException.class)
    public ResponseEntity<ErrorResponse> handleSessionManagementException(
            SessionManagementException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        log.error("Session management error - CorrelationId: {}, SessionId: {}, Error: {}", 
            correlationId, maskSessionId(ex.getSessionId()), ex.getMessage());
        
        return switch (ex.getErrorType()) {
            case SESSION_NOT_FOUND -> createErrorResponse(
                correlationId,
                "SESSION_NOT_FOUND",
                "Session not found or expired",
                HttpStatus.NOT_FOUND,
                request.getDescription(false)
            );
            case SESSION_CREATION_FAILED -> createErrorResponse(
                correlationId,
                "SESSION_CREATION_FAILED",
                "Failed to create session. Please try again",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getDescription(false)
            );
            case CONCURRENT_SESSION_LIMIT -> createErrorResponse(
                correlationId,
                "CONCURRENT_SESSION_LIMIT",
                "Maximum concurrent sessions exceeded",
                HttpStatus.CONFLICT,
                request.getDescription(false)
            );
        };
    }

    /**
     * Handle rate limit exceptions
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(
            RateLimitExceededException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        log.warn("Rate limit exceeded - CorrelationId: {}, UserId: {}, RetryAfter: {} seconds", 
            correlationId, maskUserId(ex.getUserId()), ex.getRetryAfterSeconds());
        
        ErrorResponse errorResponse = new ErrorResponse(
            correlationId,
            "RATE_LIMIT_EXCEEDED",
            "Rate limit exceeded. Please retry after " + ex.getRetryAfterSeconds() + " seconds",
            HttpStatus.TOO_MANY_REQUESTS.value(),
            LocalDateTime.now(),
            request.getDescription(false),
            List.of("Retry-After: " + ex.getRetryAfterSeconds() + " seconds")
        );
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(errorResponse);
    }

    /**
     * Handle credential management exceptions
     */
    @ExceptionHandler(CredentialManagementException.class)
    public ResponseEntity<ErrorResponse> handleCredentialManagementException(
            CredentialManagementException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        log.error("Credential management error - CorrelationId: {}, Error: {}", 
            correlationId, ex.getMessage());
        
        return switch (ex.getErrorType()) {
            case ENCRYPTION_FAILED -> createErrorResponse(
                correlationId,
                "ENCRYPTION_ERROR",
                "Failed to secure credentials. Please try again",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getDescription(false)
            );
            case DECRYPTION_FAILED -> createErrorResponse(
                correlationId,
                "DECRYPTION_ERROR",
                "Failed to retrieve credentials. Please re-authenticate",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getDescription(false)
            );
            case INVALID_CREDENTIAL_FORMAT -> createErrorResponse(
                correlationId,
                "INVALID_CREDENTIAL_FORMAT",
                "Invalid credential format provided",
                HttpStatus.BAD_REQUEST,
                request.getDescription(false)
            );
        };
    }

    /**
     * Handle validation exceptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        List<String> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
        
        log.warn("Validation error - CorrelationId: {}, Errors: {}", correlationId, validationErrors);
        
        ErrorResponse errorResponse = new ErrorResponse(
            correlationId,
            "VALIDATION_ERROR",
            "Request validation failed",
            HttpStatus.BAD_REQUEST.value(),
            LocalDateTime.now(),
            request.getDescription(false),
            validationErrors
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        log.error("Invalid argument - CorrelationId: {}, Error: {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            correlationId,
            "INVALID_REQUEST",
            "Invalid request parameters: " + ex.getMessage(),
            HttpStatus.BAD_REQUEST,
            request.getDescription(false)
        );
    }

    /**
     * Handle generic runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        String correlationId = UUID.randomUUID().toString();
        
        log.error("Runtime error - CorrelationId: {}, Error: {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            correlationId,
            "INTERNAL_ERROR",
            "An internal error occurred. Please contact support with correlation ID: " + correlationId,
            HttpStatus.INTERNAL_SERVER_ERROR,
            request.getDescription(false)
        );
    }

    /**
     * Handle resource not found exceptions (actuator endpoints)
     */
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            org.springframework.web.servlet.NoHandlerFoundException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();
        String requestedPath = ex.getRequestURL();

        log.error("No handler found - CorrelationId: {}, Path: {}, Method: {}",
            correlationId, requestedPath, ex.getHttpMethod());

        // Special handling for actuator endpoints
        if (requestedPath.startsWith("/actuator/")) {
            return createErrorResponse(
                correlationId,
                "ACTUATOR_ENDPOINT_ERROR",
                "Actuator endpoint not found. Please check management configuration.",
                HttpStatus.NOT_FOUND,
                request.getDescription(false)
            );
        }

        return createErrorResponse(
            correlationId,
            "ENDPOINT_NOT_FOUND",
            "The requested endpoint was not found: " + requestedPath,
            HttpStatus.NOT_FOUND,
            request.getDescription(false)
        );
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        // Special handling for static resource errors (now handled by ActuatorRedirectController)
        if (ex.getMessage() != null && ex.getMessage().contains("No static resource")) {
            log.debug("Static resource request handled by controller - CorrelationId: {}, Request: {}",
                correlationId, request.getDescription(false));

            return createErrorResponse(
                correlationId,
                "STATIC_RESOURCE_ERROR",
                "Resource not found. If accessing actuator endpoints, please use /api/v2/health instead.",
                HttpStatus.NOT_FOUND,
                request.getDescription(false)
            );
        }

        log.error("Unexpected error - CorrelationId: {}, Error: {}", correlationId, ex.getMessage(), ex);

        return createErrorResponse(
            correlationId,
            "UNEXPECTED_ERROR",
            "An unexpected error occurred. Please contact support with correlation ID: " + correlationId,
            HttpStatus.INTERNAL_SERVER_ERROR,
            request.getDescription(false)
        );
    }

    /**
     * Create standardized error response
     * 
     * MANDATORY: Immutable data structures - Rule #9
     */
    private ResponseEntity<ErrorResponse> createErrorResponse(
            String correlationId,
            String errorCode,
            String message,
            HttpStatus status,
            String path) {
        
        ErrorResponse errorResponse = new ErrorResponse(
            correlationId,
            errorCode,
            message,
            status.value(),
            LocalDateTime.now(),
            path,
            List.of()
        );
        
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Mask user ID for logging
     * 
     * MANDATORY: Security by default - Rule #6
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() < 3) {
            return "***";
        }
        return userId.substring(0, 2) + "***" + userId.substring(userId.length() - 1);
    }

    /**
     * Mask session ID for logging
     */
    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) {
            return "***";
        }
        return sessionId.substring(0, 4) + "***" + sessionId.substring(sessionId.length() - 4);
    }

    /**
     * Error Response DTO
     * 
     * MANDATORY: Records for DTOs - Rule #9
     */
    public record ErrorResponse(
        String correlationId,
        String errorCode,
        String message,
        int status,
        LocalDateTime timestamp,
        String path,
        List<String> details
    ) {}
}