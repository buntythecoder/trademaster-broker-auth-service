package com.trademaster.brokerauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * HashiCorp Vault Secret Management Service
 * 
 * MANDATORY: Secure secret storage in production - Rule #23
 * MANDATORY: Virtual Threads for performance - Rule #12
 * MANDATORY: Pattern matching for error handling - Rule #14
 */
@Service
@ConditionalOnProperty(
    prefix = "spring.cloud.vault",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class VaultSecretService {
    
    private final VaultTemplate vaultTemplate;
    
    @Value("${spring.cloud.vault.kv.backend:secret}")
    private String kvBackend;
    
    /**
     * Store secret in Vault
     * 
     * MANDATORY: Secure secret storage - Rule #23
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<Boolean> storeSecret(String path, String key, String value) {
        return CompletableFuture
            .supplyAsync(() -> performSecretStorage(path, key, value),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleSecretOperationResult);
    }
    
    /**
     * Store multiple secrets in Vault
     * 
     * MANDATORY: Batch operations for efficiency - Rule #22
     */
    public CompletableFuture<Boolean> storeSecrets(String path, Map<String, String> secrets) {
        return CompletableFuture
            .supplyAsync(() -> performBatchSecretStorage(path, secrets),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleSecretOperationResult);
    }
    
    /**
     * Retrieve secret from Vault
     * 
     * MANDATORY: Secure secret retrieval - Rule #23
     */
    public CompletableFuture<Optional<String>> getSecret(String path, String key) {
        return CompletableFuture
            .supplyAsync(() -> performSecretRetrieval(path, key),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleSecretRetrievalResult);
    }
    
    /**
     * Retrieve all secrets from path
     * 
     * MANDATORY: Batch retrieval for efficiency - Rule #22
     */
    public CompletableFuture<Map<String, String>> getAllSecrets(String path) {
        return CompletableFuture
            .supplyAsync(() -> performBatchSecretRetrieval(path),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleBatchRetrievalResult);
    }
    
    /**
     * Delete secret from Vault
     * 
     * MANDATORY: Secure secret deletion - Rule #23
     */
    public CompletableFuture<Boolean> deleteSecret(String path) {
        return CompletableFuture
            .supplyAsync(() -> performSecretDeletion(path),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleSecretOperationResult);
    }
    
    /**
     * Check if secret exists in Vault
     * 
     * MANDATORY: Existence checking without exposure - Rule #23
     */
    public CompletableFuture<Boolean> secretExists(String path) {
        return CompletableFuture
            .supplyAsync(() -> performSecretExistenceCheck(path),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleSecretOperationResult);
    }
    
    private boolean performSecretStorage(String path, String key, String value) {
        return switch (validateSecretStorageInputs(path, key, value)) {
            case VALID -> executeSecretStorage(path, key, value);
            case INVALID_PATH -> throw new IllegalArgumentException("Secret path cannot be empty");
            case INVALID_KEY -> throw new IllegalArgumentException("Secret key cannot be empty");
            case INVALID_VALUE -> throw new IllegalArgumentException("Secret value cannot be empty");
        };
    }
    
    private boolean performBatchSecretStorage(String path, Map<String, String> secrets) {
        return switch (validateBatchStorageInputs(path, secrets)) {
            case VALID -> executeBatchSecretStorage(path, secrets);
            case INVALID_PATH -> throw new IllegalArgumentException("Secret path cannot be empty");
            case INVALID_SECRETS -> throw new IllegalArgumentException("Secrets map cannot be empty");
        };
    }
    
    private Optional<String> performSecretRetrieval(String path, String key) {
        return switch (validateSecretRetrievalInputs(path, key)) {
            case VALID -> executeSecretRetrieval(path, key);
            case INVALID_PATH -> throw new IllegalArgumentException("Secret path cannot be empty");
            case INVALID_KEY -> throw new IllegalArgumentException("Secret key cannot be empty");
        };
    }
    
    private Map<String, String> performBatchSecretRetrieval(String path) {
        return switch (validatePathInput(path)) {
            case VALID -> executeBatchSecretRetrieval(path);
            case INVALID_PATH -> throw new IllegalArgumentException("Secret path cannot be empty");
        };
    }
    
    private boolean performSecretDeletion(String path) {
        return switch (validatePathInput(path)) {
            case VALID -> executeSecretDeletion(path);
            case INVALID_PATH -> throw new IllegalArgumentException("Secret path cannot be empty");
        };
    }
    
    private boolean performSecretExistenceCheck(String path) {
        return switch (validatePathInput(path)) {
            case VALID -> executeSecretExistenceCheck(path);
            case INVALID_PATH -> throw new IllegalArgumentException("Secret path cannot be empty");
        };
    }
    
    private boolean executeSecretStorage(String path, String key, String value) {
        try {
            String vaultPath = buildVaultPath(path);
            
            // Get existing secrets to preserve them - Rule #3 Functional Programming
            Map<String, Object> existingSecrets = retrieveExistingSecrets(vaultPath);
            
            // Add/update the new secret
            existingSecrets.put(key, value);
            
            // Store back to Vault
            vaultTemplate.write(vaultPath, existingSecrets);
            log.info("Secret stored successfully at path: {} key: {}", vaultPath, key);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to store secret at path: {} key: {}", path, key, e);
            throw new RuntimeException("Secret storage failed", e);
        }
    }
    
    private boolean executeBatchSecretStorage(String path, Map<String, String> secrets) {
        try {
            String vaultPath = buildVaultPath(path);
            
            // Convert to Object map for Vault
            Map<String, Object> vaultSecrets = new HashMap<>(secrets);
            
            vaultTemplate.write(vaultPath, vaultSecrets);
            log.info("Batch secrets stored successfully at path: {} count: {}", 
                vaultPath, secrets.size());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to store batch secrets at path: {}", path, e);
            throw new RuntimeException("Batch secret storage failed", e);
        }
    }
    
    private Optional<String> executeSecretRetrieval(String path, String key) {
        try {
            String vaultPath = buildVaultPath(path);
            return Optional.ofNullable(vaultTemplate.read(vaultPath))
                .filter(response -> response.getData() != null)
                .map(VaultResponse::getData)
                .map(data -> data.get(key))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .or(() -> {
                    log.debug("No data or key '{}' not found at path: {}", key, vaultPath);
                    return Optional.empty();
                });
        } catch (Exception e) {
            log.error("Failed to retrieve secret at path: {} key: {}", path, key, e);
            throw new RuntimeException("Secret retrieval failed", e);
        }
    }
    
    private Map<String, String> executeBatchSecretRetrieval(String path) {
        try {
            String vaultPath = buildVaultPath(path);
            return Optional.ofNullable(vaultTemplate.read(vaultPath))
                .filter(response -> response.getData() != null)
                .map(VaultResponse::getData)
                .map(this::convertToStringMap)
                .orElseGet(() -> {
                    log.debug("No data found at path: {}", vaultPath);
                    return Map.of();
                });
        } catch (Exception e) {
            log.error("Failed to retrieve batch secrets at path: {}", path, e);
            throw new RuntimeException("Batch secret retrieval failed", e);
        }
    }

    /**
     * Functional conversion of vault data to string map - Rule #3 Functional Programming
     */
    private Map<String, String> convertToStringMap(Map<String, Object> vaultData) {
        return vaultData.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toString()
            ));
    }
    
    private boolean executeSecretDeletion(String path) {
        try {
            String vaultPath = buildVaultPath(path);
            vaultTemplate.delete(vaultPath);
            log.info("Secret deleted successfully at path: {}", vaultPath);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to delete secret at path: {}", path, e);
            throw new RuntimeException("Secret deletion failed", e);
        }
    }
    
    private boolean executeSecretExistenceCheck(String path) {
        try {
            String vaultPath = buildVaultPath(path);
            VaultResponse response = vaultTemplate.read(vaultPath);
            return response != null && response.getData() != null && !response.getData().isEmpty();
            
        } catch (Exception e) {
            log.debug("Secret existence check failed for path: {}", path, e);
            return false;
        }
    }
    
    private String buildVaultPath(String path) {
        // Functional path building with pattern matching - Rule #14 Pattern Matching
        return Optional.of(path)
            .filter(p -> p.startsWith(kvBackend + "/"))
            .orElse(kvBackend + "/" + path);
    }
    
    // Validation methods using pattern matching
    
    private StorageValidation validateSecretStorageInputs(String path, String key, String value) {
        if (path == null || path.trim().isEmpty()) return StorageValidation.INVALID_PATH;
        if (key == null || key.trim().isEmpty()) return StorageValidation.INVALID_KEY;
        if (value == null || value.trim().isEmpty()) return StorageValidation.INVALID_VALUE;
        return StorageValidation.VALID;
    }
    
    private BatchStorageValidation validateBatchStorageInputs(String path, Map<String, String> secrets) {
        if (path == null || path.trim().isEmpty()) return BatchStorageValidation.INVALID_PATH;
        if (secrets == null || secrets.isEmpty()) return BatchStorageValidation.INVALID_SECRETS;
        return BatchStorageValidation.VALID;
    }
    
    private RetrievalValidation validateSecretRetrievalInputs(String path, String key) {
        if (path == null || path.trim().isEmpty()) return RetrievalValidation.INVALID_PATH;
        if (key == null || key.trim().isEmpty()) return RetrievalValidation.INVALID_KEY;
        return RetrievalValidation.VALID;
    }
    
    private PathValidation validatePathInput(String path) {
        if (path == null || path.trim().isEmpty()) return PathValidation.INVALID_PATH;
        return PathValidation.VALID;
    }
    
    // Result handlers
    
    private Boolean handleSecretOperationResult(Boolean result, Throwable throwable) {
        return Optional.ofNullable(throwable)
            .map(t -> {
                log.error("Vault operation failed", t);
                return false;
            })
            .orElse(result);
    }

    private Optional<String> handleSecretRetrievalResult(Optional<String> result, Throwable throwable) {
        return Optional.ofNullable(throwable)
            .map(t -> {
                log.error("Vault retrieval failed", t);
                return Optional.<String>empty();
            })
            .orElse(result);
    }

    private Map<String, String> handleBatchRetrievalResult(Map<String, String> result, Throwable throwable) {
        return Optional.ofNullable(throwable)
            .map(t -> {
                log.error("Vault batch retrieval failed", t);
                return Map.<String, String>of();
            })
            .orElse(result);
    }

    /**
     * Functional existing secrets retrieval - Rule #3 Functional Programming
     */
    private Map<String, Object> retrieveExistingSecrets(String vaultPath) {
        try {
            return Optional.ofNullable(vaultTemplate.read(vaultPath))
                .filter(response -> response.getData() != null)
                .map(VaultResponse::getData)
                .map(HashMap::new)
                .orElse(new HashMap<>());
        } catch (Exception e) {
            log.debug("No existing secrets found at path: {}", vaultPath);
            return new HashMap<>();
        }
    }
    
    // Validation enums
    
    private enum StorageValidation {
        VALID, INVALID_PATH, INVALID_KEY, INVALID_VALUE
    }
    
    private enum BatchStorageValidation {
        VALID, INVALID_PATH, INVALID_SECRETS
    }
    
    private enum RetrievalValidation {
        VALID, INVALID_PATH, INVALID_KEY
    }
    
    private enum PathValidation {
        VALID, INVALID_PATH
    }
}