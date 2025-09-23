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
        
        String requestPath = httpRequest.getRequestURI();
        
        // Only process internal API requests
        if (!requestPath.startsWith(INTERNAL_API_PATH)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Skip authentication if disabled (for local development)
        if (!serviceAuthEnabled) {
            log.warn("ServiceApiKeyFilter: Service authentication is DISABLED - allowing internal API access");
            setServiceAuthentication("development-service");
            chain.doFilter(request, response);
            return;
        }
        
        // Check for Kong consumer headers first (Kong has already validated the API key)
        String kongConsumerId = httpRequest.getHeader(KONG_CONSUMER_ID_HEADER);
        String kongConsumerUsername = httpRequest.getHeader(KONG_CONSUMER_USERNAME_HEADER);
        
        // If Kong consumer headers are present, Kong has already validated the API key
        if (StringUtils.hasText(kongConsumerId) && StringUtils.hasText(kongConsumerUsername)) {
            log.info("ServiceApiKeyFilter: Kong validated consumer '{}' (ID: {}), granting SERVICE access", 
                     kongConsumerUsername, kongConsumerId);
            setServiceAuthentication(kongConsumerUsername);
            chain.doFilter(request, response);
            return;
        }
        
        // Fall back to direct API key validation (for direct service calls not through Kong)
        String apiKey = httpRequest.getHeader(API_KEY_HEADER);
        
        if (!StringUtils.hasText(apiKey)) {
            log.error("ServiceApiKeyFilter: No Kong consumer headers and missing X-API-Key header for request: {} from {}", 
                     requestPath, httpRequest.getRemoteAddr());
            sendUnauthorizedResponse(httpResponse, "Missing service API key or Kong consumer headers");
            return;
        }
        
        // For fallback validation, we can use a simple check or integrate with your existing validation logic
        if (StringUtils.hasText(fallbackServiceApiKey) && !fallbackServiceApiKey.equals(apiKey)) {
            log.error("ServiceApiKeyFilter: Invalid API key for direct service request: {} from {}", 
                     requestPath, httpRequest.getRemoteAddr());
            sendUnauthorizedResponse(httpResponse, "Invalid service API key");
            return;
        }
        
        // Set service authentication for fallback case
        setServiceAuthentication("direct-service-call");
        
        log.info("ServiceApiKeyFilter: Direct API key authentication successful for request: {}", requestPath);
        
        chain.doFilter(request, response);
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
}