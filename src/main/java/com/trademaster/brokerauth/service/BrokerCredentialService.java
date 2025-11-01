package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.dto.BrokerCredentials;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Broker Credential Service
 *
 * Retrieves OAuth session credentials from Vault for trading-service consumption.
 *
 * MANDATORY: Zero Trust Security - Rule #6
 * MANDATORY: Virtual Threads - Rule #12
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Caching for performance - Rule #16
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerCredentialService {

    private final BrokerSessionService sessionService;
    private final VaultSecretService vaultSecretService;
    private final EncryptionService encryptionService;

    /**
     * Retrieve broker credentials for trading operations
     *
     * MANDATORY: Virtual Threads for async operations - Rule #12
     * MANDATORY: Functional programming patterns - Rule #3
     * MANDATORY: Caching for performance - Rule #16
     *
     * Flow:
     * 1. Get active BrokerSession from database (contains vault_path)
     * 2. Retrieve encrypted tokens from Vault using vault_path
     * 3. Decrypt tokens using EncryptionService
     * 4. Return credentials to trading-service
     *
     * @param userId User identifier
     * @param brokerType Broker type (ZERODHA, UPSTOX, etc.)
     * @return CompletableFuture with Optional BrokerCredentials
     */
    @Cacheable(value = "broker-credentials", key = "#userId + ':' + #brokerType", unless = "#result == null")
    public CompletableFuture<Optional<BrokerCredentials>> getCredentials(String userId, BrokerType brokerType) {
        log.info("Retrieving credentials for user: {} broker: {}", userId, brokerType);

        return CompletableFuture.supplyAsync(
            () -> sessionService.getActiveSession(userId, brokerType),
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
        ).thenCompose(sessionOpt -> sessionOpt
            .map(this::retrieveAndDecryptTokens)
            .orElse(CompletableFuture.completedFuture(Optional.empty())))
        .handle(this::handleCredentialRetrieval);
    }

    /**
     * Validate if credentials are still active and not expired
     *
     * MANDATORY: Virtual Threads - Rule #12
     * MANDATORY: Functional programming - Rule #3
     *
     * @param userId User identifier
     * @param brokerType Broker type
     * @return CompletableFuture with validation result
     */
    public CompletableFuture<Boolean> validateCredentials(String userId, BrokerType brokerType) {
        log.debug("Validating credentials for user: {} broker: {}", userId, brokerType);

        return CompletableFuture.supplyAsync(
            () -> sessionService.getActiveSession(userId, brokerType),
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
        ).thenApply(sessionOpt -> sessionOpt
            .map(BrokerSession::isActive)
            .orElse(false))
        .handle((result, throwable) -> {
            if (throwable != null) {
                    log.error("Credential validation failed for user: {} broker: {}",
                        userId, brokerType, throwable);
                    return false;
                }
                return result;
            });
    }

    /**
     * Retrieve and decrypt tokens from Vault
     *
     * MANDATORY: Virtual Threads - Rule #12
     * MANDATORY: Functional programming - Rule #3
     * MANDATORY: Security by default - Rule #6
     *
     * @param session BrokerSession containing vault_path
     * @return CompletableFuture with Optional BrokerCredentials
     */
    private CompletableFuture<Optional<BrokerCredentials>> retrieveAndDecryptTokens(BrokerSession session) {
        String vaultPath = session.getVaultPath();

        return retrieveEncryptedTokensFromVault(vaultPath)
            .thenCompose(encryptedTokens -> decryptTokens(encryptedTokens, session))
            .handle((credentials, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to retrieve/decrypt tokens from Vault - path: {}", vaultPath, throwable);
                    return Optional.empty();
                }
                return Optional.of(credentials);
            });
    }

    /**
     * Retrieve encrypted tokens from Vault
     *
     * MANDATORY: Virtual Threads - Rule #12
     * MANDATORY: Functional programming - Rule #3
     *
     * @param vaultPath Vault path for token storage
     * @return CompletableFuture with EncryptedTokens
     */
    private CompletableFuture<EncryptedTokens> retrieveEncryptedTokensFromVault(String vaultPath) {
        CompletableFuture<Optional<String>> accessTokenFuture =
            vaultSecretService.getSecret(vaultPath, BrokerAuthConstants.VAULT_ACCESS_TOKEN_KEY);

        CompletableFuture<Optional<String>> refreshTokenFuture =
            vaultSecretService.getSecret(vaultPath, BrokerAuthConstants.VAULT_REFRESH_TOKEN_KEY);

        return accessTokenFuture.thenCombine(refreshTokenFuture, (accessToken, refreshToken) ->
            new EncryptedTokens(
                accessToken.orElseThrow(() -> new RuntimeException("Access token not found in Vault")),
                refreshToken.orElse(null)
            ));
    }

    /**
     * Decrypt tokens using EncryptionService
     *
     * MANDATORY: Virtual Threads - Rule #12
     * MANDATORY: Functional programming - Rule #3
     * MANDATORY: Security by default - Rule #6
     *
     * @param encryptedTokens Encrypted tokens from Vault
     * @param session BrokerSession metadata
     * @return CompletableFuture with BrokerCredentials
     */
    private CompletableFuture<BrokerCredentials> decryptTokens(
            EncryptedTokens encryptedTokens, BrokerSession session) {

        CompletableFuture<Optional<String>> decryptedAccess =
            encryptionService.decrypt(encryptedTokens.accessToken());

        CompletableFuture<Optional<String>> decryptedRefresh =
            encryptedTokens.refreshToken() != null
                ? encryptionService.decrypt(encryptedTokens.refreshToken())
                : CompletableFuture.completedFuture(Optional.empty());

        return decryptedAccess.thenCombine(decryptedRefresh, (access, refresh) ->
            new BrokerCredentials(
                session.getSessionId(),
                session.getUserId(),
                session.getBrokerType(),
                access.orElseThrow(() -> new RuntimeException("Failed to decrypt access token")),
                refresh.orElse(null),
                session.getExpiresAt(),
                session.getCreatedAt()
            ));
    }

    /**
     * Handle credential retrieval errors
     *
     * MANDATORY: Error handling patterns - Rule #11
     *
     * @param credentials Retrieved credentials (may be empty)
     * @param throwable Exception if any
     * @return Optional credentials
     */
    private Optional<BrokerCredentials> handleCredentialRetrieval(
            Optional<BrokerCredentials> credentials, Throwable throwable) {

        if (throwable != null) {
            log.error("Credential retrieval failed", throwable);
            return Optional.empty();
        }

        credentials.ifPresentOrElse(
            cred -> log.info("Credentials retrieved successfully for user: {} broker: {}",
                cred.userId(), cred.brokerType()),
            () -> log.warn("No credentials found or session expired")
        );

        return credentials;
    }

    /**
     * Internal record for encrypted tokens
     *
     * MANDATORY: Immutable records - Rule #9
     */
    private record EncryptedTokens(String accessToken, String refreshToken) {}
}
