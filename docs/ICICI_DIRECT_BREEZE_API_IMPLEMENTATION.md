# ICICI Direct Breeze API Integration - Implementation Guide

## Overview

This document provides comprehensive implementation details for integrating with the ICICI Direct Breeze API following professional patterns established in TradeMaster's broker authentication service.

## API Characteristics

### Authentication Method
- **Protocol**: OAuth 2.0 with session token authentication
- **Security**: Triple-layer security (SSL + App Key encryption + Checksum validation)
- **Session Validity**: 24 hours or until midnight
- **Rate Limits**: 100 calls/minute, 5000 calls/day

### Base URLs
- **Production**: `https://api.icicidirect.com/breezeapi`
- **UAT**: `https://uatapi.icicidirect.com/icicidirectwebapi_core`
- **Login Portal**: `https://api.icicidirect.com/apiuser/login`

## Authentication Flow

### 1. App Registration
```
Visit: https://api.icicidirect.com/apiuser/login
1. Register your application
2. Obtain AppKey and secret_key pair
3. Configure redirect URL for your application
```

### 2. Session Token Generation
```
URL: https://api.icicidirect.com/apiuser/login?api_key=YOUR_API_KEY

Steps:
1. Login with ICICI Direct credentials
2. Extract session token from network logs
3. Look for 'API_Session' in Form Data
4. Use this token for API authentication
```

### 3. API Request Authentication
All authenticated requests require these headers:
```
X-Checksum: SHA256(timestamp + JSON_body + secret_key)
X-Timestamp: ISO8601 UTC datetime
X-AppKey: Your registered application key
X-SessionToken: Session token from login flow
Content-Type: application/json
```

## Implementation Details

### Core Service: ICICIDirectApiService

```java
@Service
public class ICICIDirectApiService implements BrokerApiService {
    
    // Primary authentication method using session token
    public CompletableFuture<AuthResponse> authenticate(AuthRequest request) {
        return CompletableFuture
            .supplyAsync(() -> performAuthentication(request), 
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleAuthenticationResult);
    }
    
    // Session validation and refresh
    public CompletableFuture<AuthResponse> refreshToken(String refreshToken) {
        // Implementation validates existing session token
    }
}
```

### Authentication Methods

#### Method 1: Session Token Authentication (Primary)
```java
private AuthResponse authenticateWithSessionToken(AuthRequest request) {
    String sessionToken = request.totpCode(); // Using totpCode field
    String timestamp = generateTimestamp();
    String customerDetailsUrl = apiUrl + "/api/v1/customerdetails";
    
    Request httpRequest = buildAuthenticatedRequest(
        customerDetailsUrl, "GET", null, sessionToken, timestamp
    );
    
    Response response = httpClient.newCall(httpRequest).execute();
    return processCustomerDetailsResponse(response, sessionToken);
}
```

#### Method 2: Credential-based Login (Fallback)
```java
private AuthResponse authenticateWithCredentials(AuthRequest request) {
    String requestBody = objectMapper.writeValueAsString(Map.of(
        "api_key", appKey,
        "user_id", request.userId(),
        "password", request.password()
    ));
    
    // Note: This returns instructions to obtain session token manually
}
```

### Checksum Generation

```java
private String generateChecksum(String timestamp, String requestBody, String secretKey) {
    String data = timestamp + Optional.ofNullable(requestBody).orElse("") + secretKey;
    
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(data.getBytes("UTF-8"));
    
    // Convert to hexadecimal string
    StringBuilder hexString = new StringBuilder();
    for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
            hexString.append('0');
        }
        hexString.append(hex);
    }
    return hexString.toString();
}
```

### Request Building

```java
private Request buildAuthenticatedRequest(String url, String method, String requestBody, 
                                        String sessionToken, String timestamp) {
    String checksum = generateChecksum(timestamp, requestBody, secretKey);
    
    Request.Builder builder = new Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Checksum", checksum)
        .addHeader("X-Timestamp", timestamp)
        .addHeader("X-AppKey", appKey)
        .addHeader("X-SessionToken", sessionToken);
    
    return switch (method.toUpperCase()) {
        case "GET" -> builder.get().build();
        case "POST" -> builder.post(RequestBody.create(
            Optional.ofNullable(requestBody).orElse(""), 
            MediaType.parse("application/json"))).build();
        // ... other methods
    };
}
```

## Configuration

### Application Properties
```yaml
broker:
  icici:
    name: "ICICI Direct Breeze"
    api-url: "https://api.icicidirect.com/breezeapi"
    login-url: "https://api.icicidirect.com/apiuser/login"
    app-key: ${ICICI_APP_KEY:}
    secret-key: ${ICICI_SECRET_KEY:}
    redirect-uri: ${ICICI_REDIRECT_URI:}
    rate-limits:
      per-second: 2
      per-minute: 100
      per-day: 5000
    session-validity: 86400  # 24 hours
    checksum-timeout: 300    # 5 minutes
```

### Circuit Breaker Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      icici-api:
        failure-rate-threshold: 60
        slow-call-rate-threshold: 60
        slow-call-duration-threshold: 2500ms
        minimum-number-of-calls: 8
        sliding-window-size: 15
        wait-duration-in-open-state: 30s

  ratelimiter:
    instances:
      icici-api:
        limit-for-period: 2
        limit-refresh-period: 1s
        timeout-duration: 3s
