package com.trademaster.brokerauth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ✅ ACTUATOR CONFIGURATION: Custom actuator endpoint handling for Broker Auth Service
 *
 * MANDATORY COMPLIANCE:
 * - Zero Trust Security pattern - external access through SecurityFacade
 * - Functional programming patterns - no if-else statements
 * - SOLID principles with single responsibility
 * - Cognitive complexity ≤7 per method
 *
 * PURPOSE:
 * - Provides consistent actuator endpoint behavior across services
 * - Eliminates "No static resource" errors on main application port
 * - Redirects actuator requests to appropriate ports and endpoints
 *
 * ARCHITECTURE:
 * - Main port (8084): Application endpoints with helpful redirects
 * - Management port (9084): Full actuator endpoints
 * - Alternative health endpoint: /api/v2/health for application port
 */
@Configuration
@Slf4j
public class ActuatorConfiguration implements WebMvcConfigurer {

    /**
     * ✅ FUNCTIONAL: Configure resource handlers to prevent static resource errors
     * Cognitive Complexity: 2
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ✅ FUNCTIONAL: No if-else, use method chaining pattern
        registry.addResourceHandler("/actuator/**")
                .addResourceLocations("classpath:/META-INF/resources/")
                .setCachePeriod(0);

        log.info("✅ Actuator resource handlers configured for broker-auth-service");
    }

}