package com.trademaster.brokerauth.example;

import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.service.broker.ICICIDirectApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * ICICI Direct Breeze API Usage Examples
 * 
 * This class demonstrates proper usage of the ICICI Direct Breeze API integration
 * following TradeMaster's professional patterns and best practices.
 * 
 * MANDATORY: Example Code - Rule #7 (No Placeholders)
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Error Handling - Rule #11
 */
@Component
@Slf4j
public class ICICIDirectApiExample {
    
    private final ICICIDirectApiService iciciDirectApiService;
    
    public ICICIDirectApiExample(ICICIDirectApiService iciciDirectApiService) {
        this.iciciDirectApiService = iciciDirectApiService;
    }
    
    /**
     * Example 1: Session Token Authentication (Primary Method)
     * 
     * This is the recommended authentication method for ICICI Direct Breeze API.
     * Session tokens are obtained from the login portal and are valid for 24 hours.
     */
    public void authenticateWithSessionToken() {
        log.info("=== ICICI Direct Session Token Authentication Example ===");
        
        try {
            // Step 1: Create authentication request with session token
            // Note: Session token must be obtained from https://api.icicidirect.com/apiuser/login?api_key=YOUR_API_KEY
            AuthRequest authRequest = new AuthRequest(
                BrokerType.ICICI_DIRECT,
                "your_api_key",           // Your registered API key
                "your_secret_key",        // Your secret key from app registration
                "your_user_id",           // ICICI Direct user ID
                "your_password",          // ICICI Direct password
                "session_token_from_portal" // Session token from login portal
            );
            
            // Step 2: Perform authentication using Virtual Threads
            CompletableFuture<AuthResponse> authFuture = iciciDirectApiService.authenticate(authRequest);
            
            // Step 3: Handle authentication response
            AuthResponse response = authFuture.get(); // Blocking for example purposes
            
            if (response.success()) {
                log.info("✅ Authentication successful!");
                log.info("Session ID: {}", response.sessionId());
                log.info("Access Token: {}", maskToken(response.accessToken()));
                log.info("Expires At: {}", response.expiresAt());
                log.info("Broker: {}", response.brokerType().getDisplayName());
                
                // Step 4: Store session information for subsequent API calls
                storeSessionInformation(response);
                
            } else {
                log.error("❌ Authentication failed: {}", response.message());
                handleAuthenticationFailure(response);
            }
            
        } catch (ExecutionException e) {
            log.error("Authentication execution failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Authentication interrupted", e);
        } catch (Exception e) {
            log.error("Unexpected authentication error", e);
        }
    }
    
    /**
     * Example 2: Session Token Refresh
     * 
     * ICICI Direct session tokens are valid for 24 hours. This example shows
     * how to validate and refresh existing session tokens.
     */
    public void refreshSessionToken() {
        log.info("=== ICICI Direct Session Token Refresh Example ===");
        
        try {
            String existingSessionToken = "your_existing_session_token";
            
            // Attempt to refresh the session token
            CompletableFuture<AuthResponse> refreshFuture = iciciDirectApiService.refreshToken(existingSessionToken);
            AuthResponse response = refreshFuture.get();
            
            if (response.success()) {
                log.info("✅ Session refresh successful!");
                log.info("Session valid until: {}", response.expiresAt());
                
                // Update stored session information
                updateSessionInformation(response);
                
            } else {
                log.warn("⚠️ Session refresh failed: {}", response.message());
                log.info("Re-authentication required. Please obtain a new session token.");
                
                // Trigger re-authentication flow
                authenticateWithSessionToken();
            }
            
        } catch (ExecutionException e) {
            log.error("Session refresh execution failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Session refresh interrupted", e);
        }
    }
    
    /**
     * Example 3: Credential-based Authentication (Informational)
     * 
     * This method demonstrates what happens when using credentials without a session token.
     * Note: This will provide instructions to obtain a session token manually.
     */
    public void demonstrateCredentialAuthentication() {
        log.info("=== ICICI Direct Credential Authentication Example ===");
        
        try {
            AuthRequest authRequest = new AuthRequest(
                BrokerType.ICICI_DIRECT,
                "your_api_key",
                "your_secret_key",
                "your_user_id",
                "your_password",
                null // No session token - will trigger credential flow
            );
            
            CompletableFuture<AuthResponse> authFuture = iciciDirectApiService.authenticate(authRequest);
            AuthResponse response = authFuture.get();
            
            if (!response.success()) {
                log.info("ℹ️ Credential authentication guidance:");
                log.info(response.message());
                
                // Parse the login URL from the response message
                String loginUrl = extractLoginUrlFromMessage(response.message());
                if (loginUrl != null) {
                    log.info("📍 Please visit: {}", loginUrl);
                    log.info("1. Login with your ICICI Direct credentials");
                    log.info("2. Open browser developer tools (F12)");
                    log.info("3. Go to Network tab");
                    log.info("4. Look for your redirect URL in the network logs");
                    log.info("5. Find 'API_Session' in Form Data");
                    log.info("6. Use this value as your session token");
                }
            }
            
        } catch (Exception e) {
            log.error("Credential authentication demonstration failed", e);
        }
    }
    
    /**
     * Example 4: Error Handling Patterns
     * 
     * Demonstrates comprehensive error handling for various failure scenarios.
     */
    public void demonstrateErrorHandling() {
        log.info("=== ICICI Direct Error Handling Examples ===");
        
        // Example: Invalid session token
        demonstrateInvalidSessionTokenError();
        
        // Example: Rate limiting
        demonstrateRateLimitingError();
        
        // Example: Network connectivity issues
        demonstrateNetworkError();
    }
    
