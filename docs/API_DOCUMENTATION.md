# TradeMaster Broker Authentication Service API Documentation

## Overview

The TradeMaster Broker Authentication Service provides secure authentication and session management for multiple broker integrations including Zerodha, Upstox, Angel One, and ICICI Direct. This service implements Zero Trust Security Architecture with comprehensive rate limiting, MFA, and audit logging.

## Architecture

- **Framework**: Spring Boot 3.5+ with Spring MVC (No WebFlux per TradeMaster Standards)
- **Java Version**: Java 24 with Virtual Threads enabled
- **Security**: Zero Trust Architecture with SecurityFacade pattern
- **Database**: PostgreSQL with HikariCP connection pooling
- **Cache**: Redis for session management and rate limiting
- **Messaging**: Apache Kafka for event-driven architecture
- **Monitoring**: Prometheus metrics with custom business metrics

## Base URL

```
Production: https://api.trademaster.app/api/v1/broker-auth
Development: http://localhost:8087/api/v1/broker-auth
```

## Authentication

All API endpoints require authentication via:
1. JWT Bearer Token (recommended)
2. API Key with `tm_` prefix
3. Session-based authentication

### Headers
```http
Authorization: Bearer <jwt_token>
# OR
X-API-Key: tm_<api_key>
Content-Type: application/json
X-Correlation-ID: <unique_request_id>
```

## Core Endpoints

### 1. Broker Authentication

#### POST /auth/authenticate
Authenticate user with broker credentials.

**Request Body:**
```json
{
  "brokerType": "ZERODHA|UPSTOX|ANGEL_ONE|ICICI_DIRECT",
  "credentials": {
    "apiKey": "string",
    "apiSecret": "string",
    "password": "string",
    "totpSecret": "string",
    "clientId": "string",
    "redirectUri": "string",
    "brokerUserId": "string",
    "brokerUsername": "string"
  },
  "mfaToken": "string"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "sessionId": "string",
    "accessToken": "string",
    "refreshToken": "string",
    "expiresAt": "2024-12-07T10:30:00Z",
    "brokerType": "ZERODHA",
    "permissions": ["READ", "TRADE", "FUNDS"]
  },
  "timestamp": "2024-12-07T10:00:00Z",
  "correlationId": "req_123456789"
}
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid broker credentials provided",
    "details": "API key validation failed"
  },
  "timestamp": "2024-12-07T10:00:00Z",
  "correlationId": "req_123456789"
}
```

**Response (429 Too Many Requests):**
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded for broker authentication",
    "retryAfter": 60
  },
  "timestamp": "2024-12-07T10:00:00Z",
  "correlationId": "req_123456789"
}
```

### 2. Session Management

#### GET /sessions/{sessionId}
Retrieve session information.

**Path Parameters:**
- `sessionId`: Session identifier

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "sessionId": "string",
    "userId": "string",
    "brokerType": "ZERODHA",
    "status": "ACTIVE|EXPIRED|REVOKED",
    "createdAt": "2024-12-07T10:00:00Z",
    "lastAccessedAt": "2024-12-07T10:30:00Z",
    "expiresAt": "2024-12-07T18:00:00Z",
    "permissions": ["READ", "TRADE"]
  }
}
```

#### DELETE /sessions/{sessionId}
Revoke/invalidate a session.

**Response (204 No Content)**

#### POST /sessions/refresh
Refresh an expired session.

**Request Body:**
```json
{
  "refreshToken": "string",
  "sessionId": "string"
}
```

### 3. OAuth Callbacks

#### GET /oauth/{broker}/callback
Handle OAuth callback from broker.

**Path Parameters:**
- `broker`: Broker name (zerodha, upstox, etc.)

**Query Parameters:**
- `code`: Authorization code from broker
- `state`: CSRF protection token

**Response (302 Redirect):**
Redirects to configured success/failure URL with query parameters.

### 4. Credential Management

#### POST /credentials
Store encrypted broker credentials.

**Request Body:**
```json
{
  "brokerType": "ZERODHA",
  "credentials": {
    "apiKey": "string",
    "apiSecret": "string",
    "accountName": "string"
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "credentialId": "string",
    "brokerType": "ZERODHA",
    "isVerified": false,
    "createdAt": "2024-12-07T10:00:00Z"
  }
}
```

