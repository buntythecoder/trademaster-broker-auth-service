package com.trademaster.brokerauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Credential Encryption Service
 * 
 * MANDATORY: AES-256-GCM encryption for all sensitive data - Rule #23
 * MANDATORY: Virtual Threads for performance - Rule #12
 * MANDATORY: Functional error handling - Rule #11
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialEncryptionService {
    
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    
    @Value("${broker.encryption.master-key:}")
    private String masterKeyBase64;
    
    /**
     * Encrypt sensitive credential data
     * 
     * MANDATORY: Result type for error handling - Rule #11
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<Optional<String>> encryptCredential(String plainText) {
        return CompletableFuture
            .supplyAsync(() -> performEncryption(plainText), 
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleEncryptionResult);
    }
    
    /**
     * Decrypt sensitive credential data
     * 
     * MANDATORY: Result type for error handling - Rule #11
     * MANDATORY: Virtual Threads - Rule #12
     */
    public CompletableFuture<Optional<String>> decryptCredential(String encryptedText) {
        return CompletableFuture
            .supplyAsync(() -> performDecryption(encryptedText),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleDecryptionResult);
    }
    
    /**
     * Generate new encryption key for testing/setup
     * 
     * MANDATORY: Secure key generation - Rule #23
     */
    public String generateNewEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
            keyGenerator.init(256); // AES-256
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            log.error("Failed to generate encryption key", e);
            throw new RuntimeException("Key generation failed", e);
        }
    }
    
    /**
     * Perform actual encryption operation
     * 
     * MANDATORY: Pattern matching - Rule #14
     * MANDATORY: No if-else - Rule #3
     */
    private String performEncryption(String plainText) {
        return switch (validateEncryptionInputs(plainText)) {
            case VALID -> executeEncryption(plainText);
            case EMPTY_PLAIN_TEXT -> throw new IllegalArgumentException("Plain text cannot be empty");
            case NO_MASTER_KEY -> throw new IllegalStateException("Master key not configured");
            case EMPTY_ENCRYPTED_TEXT -> throw new IllegalArgumentException("Invalid validation state");
        };
    }
    
    private String performDecryption(String encryptedText) {
        return switch (validateDecryptionInputs(encryptedText)) {
            case VALID -> executeDecryption(encryptedText);
            case EMPTY_ENCRYPTED_TEXT -> throw new IllegalArgumentException("Encrypted text cannot be empty");
            case NO_MASTER_KEY -> throw new IllegalStateException("Master key not configured");
            case EMPTY_PLAIN_TEXT -> throw new IllegalArgumentException("Invalid validation state");
        };
    }
    
    private String executeEncryption(String plainText) {
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(
                Base64.getDecoder().decode(masterKeyBase64), ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            
            // Encrypt the data
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + encrypted data
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption operation failed", e);
        }
    }
    
    private String executeDecryption(String encryptedText) {
        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(
                Base64.getDecoder().decode(masterKeyBase64), ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            // Decrypt the data
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption operation failed", e);
        }
    }
    
    private ValidationResult validateEncryptionInputs(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return ValidationResult.EMPTY_PLAIN_TEXT;
        }
        if (masterKeyBase64 == null || masterKeyBase64.trim().isEmpty()) {
            return ValidationResult.NO_MASTER_KEY;
        }
        return ValidationResult.VALID;
    }
    
    private ValidationResult validateDecryptionInputs(String encryptedText) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return ValidationResult.EMPTY_ENCRYPTED_TEXT;
        }
        if (masterKeyBase64 == null || masterKeyBase64.trim().isEmpty()) {
            return ValidationResult.NO_MASTER_KEY;
        }
        return ValidationResult.VALID;
    }
    
    private Optional<String> handleEncryptionResult(String result, Throwable throwable) {
        if (throwable != null) {
            log.error("Encryption failed", throwable);
            return Optional.empty();
        }
        return Optional.of(result);
    }
    
    private Optional<String> handleDecryptionResult(String result, Throwable throwable) {
        if (throwable != null) {
            log.error("Decryption failed", throwable);
            return Optional.empty();
        }
        return Optional.of(result);
    }
    
    private enum ValidationResult {
        VALID,
        EMPTY_PLAIN_TEXT,
        EMPTY_ENCRYPTED_TEXT,
        NO_MASTER_KEY
    }
}