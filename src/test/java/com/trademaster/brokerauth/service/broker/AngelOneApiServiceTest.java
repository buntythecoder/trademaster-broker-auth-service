package com.trademaster.brokerauth.service.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.enums.BrokerType;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Angel One API Service Tests
 * 
 * MANDATORY: Testing Standards - Rule #20
 * MANDATORY: Virtual Thread Testing - Rule #12
 * MANDATORY: Functional Testing - Rule #3
 */
class AngelOneApiServiceTest {
    
    private MockWebServer mockWebServer;
    private AngelOneApiService angelOneApiService;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        objectMapper = new ObjectMapper();
        
        angelOneApiService = new AngelOneApiService(
            new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
            objectMapper
        );
        
        // Set test configuration using reflection
        String baseUrl = mockWebServer.url("").toString().replaceAll("/$", "");
        ReflectionTestUtils.setField(angelOneApiService, "apiUrl", baseUrl);
        ReflectionTestUtils.setField(angelOneApiService, "clientCode", "TEST123");
        ReflectionTestUtils.setField(angelOneApiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(angelOneApiService, "password", "test-password");
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void shouldSupportAngelOneBrokerType() {
        // When & Then
        assertThat(angelOneApiService.supports(BrokerType.ANGEL_ONE)).isTrue();
        assertThat(angelOneApiService.supports(BrokerType.ZERODHA)).isFalse();
        assertThat(angelOneApiService.supports(BrokerType.UPSTOX)).isFalse();
    }
    
    @Test
    void shouldReturnCorrectBrokerName() {
        // When & Then
        assertThat(angelOneApiService.getBrokerName()).isEqualTo("Angel One SmartAPI");
    }
    
    @Test
    void shouldAuthenticateWithPasswordSuccessfully() throws Exception {
        // Given
        String mockLoginResponse = """
            {
                "status": true,
                "message": "Login successful",
                "data": {
                    "jwtToken": "test-jwt-token",
                    "refreshToken": "test-refresh-token",
                    "feedToken": "test-feed-token"
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockLoginResponse)
            .addHeader("Content-Type", "application/json"));

        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ANGEL_ONE)
            .apiKey("TEST_API_KEY")
            .apiSecret("TEST_API_SECRET")
            .userId("TEST123")
            .build();

        // When
        CompletableFuture<AuthResponse> future = angelOneApiService.authenticate(request);
        AuthResponse response = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.brokerType()).isEqualTo(BrokerType.ANGEL_ONE);
        assertThat(response.message()).contains("successful");
        
        // Verify request was made correctly
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).contains("/rest/auth/angelbroking/user/v1/loginByPassword");
        assertThat(recordedRequest.getHeader("X-ApiKey")).isEqualTo("test-api-key");
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");
    }
    
    @Test
    void shouldAuthenticateWithTotpSuccessfully() throws Exception {
        // Given
        String mockTotpResponse = """
            {
                "status": true,
                "message": "TOTP verification successful",
                "data": {
                    "jwtToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
                    "refreshToken": "refresh-token-12345",
                    "feedToken": "feed-token-67890"
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockTotpResponse)
            .addHeader("Content-Type", "application/json"));

        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ANGEL_ONE)
            .apiKey("TEST_API_KEY")
            .apiSecret("TEST_API_SECRET")
            .userId("TEST123")
            .totpCode("123456")
            .build();

        // When
        CompletableFuture<AuthResponse> future = angelOneApiService.authenticate(request);
        AuthResponse response = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.accessToken()).startsWith("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-12345");
        assertThat(response.brokerType()).isEqualTo(BrokerType.ANGEL_ONE);
        assertThat(response.expiresAt()).isNotNull();
        
        // Verify request headers
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).contains("/rest/auth/angelbroking/jwt/v1/generateTokens");
        assertThat(recordedRequest.getHeader("X-ApiKey")).isEqualTo("test-api-key");
    }
    
    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // Given
        String mockRefreshResponse = """
            {
                "status": true,
                "message": "Token refresh successful",
                "data": {
                    "jwtToken": "new-jwt-token-12345",
                    "refreshToken": "new-refresh-token-12345",
                    "feedToken": "new-feed-token-12345"
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockRefreshResponse)
            .addHeader("Content-Type", "application/json"));
        
        // When
        CompletableFuture<AuthResponse> future = angelOneApiService.refreshToken("old-refresh-token");
        AuthResponse response = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-jwt-token-12345");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token-12345");
        
        // Verify authorization header
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer old-refresh-token");
    }
    
    @Test
    void shouldHandleAuthenticationFailure() throws Exception {
        // Given
        String mockErrorResponse = """
            {
                "status": false,
                "message": "Invalid credentials",
                "errorcode": "AG8001"
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody(mockErrorResponse)
            .addHeader("Content-Type", "application/json"));
        
        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ANGEL_ONE)
            .apiKey("TEST_API_KEY")
            .apiSecret("TEST_API_SECRET")
            .userId("INVALID")
            .build();
        
        // When
        CompletableFuture<AuthResponse> future = angelOneApiService.authenticate(request);
        AuthResponse response = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("failed");
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
    }
    
    @Test
    void shouldHandleNetworkErrorGracefully() throws Exception {
        // Given - Server returns connection error
        mockWebServer.enqueue(new MockResponse().setSocketPolicy(
            okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));

        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ANGEL_ONE)
            .apiKey("TEST_API_KEY")
            .apiSecret("TEST_API_SECRET")
            .userId("TEST123")
            .build();
        
        // When
        CompletableFuture<AuthResponse> future = angelOneApiService.authenticate(request);
        AuthResponse response = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("error");
    }
    
    @Test
    void shouldHandleInvalidResponseFormat() throws Exception {
        // Given
        String invalidResponse = "{ invalid json }";

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(invalidResponse)
            .addHeader("Content-Type", "application/json"));

        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ANGEL_ONE)
            .apiKey("TEST_API_KEY")
            .apiSecret("TEST_API_SECRET")
            .userId("TEST123")
            .totpCode("123456")
            .build();
        
        // When
        CompletableFuture<AuthResponse> future = angelOneApiService.authenticate(request);
        AuthResponse response = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("parse");
    }
    
    @Test
    void shouldSetCorrectRequestHeaders() throws Exception {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("""
                {
                    "status": true,
                    "message": "Success",
                    "data": {
                        "jwtToken": "test-token",
                        "refreshToken": "test-refresh",
                        "feedToken": "test-feed"
                    }
                }
                """)
            .addHeader("Content-Type", "application/json"));

        AuthRequest request = AuthRequest.builder()
            .brokerType(BrokerType.ANGEL_ONE)
            .apiKey("TEST_API_KEY")
            .apiSecret("TEST_API_SECRET")
            .userId("TEST123")
            .totpCode("123456")
            .build();
        
        // When
        angelOneApiService.authenticate(request).get(10, TimeUnit.SECONDS);
        
        // Then
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(recordedRequest.getHeader("X-ApiKey")).isEqualTo("test-api-key");
        assertThat(recordedRequest.getHeader("X-ClientLocalIP")).isEqualTo("192.168.1.1");
        assertThat(recordedRequest.getHeader("X-ClientLocalMACAddress")).isEqualTo("00:00:00:00:00:00");
        assertThat(recordedRequest.getHeader("X-ClientPublicIP")).isEqualTo("106.51.74.76");
        assertThat(recordedRequest.getHeader("X-SourceID")).isEqualTo("WEB");
        assertThat(recordedRequest.getHeader("X-UserType")).isEqualTo("USER");
    }
}