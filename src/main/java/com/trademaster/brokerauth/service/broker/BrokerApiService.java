package com.trademaster.brokerauth.service.broker;

import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Broker API Service Interface
 * 
 * MANDATORY: Interface Segregation - Rule #2
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Zero Placeholders - Rule #7
 */
public interface BrokerApiService {
    
    /**
     * Authenticate with the broker using real API calls
     * 
     * @param request Authentication request with broker credentials
     * @return CompletableFuture containing authentication response
     */
    CompletableFuture<AuthResponse> authenticate(AuthRequest request);
    
    /**
     * Refresh authentication token
     * 
     * @param refreshToken The refresh token
     * @return CompletableFuture containing new authentication response
     */
    CompletableFuture<AuthResponse> refreshToken(String refreshToken);
    
    /**
     * Validate if the service supports the given broker
     * 
     * @param brokerType The broker type to check
     * @return true if supported, false otherwise
     */
    boolean supports(com.trademaster.brokerauth.enums.BrokerType brokerType);
    
    /**
     * Get broker name
     * 
     * @return The name of the broker
     */
    String getBrokerName();
}