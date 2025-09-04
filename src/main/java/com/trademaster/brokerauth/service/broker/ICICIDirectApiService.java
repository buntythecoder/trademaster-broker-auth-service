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
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * ICICI Direct Breeze API Service
 * 
 * MANDATORY: Real API Integration - Rule #7
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Pattern Matching - Rule #14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ICICIDirectApiService implements BrokerApiService {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Value("${broker.icici.api-url:https://api.icicidirect.com/breezeapi}")
    private String apiUrl;
    
    @Value("${broker.icici.login-url:https://api.icicidirect.com/apiuser/login}")
    private String loginUrl;
    
    @Value("${broker.icici.app-key:}")
    private String appKey;
    
    @Value("${broker.icici.secret-key:}")
    private String secretKey;
    
    private static final String API_VERSION = "v1";
    private static final String LOGIN_ENDPOINT = "/customer/authentication/login";
    private static final String PORTFOLIO_ENDPOINT = "/api/v1/portfolioholdings";
    private static final String FUNDS_ENDPOINT = "/api/v1/funds";
    
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
            .supplyAsync(() -> performSessionValidation(refreshToken),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleAuthenticationResult);
    }
    
    @Override
    public boolean supports(BrokerType brokerType) {
        return brokerType == BrokerType.ICICI_DIRECT;
    }
    
    @Override
    public String getBrokerName() {
        return "ICICI Direct Breeze";
    }
    
    /**
     * Perform ICICI Direct authentication using real Breeze API
     * 
     * MANDATORY: Pattern matching - Rule #14
     * MANDATORY: No if-else - Rule #3
     */
    private AuthResponse performAuthentication(AuthRequest request) {
        log.info("Authenticating with ICICI Direct Breeze API for user: {}", request.userId());
        
        return switch (determineAuthMethod(request)) {
            case SESSION_TOKEN -> authenticateWithSessionToken(request);
            case CREDENTIALS -> authenticateWithCredentials(request);
            case INVALID -> createFailureResponse("Invalid authentication parameters for ICICI Direct");
        };
    }
    
    private AuthResponse performSessionValidation(String sessionToken) {
        log.info("Validating ICICI Direct session token");
        
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String requestBody = "{}";
            String checksum = generateChecksum(timestamp, requestBody, secretKey);
            
            Request request = new Request.Builder()
                .url(apiUrl + FUNDS_ENDPOINT)
                .get()
                .addHeader("X-Checksum", checksum)
                .addHeader("X-Timestamp", timestamp)
                .addHeader("X-AppKey", appKey)
                .addHeader("X-SessionToken", sessionToken)
                .addHeader("Content-Type", "application/json")
                .build();
            
            Response response = httpClient.newCall(request).execute();
            return processSessionValidationResponse(response, sessionToken);
            
        } catch (Exception e) {
            log.error("Failed to validate ICICI Direct session", e);
            return createFailureResponse("Session validation failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithSessionToken(AuthRequest request) {
        try {
            String sessionToken = request.apiKey(); // Session token passed as API key
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String requestBody = "{}";
            String checksum = generateChecksum(timestamp, requestBody, secretKey);
            
            Request httpRequest = new Request.Builder()
                .url(apiUrl + PORTFOLIO_ENDPOINT)
                .get()
                .addHeader("X-Checksum", checksum)
                .addHeader("X-Timestamp", timestamp)
                .addHeader("X-AppKey", appKey)
                .addHeader("X-SessionToken", sessionToken)
                .addHeader("Content-Type", "application/json")
                .build();
            
            Response response = httpClient.newCall(httpRequest).execute();
            return processApiResponse(response, "Session Authentication", sessionToken);
            
        } catch (Exception e) {
            log.error("ICICI Direct session authentication failed", e);
            return createFailureResponse("Session authentication failed: " + e.getMessage());
        }
    }
    
    private AuthResponse authenticateWithCredentials(AuthRequest request) {
        // ICICI Direct requires manual session token generation via web portal
        // This method provides instructions for obtaining session token
        
        String instructions = String.format(
            "ICICI Direct requires manual session token generation. Please follow these steps:\n" +
            "1. Visit: %s?api_key=%s\n" +
            "2. Login with your ICICI Direct credentials\n" +
            "3. Copy the generated session token\n" +
            "4. Use the session token as the API key in your authentication request",
            loginUrl, appKey
        );
        
        log.info("ICICI Direct credentials authentication - providing session token instructions");
        
        return new AuthResponse(
            null,
            null,
            null,
            BrokerType.ICICI_DIRECT,
            null,
            false,
            instructions
        );
    }
    
    private AuthResponse processApiResponse(Response response, String operation, String sessionToken) throws IOException {
        String responseBody = response.body().string();
        
        if (!response.isSuccessful()) {
            log.error("ICICI Direct {} failed: HTTP {}: {}", operation, response.code(), responseBody);
            return createFailureResponse(String.format("%s failed: HTTP %d", operation, response.code()));
        }
        
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            // ICICI Direct API returns different structures based on endpoint
            // Success is indicated by HTTP 200 and valid JSON response
            if (jsonResponse.has("Error")) {
                JsonNode errorNode = jsonResponse.get("Error");
                String errorMessage = errorNode.asText();
                return createFailureResponse(operation + " failed: " + errorMessage);
            }
            
            return new AuthResponse(
                java.util.UUID.randomUUID().toString(),
                sessionToken, // Use session token as access token
                sessionToken, // Use session token as refresh token
                BrokerType.ICICI_DIRECT,
                LocalDateTime.now().plusHours(BrokerAuthConstants.ICICI_SESSION_EXPIRY_HOURS),
                true,
                "Authentication successful"
            );
            
        } catch (Exception e) {
            log.error("Failed to parse ICICI Direct API response", e);
            return createFailureResponse("Failed to parse API response");
        }
    }
    
    private AuthResponse processSessionValidationResponse(Response response, String sessionToken) throws IOException {
        String responseBody = response.body().string();
        
        return switch (response.code()) {
            case 200 -> {
                log.info("ICICI Direct session validation successful");
                yield new AuthResponse(
                    java.util.UUID.randomUUID().toString(),
                    sessionToken,
                    sessionToken,
                    BrokerType.ICICI_DIRECT,
                    LocalDateTime.now().plusHours(BrokerAuthConstants.ICICI_SESSION_EXPIRY_HOURS),
                    true,
                    "Session validation successful"
                );
            }
            case 401 -> {
                log.warn("ICICI Direct session expired or invalid");
                yield createFailureResponse("Session expired or invalid. Please generate new session token.");
            }
            case 403 -> {
                log.error("ICICI Direct API access forbidden");
                yield createFailureResponse("API access forbidden. Check app key and permissions.");
            }
            case 429 -> {
                log.warn("ICICI Direct rate limit exceeded");
                yield createFailureResponse("Rate limit exceeded. Please retry after some time.");
            }
            default -> {
                log.error("ICICI Direct session validation failed: HTTP {}: {}", response.code(), responseBody);
                yield createFailureResponse(String.format("Session validation failed: HTTP %d", response.code()));
            }
        };
    }
    
    private String generateChecksum(String timestamp, String requestBody, String secretKey) {
        try {
            String data = timestamp + requestBody + secretKey;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            
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
    
    private AuthMethod determineAuthMethod(AuthRequest request) {
        // Session token is passed as apiKey, credentials require userId and password
        if (request.apiKey() != null && request.apiKey().length() > 10) {
            return AuthMethod.SESSION_TOKEN;
        }
        if (request.userId() != null && request.apiSecret() != null) {
            return AuthMethod.CREDENTIALS;
        }
        return AuthMethod.INVALID;
    }
    
    private AuthResponse createFailureResponse(String message) {
        return new AuthResponse(null, null, null, BrokerType.ICICI_DIRECT, null, false, message);
    }
    
    private AuthResponse handleAuthenticationResult(AuthResponse result, Throwable throwable) {
        if (throwable != null) {
            log.error("ICICI Direct authentication error", throwable);
            return createFailureResponse("Authentication error: " + throwable.getMessage());
        }
        return result;
    }
    
    private enum AuthMethod {
        SESSION_TOKEN,
        CREDENTIALS,
        INVALID
    }
}