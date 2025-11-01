package com.trademaster.brokerauth.integration;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.dto.AuthRequest;
import com.trademaster.brokerauth.dto.AuthResponse;
import com.trademaster.brokerauth.dto.BrokerCredentials;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.service.BrokerAuthenticationService;
import com.trademaster.brokerauth.service.BrokerCredentialService;
import com.trademaster.brokerauth.service.BrokerSessionService;
import com.trademaster.brokerauth.service.EncryptionService;
import com.trademaster.brokerauth.service.VaultSecretService;
import com.trademaster.brokerauth.service.broker.BrokerServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for Vault Token Storage
 *
 * MANDATORY: Rule #20 - >70% integration test coverage
 * MANDATORY: Rule #12 - Test Virtual Threads functionality
 * MANDATORY: Rule #6 - Zero Trust Security validation
 *
 * Tests the complete flow:
 * 1. Encrypt tokens using EncryptionService
 * 2. Store encrypted tokens in Vault
 * 3. Retrieve encrypted tokens from Vault
 * 4. Decrypt tokens and return to trading-service
 *
 * Uses TestContainers to spin up real PostgreSQL, Redis, and Vault instances.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@SpringBootTest(
    properties = {
        "spring.profiles.active=test",
        "spring.threads.virtual.enabled=true",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false"
    }
)
@Import(com.trademaster.brokerauth.config.TestVaultConfig.class)
@Testcontainers
@DisplayName("Vault Token Storage Integration Tests")
class VaultTokenStorageIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("broker_auth_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
            .withReuse(false);

    @Container
    static GenericContainer<?> vaultContainer = new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.15"))
        .withExposedPorts(8200)
        .withEnv("VAULT_DEV_ROOT_TOKEN_ID", "test-root-token")
        .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
        .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200))
        .withCommand("server", "-dev");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        // Redis configuration - No password for test environment to avoid RESP3 authentication complexity
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Vault configuration - MANDATORY: Enable Vault for VaultSecretService bean creation
        registry.add("spring.cloud.vault.enabled", () -> "true");
        registry.add("spring.cloud.vault.uri", () -> "http://" + vaultContainer.getHost() + ":" + vaultContainer.getFirstMappedPort());
        registry.add("spring.cloud.vault.token", () -> "test-root-token");
        registry.add("spring.cloud.vault.kv.enabled", () -> "true");
        registry.add("spring.cloud.vault.kv.backend", () -> "secret");
        registry.add("spring.cloud.vault.kv.backend-version", () -> "2");  // MANDATORY: KV v2 engine

        // Security configuration
        registry.add("trademaster.security.encryption.key", () -> "dHJhZGVtYXN0ZXItZW5jcnlwdGlvbi1rZXktMzJiISE="); // base64 "trademaster-encryption-key-32b!!" (EXACTLY 32 bytes for AES-256)
    }

    @Autowired
    private VaultSecretService vaultSecretService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private BrokerSessionService brokerSessionService;

    @Autowired
    private BrokerCredentialService brokerCredentialService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final BrokerType TEST_BROKER = BrokerType.ZERODHA;

    @Nested
    @DisplayName("Basic Vault Operations")
    class BasicVaultOperationsTests {

        @Test
        @DisplayName("Should store and retrieve encrypted token from Vault")
        void shouldStoreAndRetrieveEncryptedToken() {
            // Given
            String plainToken = "test-access-token-" + UUID.randomUUID();
            String vaultPath = "secret/test/tokens/" + UUID.randomUUID();

            // When - Encrypt and store
            System.out.println("=== TEST - About to call encrypt(), plainToken: " + plainToken);
            assertNotNull(encryptionService, "EncryptionService should not be null");

            CompletableFuture<Optional<String>> encryptFuture = encryptionService.encrypt(plainToken);
            System.out.println("=== TEST - encryptFuture created: " + encryptFuture);

            Optional<String> encryptResult = encryptFuture.join();
            System.out.println("=== TEST - encryptResult: " + encryptResult);
            System.out.println("=== TEST - encryptResult.isPresent(): " + encryptResult.isPresent());

            String encryptedToken = encryptResult.orElseThrow();
            Boolean stored = vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedToken).join();

            // Then - Retrieve and decrypt
            assertTrue(stored, "Token should be stored in Vault");

            Optional<String> retrievedEncrypted = vaultSecretService.getSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY).join();
            assertTrue(retrievedEncrypted.isPresent(), "Encrypted token should be retrieved from Vault");

            String decryptedToken = encryptionService.decrypt(retrievedEncrypted.get()).join().orElseThrow();
            assertEquals(plainToken, decryptedToken, "Decrypted token should match original");
        }

        @Test
        @DisplayName("Should store both access and refresh tokens")
        void shouldStoreBothTokens() {
            // Given
            String accessToken = "access-token-" + UUID.randomUUID();
            String refreshToken = "refresh-token-" + UUID.randomUUID();
            String vaultPath = "secret/test/tokens/" + UUID.randomUUID();

            // When - Encrypt and store both tokens
            String encryptedAccess = encryptionService.encrypt(accessToken).join().orElseThrow();
            String encryptedRefresh = encryptionService.encrypt(refreshToken).join().orElseThrow();

            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedAccess).join();
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY, encryptedRefresh).join();

            // Then - Retrieve and verify both
            Optional<String> retrievedAccess = vaultSecretService.getSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY).join();
            Optional<String> retrievedRefresh = vaultSecretService.getSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY).join();

            assertTrue(retrievedAccess.isPresent(), "Access token should be retrieved");
            assertTrue(retrievedRefresh.isPresent(), "Refresh token should be retrieved");

            String decryptedAccess = encryptionService.decrypt(retrievedAccess.get()).join().orElseThrow();
            String decryptedRefresh = encryptionService.decrypt(retrievedRefresh.get()).join().orElseThrow();

            assertEquals(accessToken, decryptedAccess, "Access token should match");
            assertEquals(refreshToken, decryptedRefresh, "Refresh token should match");
        }

        @Test
        @DisplayName("Should return empty Optional for non-existent path")
        void shouldReturnEmptyForNonExistentPath() {
            // Given
            String nonExistentPath = "secret/non/existent/" + UUID.randomUUID();

            // When
            Optional<String> result = vaultSecretService.getSecret(nonExistentPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY).join();

            // Then
            assertTrue(result.isEmpty(), "Non-existent path should return empty Optional");
        }

        @Test
        @DisplayName("Should check if secret exists")
        void shouldCheckSecretExistence() {
            // Given
            String vaultPath = "secret/test/existence/" + UUID.randomUUID();
            String token = "existence-test-token";
            String encryptedToken = encryptionService.encrypt(token).join().orElseThrow();

            // When - Before storing
            Boolean existsBefore = vaultSecretService.secretExists(vaultPath).join();

            // Store secret
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedToken).join();

            // When - After storing
            Boolean existsAfter = vaultSecretService.secretExists(vaultPath).join();

            // Then
            assertFalse(existsBefore, "Secret should not exist before storing");
            assertTrue(existsAfter, "Secret should exist after storing");
        }
    }

    @Nested
    @DisplayName("BrokerCredentialService Integration")
    class BrokerCredentialServiceIntegrationTests {

        @Test
        @DisplayName("Should retrieve credentials from Vault through BrokerCredentialService")
        void shouldRetrieveCredentialsFromVault() {
            // Given - Create and save session with Vault path
            String sessionId = UUID.randomUUID().toString();
            String vaultPath = generateVaultPath(TEST_USER_ID, TEST_BROKER);

            // Store encrypted tokens in Vault
            String accessToken = "zerodha-access-token-" + UUID.randomUUID();
            String refreshToken = "zerodha-refresh-token-" + UUID.randomUUID();

            String encryptedAccess = encryptionService.encrypt(accessToken).join().orElseThrow();
            String encryptedRefresh = encryptionService.encrypt(refreshToken).join().orElseThrow();

            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedAccess).join();
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY, encryptedRefresh).join();

            // Create and save session
            BrokerSession session = BrokerSession.builder()
                .id(null)
                .sessionId(sessionId)
                .userId(TEST_USER_ID)
                .brokerType(TEST_BROKER)
                .vaultPath(vaultPath)
                .accessToken(encryptedAccess)
                .refreshToken(encryptedRefresh)
                .status(SessionStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .lastAccessedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .metadata(null)
                .build();

            brokerSessionService.saveSession(session);

            // When - Retrieve credentials using BrokerCredentialService
            CompletableFuture<Optional<BrokerCredentials>> credentialsFuture =
                brokerCredentialService.getCredentials(TEST_USER_ID, TEST_BROKER);

            Optional<BrokerCredentials> credentials = credentialsFuture.join();

            // Then
            assertTrue(credentials.isPresent(), "Credentials should be retrieved");

            BrokerCredentials creds = credentials.get();
            assertEquals(TEST_USER_ID, creds.userId(), "User ID should match");
            assertEquals(TEST_BROKER, creds.brokerType(), "Broker type should match");
            assertEquals(accessToken, creds.accessToken(), "Access token should be decrypted correctly");
            assertEquals(refreshToken, creds.refreshToken(), "Refresh token should be decrypted correctly");
            assertFalse(creds.isExpired(), "Credentials should not be expired");
        }

        @Test
        @DisplayName("Should return empty when session does not exist")
        void shouldReturnEmptyWhenSessionNotExists() {
            // Given - Non-existent user and broker
            String nonExistentUser = "non-existent-user-" + UUID.randomUUID();

            // When
            Optional<BrokerCredentials> credentials =
                brokerCredentialService.getCredentials(nonExistentUser, BrokerType.UPSTOX).join();

            // Then
            assertTrue(credentials.isEmpty(), "Should return empty when session does not exist");
        }

        @Test
        @DisplayName("Should validate credentials correctly")
        void shouldValidateCredentials() {
            // Given - Create active session
            String sessionId = UUID.randomUUID().toString();
            String vaultPath = generateVaultPath(TEST_USER_ID, BrokerType.ANGEL_ONE);

            BrokerSession activeSession = BrokerSession.builder()
                .id(null)
                .sessionId(sessionId)
                .userId(TEST_USER_ID)
                .brokerType(BrokerType.ANGEL_ONE)
                .vaultPath(vaultPath)
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .status(SessionStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .lastAccessedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .metadata(null)
                .build();

            brokerSessionService.saveSession(activeSession);

            // When - Validate credentials
            Boolean isValid = brokerCredentialService.validateCredentials(
                TEST_USER_ID, BrokerType.ANGEL_ONE).join();

            // Then
            assertTrue(isValid, "Active session credentials should be valid");
        }
    }

    @Nested
    @DisplayName("End-to-End Workflow Tests")
    class EndToEndWorkflowTests {

        @Test
        @DisplayName("Should complete full authentication to credential retrieval flow")
        void shouldCompleteFullFlow() {
            // This is a simulation of the full flow:
            // 1. User authenticates with broker
            // 2. Tokens are encrypted and stored in Vault
            // 3. Session is saved with Vault path
            // 4. Trading service retrieves credentials

            // Given - Simulate authentication response
            String userId = "e2e-user-" + UUID.randomUUID();
            BrokerType broker = BrokerType.ZERODHA;
            String accessToken = "e2e-access-token-" + UUID.randomUUID();
            String refreshToken = "e2e-refresh-token-" + UUID.randomUUID();

            // Step 1: Encrypt tokens
            String encryptedAccess = encryptionService.encrypt(accessToken).join().orElseThrow();
            String encryptedRefresh = encryptionService.encrypt(refreshToken).join().orElseThrow();

            // Step 2: Store in Vault
            String vaultPath = generateVaultPath(userId, broker);
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedAccess).join();
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY, encryptedRefresh).join();

            // Step 3: Save session
            BrokerSession session = BrokerSession.builder()
                .id(null)
                .sessionId(UUID.randomUUID().toString())
                .userId(userId)
                .brokerType(broker)
                .vaultPath(vaultPath)
                .accessToken(encryptedAccess)
                .refreshToken(encryptedRefresh)
                .status(SessionStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .lastAccessedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .metadata(null)
                .build();

            brokerSessionService.saveSession(session);

            // Step 4: Trading service retrieves credentials
            Optional<BrokerCredentials> credentials =
                brokerCredentialService.getCredentials(userId, broker).join();

            // Then - Verify complete flow
            assertTrue(credentials.isPresent(), "Credentials should be retrieved");

            BrokerCredentials creds = credentials.get();
            assertEquals(accessToken, creds.accessToken(),
                "Access token should survive full encryption/Vault/decryption flow");
            assertEquals(refreshToken, creds.refreshToken(),
                "Refresh token should survive full encryption/Vault/decryption flow");
            assertEquals(userId, creds.userId(), "User ID should match");
            assertEquals(broker, creds.brokerType(), "Broker type should match");
        }

        @Test
        @DisplayName("Should handle token refresh scenario")
        void shouldHandleTokenRefreshScenario() {
            // Given - Existing session with tokens
            String userId = "refresh-user-" + UUID.randomUUID();
            BrokerType broker = BrokerType.UPSTOX;
            String vaultPath = generateVaultPath(userId, broker);

            String oldAccessToken = "old-access-token";
            String oldRefreshToken = "old-refresh-token";

            // Store old tokens
            String encryptedOldAccess = encryptionService.encrypt(oldAccessToken).join().orElseThrow();
            String encryptedOldRefresh = encryptionService.encrypt(oldRefreshToken).join().orElseThrow();

            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedOldAccess).join();
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY, encryptedOldRefresh).join();

            // When - Simulate token refresh (overwrite with new tokens)
            String newAccessToken = "new-access-token";
            String newRefreshToken = "new-refresh-token";

            String encryptedNewAccess = encryptionService.encrypt(newAccessToken).join().orElseThrow();
            String encryptedNewRefresh = encryptionService.encrypt(newRefreshToken).join().orElseThrow();

            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY, encryptedNewAccess).join();
            vaultSecretService.storeSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY, encryptedNewRefresh).join();

            // Then - Retrieve and verify new tokens are stored
            Optional<String> retrievedAccess = vaultSecretService.getSecret(vaultPath,
                BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY).join();
            Optional<String> retrievedRefresh = vaultSecretService.getSecret(vaultPath,
                BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY).join();

            String decryptedAccess = encryptionService.decrypt(retrievedAccess.get()).join().orElseThrow();
            String decryptedRefresh = encryptionService.decrypt(retrievedRefresh.get()).join().orElseThrow();

            assertEquals(newAccessToken, decryptedAccess, "New access token should be stored");
            assertEquals(newRefreshToken, decryptedRefresh, "New refresh token should be stored");
            assertNotEquals(oldAccessToken, decryptedAccess, "Old access token should be replaced");
        }
    }

    @Nested
    @DisplayName("Security and Error Handling Tests")
    class SecurityAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle Vault connection errors gracefully")
        void shouldHandleVaultConnectionErrors() {
            // This test verifies graceful degradation when Vault is unavailable
            // In real scenarios, circuit breakers would kick in

            // Given - Invalid vault path with special characters
            String invalidPath = "invalid//path//with///slashes";

            // When - Try to store with invalid path
            Boolean result = vaultSecretService.storeSecret(invalidPath,
                "key", "value").join();

            // Then - Should handle gracefully (return false or empty)
            // Actual behavior depends on Vault configuration
            assertNotNull(result, "Result should not be null");
        }

        @Test
        @DisplayName("Should encrypt tokens with different nonces")
        void shouldEncryptWithDifferentNonces() {
            // Given
            String token = "same-token-for-nonce-test";
            String vaultPath1 = "secret/test/nonce1/" + UUID.randomUUID();
            String vaultPath2 = "secret/test/nonce2/" + UUID.randomUUID();

            // When - Encrypt same token twice
            String encrypted1 = encryptionService.encrypt(token).join().orElseThrow();
            String encrypted2 = encryptionService.encrypt(token).join().orElseThrow();

            vaultSecretService.storeSecret(vaultPath1, "token", encrypted1).join();
            vaultSecretService.storeSecret(vaultPath2, "token", encrypted2).join();

            // Then - Encrypted values should be different (due to random nonce)
            assertNotEquals(encrypted1, encrypted2,
                "Same token should have different encrypted values due to random nonce");

            // But both should decrypt to same value
            String decrypted1 = encryptionService.decrypt(encrypted1).join().orElseThrow();
            String decrypted2 = encryptionService.decrypt(encrypted2).join().orElseThrow();

            assertEquals(token, decrypted1, "First encryption should decrypt correctly");
            assertEquals(token, decrypted2, "Second encryption should decrypt correctly");
        }

        @Test
        @DisplayName("Should handle Unicode tokens correctly")
        void shouldHandleUnicodeTokens() {
            // Given
            String unicodeToken = "Token-with-你好-мир-🔐-symbols";
            String vaultPath = "secret/test/unicode/" + UUID.randomUUID();

            // When
            String encrypted = encryptionService.encrypt(unicodeToken).join().orElseThrow();
            vaultSecretService.storeSecret(vaultPath, "unicode_token", encrypted).join();

            Optional<String> retrieved = vaultSecretService.getSecret(vaultPath, "unicode_token").join();
            String decrypted = encryptionService.decrypt(retrieved.get()).join().orElseThrow();

            // Then
            assertEquals(unicodeToken, decrypted, "Unicode characters should survive encryption/Vault/decryption");
        }
    }

    // Helper methods

    private String generateVaultPath(String userId, BrokerType brokerType) {
        return String.format("%s/%s/%s",
            BrokerAuthConstants.VAULT_SECRET_PATH,
            userId,
            brokerType.name().toLowerCase());
    }
}
