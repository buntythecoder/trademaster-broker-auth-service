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
 * Angel One SmartAPI Service
 * 
 * MANDATORY: Real API Integration - Rule #7
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Pattern Matching - Rule #14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AngelOneApiService implements BrokerApiService {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${broker.angel-one.api-url:https://apiconnect.angelbroking.com}")
    private String apiUrl;
    
    @Value("${broker.angel-one.client-code:}")
    private String clientCode;
    
    @Value("${broker.angel-one.password:}")
    private String password;
    
    @Value("${broker.angel-one.api-key:}")
    private String apiKey;
    
    private static final String LOGIN_ENDPOINT = "/rest/auth/angelbroking/user/v1/loginByPassword";
    private static final String TOTP_ENDPOINT = "/rest/auth/angelbroking/jwt/v1/generateTokens";
    private static final String REFRESH_ENDPOINT = "/rest/auth/angelbroking/jwt/v1/generateTokens";
    private static final String LOGOUT_ENDPOINT = "/rest/secure/angelbroking/user/v1/logout";
    
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
        return brokerType == BrokerType.ANGEL_ONE;
    }
    
    @Override
    public String getBrokerName() {
        return "Angel One SmartAPI";
    }
    
    /**
     * Perform Angel One authentication using real SmartAPI
     * 
     * MANDATORY: Pattern matching - Rule #14
     * MANDATORY: No if-else - Rule #3
     */
    private AuthResponse performAuthentication(AuthRequest request) {
        log.info("Authenticating with Angel One SmartAPI for user: {}", request.userId());
        
        return switch (determineAuthMethod(request)) {
            case PASSWORD_LOGIN -> authenticateWithPassword(request);
            case TOTP_VERIFICATION -> authenticateWithTotp(request);
            case INVALID -> createFailureResponse("Invalid authentication parameters for Angel One");
        };
    }
    
    private AuthResponse performTokenRefresh(String refreshToken) {
        log.info("Refreshing Angel One token");
        
        try {
            String requestBody = objectMapper.writeValueAsString(
                new RefreshTokenRequest(refreshToken)
            );
            
            Request request = new Request.Builder()
                .url(apiUrl + REFRESH_ENDPOINT)
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-UserType", "USER")
                .addHeader("X-SourceID", "WEB")
                .addHeader("X-ClientLocalIP", "192.168.1.1")
                .addHeader("X-ClientPublicIP", "106.51.74.76")
                .addHeader("X-MACAddress", "00:00:00:00:00:00")
                .build();
            
            Response response = httpClient.newCall(request).execute();
            return processApiResponse(response, "Token refresh");
            
        } catch (Exception e) {
            log.error("Failed to refresh Angel One token", e);
            return createFailureResponse("Token refresh failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithPassword(AuthRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                new LoginRequest(request.userId(), request.apiSecret())
            );
            
            Request httpRequest = new Request.Builder()
                .url(apiUrl + LOGIN_ENDPOINT)
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-UserType", "USER")
                .addHeader("X-SourceID", "WEB")
                .addHeader("X-ClientLocalIP", "192.168.1.1")
                .addHeader("X-ClientPublicIP", "106.51.74.76")
                .addHeader("X-MACAddress", "00:00:00:00:00:00")
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            return processPasswordResponse(response);
            
        } catch (Exception e) {
            log.error("Angel One password authentication failed", e);
            return createFailureResponse("Password authentication failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithTotp(AuthRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                new TotpRequest(request.userId(), request.totpCode())
            );
            
            Request httpRequest = new Request.Builder()
                .url(apiUrl + TOTP_ENDPOINT)
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-UserType", "USER")
                .addHeader("X-SourceID", "WEB")
                .addHeader("X-ClientLocalIP", "192.168.1.1")
                .addHeader("X-ClientPublicIP", "106.51.74.76")
                .addHeader("X-MACAddress", "00:00:00:00:00:00")
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            return processApiResponse(response, "TOTP Authentication");
            
        } catch (Exception e) {
            log.error("Angel One TOTP authentication failed", e);
            return createFailureResponse("TOTP authentication failed: " + e.getMessage());
        }
    }
    
    private AuthResponse processPasswordResponse(Response response) throws IOException {
        String responseBody = response.body().string();
        
        if (!response.isSuccessful()) {
            log.error("Angel One password authentication failed: {}", responseBody);
            return createFailureResponse("Password authentication failed: " + responseBody);
        }
        
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            if (!jsonResponse.get("status").asBoolean()) {
                String message = jsonResponse.get("message").asText();
                return createFailureResponse("Authentication failed: " + message);
            }
            
            // Password authentication successful, need TOTP next
            return new AuthResponse(
                null, // No session ID yet
                null, // No access token yet
                null, // No refresh token yet
                BrokerType.ANGEL_ONE,
                null, // No expiry yet
                false, // Not complete yet
                "Password authentication successful. Please provide TOTP."
            );
            
        } catch (Exception e) {
            log.error("Failed to parse Angel One password response", e);
            return createFailureResponse("Failed to parse authentication response");
        }
    }
    
    private AuthResponse processApiResponse(Response response, String operation) throws IOException {
        String responseBody = response.body().string();
        
        if (!response.isSuccessful()) {
            log.error("Angel One {} failed: {}", operation, responseBody);
            return createFailureResponse(String.format("%s failed: %s", operation, responseBody));
        }
        
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            if (!jsonResponse.get("status").asBoolean()) {
                String message = jsonResponse.get("message").asText();
                return createFailureResponse(operation + " failed: " + message);
            }
            
            JsonNode dataNode = jsonResponse.get("data");
            if (dataNode == null) {
                return createFailureResponse("Invalid response format from Angel One API");
            }
            
            String jwtToken = dataNode.get("jwtToken").asText();
            String refreshToken = dataNode.get("refreshToken").asText();
            String feedToken = dataNode.has("feedToken") ? dataNode.get("feedToken").asText() : null;
            
            return new AuthResponse(
                java.util.UUID.randomUUID().toString(),
                jwtToken,
                refreshToken,
                BrokerType.ANGEL_ONE,
                LocalDateTime.now().plusHours(BrokerAuthConstants.ANGEL_ONE_SESSION_EXPIRY_HOURS),
                true,
                "Authentication successful"
            );
            
        } catch (Exception e) {
            log.error("Failed to parse Angel One API response", e);
            return createFailureResponse("Failed to parse API response");
        }
    }
    
    private AuthMethod determineAuthMethod(AuthRequest request) {
        if (request.userId() != null && request.apiSecret() != null && request.totpCode() == null) {
            return AuthMethod.PASSWORD_LOGIN;
        }
        if (request.userId() != null && request.totpCode() != null) {
            return AuthMethod.TOTP_VERIFICATION;
        }
        return AuthMethod.INVALID;
    }
    
    private AuthResponse createFailureResponse(String message) {
        return new AuthResponse(null, null, null, BrokerType.ANGEL_ONE, null, false, message);
    }
    
    private AuthResponse handleAuthenticationResult(AuthResponse result, Throwable throwable) {
        if (throwable != null) {
            log.error("Angel One authentication error", throwable);
            return createFailureResponse("Authentication error: " + throwable.getMessage());
        }
        return result;
    }
    
    private enum AuthMethod {
        PASSWORD_LOGIN,
        TOTP_VERIFICATION,
        INVALID
    }
    
    // Request DTOs as Records (Rule #9)
    private record LoginRequest(
        String clientcode,
        String password
    ) {}
    
    private record TotpRequest(
        String clientcode,
        String totp
    ) {}
    
    private record RefreshTokenRequest(
        String refreshToken
    ) {}
}