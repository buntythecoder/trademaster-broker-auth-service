package com.trademaster.brokerauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service Discovery Configuration
 * 
 * MANDATORY: Service discovery for microservices - Rule #26
 * MANDATORY: Health endpoints for monitoring - Rule #22
 * MANDATORY: Service metadata for routing - Rule #26
 */
@Configuration
public class ServiceDiscoveryConfig {
    
    @Value("${spring.application.name:broker-auth-service}")
    private String serviceName;
    
    @Value("${server.port:8087}")
    private int serverPort;
    
    @Value("${app.version:1.0.0}")
    private String serviceVersion;
    
    /**
     * Service Information Contributor
     * 
     * MANDATORY: Service metadata for discovery - Rule #26
     */
    @Bean
    public InfoContributor serviceInfoContributor() {
        return builder -> builder
            .withDetail("service", Map.of(
                "name", serviceName,
                "version", serviceVersion,
                "port", serverPort,
                "type", "broker-authentication",
                "description", "TradeMaster Broker Authentication Service",
                "capabilities", Map.of(
                    "brokers", "ZERODHA,UPSTOX,ANGEL_ONE,ICICI_DIRECT",
                    "authentication", "JWT,OAuth2,API_KEY",
                    "security", "AES256,VAULT,RATE_LIMITING",
                    "monitoring", "PROMETHEUS,HEALTH_CHECKS,STRUCTURED_LOGGING"
                ),
                "dependencies", Map.of(
                    "database", "PostgreSQL",
                    "cache", "Redis",
                    "messaging", "Kafka",
                    "secrets", "HashiCorp Vault"
                ),
                "endpoints", Map.of(
                    "health", "/actuator/health",
                    "metrics", "/actuator/metrics",
                    "info", "/actuator/info",
                    "api", "/api/v1/auth",
                    "swagger", "/swagger-ui.html"
                ),
                "startup", Map.of(
                    "timestamp", LocalDateTime.now().toString(),
                    "jvm", System.getProperty("java.version"),
                    "threads", "virtual",
                    "profile", System.getProperty("spring.profiles.active", "default")
                )
            ));
    }
    
    /**
     * Kubernetes Service Discovery Configuration
     * 
     * MANDATORY: K8s service discovery - Rule #26
     */
    @Bean
    @Profile("kubernetes")
    public InfoContributor kubernetesInfoContributor() {
        return builder -> builder
            .withDetail("kubernetes", Map.of(
                "namespace", System.getenv().getOrDefault("KUBERNETES_NAMESPACE", "default"),
                "podName", System.getenv().getOrDefault("HOSTNAME", "unknown"),
                "serviceName", serviceName,
                "serviceLabels", Map.of(
                    "app", serviceName,
                    "version", serviceVersion,
                    "component", "authentication",
                    "tier", "backend"
                ),
                "ports", Map.of(
                    "http", serverPort,
                    "metrics", serverPort,
                    "health", serverPort
                ),
                "readinessProbe", "/actuator/health/readiness",
                "livenessProbe", "/actuator/health/liveness"
            ));
    }
    
    /**
     * Docker Service Discovery Configuration
     * 
     * MANDATORY: Docker service discovery - Rule #26
     */
    @Bean
    @Profile("docker")
    public InfoContributor dockerInfoContributor() {
        return builder -> builder
            .withDetail("docker", Map.of(
                "containerName", System.getenv().getOrDefault("HOSTNAME", serviceName),
                "networkMode", "bridge",
                "ports", Map.of(
                    "internal", serverPort,
                    "external", System.getenv().getOrDefault("EXTERNAL_PORT", String.valueOf(serverPort))
                ),
                "volumes", Map.of(
                    "logs", "/app/logs",
                    "config", "/app/config"
                ),
                "healthCheck", Map.of(
                    "interval", "30s",
                    "timeout", "10s",
                    "retries", 3,
                    "startPeriod", "60s"
                )
            ));
    }
    
