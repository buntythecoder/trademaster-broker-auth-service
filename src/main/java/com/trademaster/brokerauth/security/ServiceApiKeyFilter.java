package com.trademaster.brokerauth.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Service API Key Authentication Filter - Kong Dynamic Integration for Broker Auth Service
 * 
 * Updated to work with Kong API Gateway dynamic API keys instead of hardcoded keys.
 * Recognizes Kong consumer headers when API key validation is performed by Kong.
 * Falls back to direct API key validation when needed.
 * 
 * Security Features:
 * - Kong consumer header recognition (X-Consumer-ID, X-Consumer-Username)
 * - Dynamic API key validation through Kong
 * - Request path filtering (only /api/internal/*)
 * - Audit logging for service authentication
 * - Fail-safe authentication bypass for health checks
 * 
 * @author TradeMaster Development Team
 * @version 2.0.0 (Kong Integration)
 */
@Component
@Order(1) // Run before JWT filter
@Slf4j
public class ServiceApiKeyFilter implements Filter {
    
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String SERVICE_ID_HEADER = "X-Service-ID";
    private static final String KONG_CONSUMER_ID_HEADER = "X-Consumer-ID";
    private static final String KONG_CONSUMER_USERNAME_HEADER = "X-Consumer-Username";
    private static final String INTERNAL_API_PATH = "/api/internal/";
    
    @Value("${trademaster.security.service.api-key:}")
    private String fallbackServiceApiKey;
    
    @Value("${trademaster.security.service.enabled:true}")
    private boolean serviceAuthEnabled;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        processRequest(httpRequest, httpResponse, chain);
    }

    /**
     * Functional request processing pipeline - Rule #3 Functional Programming
     */
    private void processRequest(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        RequestContext context = RequestContext.of(request, response, chain);

        // Functional pipeline with pattern matching
        AuthenticationResult result = Optional.of(context)
            .filter(this::isInternalApiRequest)
            .map(this::determineAuthenticationStrategy)
            .orElse(AuthenticationStrategy.bypass())
            .executeStrategy(context);

        // Pattern matching for result handling
        handleAuthenticationResult(result, context);
    }

    /**
     * Predicate for internal API request filtering
     */
    private boolean isInternalApiRequest(RequestContext context) {
        return context.requestPath().startsWith(INTERNAL_API_PATH);
    }

    /**
     * Functional strategy determination using pattern matching
     */
    private AuthenticationStrategy determineAuthenticationStrategy(RequestContext context) {
        return switch (true) {
            case boolean b when !serviceAuthEnabled ->
                AuthenticationStrategy.development();
            case boolean b when hasKongConsumerHeaders(context) ->
                AuthenticationStrategy.kongValidated();
            case boolean b when hasValidApiKey(context) ->
                AuthenticationStrategy.directApiKey();
            default ->
                AuthenticationStrategy.unauthorized("Missing service API key or Kong consumer headers");
        };
    }

    /**
     * Pattern matching for result handling
     */
    private void handleAuthenticationResult(AuthenticationResult result, RequestContext context)
            throws IOException, ServletException {

        switch (result.type()) {
            case SUCCESS -> {
                setServiceAuthentication(result.serviceId());
                result.logMessage().ifPresent(msg -> log.info("ServiceApiKeyFilter: {}", msg));
                context.chain().doFilter(context.request(), context.response());
            }
            case BYPASS -> {
                context.chain().doFilter(context.request(), context.response());
            }
            case FAILURE -> {
                log.error("ServiceApiKeyFilter: Authentication failed for request: {} from {} - {}",
                         context.requestPath(), context.request().getRemoteAddr(), result.errorMessage());
                sendUnauthorizedResponse(context.response(), result.errorMessage());
            }
        }
    }

    /**
     * Functional predicate for Kong consumer header validation
     */
    private boolean hasKongConsumerHeaders(RequestContext context) {
        return Optional.ofNullable(context.request().getHeader(KONG_CONSUMER_ID_HEADER))
            .filter(StringUtils::hasText)
            .flatMap(id -> Optional.ofNullable(context.request().getHeader(KONG_CONSUMER_USERNAME_HEADER))
                .filter(StringUtils::hasText)
                .map(username -> true))
            .orElse(false);
    }

    /**
     * Functional predicate for API key validation
     */
    private boolean hasValidApiKey(RequestContext context) {
        return Optional.ofNullable(context.request().getHeader(API_KEY_HEADER))
            .filter(StringUtils::hasText)
            .filter(apiKey -> !StringUtils.hasText(fallbackServiceApiKey) ||
                             fallbackServiceApiKey.equals(apiKey))
            .isPresent();
    }
    
    /**
     * Set service authentication in Spring Security context
     */
    private void setServiceAuthentication(String serviceId) {
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_SERVICE"),
            new SimpleGrantedAuthority("ROLE_INTERNAL")
        );
        
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(serviceId, null, authorities);
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    
    /**
     * Send unauthorized response
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"error\":\"SERVICE_AUTHENTICATION_FAILED\",\"message\":\"%s\",\"timestamp\":%d,\"service\":\"broker-auth-service\"}",
            message, System.currentTimeMillis()));
    }

    /**
     * Immutable request context record - Rule #9 Immutability
     */
    private record RequestContext(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain,
        String requestPath
    ) {
        public static RequestContext of(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
            return new RequestContext(request, response, chain, request.getRequestURI());
        }
    }

    /**
     * Authentication strategy sealed class - Rule #14 Pattern Matching
     */
    private sealed interface AuthenticationStrategy {
        AuthenticationResult executeStrategy(RequestContext context);

        static AuthenticationStrategy development() {
            return new DevelopmentStrategy();
        }

        static AuthenticationStrategy kongValidated() {
            return new KongValidatedStrategy();
        }

        static AuthenticationStrategy directApiKey() {
            return new DirectApiKeyStrategy();
        }

        static AuthenticationStrategy unauthorized(String message) {
            return new UnauthorizedStrategy(message);
        }

        static AuthenticationStrategy bypass() {
            return new BypassStrategy();
        }
    }

    /**
     * Strategy implementations using records - Rule #9 Immutability
     */
    private record DevelopmentStrategy() implements AuthenticationStrategy {
        @Override
        public AuthenticationResult executeStrategy(RequestContext context) {
            return AuthenticationResult.success("development-service",
                "Service authentication is DISABLED - allowing internal API access");
        }
    }

    private record KongValidatedStrategy() implements AuthenticationStrategy {
        @Override
        public AuthenticationResult executeStrategy(RequestContext context) {
            String konsumerId = context.request().getHeader(KONG_CONSUMER_ID_HEADER);
            String consumerUsername = context.request().getHeader(KONG_CONSUMER_USERNAME_HEADER);
            return AuthenticationResult.success(consumerUsername,
                String.format("Kong validated consumer '%s' (ID: %s), granting SERVICE access",
                             consumerUsername, konsumerId));
        }
    }

    private record DirectApiKeyStrategy() implements AuthenticationStrategy {
        @Override
        public AuthenticationResult executeStrategy(RequestContext context) {
            return AuthenticationResult.success("direct-service-call",
                String.format("Direct API key authentication successful for request: %s",
                             context.requestPath()));
        }
    }

    private record UnauthorizedStrategy(String message) implements AuthenticationStrategy {
        @Override
        public AuthenticationResult executeStrategy(RequestContext context) {
            return AuthenticationResult.failure(message);
        }
    }

    private record BypassStrategy() implements AuthenticationStrategy {
        @Override
        public AuthenticationResult executeStrategy(RequestContext context) {
            return AuthenticationResult.bypass();
        }
    }

    /**
     * Authentication result with pattern matching support - Rule #14
     */
    private record AuthenticationResult(
        ResultType type,
        String serviceId,
        String errorMessage,
        Optional<String> logMessage
    ) {
        public static AuthenticationResult success(String serviceId, String logMessage) {
            return new AuthenticationResult(ResultType.SUCCESS, serviceId, null, Optional.of(logMessage));
        }

        public static AuthenticationResult bypass() {
            return new AuthenticationResult(ResultType.BYPASS, null, null, Optional.empty());
        }

        public static AuthenticationResult failure(String errorMessage) {
            return new AuthenticationResult(ResultType.FAILURE, null, errorMessage, Optional.empty());
        }
    }

    /**
     * Result type enumeration for pattern matching
     */
    private enum ResultType {
        SUCCESS, BYPASS, FAILURE
    }
}