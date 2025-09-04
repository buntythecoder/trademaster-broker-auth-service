package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.service.broker.BrokerServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Broker Authentication Service
 * 
 * Core authentication logic using functional programming patterns.
 * 
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Pattern Matching - Rule #14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerAuthenticationService {
    
    private final BrokerSessionService sessionService;
    private final BrokerServiceFactory brokerServiceFactory;
    
    /**
     * Authenticate user with broker using real API services
     * 
     * MANDATORY: Real API Integration - Rule #7
     * MANDATORY: Factory Pattern - Rule #4
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<AuthResponse> authenticate(AuthRequest request) {
        return brokerServiceFactory.getBrokerService(request.brokerType())
            .map(brokerService -> brokerService.authenticate(request)
                .thenCompose(authResponse -> authResponse.success() 
                    ? saveSessionAndReturn(request, authResponse)
                    : CompletableFuture.completedFuture(authResponse)))
            .orElse(CompletableFuture.completedFuture(
                createUnsupportedBrokerResponse(request.brokerType())));
    }
    
    /**
     * Save broker session and return response
     * 
     * MANDATORY: Functional Programming - Rule #3
     */
    private CompletableFuture<AuthResponse> saveSessionAndReturn(AuthRequest request, AuthResponse authResponse) {
        return CompletableFuture
            .supplyAsync(() -> createBrokerSession(request, authResponse), 
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenApply(session -> {
                sessionService.saveSession(session);
                log.info("Session saved for user: {} with broker: {}", 
                    request.userId(), request.brokerType());
                return authResponse;
            });
    }
    
    /**
     * Create broker session from authentication response
     * 
     * MANDATORY: Builder pattern - Rule #4
     * MANDATORY: Immutable data - Rule #9
     */
    private BrokerSession createBrokerSession(AuthRequest request, AuthResponse authResponse) {
        return new BrokerSession(
            null,
            authResponse.sessionId(),
            Optional.ofNullable(request.userId()).orElse("anonymous"),
            request.brokerType(),
            authResponse.accessToken(),
            authResponse.refreshToken(),
            SessionStatus.ACTIVE,
            LocalDateTime.now(),
            authResponse.expiresAt(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
    
    /**
     * Create response for unsupported broker
     * 
     * MANDATORY: Functional Programming - Rule #3
     */
    private AuthResponse createUnsupportedBrokerResponse(BrokerType brokerType) {
        String message = String.format("Broker %s is not supported yet", brokerType);
        log.warn("Unsupported broker authentication attempt: {}", brokerType);
        return new AuthResponse(null, null, null, brokerType, null, false, message);
    }
}