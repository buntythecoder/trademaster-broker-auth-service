package com.trademaster.brokerauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Import;

/**
 * Eureka Service Discovery Configuration
 * 
 * MANDATORY: Enable service discovery for Config Server integration - Rule #24
 * MANDATORY: Conditional activation based on profile - Rule #16
 */
@Configuration
@Import(EurekaClientAutoConfiguration.class)
@Profile("!dev")  // Disable in dev environment for local development
@ConfigurationProperties(prefix = "eureka")
public class EurekaConfig {
    
    // Configuration handled by application.yml properties
    // This class enables Eureka client functionality through auto-configuration import
}