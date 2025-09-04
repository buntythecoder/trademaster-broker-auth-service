package com.trademaster.brokerauth.service;

import com.trademaster.brokerauth.entity.BrokerAccount;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.repository.BrokerAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Credential Management Service
 * 
 * MANDATORY: Secure credential storage with encryption - Rule #23
 * MANDATORY: Virtual Threads for performance - Rule #12
 * MANDATORY: Transactional data operations - Rule #26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialManagementService {
    
    private final BrokerAccountRepository brokerAccountRepository;
    private final CredentialEncryptionService encryptionService;
    
    /**
     * Store encrypted broker credentials for user
     * 
     * MANDATORY: Functional programming patterns - Rule #3
     * MANDATORY: Virtual Threads - Rule #12
     */
    @Transactional
    public CompletableFuture<Optional<BrokerAccount>> storeCredentials(
            String userId, BrokerType brokerType, String brokerUserId, 
            String password, String apiKey, String apiSecret, String totpSecret) {
        
        return CompletableFuture
            .supplyAsync(() -> createEncryptedAccount(userId, brokerType, brokerUserId, 
                        password, apiKey, apiSecret, totpSecret),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleCredentialStorage);
    }
    
    /**
     * Retrieve and decrypt broker credentials
     * 
     * MANDATORY: Secure data access - Rule #23
     */
    @Transactional(readOnly = true)
    public CompletableFuture<Optional<BrokerAccount>> getDecryptedCredentials(
            String userId, BrokerType brokerType) {
        
        return CompletableFuture
            .supplyAsync(() -> brokerAccountRepository.findByUserIdAndBrokerType(userId, brokerType),
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(this::decryptAccountCredentials);
    }
    
    /**
     * Update broker credentials with encryption
     * 
     * MANDATORY: Secure updates - Rule #23
     */
    @Transactional
    public CompletableFuture<Boolean> updateCredentials(
            String userId, BrokerType brokerType, String password, 
            String apiKey, String apiSecret, String totpSecret) {
        
        return CompletableFuture
            .supplyAsync(() -> brokerAccountRepository.findByUserIdAndBrokerType(userId, brokerType),
                        Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(accountOpt -> accountOpt.map(account -> 
                updateAccountCredentials(account, password, apiKey, apiSecret, totpSecret))
                .orElse(CompletableFuture.completedFuture(false)));
    }
    
    /**
     * Deactivate broker account
     * 
     * MANDATORY: Security operations - Rule #23
     */
    @Transactional
    public CompletableFuture<Boolean> deactivateAccount(String userId, BrokerType brokerType) {
        return CompletableFuture
            .supplyAsync(() -> brokerAccountRepository
                .deactivateAccount(userId, brokerType, LocalDateTime.now()) > 0,
                Executors.newVirtualThreadPerTaskExecutor())
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to deactivate account for user: {} broker: {}", 
                        userId, brokerType, throwable);
                    return false;
                }
                log.info("Account deactivated for user: {} broker: {}", userId, brokerType);
                return result;
            });
    }
    
    /**
     * Get all active accounts for user
     * 
     * MANDATORY: User data access - Rule #23
     */
    @Transactional(readOnly = true)
    public CompletableFuture<List<BrokerAccount>> getUserActiveAccounts(String userId) {
        return CompletableFuture
            .supplyAsync(() -> brokerAccountRepository.findAllActiveByUserId(userId),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle((accounts, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to retrieve accounts for user: {}", userId, throwable);
                    return List.of();
                }
                return accounts;
            });
    }
    
    private BrokerAccount createEncryptedAccount(
            String userId, BrokerType brokerType, String brokerUserId,
            String password, String apiKey, String apiSecret, String totpSecret) {
        
        try {
            BrokerAccount account = new BrokerAccount();
            account.setUserId(userId);
            account.setBrokerType(brokerType);
            account.setBrokerUserId(brokerUserId);
            account.setIsActive(true);
            
            // Encrypt credentials asynchronously
            Optional<String> encryptedPassword = password != null ? 
                encryptionService.encryptCredential(password).join() : Optional.empty();
            Optional<String> encryptedApiKey = apiKey != null ?
                encryptionService.encryptCredential(apiKey).join() : Optional.empty();
            Optional<String> encryptedApiSecret = apiSecret != null ?
                encryptionService.encryptCredential(apiSecret).join() : Optional.empty();
            Optional<String> encryptedTotpSecret = totpSecret != null ?
                encryptionService.encryptCredential(totpSecret).join() : Optional.empty();
            
            account.setEncryptedPassword(encryptedPassword.orElse(null));
            account.setEncryptedApiKey(encryptedApiKey.orElse(null));
            account.setEncryptedApiSecret(encryptedApiSecret.orElse(null));
            account.setEncryptedTotpSecret(encryptedTotpSecret.orElse(null));
            
            return brokerAccountRepository.save(account);
            
        } catch (Exception e) {
            log.error("Failed to create encrypted account for user: {} broker: {}", 
                userId, brokerType, e);
            throw new RuntimeException("Credential storage failed", e);
        }
    }
    
    private CompletableFuture<Optional<BrokerAccount>> decryptAccountCredentials(
            Optional<BrokerAccount> accountOpt) {
        
        if (accountOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        BrokerAccount account = accountOpt.get();
        
        return CompletableFuture.allOf(
            decryptField(account.getEncryptedPassword()),
            decryptField(account.getEncryptedApiKey()),
            decryptField(account.getEncryptedApiSecret()),
            decryptField(account.getEncryptedTotpSecret())
        ).thenApply(v -> Optional.of(account));
    }
    
    private CompletableFuture<Boolean> updateAccountCredentials(
            BrokerAccount account, String password, String apiKey, 
            String apiSecret, String totpSecret) {
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    if (password != null) {
                        Optional<String> encrypted = encryptionService.encryptCredential(password).join();
                        account.setEncryptedPassword(encrypted.orElse(null));
                    }
                    if (apiKey != null) {
                        Optional<String> encrypted = encryptionService.encryptCredential(apiKey).join();
                        account.setEncryptedApiKey(encrypted.orElse(null));
                    }
                    if (apiSecret != null) {
                        Optional<String> encrypted = encryptionService.encryptCredential(apiSecret).join();
                        account.setEncryptedApiSecret(encrypted.orElse(null));
                    }
                    if (totpSecret != null) {
                        Optional<String> encrypted = encryptionService.encryptCredential(totpSecret).join();
                        account.setEncryptedTotpSecret(encrypted.orElse(null));
                    }
                    
                    brokerAccountRepository.save(account);
                    return true;
                    
                } catch (Exception e) {
                    log.error("Failed to update credentials for account: {}", account.getId(), e);
                    return false;
                }
            }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    private CompletableFuture<Optional<String>> decryptField(String encryptedField) {
        if (encryptedField == null || encryptedField.trim().isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return encryptionService.decryptCredential(encryptedField);
    }
    
    private Optional<BrokerAccount> handleCredentialStorage(BrokerAccount account, Throwable throwable) {
        if (throwable != null) {
            log.error("Credential storage failed", throwable);
            return Optional.empty();
        }
        log.info("Credentials stored successfully for user: {} broker: {}", 
            account.getUserId(), account.getBrokerType());
        return Optional.of(account);
    }
}