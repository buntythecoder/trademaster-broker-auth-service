package com.trademaster.brokerauth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * JWT Authentication Entry Point for TradeMaster Golden Specification Compliance
 *
 * MANDATORY: Golden Specification Security Implementation
 * MANDATORY: Zero Trust Security Model - External Access Protection
 * MANDATORY: Proper error handling for authentication failures
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException {

        String requestPath = request.getRequestURI();
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        log.warn("JWT Authentication failed for request: {} from IP: {} - {}",
                requestPath, clientIp, authException.getMessage());

        // Create detailed error response
        Map<String, Object> errorResponse = Map.of(
            "error", "AUTHENTICATION_REQUIRED",
            "message", "Valid JWT token required for access",
            "path", requestPath,
            "timestamp", Instant.now().toString(),
            "status", HttpServletResponse.SC_UNAUTHORIZED,
            "details", Map.of(
                "reason", authException.getMessage(),
                "required_header", "Authorization: Bearer <jwt-token>",
                "token_endpoint", "/api/v1/broker-auth/authenticate"
            )
        );

        // Set response headers
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Add security headers
        response.setHeader("WWW-Authenticate", "Bearer realm=\"TradeMaster API\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // Write JSON response
        objectMapper.writeValue(response.getWriter(), errorResponse);

        // Audit log for security monitoring
        log.info("SECURITY_EVENT: JWT authentication failure - IP: {}, Path: {}, UserAgent: {}",
                clientIp, requestPath, userAgent);
    }

    /**
     * Extract client IP address from request headers
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}