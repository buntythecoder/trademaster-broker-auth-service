package com.trademaster.brokerauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Kong API Gateway Configuration Properties
 * 
 * Binds Kong-specific configuration from application.yml for service-to-service authentication
 * and routing configuration per TradeMaster Golden Specification.
 * 
 * MANDATORY: Zero Trust Security - Rule #6
 * MANDATORY: Functional Programming - Rule #3
 * 
 * @author TradeMaster Development Team
 * @version 2.0.0 (Kong Integration)
 */
@Component
@ConfigurationProperties(prefix = "trademaster.security.kong")
@Data
@Validated
public class KongConfiguration {
    
    /**
     * Kong Consumer Headers Configuration
     */
    private Headers headers = new Headers();
    
    /**
     * Service API Keys for authenticated service-to-service communication
     */
    private Map<String, String> serviceKeys;
    
    /**
     * Kong Upstream Configuration for service registration
     */
    private Upstream upstream = new Upstream();
    
    @Data
    public static class Headers {
        private String consumerId = "X-Consumer-ID";
        private String consumerUsername = "X-Consumer-Username";
        private String consumerCustomId = "X-Consumer-Custom-ID";
        private String apiKey = "X-API-Key";
    }
    
    @Data
    public static class Upstream {
        private String name;
        private String url;
        private String healthCheckUrl;
        private Routes routes = new Routes();
    }
    
    @Data
    public static class Routes {
        private Route external = new Route();
        private Route internal = new Route();
    }
    
    @Data
    public static class Route {
        private String path;
        private String[] methods;
        private boolean stripPath = false;
    }
}