package com.trademaster.brokerauth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Spring Cloud Config Client Configuration
 * 
 * MANDATORY: Configuration refresh and monitoring - Rule #16
 * MANDATORY: Structured logging for config events - Rule #15
 * MANDATORY: Virtual Threads compatibility - Rule #12
 */
@Configuration
@RefreshScope
@EnableScheduling
@ConfigurationProperties(prefix = "spring.cloud.config")
@Validated
@Slf4j
public class ConfigClientConfig {
    
    @Value("${spring.cloud.config.uri:http://localhost:8888/config}")
    private String configServerUri;
    
    @Value("${spring.cloud.config.name:broker-auth-service}")
    private String configName;
    
    @Value("${spring.cloud.config.profile:dev}")
    private String configProfile;
    
    @PostConstruct
    public void logConfigurationSource() {
        log.info("Configuration loaded from Config Server: uri={}, name={}, profile={}", 
                configServerUri, configName, configProfile);
    }
    
    /**
     * Handle configuration refresh events
     * 
     * MANDATORY: Functional event handling - Rule #3
     * MANDATORY: Structured logging - Rule #15
     */
    @EventListener
    public void handleConfigurationChange(EnvironmentChangeEvent event) {
        Set<String> changedKeys = event.getKeys();
        
        log.info("Configuration refresh detected: {} properties changed", changedKeys.size());
        
        changedKeys.stream()
            .filter(key -> !key.contains("password") && !key.contains("secret"))
            .forEach(key -> log.debug("Configuration changed: {}", key));
        
        // Log sensitive property changes without values
        long sensitiveChanges = changedKeys.stream()
            .filter(key -> key.contains("password") || key.contains("secret"))
            .count();
        
        if (sensitiveChanges > 0) {
            log.warn("Configuration refresh included {} sensitive properties", sensitiveChanges);
        }
    }
}