package com.trademaster.brokerauth.service.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.enums.BrokerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Upstox API Service
 * 
 * MANDATORY: Real API Integration - Rule #7
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Functional Programming - Rule #3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxApiService implements BrokerApiService {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${broker.upstox.api-url:https://api.upstox.com/v2}")
    private String apiUrl;
    
    @Value("${broker.upstox.client-id:}")
    private String clientId;
    
    @Value("${broker.upstox.client-secret:}")
    private String clientSecret;
    
    @Value("${broker.upstox.redirect-uri:}")
    private String redirectUri;
    
    @Override
    public CompletableFuture<AuthResponse> authenticate(AuthRequest request) {
        return CompletableFuture
            .supplyAsync(() -> performAuthentication(request),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleAuthenticationResult);
    }
    
    @Override
    public CompletableFuture<AuthResponse> refreshToken(String refreshToken) {
        return CompletableFuture
            .supplyAsync(() -> performTokenRefresh(refreshToken),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleAuthenticationResult);
    }
    
    @Override
    public boolean supports(BrokerType brokerType) {
        return brokerType == BrokerType.UPSTOX;
    }
    
    @Override
    public String getBrokerName() {
        return "Upstox Pro";
    }
    
    /**
     * Perform Upstox OAuth authentication
     * 
     * MANDATORY: Pattern matching - Rule #14
     */
    private AuthResponse performAuthentication(AuthRequest request) {
        log.info("Authenticating with Upstox for user: {}", request.userId());
        
        return switch (determineAuthMethod(request)) {
            case AUTHORIZATION_CODE -> authenticateWithCode(request);
            case CLIENT_CREDENTIALS -> authenticateWithCredentials(request);
            case INVALID -> createFailureResponse("Invalid authentication parameters for Upstox");
        };
    }
    
    private AuthResponse performTokenRefresh(String refreshToken) {
        log.info("Refreshing Upstox token");
        
        try {
            RequestBody requestBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();
            
            Request request = new Request.Builder()
                .url(apiUrl + "/login/authorization/token")
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
            
            Response response = httpClient.newCall(request).execute();
            return processTokenResponse(response, "Token refresh");
            
        } catch (IOException e) {
            log.error("Failed to refresh Upstox token", e);
            return createFailureResponse("Token refresh failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithCode(AuthRequest request) {
        try {
            RequestBody requestBody = new FormBody.Builder()
                .add("code", request.totpCode()) // Using totpCode field for authorization code
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build();
            
            Request httpRequest = new Request.Builder()
                .url(apiUrl + "/login/authorization/token")
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            return processTokenResponse(response, "Authentication");
            
        } catch (IOException e) {
            log.error("Upstox authentication failed", e);
            return createFailureResponse("Authentication failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithCredentials(AuthRequest request) {
        try {
            RequestBody requestBody = new FormBody.Builder()
                .add("client_id", request.apiKey())
                .add("client_secret", request.apiSecret())
                .add("grant_type", "client_credentials")
                .build();
            
            Request httpRequest = new Request.Builder()
                .url(apiUrl + "/login/authorization/token")
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            return processTokenResponse(response, "Client credentials authentication");
            
        } catch (IOException e) {
            log.error("Upstox client credentials authentication failed", e);
            return createFailureResponse("Client credentials authentication failed: " + e.getMessage());
        }
    }
    
    private AuthResponse processTokenResponse(Response response, String operation) throws IOException {
        String responseBody = response.body().string();
        
        if (!response.isSuccessful()) {
            log.error("Upstox {} failed: {}", operation, responseBody);
            return createFailureResponse(String.format("%s failed: %s", operation, responseBody));
        }
        
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            String accessToken = jsonResponse.get("access_token").asText();
            String refreshToken = jsonResponse.has("refresh_token") 
                ? jsonResponse.get("refresh_token").asText() 
                : null;
            
            return new AuthResponse(
                java.util.UUID.randomUUID().toString(),
                accessToken,
                refreshToken,
                BrokerType.UPSTOX,
                LocalDateTime.now().plusHours(BrokerAuthConstants.UPSTOX_SESSION_EXPIRY_HOURS),
                true,
                "Authentication successful"
            );
            
        } catch (Exception e) {
            log.error("Failed to parse Upstox API response", e);
            return createFailureResponse("Failed to parse API response");
        }
    }
    
    private AuthMethod determineAuthMethod(AuthRequest request) {
        if (request.totpCode() != null) {
            return AuthMethod.AUTHORIZATION_CODE;
        }
        if (request.apiKey() != null && request.apiSecret() != null) {
            return AuthMethod.CLIENT_CREDENTIALS;
        }
        return AuthMethod.INVALID;
    }
    
    private AuthResponse createFailureResponse(String message) {
        return new AuthResponse(null, null, null, BrokerType.UPSTOX, null, false, message);
    }
    
    private AuthResponse handleAuthenticationResult(AuthResponse result, Throwable throwable) {
        if (throwable != null) {
            log.error("Upstox authentication error", throwable);
            return createFailureResponse("Authentication error: " + throwable.getMessage());
        }
        return result;
    }
    
    private enum AuthMethod {
        AUTHORIZATION_CODE,
        CLIENT_CREDENTIALS,
        INVALID
    }
}