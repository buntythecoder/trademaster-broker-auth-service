package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.service.broker.BrokerApiService;
import com.trademaster.brokerauth.service.broker.BrokerServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Basic test for BrokerAuthenticationService
 * 
 * MANDATORY: Testing Standards - Rule #20
 * MANDATORY: Virtual Thread Testing - Rule #20
 */
@ExtendWith(MockitoExtension.class)
class BrokerAuthenticationServiceTest {
    
    @Mock
    private BrokerSessionService sessionService;
    
    @Mock
    private BrokerServiceFactory brokerServiceFactory;
    
    @Mock
    private BrokerApiService brokerApiService;
    
    private BrokerAuthenticationService authenticationService;
    
    @BeforeEach
    void setUp() {
        authenticationService = new BrokerAuthenticationService(sessionService, brokerServiceFactory);
    }
    
    @Test
    void authenticate_withSupportedBroker_shouldReturnSuccessfulResponse() {
        // Given
        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ZERODHA)
            .apiKey("test-key")
            .apiSecret("test-secret")
            .userId("testuser")
            .totpCode("123456")
            .build();
        AuthResponse expectedResponse = new AuthResponse(
            "session-123",
            "access-token-123",
            "refresh-token-123",
            BrokerType.ZERODHA,
            LocalDateTime.now().plusHours(24),
            true,
            "Authentication successful"
        );
        
        when(brokerServiceFactory.getBrokerService(BrokerType.ZERODHA))
            .thenReturn(Optional.of(brokerApiService));
        when(brokerApiService.authenticate(request))
            .thenReturn(CompletableFuture.completedFuture(expectedResponse));
        
        // When
        CompletableFuture<AuthResponse> result = authenticationService.authenticate(request);
        
        // Then
        AuthResponse actualResponse = result.join();
        assertTrue(actualResponse.success());
        assertEquals(expectedResponse.sessionId(), actualResponse.sessionId());
        assertEquals(expectedResponse.accessToken(), actualResponse.accessToken());
        assertEquals(expectedResponse.brokerType(), actualResponse.brokerType());
        
        // Verify session was saved
        verify(sessionService, times(1)).saveSession(any());
    }
    
    @Test
    void authenticate_withUnsupportedBroker_shouldReturnFailureResponse() {
        // Given
        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.UPSTOX)
            .apiKey("test-key")
            .apiSecret("test-secret")
            .userId("testuser")
            .build();
        
        when(brokerServiceFactory.getBrokerService(BrokerType.UPSTOX))
            .thenReturn(Optional.empty());
        
        // When
        CompletableFuture<AuthResponse> result = authenticationService.authenticate(request);
        
        // Then
        AuthResponse actualResponse = result.join();
        assertFalse(actualResponse.success());
        assertEquals(BrokerType.UPSTOX, actualResponse.brokerType());
        assertTrue(actualResponse.message().contains("not supported"));
        
        // Verify no session was saved
        verify(sessionService, never()).saveSession(any());
    }
    
    @Test
    void authenticate_withFailedBrokerAuthentication_shouldReturnFailureResponse() {
        // Given
        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ZERODHA)
            .apiKey("invalid-key")
            .apiSecret("invalid-secret")
            .userId("testuser")
            .build();
        AuthResponse failureResponse = new AuthResponse(
            null, null, null, BrokerType.ZERODHA, null, false, "Invalid credentials"
        );
        
        when(brokerServiceFactory.getBrokerService(BrokerType.ZERODHA))
            .thenReturn(Optional.of(brokerApiService));
        when(brokerApiService.authenticate(request))
            .thenReturn(CompletableFuture.completedFuture(failureResponse));
        
        // When
        CompletableFuture<AuthResponse> result = authenticationService.authenticate(request);
        
        // Then
        AuthResponse actualResponse = result.join();
        assertFalse(actualResponse.success());
        assertEquals("Invalid credentials", actualResponse.message());
        
        // Verify no session was saved
        verify(sessionService, never()).saveSession(any());
    }
}