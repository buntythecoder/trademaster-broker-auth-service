package com.trademaster.brokerauth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ✅ OPENAPI CONFIGURATION: Production-Ready API Documentation for Broker Auth Service
 * 
 * MANDATORY COMPLIANCE:
 * - Rule #6: Zero Trust Security with JWT authentication documentation
 * - Rule #15: Structured logging with correlation IDs
 * - Rule #22: Performance standards documented in API specs
 * 
 * FEATURES:
 * - Complete API documentation with broker authentication examples
 * - Security scheme definitions for JWT and API key authentication
 * - Environment-specific server configurations
 * - Contact and license information for enterprise use
 * - Broker integration endpoint documentation
 * - Internal API documentation for service-to-service calls
 * 
 * Cognitive Complexity: ≤7 per method, ≤15 total per class
 */
@Configuration
@Slf4j
public class OpenApiConfiguration {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;
    
    @Value("${server.port:8087}")
    private String serverPort;

    /**
     * ✅ FUNCTIONAL: Configure OpenAPI documentation for Broker Auth Service
     * Cognitive Complexity: 3
     */
    @Bean
    public OpenAPI brokerAuthOpenAPI() {
        log.info("Configuring OpenAPI documentation for Broker Auth Service");
        
        return new OpenAPI()
            .info(buildApiInfo())
            .servers(buildServerList())
            .addSecurityItem(buildJwtSecurityRequirement())
            .addSecurityItem(buildApiKeySecurityRequirement())
            .components(buildSecurityComponents());
    }

    /**
     * ✅ FUNCTIONAL: Build API information for Broker Auth Service
     * Cognitive Complexity: 1
     */
    private Info buildApiInfo() {
        return new Info()
            .title("TradeMaster Broker Auth Service API")
            .version("1.0.0")
            .description("""
                ## TradeMaster Broker Auth Service
                
                **Production-ready broker authentication service with multi-broker integration**
                
                ### Core Features
                - **Multi-Broker Support**: Integration with Zerodha, Upstox, Angel One, and ICICI Direct
                - **Session Management**: Secure session handling with Redis caching
                - **Credential Encryption**: AES-256 encryption with HashiCorp Vault integration
                - **Rate Limiting**: Broker-specific rate limiting and circuit breaker protection
                - **OAuth2 & API Key**: Support for multiple authentication methods
                - **Real-time Monitoring**: Performance tracking with SLA compliance
                
                ### Supported Brokers
                - **Zerodha Kite**: OAuth2 flow with session tokens (10 req/sec, 3K/min)
                - **Upstox Pro**: OAuth2 with refresh tokens (25 req/sec, 250/min)
                - **Angel One SmartAPI**: TOTP-based authentication (25 req/sec, 200/min)
                - **ICICI Direct Breeze**: Session-based authentication (2 req/sec, 100/min)
                
                ### Architecture
                - **Java 24 + Virtual Threads**: High-concurrency session processing
                - **Zero Trust Security**: Full SecurityFacade pattern for external access
                - **Consul Service Discovery**: Dynamic service registration and health monitoring
                - **Kong API Gateway**: Internal service-to-service authentication
                - **Circuit Breaker Protection**: Resilient broker API integration
                
                ### SLA Targets
                - **Authentication Requests**: ≤100ms processing time
                - **Session Validation**: ≤25ms response time
                - **Broker API Calls**: ≤500ms with circuit breaker protection
                - **Credential Operations**: ≤50ms with encryption/decryption
                
                ### Security Features
                - **AES-256 Encryption**: All broker credentials encrypted at rest
                - **HashiCorp Vault**: Secure secret management and rotation
                - **JWT Authentication**: Stateless authentication for external APIs
                - **API Key Authentication**: Kong-managed keys for internal services
                - **Audit Logging**: Comprehensive security event logging
                
                ### Monitoring & Observability
                - Prometheus metrics at `/actuator/prometheus`
                - Health checks at `/actuator/health` and `/api/v2/health`
                - Circuit breaker status monitoring
                - Session and rate limiting metrics
                
                ### Internal API Access
                Internal service endpoints (`/api/internal/*`) require Kong API key authentication
                and are used by other TradeMaster services for session validation and data access.
                """)
            .contact(buildContactInfo())
            .license(buildLicenseInfo());
    }

