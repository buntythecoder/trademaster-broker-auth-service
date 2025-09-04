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
 * Zerodha Kite API Service
 * 
 * MANDATORY: Real API Integration - Rule #7
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Error Handling - Rule #11
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZerodhaApiService implements BrokerApiService {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${broker.zerodha.api-url:https://api.kite.trade}")
    private String apiUrl;
    
    @Value("${broker.zerodha.app-id:}")
    private String appId;
    
    @Value("${broker.zerodha.api-secret:}")
    private String apiSecret;
    
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
        return brokerType == BrokerType.ZERODHA;
    }
    
    @Override
    public String getBrokerName() {
        return "Zerodha Kite";
    }
    
    /**
     * Perform actual Zerodha authentication
     * 
     * MANDATORY: Pattern matching - Rule #14
     * MANDATORY: No if-else - Rule #3
     */
    private AuthResponse performAuthentication(AuthRequest request) {
        log.info("Authenticating with Zerodha for user: {}", request.userId());
        
        return switch (determineAuthMethod(request)) {
            case API_KEY_SECRET -> authenticateWithApiKey(request);
            case REQUEST_TOKEN -> authenticateWithRequestToken(request);
            case INVALID -> createFailureResponse("Invalid authentication parameters for Zerodha");
        };
    }
    
    private AuthResponse performTokenRefresh(String refreshToken) {
        log.info("Refreshing Zerodha token");
        
        try {
            RequestBody requestBody = new FormBody.Builder()
                .add("api_key", appId)
                .add("refresh_token", refreshToken)
                .build();
            
            Request request = new Request.Builder()
                .url(apiUrl + "/session/refresh_token")
                .post(requestBody)
                .addHeader("X-Kite-Version", "3")
                .build();
            
            Response response = httpClient.newCall(request).execute();
            return processApiResponse(response, "Token refresh");
            
        } catch (IOException e) {
            log.error("Failed to refresh Zerodha token", e);
            return createFailureResponse("Token refresh failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithApiKey(AuthRequest request) {
        // For Zerodha, API key authentication requires a request token from the login flow
        log.warn("API key authentication requires request token from Zerodha login flow");
        return createFailureResponse("Zerodha authentication requires request token from login flow");
    }
    
    private AuthResponse authenticateWithRequestToken(AuthRequest request) {
        try {
            // Generate session using request token
            String checksum = generateChecksum(request.apiKey(), request.totpCode(), apiSecret);
            
            RequestBody requestBody = new FormBody.Builder()
                .add("api_key", request.apiKey())
                .add("request_token", request.totpCode()) // Using totpCode field for request token
                .add("checksum", checksum)
                .build();
            
            Request httpRequest = new Request.Builder()
                .url(apiUrl + "/session/token")
                .post(requestBody)
                .addHeader("X-Kite-Version", "3")
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            return processApiResponse(response, "Authentication");
            
        } catch (IOException e) {
            log.error("Zerodha authentication failed", e);
            return createFailureResponse("Authentication failed: " + e.getMessage());
        }
    }
    
    private AuthResponse processApiResponse(Response response, String operation) throws IOException {
        String responseBody = response.body().string();
        
        if (!response.isSuccessful()) {
            log.error("Zerodha {} failed: {}", operation, responseBody);
            return createFailureResponse(String.format("%s failed: %s", operation, responseBody));
        }
        
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            JsonNode dataNode = jsonResponse.get("data");
            
            if (dataNode == null) {
                return createFailureResponse("Invalid response format from Zerodha API");
            }
            
            String accessToken = dataNode.get("access_token").asText();
            String refreshToken = dataNode.get("refresh_token").asText();
            
            return new AuthResponse(
                java.util.UUID.randomUUID().toString(), // Generate session ID
                accessToken,
                refreshToken,
                BrokerType.ZERODHA,
                LocalDateTime.now().plusHours(BrokerAuthConstants.ZERODHA_SESSION_EXPIRY_HOURS),
                true,
                "Authentication successful"
            );
            
        } catch (Exception e) {
            log.error("Failed to parse Zerodha API response", e);
            return createFailureResponse("Failed to parse API response");
        }
    }
    
    private AuthMethod determineAuthMethod(AuthRequest request) {
        if (request.apiKey() != null && request.totpCode() != null) {
            return AuthMethod.REQUEST_TOKEN;
        }
        if (request.apiKey() != null && request.apiSecret() != null) {
            return AuthMethod.API_KEY_SECRET;
        }
        return AuthMethod.INVALID;
    }
    
    private String generateChecksum(String apiKey, String requestToken, String apiSecret) {
        String data = apiKey + requestToken + apiSecret;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate checksum", e);
        }
    }
    
    private AuthResponse createFailureResponse(String message) {
        return new AuthResponse(null, null, null, BrokerType.ZERODHA, null, false, message);
    }
    
    private AuthResponse handleAuthenticationResult(AuthResponse result, Throwable throwable) {
        if (throwable != null) {
            log.error("Zerodha authentication error", throwable);
            return createFailureResponse("Authentication error: " + throwable.getMessage());
        }
        return result;
    }
    
    private enum AuthMethod {
        API_KEY_SECRET,
        REQUEST_TOKEN,
        INVALID
    }
}