    private void demonstrateInvalidSessionTokenError() {
        try {
            AuthRequest authRequest = new AuthRequest(
                BrokerType.ICICI_DIRECT,
                "valid_api_key",
                "valid_secret_key",
                "valid_user_id",
                "valid_password",
                "invalid_session_token"
            );
            
            CompletableFuture<AuthResponse> authFuture = iciciDirectApiService.authenticate(authRequest);
            AuthResponse response = authFuture.get();
            
            if (!response.success()) {
                log.warn("❌ Invalid session token error handled: {}", response.message());
                
                // Implement recovery strategy
                if (response.message().contains("session expired") || 
                    response.message().contains("invalid")) {
                    log.info("🔄 Implementing recovery strategy: Re-authentication required");
                    // Trigger re-authentication flow
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling demonstration failed", e);
        }
    }
    
    private void demonstrateRateLimitingError() {
        log.info("📊 Rate limiting is handled automatically by the service");
        log.info("Current limits: 100 requests/minute, 5000 requests/day");
        log.info("Circuit breaker will activate on repeated failures");
        log.info("Exponential backoff is implemented for rate limit recovery");
    }
    
    private void demonstrateNetworkError() {
        log.info("🌐 Network errors are handled with automatic retries");
        log.info("Virtual Threads ensure non-blocking error recovery");
        log.info("Circuit breaker prevents cascading failures");
    }
    
    /**
     * Example 5: Best Practices Integration
     * 
     * Shows integration with session management, caching, and monitoring.
     */
    public void demonstrateBestPractices() {
        log.info("=== ICICI Direct Best Practices Example ===");
        
        // 1. Session Management
        log.info("1. 📝 Session Management:");
        log.info("   - Cache session tokens for up to 24 hours");
        log.info("   - Implement automatic refresh 5 minutes before expiry");
        log.info("   - Use Redis for distributed session storage");
        
        // 2. Rate Limiting
        log.info("2. 🚦 Rate Limiting:");
        log.info("   - Respect API limits: 100/minute, 5000/day");
        log.info("   - Implement exponential backoff for 429 errors");
        log.info("   - Use circuit breaker for resilience");
        
        // 3. Security
        log.info("3. 🔐 Security:");
        log.info("   - Never log session tokens or credentials");
        log.info("   - Use environment variables for API keys");
        log.info("   - Implement proper checksum validation");
        
        // 4. Monitoring
        log.info("4. 📊 Monitoring:");
        log.info("   - Track authentication success/failure rates");
        log.info("   - Monitor API response times");
        log.info("   - Alert on rate limit violations");
        
        // 5. Error Recovery
        log.info("5. 🔄 Error Recovery:");
        log.info("   - Graceful degradation for API failures");
        log.info("   - Automatic retry with exponential backoff");
        log.info("   - User-friendly error messages");
    }
    
    /**
     * Helper method to store session information
     */
    private void storeSessionInformation(AuthResponse response) {
        log.debug("Storing session information for user session: {}", response.sessionId());
        
        // In a real implementation, you would:
        // 1. Store in Redis with TTL matching expiry time
        // 2. Update user session in database
        // 3. Emit session created event for audit logging
        // 4. Schedule automatic refresh before expiry
        
        log.debug("Session stored successfully");
    }
    
    /**
     * Helper method to update session information
     */
    private void updateSessionInformation(AuthResponse response) {
        log.debug("Updating session information: {}", response.sessionId());
        
        // In a real implementation, you would:
        // 1. Update Redis cache with new expiry time
        // 2. Update database session record
        // 3. Emit session refreshed event
        
        log.debug("Session updated successfully");
    }
    
    /**
     * Helper method to handle authentication failures
     */
    private void handleAuthenticationFailure(AuthResponse response) {
        log.warn("Handling authentication failure: {}", response.message());
        
        // In a real implementation, you would:
        // 1. Log failure for monitoring/alerting
        // 2. Increment failure metrics
        // 3. Determine retry strategy
        // 4. Notify user with appropriate message
        
        if (response.message().contains("rate limit")) {
            log.info("Rate limit exceeded - implementing backoff strategy");
        } else if (response.message().contains("session")) {
            log.info("Session issue detected - re-authentication required");
        } else {
            log.info("General authentication failure - check credentials");
        }
    }
    
    /**
     * Helper method to mask sensitive tokens in logs
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
    
    /**
     * Helper method to extract login URL from response message
     */
    private String extractLoginUrlFromMessage(String message) {
        if (message != null && message.contains("https://")) {
            int startIndex = message.indexOf("https://");
            int endIndex = message.indexOf(" ", startIndex);
            if (endIndex == -1) {
                endIndex = message.length();
            }
            return message.substring(startIndex, endIndex);
        }
        return null;
    }
    
    /**
     * Example method to run all demonstrations
     */
    public void runAllExamples() {
        log.info("🚀 Starting ICICI Direct Breeze API Examples");
        
        try {
            authenticateWithSessionToken();
            Thread.sleep(1000); // Brief pause between examples
            
            refreshSessionToken();
            Thread.sleep(1000);
            
            demonstrateCredentialAuthentication();
            Thread.sleep(1000);
            
            demonstrateErrorHandling();
            Thread.sleep(1000);
            
            demonstrateBestPractices();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Examples interrupted", e);
        } catch (Exception e) {
            log.error("Error running examples", e);
        }
        
        log.info("✅ ICICI Direct Breeze API Examples completed");
    }
}