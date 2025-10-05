package com.trademaster.brokerauth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Internal Service Client for Broker Auth Service
 * 
 * Example client for making internal service-to-service calls with API key authentication.
 * This demonstrates how broker-auth-service calls other internal services.
 * 
 * Usage Example:
 * - Call trading-service for position validation
 * - Call event-bus-service for authentication events
 * - Call portfolio-service for account validation
 * 
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Component
@Slf4j
public class InternalServiceClient {
    
    private final RestTemplate restTemplate;
    private final RestTemplate circuitBreakerRestTemplate;
    
    @Value("${trademaster.security.service.api-key:default-broker-auth-key}")
    private String serviceApiKey;
    
    @Value("${trademaster.service.name:broker-auth-service}")
    private String serviceName;
    
    /**
     * ✅ CONSTRUCTOR INJECTION: Use configured RestTemplate with connection pooling
     * Primary RestTemplate has connection pooling configured via HttpClientConfig
     * Circuit breaker RestTemplate provides additional resilience for service calls
     */
    public InternalServiceClient(RestTemplate restTemplate, 
                               @Qualifier("circuitBreakerRestTemplate") RestTemplate circuitBreakerRestTemplate) {
        this.restTemplate = restTemplate;
        this.circuitBreakerRestTemplate = circuitBreakerRestTemplate;
        log.info("✅ Broker Auth Service InternalServiceClient initialized with connection pooling");
    }
    
    /**
     * Example: Call trading service for position validation
     * This shows how broker-auth-service would validate user positions
     */
    public Map<String, Object> validateUserPositions(String userId) {
        try {
            String url = "http://trading-service:8083/api/internal/v1/trading/positions/" + userId + "/validate";
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            log.info("Calling trading service for position validation: {} with headers: {}", url, headers.keySet());
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            log.info("Position validation successful: {}", response.getStatusCode());
            return response.getBody();
            
        } catch (RestClientException e) {
            log.error("Position validation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Position validation failed", e);
        }
    }
    
    /**
     * Example: Publish authentication event to event-bus service
     */
    public void publishAuthEvent(String userId, String event, Object eventData) {
        try {
            String url = "http://event-bus-service:8082/api/internal/v1/events/auth";
            
            Map<String, Object> payload = Map.of(
                "userId", userId,
                "event", event,
                "data", eventData,
                "timestamp", System.currentTimeMillis()
            );
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            ResponseEntity<Map> response = circuitBreakerRestTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            log.info("Authentication event published successfully: {}", response.getStatusCode());
            
        } catch (RestClientException e) {
            log.error("Failed to publish authentication event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish authentication event", e);
        }
    }
    
    /**
     * Example: Call portfolio service for account validation
     */
    public Map<String, Object> validateBrokerAccount(String userId, String brokerId) {
        try {
            String url = String.format("http://portfolio-service:8085/api/internal/v1/accounts/%s/broker/%s/validate", 
                userId, brokerId);
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            return response.getBody();
            
        } catch (RestClientException e) {
            log.error("Failed to validate broker account: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to validate broker account", e);
        }
    }
    
    /**
     * Create headers for service-to-service authentication
     */
    private HttpHeaders createServiceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", serviceApiKey);
        headers.set("X-Service-ID", serviceName);
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");
        
        return headers;
    }
    
    /**
     * Health check for service connectivity
     */
    public boolean checkServiceHealth(String serviceUrl) {
        try {
            String url = serviceUrl + "/api/internal/v1/auth/health";
            
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Service health check failed for: {}", serviceUrl, e);
            return false;
        }
    }
}