    /**
     * Load Balancer Configuration
     * 
     * MANDATORY: Load balancer integration - Rule #26
     */
    @Bean
    public InfoContributor loadBalancerInfoContributor() {
        return builder -> builder
            .withDetail("loadBalancer", Map.of(
                "healthCheck", Map.of(
                    "path", "/actuator/health",
                    "port", serverPort,
                    "protocol", "HTTP",
                    "interval", 30,
                    "timeout", 10,
                    "healthyThreshold", 2,
                    "unhealthyThreshold", 3
                ),
                "routing", Map.of(
                    "pathPattern", "/api/v1/auth/*",
                    "method", "ROUND_ROBIN",
                    "stickySession", false,
                    "timeout", 30000
                ),
                "ssl", Map.of(
                    "enabled", true,
                    "protocols", "TLSv1.2,TLSv1.3",
                    "ciphers", "HIGH:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!PSK:!SRP:!CAMELLIA"
                )
            ));
    }
    
    /**
     * API Gateway Configuration
     * 
     * MANDATORY: API Gateway integration - Rule #26
     */
    @Bean
    public InfoContributor apiGatewayInfoContributor() {
        return builder -> builder
            .withDetail("apiGateway", Map.of(
                "routes", Map.of(
                    "authentication", Map.of(
                        "path", "/api/v1/auth/**",
                        "methods", "GET,POST,PUT,DELETE",
                        "timeout", 30000,
                        "retries", 3
                    ),
                    "health", Map.of(
                        "path", "/actuator/health",
                        "methods", "GET",
                        "timeout", 5000,
                        "public", true
                    )
                ),
                "security", Map.of(
                    "authentication", "JWT",
                    "rateLimiting", true,
                    "cors", true,
                    "requestLogging", true
                ),
                "circuitBreaker", Map.of(
                    "enabled", true,
                    "failureThreshold", 50,
                    "timeout", 30000,
                    "fallback", "/api/v1/auth/fallback"
                )
            ));
    }
    
    /**
     * Monitoring Integration Configuration
     * 
     * MANDATORY: Monitoring system integration - Rule #22
     */
    @Bean
    public InfoContributor monitoringInfoContributor() {
        return builder -> builder
            .withDetail("monitoring", Map.of(
                "metrics", Map.of(
                    "prometheus", Map.of(
                        "enabled", true,
                        "endpoint", "/actuator/prometheus",
                        "scrapeInterval", "15s",
                        "labels", Map.of(
                            "service", serviceName,
                            "version", serviceVersion,
                            "environment", System.getProperty("spring.profiles.active", "development")
                        )
                    )
                ),
                "logging", Map.of(
                    "structured", true,
                    "format", "JSON",
                    "level", "INFO",
                    "appenders", "CONSOLE,FILE,KAFKA"
                ),
                "tracing", Map.of(
                    "enabled", true,
                    "sampler", "probabilistic",
                    "samplingRate", 0.1,
                    "exporters", "jaeger,zipkin"
                ),
                "alerts", Map.of(
                    "healthCheck", true,
                    "highLatency", true,
                    "errorRate", true,
                    "memoryUsage", true
                )
            ));
    }
    
    /**
     * Security Configuration for Service Discovery
     * 
     * MANDATORY: Secure service communication - Rule #23
     */
    @Bean
    public InfoContributor securityInfoContributor() {
        return builder -> builder
            .withDetail("security", Map.of(
                "authentication", Map.of(
                    "type", "JWT",
                    "issuer", "trademaster-auth-service",
                    "audience", "trademaster-services",
                    "algorithms", "RS256,ES256"
                ),
                "authorization", Map.of(
                    "rbac", true,
                    "permissions", "READ,WRITE,ADMIN",
                    "roles", "USER,TRADER,ADMIN"
                ),
                "transport", Map.of(
                    "tls", true,
                    "mtls", true,
                    "certificates", "X.509",
                    "keyStore", "PKCS12"
                ),
                "compliance", Map.of(
                    "gdpr", true,
                    "sox", true,
                    "pci", true,
                    "audit", true
                )
            ));
    }
}