    /**
     * ✅ FUNCTIONAL: Build contact information
     * Cognitive Complexity: 1
     */
    private Contact buildContactInfo() {
        return new Contact()
            .name("TradeMaster Engineering Team")
            .email("engineering@trademaster.com")
            .url("https://trademaster.com/support");
    }

    /**
     * ✅ FUNCTIONAL: Build license information
     * Cognitive Complexity: 1
     */
    private License buildLicenseInfo() {
        return new License()
            .name("TradeMaster Enterprise License")
            .url("https://trademaster.com/license");
    }

    /**
     * ✅ FUNCTIONAL: Build server list for different environments
     * Cognitive Complexity: 2
     */
    private List<Server> buildServerList() {
        return List.of(
            new Server()
                .url("http://localhost:" + serverPort + contextPath)
                .description("Development Environment"),
            new Server()
                .url("https://api-dev.trademaster.com/broker-auth")
                .description("Development Environment (Remote)"),
            new Server()
                .url("https://api-staging.trademaster.com/broker-auth")
                .description("Staging Environment"),
            new Server()
                .url("https://api.trademaster.com/broker-auth")
                .description("Production Environment")
        );
    }

    /**
     * ✅ FUNCTIONAL: Build JWT security requirement
     * Cognitive Complexity: 1
     */
    private SecurityRequirement buildJwtSecurityRequirement() {
        return new SecurityRequirement().addList("Bearer Authentication");
    }

    /**
     * ✅ FUNCTIONAL: Build API Key security requirement
     * Cognitive Complexity: 1
     */
    private SecurityRequirement buildApiKeySecurityRequirement() {
        return new SecurityRequirement().addList("API Key Authentication");
    }

    /**
     * ✅ FUNCTIONAL: Build security components for JWT and API key authentication
     * Cognitive Complexity: 2
     */
    private Components buildSecurityComponents() {
        return new Components()
            .addSecuritySchemes("Bearer Authentication", 
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("""
                        ### JWT Authentication
                        
                        **Required for all external broker authentication endpoints**
                        
                        #### How to obtain a token:
                        1. Authenticate with the TradeMaster Authentication Service
                        2. Extract the JWT token from the response
                        3. Include the token in the Authorization header: `Bearer <token>`
                        
                        #### Token Structure:
                        - **Issuer**: TradeMaster Authentication Service
                        - **Expiration**: 1 hour (configurable)
                        - **Claims**: user ID, roles, permissions, broker access rights
                        
                        #### Example:
                        ```
                        Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
                        ```
                        
                        #### Security Notes:
                        - Tokens are validated on every request
                        - Role-based access control enforced
                        - Correlation IDs logged for audit trails
                        - Broker-specific permissions validated
                        """))
            .addSecuritySchemes("API Key Authentication",
                new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")
                    .description("""
                        ### API Key Authentication
                        
                        **Required for internal service-to-service communication**
                        
                        #### Kong Gateway Integration:
                        - Kong validates API keys and sets consumer headers
                        - Services recognize `X-Consumer-ID` and `X-Consumer-Username`
                        - Fallback to direct API key validation if needed
                        
                        #### Usage:
                        ```
                        X-API-Key: [service-api-key]
                        X-Service-ID: [calling-service-name]
                        ```
                        
                        #### Internal Endpoints:
                        - `/api/internal/v1/broker-auth/health` - Health check (no auth)
                        - `/api/internal/v1/broker-auth/status` - Service status
                        - `/api/internal/v1/broker-auth/sessions/{sessionId}/validate` - Session validation
                        - `/api/internal/v1/broker-auth/users/{userId}/sessions` - User sessions
                        
                        #### Service Integration Examples:
                        - **Trading Service**: Validate sessions before order execution
                        - **Portfolio Service**: Get account data for position tracking
                        - **Risk Service**: Access session data for risk calculations
                        - **Notification Service**: Check session status for alerts
                        """));
    }
}