#### GET /credentials
List user's broker credentials.

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "credentials": [
      {
        "credentialId": "string",
        "brokerType": "ZERODHA",
        "accountName": "string",
        "isActive": true,
        "isVerified": true,
        "lastUsed": "2024-12-07T10:00:00Z"
      }
    ]
  }
}
```

### 5. Multi-Factor Authentication (MFA)

#### POST /mfa/setup
Setup MFA for user account.

**Request Body:**
```json
{
  "method": "TOTP|SMS|EMAIL"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "secret": "string",
    "qrCodeUrl": "string",
    "backupCodes": ["string"]
  }
}
```

#### POST /mfa/verify
Verify MFA token.

**Request Body:**
```json
{
  "token": "123456",
  "method": "TOTP"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "verified": true,
    "validUntil": "2024-12-07T10:05:00Z"
  }
}
```

### 6. API Key Management

#### POST /api-keys
Create new API key.

**Request Body:**
```json
{
  "name": "My Trading Bot",
  "scopes": ["broker.auth", "sessions.read"],
  "expiresAt": "2024-12-07T10:00:00Z"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "apiKey": "tm_1234567890abcdef",
    "name": "My Trading Bot",
    "scopes": ["broker.auth", "sessions.read"],
    "createdAt": "2024-12-07T10:00:00Z",
    "expiresAt": "2024-12-07T10:00:00Z"
  }
}
```

#### GET /api-keys
List user's API keys.

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "apiKeys": [
      {
        "keyId": "string",
        "name": "My Trading Bot",
        "scopes": ["broker.auth"],
        "status": "ACTIVE|REVOKED",
        "lastUsed": "2024-12-07T10:00:00Z",
        "expiresAt": "2024-12-07T10:00:00Z"
      }
    ]
  }
}
```

#### DELETE /api-keys/{keyId}
Revoke an API key.

**Response (204 No Content)**

## Rate Limiting

The service implements multi-tier rate limiting:

### Global Limits
- **Authentication**: 10 requests per minute per user
- **Session Operations**: 50 requests per minute per user
- **Credential Management**: 5 requests per minute per user

### Broker-Specific Limits
- **Zerodha**: 100 requests per minute
- **Upstox**: 150 requests per minute
- **Angel One**: 120 requests per minute
- **ICICI Direct**: 60 requests per minute

### Rate Limit Headers
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1701943200
X-RateLimit-Retry-After: 60
```

## Error Handling

### Standard Error Response
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": "Additional error context",
    "field": "fieldName"
  },
  "timestamp": "2024-12-07T10:00:00Z",
  "correlationId": "req_123456789",
  "path": "/api/v1/broker-auth/authenticate"
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_REQUEST` | 400 | Malformed request or missing required fields |
| `INVALID_CREDENTIALS` | 400 | Invalid broker credentials |
| `UNAUTHORIZED_ACCESS` | 401 | Authentication required or invalid |
| `FORBIDDEN_OPERATION` | 403 | Operation not allowed for user |
| `RESOURCE_NOT_FOUND` | 404 | Requested resource not found |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `BROKER_API_ERROR` | 502 | External broker API error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error |

## Security Features

### 1. Zero Trust Architecture
- All external requests go through SecurityFacade
- Internal service communication uses direct injection
- Comprehensive input validation and sanitization

### 2. Encryption
- AES-256-GCM encryption for stored credentials
- PBKDF2 key derivation with 100,000+ iterations
- Secure random salt generation

### 3. Audit Logging
- All authentication attempts logged
- Security violations tracked
- Correlation IDs for request tracing

### 4. OWASP Security Controls
- XSS protection with input sanitization
- SQL injection prevention with parameterized queries
- CSRF protection with state tokens
- Secure headers implementation

## Health Monitoring

### Health Check Endpoint
```
GET /actuator/health
```

### Available Health Indicators
- **Database**: PostgreSQL connectivity and performance
- **Redis**: Cache connectivity and memory usage
- **Security**: Security services (encryption, MFA, API keys)
- **Broker APIs**: External broker connectivity
- **Custom Metrics**: Business metrics and system performance

### Prometheus Metrics
Available at `/actuator/prometheus`:

- `broker_auth_success_total`: Successful authentications
- `broker_auth_failure_total`: Failed authentications  
- `broker_auth_duration_seconds`: Authentication duration
- `broker_sessions_active`: Active sessions count
- `broker_api_calls_total`: API calls per broker
- `security_violations_total`: Security violation events

## Development and Testing

### Environment Configuration
```yaml
# Development
DATABASE_URL: jdbc:postgresql://localhost:5432/trademaster_dev
REDIS_URL: redis://localhost:6379
BROKER_ENCRYPTION_KEY: dev-encryption-key-32-chars

# Production
DATABASE_URL: ${VAULT_DATABASE_URL}
REDIS_URL: ${VAULT_REDIS_URL}  
BROKER_ENCRYPTION_KEY: ${VAULT_ENCRYPTION_KEY}
```

### Testing Endpoints
In development mode, additional testing endpoints are available:

#### POST /test/auth/simulate
Simulate broker authentication for testing.

#### GET /test/health/detailed
Detailed health information for debugging.

## Support and Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Verify broker credentials are correctly configured
   - Check rate limiting status
   - Ensure MFA token is valid and not expired

2. **Session Issues**
   - Verify session hasn't expired
   - Check if session was revoked
   - Ensure proper session refresh handling

3. **Rate Limiting**
   - Implement exponential backoff
   - Monitor rate limit headers
   - Use appropriate request batching

### Support Channels
- **Documentation**: https://docs.trademaster.app
- **GitHub Issues**: https://github.com/trademaster/broker-auth-service/issues
- **Support Email**: support@trademaster.app

## Changelog

### Version 1.0.0
- Initial release with multi-broker authentication
- Zero Trust Security implementation
- Comprehensive rate limiting and monitoring
- Full audit logging and compliance features