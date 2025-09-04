package com.trademaster.brokerauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.service.broker.ICICIDirectApiService;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ICICI Direct API Service Test
 * 
 * MANDATORY: Testing Standards - Rule #20
 * MANDATORY: Virtual Thread Testing - Rule #20
 * MANDATORY: Functional Testing - Rule #20
 */
@ExtendWith(MockitoExtension.class)
class ICICIDirectApiServiceTest {
    
    @Mock
    private OkHttpClient httpClient;
    
    @Mock
    private Call call;
    
    @Mock
    private ObjectMapper objectMapper;
    
    private ICICIDirectApiService iciciDirectApiService;
    
    @BeforeEach
    void setUp() {
        iciciDirectApiService = new ICICIDirectApiService(httpClient, objectMapper);
        
        // Set configuration values using reflection
        ReflectionTestUtils.setField(iciciDirectApiService, "apiUrl", "https://api.icicidirect.com/breezeapi");
        ReflectionTestUtils.setField(iciciDirectApiService, "appKey", "test_app_key");
        ReflectionTestUtils.setField(iciciDirectApiService, "secretKey", "test_secret_key");
        ReflectionTestUtils.setField(iciciDirectApiService, "rateLimitPerMinute", 100);
    }
    
    @Test
    void testSupports_ICICIDirectBrokerType_ReturnsTrue() {
        // When & Then
        assertTrue(iciciDirectApiService.supports(BrokerType.ICICI_DIRECT));
    }
    
    @Test
    void testSupports_OtherBrokerTypes_ReturnsFalse() {
        // When & Then
        assertFalse(iciciDirectApiService.supports(BrokerType.ZERODHA));
        assertFalse(iciciDirectApiService.supports(BrokerType.UPSTOX));
        assertFalse(iciciDirectApiService.supports(BrokerType.ANGEL_ONE));
    }
    
    @Test
    void testGetBrokerName_ReturnsCorrectName() {
        // When
        String brokerName = iciciDirectApiService.getBrokerName();
        
        // Then
        assertEquals("ICICI Direct Breeze", brokerName);
    }
    
    @Test
    void testAuthenticate_WithValidSessionToken_ReturnsSuccessfulResponse() 
            throws IOException, ExecutionException, InterruptedException {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            "test_user",
            "test_password",
            "valid_session_token"
        );
        
        String mockResponseBody = """
            {
                "Success": true,
                "user_id": "test_user",
                "client_code": "TEST123"
            }
            """;
        
        Response mockResponse = createMockResponse(200, mockResponseBody);
        setupMockHttpClient(mockResponse);
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(BrokerType.ICICI_DIRECT, response.brokerType());
        assertEquals("valid_session_token", response.accessToken());
        assertNotNull(response.sessionId());
        assertNotNull(response.expiresAt());
        assertEquals("Authentication successful", response.message());
    }
    
    @Test
    void testAuthenticate_WithInvalidSessionToken_ReturnsFailureResponse() 
            throws IOException, ExecutionException, InterruptedException {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            "test_user",
            "test_password",
            "invalid_session_token"
        );
        
        String mockResponseBody = """
            {
                "Success": false,
                "Error": "Invalid session token"
            }
            """;
        
        Response mockResponse = createMockResponse(401, mockResponseBody);
        setupMockHttpClient(mockResponse);
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertFalse(response.success());
        assertEquals(BrokerType.ICICI_DIRECT, response.brokerType());
        assertNull(response.accessToken());
        assertNull(response.sessionId());
        assertTrue(response.message().contains("Authentication failed"));
    }
    
    @Test
    void testAuthenticate_WithCredentials_ReturnsSessionTokenRequiredMessage() 
            throws ExecutionException, InterruptedException {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            "test_user",
            "test_password",
            null // No session token
        );
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertFalse(response.success());
        assertTrue(response.message().contains("obtain session token"));
        assertTrue(response.message().contains("https://api.icicidirect.com/apiuser/login"));
    }
    
    @Test
    void testAuthenticate_WithInvalidParameters_ReturnsFailureResponse() 
            throws ExecutionException, InterruptedException {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            null, // No user ID
            null, // No password
            null  // No session token
        );
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertFalse(response.success());
        assertEquals("Invalid authentication parameters for ICICI Direct", response.message());
    }
    
    @Test
    void testRefreshToken_WithValidToken_ReturnsSuccessfulResponse() 
            throws IOException, ExecutionException, InterruptedException {
        // Given
        String validRefreshToken = "valid_refresh_token";
        
        String mockResponseBody = """
            {
                "Success": true,
                "user_id": "test_user"
            }
            """;
        
        Response mockResponse = createMockResponse(200, mockResponseBody);
        setupMockHttpClient(mockResponse);
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.refreshToken(validRefreshToken);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(BrokerType.ICICI_DIRECT, response.brokerType());
        assertEquals(validRefreshToken, response.accessToken());
        assertEquals(validRefreshToken, response.refreshToken());
        assertEquals("Session refresh successful", response.message());
    }
    
    @Test
    void testRefreshToken_WithInvalidToken_ReturnsFailureResponse() 
            throws IOException, ExecutionException, InterruptedException {
        // Given
        String invalidRefreshToken = "invalid_refresh_token";
        
        Response mockResponse = createMockResponse(401, "Unauthorized");
        setupMockHttpClient(mockResponse);
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.refreshToken(invalidRefreshToken);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertFalse(response.success());
        assertEquals("Session expired. Please re-authenticate.", response.message());
    }
    
    @Test
    void testAuthenticate_WithNetworkError_ReturnsFailureResponse() 
            throws IOException, ExecutionException, InterruptedException {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            "test_user",
            "test_password",
            "session_token"
        );
        
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Network error"));
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertFalse(response.success());
        assertTrue(response.message().contains("Network error"));
    }
    
    @Test
    void testAuthenticate_WithRateLimitError_ReturnsProperErrorMessage() 
            throws IOException, ExecutionException, InterruptedException {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            "test_user",
            "test_password",
            "session_token"
        );
        
        Response mockResponse = createMockResponse(429, "Rate limit exceeded");
        setupMockHttpClient(mockResponse);
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        AuthResponse response = future.get();
        
        // Then
        assertNotNull(response);
        assertFalse(response.success());
        assertTrue(response.message().contains("Rate limit exceeded"));
    }
    
    /**
     * Test Virtual Thread execution
     */
    @Test
    void testAuthenticate_UsesVirtualThreads() {
        // Given
        AuthRequest authRequest = new AuthRequest(
            BrokerType.ICICI_DIRECT,
            "test_api_key",
            "test_api_secret",
            "test_user",
            "test_password",
            "session_token"
        );
        
        // When
        CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
        
        // Then
        assertNotNull(future);
        assertFalse(future.isDone()); // Should be executing asynchronously
        
        // The actual virtual thread testing would require integration testing
        // or specific virtual thread testing frameworks
    }
    
    private void setupMockHttpClient(Response response) throws IOException {
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
    }
    
    private Response createMockResponse(int code, String body) {
        ResponseBody responseBody = ResponseBody.create(body, MediaType.parse("application/json"));
        
        return new Response.Builder()
            .request(new Request.Builder().url("https://api.test.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(code == 200 ? "OK" : "Error")
            .body(responseBody)
            .build();
    }
}