```

## Error Handling

### Common Error Codes
| HTTP Code | Description | Recovery Action |
|-----------|-------------|-----------------|
| 401 | Authentication failed | Re-obtain session token |
| 403 | Access forbidden | Check permissions |
| 429 | Rate limit exceeded | Implement backoff strategy |
| 500 | Server error | Retry with exponential backoff |

### Error Response Processing
```java
private String formatErrorMessage(int statusCode, String responseBody) {
    return switch (statusCode) {
        case 401 -> "Authentication failed - Invalid credentials or session expired";
        case 403 -> "Access forbidden - Insufficient permissions";
        case 429 -> "Rate limit exceeded - Too many requests";
        case 500 -> "Server error - Please try again later";
        default -> String.format("API request failed (HTTP %d): %s", statusCode, responseBody);
    };
}
```

## Usage Examples

### Basic Authentication
```java
AuthRequest request = new AuthRequest(
    BrokerType.ICICI_DIRECT,
    "your_api_key",
    "your_api_secret",
    "your_user_id",
    "your_password",
    "session_token_from_login_portal"
);

CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(request);
AuthResponse response = future.get();

if (response.success()) {
    System.out.println("Authentication successful: " + response.sessionId());
} else {
    System.err.println("Authentication failed: " + response.message());
}
```

### Session Refresh
```java
String sessionToken = "existing_session_token";
CompletableFuture<AuthResponse> future = iciciDirectApiService.refreshToken(sessionToken);
AuthResponse response = future.get();

if (response.success()) {
    System.out.println("Session refreshed: " + response.expiresAt());
} else {
    System.err.println("Session expired, re-authentication required");
}
```

## Best Practices

### 1. Session Management
- Cache session tokens for up to 24 hours
- Implement automatic refresh before expiry
- Handle session expiry gracefully with re-authentication

### 2. Rate Limiting
- Respect API rate limits (100/minute, 5000/day)
- Implement exponential backoff for rate limit errors
- Use circuit breaker patterns for resilience

### 3. Security
- Never log sensitive information (session tokens, credentials)
- Use secure storage for API keys and secrets
- Implement proper checksum validation

### 4. Error Handling
- Implement comprehensive error handling for all scenarios
- Log errors with correlation IDs for debugging
- Provide meaningful error messages to users

### 5. Performance
- Use Virtual Threads for I/O operations
- Implement connection pooling with OkHttp
- Cache successful authentication responses

## Testing

### Unit Tests
```java
@Test
void testAuthenticate_WithValidSessionToken_ReturnsSuccessfulResponse() {
    // Mock setup
    AuthRequest authRequest = new AuthRequest(/* ... */);
    Response mockResponse = createMockResponse(200, validResponseBody);
    setupMockHttpClient(mockResponse);
    
    // Execute
    CompletableFuture<AuthResponse> future = iciciDirectApiService.authenticate(authRequest);
    AuthResponse response = future.get();
    
    // Verify
    assertTrue(response.success());
    assertEquals(BrokerType.ICICI_DIRECT, response.brokerType());
}
```

### Integration Tests
- Test with actual ICICI Direct test environment
- Validate session token generation flow
- Test rate limiting behavior
- Verify error handling scenarios

## Monitoring and Observability

### Metrics to Monitor
- Authentication success/failure rates
- API response times
- Rate limit violations
- Session expiry patterns
- Error code distributions

### Logging Strategy
```java
log.info("Authenticating with ICICI Direct Breeze for user: {}", 
         Optional.ofNullable(request.userId()).orElse("unknown"));

log.error("ICICI Direct authentication failed: HTTP {} - {}", 
          response.code(), sanitizeResponseForLogging(responseBody));
```

## Troubleshooting

### Common Issues

1. **Session Token Expiry**
   - Solution: Implement automatic re-authentication
   - Monitor session expiry times

2. **Rate Limit Exceeded**
   - Solution: Implement exponential backoff
   - Review API usage patterns

3. **Checksum Validation Errors**
   - Solution: Verify timestamp format and payload structure
   - Check secret key configuration

4. **Network Connectivity Issues**
   - Solution: Implement retry logic with circuit breaker
   - Use multiple API endpoints if available

## Security Considerations

1. **Credential Management**
   - Store API keys securely using environment variables
   - Never commit secrets to version control
   - Use secure key management systems in production

2. **Session Security**
   - Implement secure session storage
   - Use encryption for session tokens at rest
   - Implement proper session invalidation

3. **Request Security**
   - Always use HTTPS for API calls
   - Validate all input parameters
   - Implement proper error handling without information leakage

## Compliance and Regulatory Considerations

- Maintain audit logs for all API interactions
- Implement data retention policies
- Ensure compliance with financial regulations
- Document all API interactions for regulatory review

## Support and Resources

- **Official Documentation**: https://uatapi.icicidirect.com/iciciDirectWebApi_core/documents/index.html
- **API Portal**: https://api.icicidirect.com/
- **GitHub Repository**: https://github.com/Idirect-Tech/Breeze-Java-SDK
- **Support**: Contact ICICI Direct API support team

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2024-01-XX | Initial implementation |
| 1.0.1 | 2024-01-XX | Added comprehensive error handling |
| 1.0.2 | 2024-01-XX | Enhanced session management |