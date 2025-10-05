package com.trademaster.brokerauth.config;

import com.trademaster.brokerauth.security.ServiceApiKeyFilter;
import com.trademaster.brokerauth.security.JwtAuthenticationEntryPoint;
import com.trademaster.brokerauth.security.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security Configuration for Broker Auth Service
 * 
 * MANDATORY: Zero Trust Security - Rule #6
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Kong Integration - Golden Specification
 * 
 * Security Layers:
 * - External APIs (/api/v1/*): Full Zero Trust with SecurityFacade
 * - Internal APIs (/api/internal/*): Kong API Key authentication via ServiceApiKeyFilter
 * - Health Endpoints: Public access for load balancers
 * 
 * Filter Order:
 * 1. ServiceApiKeyFilter (Order 1) - Handles Kong consumer headers and API key validation
 * 2. UsernamePasswordAuthenticationFilter - Standard Spring Security
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final ServiceApiKeyFilter serviceApiKeyFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtRequestFilter jwtRequestFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - Health checks for load balancers
                .requestMatchers("/actuator/health", "/actuator/info", "/api/v2/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                
                // Internal endpoints - Kong API key authentication (handled by ServiceApiKeyFilter)
                .requestMatchers("/api/internal/**").hasRole("SERVICE")
                
                // External endpoints - JWT authentication (handled by SecurityFacade in controllers)
                .requestMatchers("/api/v1/**").permitAll() // Authentication handled by SecurityFacade
                
                .anyRequest().denyAll()
            )
            // JWT authentication entry point for external API failures
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            // Add custom authentication filters in order
            .addFilterBefore(serviceApiKeyFilter, UsernamePasswordAuthenticationFilter.class)  // Order 1: Kong API keys
            .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)      // Order 2: JWT validation
            .build();
    }
}