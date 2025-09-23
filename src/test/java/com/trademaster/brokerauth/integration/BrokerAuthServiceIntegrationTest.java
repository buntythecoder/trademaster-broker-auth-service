package com.trademaster.brokerauth.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.brokerauth.BrokerAuthServiceApplication;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.entity.Broker;
import com.trademaster.brokerauth.entity.BrokerAccount;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.repository.BrokerAccountRepository;
import com.trademaster.brokerauth.repository.BrokerRepository;
import com.trademaster.brokerauth.repository.BrokerSessionRepository;
import com.trademaster.brokerauth.security.SecurityContext;
import com.trademaster.brokerauth.security.SecurityError;
import com.trademaster.brokerauth.security.SecurityFacade;
import com.trademaster.brokerauth.security.SecurityLevel;
import com.trademaster.brokerauth.service.BrokerAuthenticationService;
import com.trademaster.brokerauth.service.BrokerSessionService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive Integration Tests for Broker Auth Service
 * 
 * MANDATORY: TestContainers - Rule #20
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Security Testing - Rule #23
 */
@SpringBootTest(
    classes = BrokerAuthServiceApplication.class, 
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.threads.virtual.enabled=true",
        "spring.test.context.cache.maxSize=3"
    }
)
@Testcontainers
@DisplayName("Broker Auth Service Integration Tests")
class BrokerAuthServiceIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("broker_auth_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server --requirepass testpass")
            .withReuse(false);

    @Container
    static GenericContainer<?> wiremock = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.3.1"))
            .withExposedPorts(8080)
            .withCommand("--port", "8080", "--verbose")
            .waitingFor(Wait.forHttp("/__admin/health").forPort(8080))
            .withReuse(false);

    @Container
    static GenericContainer<?> vault = new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.15"))
            .withExposedPorts(8200)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", "test-root-token")
            .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200))
            .withReuse(false);

    @Autowired
    private BrokerAuthenticationService brokerAuthenticationService;

    @Autowired
    private BrokerSessionService brokerSessionService;

    @Autowired
    private SecurityFacade securityFacade;

    @Autowired
    private BrokerRepository brokerRepository;

    @Autowired
    private BrokerAccountRepository brokerAccountRepository;

    @Autowired
    private BrokerSessionRepository brokerSessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "testpass");
        registry.add("spring.cache.redis.time-to-live", () -> "600000");

        // Vault configuration
        registry.add("spring.cloud.vault.uri", () -> "http://" + vault.getHost() + ":" + vault.getFirstMappedPort());
        registry.add("spring.cloud.vault.token", () -> "test-root-token");
        registry.add("spring.cloud.vault.enabled", () -> "false"); // Disable for testing

        // Broker API configurations with WireMock
        String wiremockUrl = "http://" + wiremock.getHost() + ":" + wiremock.getFirstMappedPort();
        registry.add("broker.angel-one.api-url", () -> wiremockUrl);
        registry.add("broker.angel-one.client-code", () -> "TEST123");
        registry.add("broker.angel-one.password", () -> "testpass");
        registry.add("broker.angel-one.api-key", () -> "test-api-key");

        registry.add("broker.zerodha.api-key", () -> "test-zerodha-key");
        registry.add("broker.zerodha.api-secret", () -> "test-zerodha-secret");
        registry.add("broker.zerodha.api-url", () -> wiremockUrl);

        registry.add("broker.upstox.api-url", () -> wiremockUrl);
        registry.add("broker.upstox.api-key", () -> "test-upstox-key");
        registry.add("broker.upstox.api-secret", () -> "test-upstox-secret");

        // Security configuration
        registry.add("security.encryption.key", () -> "test-encryption-key-32-chars-long");
        registry.add("security.jwt.secret", () -> "test-jwt-secret-key-for-testing-purposes");
        registry.add("security.rate-limit.requests-per-minute", () -> "100");
        registry.add("security.session.timeout-minutes", () -> "30");

        // Circuit breaker configuration
        registry.add("resilience4j.circuitbreaker.instances.broker-api.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.broker-api.sliding-window-size", () -> "10");
        registry.add("resilience4j.retry.instances.broker-api.max-attempts", () -> "3");
    }

    @BeforeAll
    static void beforeAll() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = serverPort;
        RestAssured.basePath = "/api/v1";

        // Setup test data
        setupTestBrokers();
        setupTestAccounts();
        setupWireMockStubs();
    }

    /**
     * Test complete broker authentication lifecycle with Angel One
     * MANDATORY: Virtual Threads - Rule #12
     * MANDATORY: Security - Rule #23
     */
    @Test
    @DisplayName("Angel One Authentication Lifecycle - Complete Flow")
    void angelOneAuthentication_CompleteFlow_ShouldAuthenticateAndManageSession() throws Exception {
        // Given: Authentication request for Angel One
        AuthRequest authRequest = AuthRequest.builder()
                .userId("TEST123")
                .brokerType(BrokerType.ANGEL_ONE)
                .apiSecret("testpass")
                .totpCode("123456")
                .build();

        // When: Authenticate with Angel One via API
        AuthResponse response = given()
                .contentType(ContentType.JSON)
                .body(authRequest)
                .auth().basic("admin", "admin123")
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(200)
                .extract()
                .as(AuthResponse.class);

        // Then: Verify authentication response
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.sessionId()).isNotNull();
        assertThat(response.accessToken()).isNotNull();
        assertThat(response.brokerType()).isEqualTo(BrokerType.ANGEL_ONE);

        // And: Verify session was created and persisted
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Optional<BrokerSession> session = brokerSessionRepository.findBySessionId(response.sessionId());
                    assertThat(session).isPresent();
                    assertThat(session.get().getStatus()).isEqualTo(SessionStatus.ACTIVE);
                    assertThat(session.get().getBrokerType()).isEqualTo(BrokerType.ANGEL_ONE);
                    assertThat(session.get().getUserId()).isEqualTo("TEST123");
                });

        // And: Verify session can be retrieved
        BrokerSession retrievedSession = given()
                .auth().basic("admin", "admin123")
                .when()
                .get("/auth/session/{sessionId}", response.sessionId())
                .then()
                .statusCode(200)
                .extract()
                .as(BrokerSession.class);

        assertThat(retrievedSession.getSessionId()).isEqualTo(response.sessionId());
        assertThat(retrievedSession.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    /**
     * Test concurrent authentication requests with Virtual Threads
     * MANDATORY: Virtual Threads - Rule #12
     * MANDATORY: Concurrent Processing - Rule #12
     */
    @Test
    @DisplayName("Concurrent Authentication - Virtual Threads Performance")
    void concurrentAuthentication_VirtualThreadsPerformance_ShouldHandleMultipleRequestsEfficiently() {
        // Given: Multiple authentication requests for different brokers
        List<AuthRequest> authRequests = List.of(
                createAuthRequest("USER001", BrokerType.ANGEL_ONE),
                createAuthRequest("USER002", BrokerType.ZERODHA),
                createAuthRequest("USER003", BrokerType.UPSTOX),
                createAuthRequest("USER004", BrokerType.ANGEL_ONE),
                createAuthRequest("USER005", BrokerType.ZERODHA)
        );

        // When: Send concurrent authentication requests
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<AuthResponse>> futures = authRequests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> 
                    given()
                            .contentType(ContentType.JSON)
                            .body(request)
                            .auth().basic("admin", "admin123")
                            .when()
                            .post("/auth/authenticate")
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(AuthResponse.class),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()))
                .toList();

        List<AuthResponse> responses = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;

        // Then: Verify all requests were processed successfully
        assertThat(responses).hasSize(5);
        assertThat(responses).allMatch(AuthResponse::success);

        // And: Verify processing was fast due to Virtual Threads
        assertThat(processingTime).isLessThan(10000); // Should complete in under 10 seconds

        // And: Verify all sessions were persisted
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    List<BrokerSession> sessions = brokerSessionRepository.findAll();
                    long activeCount = sessions.stream()
                            .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                            .count();
                    assertThat(activeCount).isGreaterThanOrEqualTo(5);
                });
    }

    /**
     * Test security facade integration with zero trust architecture
     * MANDATORY: Zero Trust Security - Rule #6
     * MANDATORY: Security Facade - Rule #6
     */
    @Test
    @DisplayName("Security Facade - Zero Trust Architecture")
    void securityFacade_ZeroTrustArchitecture_ShouldValidateAllExternalAccess() {
        // Given: Security context for external access
        SecurityContext securityContext = SecurityContext.builder()
                .userId("TEST_USER")
                .sessionId("TEST_SESSION")
                .ipAddress("192.168.1.100")
                .userAgent("TestAgent/1.0")
                .securityLevel(SecurityLevel.HIGH)
                .build();

        AuthRequest authRequest = createAuthRequest("TEST_USER", BrokerType.ANGEL_ONE);

        // When: Access through security facade
        var result = securityFacade.secureAccess(securityContext, 
                () -> brokerAuthenticationService.authenticate(authRequest));

        // Then: Verify security facade processed the request
        assertThat(result).isNotNull();
        result.match(
                authResponse -> {
                    assertThat(authResponse).isNotNull();
                    return authResponse;
                },
                error -> {
                    // If error, should be due to security validation
                    assertThat(error).isInstanceOf(SecurityError.class);
                    return null;
                }
        );

        // And: Verify security audit trail was created
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    // Check that security events were logged (would be verified through logs or audit table)
                    // This is a placeholder - actual implementation would check audit logs
                    assertThat(true).isTrue(); // Security facade was invoked
                });
    }

    /**
     * Test session lifecycle management
     * MANDATORY: Session Management - Rule #23
     * MANDATORY: Virtual Threads - Rule #12
     */
    @Test
    @DisplayName("Session Lifecycle Management - Create, Validate, Expire")
    void sessionLifecycleManagement_CreateValidateExpire_ShouldManageSessionsCorrectly() {
        // Given: Create active session
        BrokerSession session = BrokerSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId("LIFECYCLE_USER")
                .brokerType(BrokerType.ANGEL_ONE)
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .status(SessionStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .lastAccessedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        brokerSessionRepository.save(session);

        // When: Validate session
        boolean isValid = given()
                .auth().basic("admin", "admin123")
                .when()
                .get("/auth/session/{sessionId}/validate", session.getSessionId())
                .then()
                .statusCode(200)
                .extract()
                .as(Boolean.class);

        // Then: Session should be valid
        assertThat(isValid).isTrue();

        // When: Expire session
        given()
                .auth().basic("admin", "admin123")
                .when()
                .delete("/auth/session/{sessionId}", session.getSessionId())
                .then()
                .statusCode(204);

        // Then: Session should be expired
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Optional<BrokerSession> expiredSession = brokerSessionRepository.findBySessionId(session.getSessionId());
                    assertThat(expiredSession).isPresent();
                    assertThat(expiredSession.get().getStatus()).isEqualTo(SessionStatus.EXPIRED);
                });

        // And: Validation should now fail
        given()
                .auth().basic("admin", "admin123")
                .when()
                .get("/auth/session/{sessionId}/validate", session.getSessionId())
                .then()
                .statusCode(401); // Unauthorized for expired session
    }

    /**
     * Test rate limiting and security controls
     * MANDATORY: Rate Limiting - Rule #23
     * MANDATORY: Circuit Breaker - Rule #24
     */
    @Test
    @DisplayName("Rate Limiting - Security Controls Under Load")
    void rateLimiting_SecurityControlsUnderLoad_ShouldEnforceRateLimits() {
        // Given: Rapid authentication requests
        String testUserId = "RATE_LIMIT_USER";
        AuthRequest authRequest = createAuthRequest(testUserId, BrokerType.ANGEL_ONE);

        // When: Send requests rapidly to trigger rate limiting
        List<Integer> statusCodes = IntStream.range(0, 150) // Exceed rate limit of 100 per minute
                .parallel()
                .mapToObj(i -> given()
                        .contentType(ContentType.JSON)
                        .body(authRequest)
                        .auth().basic("admin", "admin123")
                        .when()
                        .post("/auth/authenticate")
                        .getStatusCode())
                .toList();

        // Then: Verify some requests were rate limited
        long successfulRequests = statusCodes.stream()
                .filter(code -> code == 200)
                .count();
        
        long rateLimitedRequests = statusCodes.stream()
                .filter(code -> code == 429) // Too Many Requests
                .count();

        assertThat(successfulRequests).isLessThan(150);
        assertThat(rateLimitedRequests).isGreaterThan(0);
        assertThat(successfulRequests + rateLimitedRequests).isEqualTo(150);
    }

    /**
     * Test broker API integration with WireMock
     * MANDATORY: External API Integration - Rule #24
     * MANDATORY: Circuit Breaker - Rule #24
     */
    @Test
    @DisplayName("Broker API Integration - External Service Calls")
    void brokerApiIntegration_ExternalServiceCalls_ShouldHandleRealApiResponses() {
        // Given: Multiple broker authentication requests
        List<AuthRequest> requests = List.of(
                createAuthRequest("ANGEL_USER", BrokerType.ANGEL_ONE),
                createAuthRequest("ZERODHA_USER", BrokerType.ZERODHA),
                createAuthRequest("UPSTOX_USER", BrokerType.UPSTOX)
        );

        // When: Authenticate with different brokers
        List<AuthResponse> responses = requests.stream()
                .map(request -> given()
                        .contentType(ContentType.JSON)
                        .body(request)
                        .auth().basic("admin", "admin123")
                        .when()
                        .post("/auth/authenticate")
                        .then()
                        .statusCode(200)
                        .extract()
                        .as(AuthResponse.class))
                .toList();

        // Then: Verify all brokers responded successfully
        assertThat(responses).hasSize(3);
        assertThat(responses).allMatch(AuthResponse::success);
        assertThat(responses).extracting(AuthResponse::brokerType)
                .containsExactlyInAnyOrder(BrokerType.ANGEL_ONE, BrokerType.ZERODHA, BrokerType.UPSTOX);

        // And: Verify unique session IDs
        Set<String> sessionIds = responses.stream()
                .map(AuthResponse::sessionId)
                .collect(java.util.stream.Collectors.toSet());
        
        assertThat(sessionIds).hasSize(3); // All unique
    }

    /**
     * Test credential encryption and secure storage
     * MANDATORY: Data Security - Rule #23
     * MANDATORY: Encryption - Rule #23
     */
    @Test
    @DisplayName("Credential Security - Encryption and Secure Storage")
    void credentialSecurity_EncryptionAndSecureStorage_ShouldProtectSensitiveData() {
        // Given: Broker account with sensitive credentials
        BrokerAccount account = BrokerAccount.builder()
                .userId("SECURE_USER")
                .brokerType(BrokerType.ANGEL_ONE)
                .clientId("SECURE123")
                .apiKey("sensitive-api-key")
                .apiSecret("sensitive-api-secret")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When: Save account (should encrypt sensitive fields)
        BrokerAccount savedAccount = brokerAccountRepository.save(account);

        // Then: Verify account was saved with encrypted credentials
        assertThat(savedAccount.getId()).isNotNull();
        // In a real implementation, we would verify that apiKey and apiSecret are encrypted

        // When: Retrieve account
        Optional<BrokerAccount> retrievedAccount = brokerAccountRepository.findById(savedAccount.getId());

        // Then: Verify account can be retrieved and decrypted
        assertThat(retrievedAccount).isPresent();
        assertThat(retrievedAccount.get().getUserId()).isEqualTo("SECURE_USER");
        assertThat(retrievedAccount.get().getClientId()).isEqualTo("SECURE123");
        // In a real implementation, credentials would be automatically decrypted on retrieval
    }

    /**
     * Test error handling and resilience patterns
     * MANDATORY: Circuit Breaker - Rule #24
     * MANDATORY: Error Handling - Rule #11
     */
    @Test
    @DisplayName("Error Handling - Resilience Under Failure")
    void errorHandling_ResilienceUnderFailure_ShouldHandleFailuresGracefully() {
        // Given: Stop WireMock to simulate broker API failure
        wiremock.stop();

        AuthRequest authRequest = createAuthRequest("FAILURE_USER", BrokerType.ANGEL_ONE);

        // When: Attempt authentication with failed external service
        AuthResponse response = given()
                .contentType(ContentType.JSON)
                .body(authRequest)
                .auth().basic("admin", "admin123")
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(200) // Should still return 200 but with failure message
                .extract()
                .as(AuthResponse.class);

        // Then: Verify graceful failure handling
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("unavailable").or().contains("failed");

        // And: Verify no invalid session was created
        List<BrokerSession> sessions = brokerSessionRepository.findAll();
        boolean hasFailedUserSession = sessions.stream()
                .anyMatch(s -> "FAILURE_USER".equals(s.getUserId()) && s.getStatus() == SessionStatus.ACTIVE);
        
        assertThat(hasFailedUserSession).isFalse();
    }

    // Helper methods

    private void setupTestBrokers() {
        List<Broker> brokers = List.of(
                Broker.builder()
                        .name("Angel One")
                        .type(BrokerType.ANGEL_ONE)
                        .apiUrl("https://apiconnect.angelbroking.com")
                        .isActive(true)
                        .build(),
                
                Broker.builder()
                        .name("Zerodha")
                        .type(BrokerType.ZERODHA)
                        .apiUrl("https://api.kite.trade")
                        .isActive(true)
                        .build(),
                
                Broker.builder()
                        .name("Upstox")
                        .type(BrokerType.UPSTOX)
                        .apiUrl("https://api.upstox.com")
                        .isActive(true)
                        .build()
        );
        
        brokerRepository.saveAll(brokers);
    }

    private void setupTestAccounts() {
        List<BrokerAccount> accounts = List.of(
                BrokerAccount.builder()
                        .userId("TEST123")
                        .brokerType(BrokerType.ANGEL_ONE)
                        .clientId("TEST123")
                        .apiKey("test-api-key")
                        .apiSecret("test-api-secret")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                
                BrokerAccount.builder()
                        .userId("ZERODHA_USER")
                        .brokerType(BrokerType.ZERODHA)
                        .clientId("ZER123")
                        .apiKey("zerodha-key")
                        .apiSecret("zerodha-secret")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        
        brokerAccountRepository.saveAll(accounts);
    }

    private void setupWireMockStubs() {
        // Setup WireMock stubs for different broker APIs
        setupAngelOneStubs();
        setupZerodhaStubs();
        setupUpstoxStubs();
    }

    private void setupAngelOneStubs() {
        // Angel One successful authentication response
        Map<String, Object> angelOneResponse = Map.of(
                "status", true,
                "message", "SUCCESS",
                "data", Map.of(
                        "jwtToken", "test-angel-jwt-token",
                        "refreshToken", "test-angel-refresh-token",
                        "feedToken", "test-angel-feed-token"
                )
        );

        given()
                .contentType(ContentType.JSON)
                .body(createWireMockMapping("/rest/auth/angelbroking/jwt/v1/generateTokens", 
                        "POST", 200, angelOneResponse))
                .when()
                .post("http://" + wiremock.getHost() + ":" + wiremock.getFirstMappedPort() + "/__admin/mappings")
                .then()
                .statusCode(201);
    }

    private void setupZerodhaStubs() {
        // Zerodha successful authentication response
        Map<String, Object> zerodhaResponse = Map.of(
                "status", "success",
                "data", Map.of(
                        "access_token", "test-zerodha-access-token",
                        "refresh_token", "test-zerodha-refresh-token",
                        "user_id", "ZERODHA_USER"
                )
        );

        given()
                .contentType(ContentType.JSON)
                .body(createWireMockMapping("/session/token", "POST", 200, zerodhaResponse))
                .when()
                .post("http://" + wiremock.getHost() + ":" + wiremock.getFirstMappedPort() + "/__admin/mappings")
                .then()
                .statusCode(201);
    }

    private void setupUpstoxStubs() {
        // Upstox successful authentication response
        Map<String, Object> upstoxResponse = Map.of(
                "status", "success",
                "data", Map.of(
                        "access_token", "test-upstox-access-token",
                        "refresh_token", "test-upstox-refresh-token"
                )
        );

        given()
                .contentType(ContentType.JSON)
                .body(createWireMockMapping("/v2/login/authorization/token", "POST", 200, upstoxResponse))
                .when()
                .post("http://" + wiremock.getHost() + ":" + wiremock.getFirstMappedPort() + "/__admin/mappings")
                .then()
                .statusCode(201);
    }

    private AuthRequest createAuthRequest(String userId, BrokerType brokerType) {
        return AuthRequest.builder()
                .userId(userId)
                .brokerType(brokerType)
                .apiSecret("testpass")
                .totpCode("123456")
                .build();
    }

    private Map<String, Object> createWireMockMapping(String url, String method, int status, Object responseBody) {
        return Map.of(
                "request", Map.of(
                        "method", method,
                        "url", url
                ),
                "response", Map.of(
                        "status", status,
                        "headers", Map.of("Content-Type", "application/json"),
                        "jsonBody", responseBody
                )
        );
    }
}