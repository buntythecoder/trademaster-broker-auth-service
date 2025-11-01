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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * AES-256-GCM Encryption Service for Broker Token Security
 *
 * MANDATORY: Rule #23 - Security by Default (Zero Trust Architecture)
 * MANDATORY: Rule #12 - Virtual Threads for async operations
 * MANDATORY: Rule #3 - Functional Programming with Result types
 * MANDATORY: Rule #11 - No try-catch, functional error handling
 *
 * Security Features:
 * - AES-256-GCM authenticated encryption
 * - Random 96-bit nonce for each encryption
 * - 128-bit authentication tag
 * - Base64 encoding for storage
 * - Thread-safe SecureRandom
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

    @Value("${trademaster.security.encryption.key}")
    private String masterKeyBase64;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    private static final int AES_KEY_SIZE = 256; // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt plaintext using AES-256-GCM
     *
     * MANDATORY: Rule #12 - Virtual Threads for async operations
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param plaintext Text to encrypt
     * @return CompletableFuture with Base64-encoded encrypted data
     */
    public CompletableFuture<Optional<String>> encrypt(String plaintext) {
        System.out.println("=== DEBUG encrypt() - CALLED with plaintext length: " + (plaintext == null ? "NULL" : plaintext.length()));
        return CompletableFuture
            .supplyAsync(() -> performEncryption(plaintext),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleEncryptionResult);
    }

    /**
     * Decrypt ciphertext using AES-256-GCM
     *
     * MANDATORY: Rule #12 - Virtual Threads for async operations
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @param ciphertext Base64-encoded encrypted data
     * @return CompletableFuture with decrypted plaintext
     */
    public CompletableFuture<Optional<String>> decrypt(String ciphertext) {
        return CompletableFuture
            .supplyAsync(() -> performDecryption(ciphertext),
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleDecryptionResult);
    }

    /**
     * Generate new AES-256 encryption key
     *
     * MANDATORY: Rule #3 - Functional Programming
     *
     * @return Base64-encoded encryption key
     */
    public CompletableFuture<Optional<String>> generateKey() {
        return CompletableFuture
            .supplyAsync(this::performKeyGeneration,
                        Executors.newVirtualThreadPerTaskExecutor())
            .handle(this::handleKeyGenerationResult);
    }

    private Optional<String> performEncryption(String plaintext) {
        return switch (validateEncryptionInput(plaintext)) {
            case VALID -> executeEncryption(plaintext);
            case INVALID_INPUT -> throw new IllegalArgumentException("Plaintext cannot be empty");
        };
    }

    private Optional<String> performDecryption(String ciphertext) {
        return switch (validateDecryptionInput(ciphertext)) {
            case VALID -> executeDecryption(ciphertext);
            case INVALID_INPUT -> throw new IllegalArgumentException("Ciphertext cannot be empty");
        };
    }

    private Optional<String> performKeyGeneration() {
        return executeKeyGeneration();
    }

    private Optional<String> executeEncryption(String plaintext) {
        try {
            System.out.println("=== DEBUG executeEncryption - CALLED! plaintext length: " + plaintext.length());
            System.out.println("=== DEBUG executeEncryption - masterKeyBase64: " + (masterKeyBase64 == null ? "NULL" : "PRESENT (length: " + masterKeyBase64.length() + ")"));
            log.debug("executeEncryption - Starting encryption, plaintext length: {}", plaintext.length());
            log.debug("executeEncryption - masterKeyBase64: {}", masterKeyBase64 == null ? "NULL" : "PRESENT (length: " + masterKeyBase64.length() + ")");

            // Generate random nonce
            byte[] nonce = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(nonce);

            // Get secret key
            SecretKey secretKey = getSecretKey();
            log.debug("executeEncryption - SecretKey obtained successfully");

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextBytes = cipher.doFinal(plaintextBytes);

            // Combine nonce + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(nonce.length + ciphertextBytes.length);
            byteBuffer.put(nonce);
            byteBuffer.put(ciphertextBytes);

            // Encode to Base64
            String encrypted = Base64.getEncoder().encodeToString(byteBuffer.array());

            log.debug("Successfully encrypted data, length: {}", encrypted.length());
            return Optional.of(encrypted);

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption operation failed", e);
        }
    }

    private Optional<String> executeDecryption(String ciphertext) {
        try {
            // Decode from Base64
            byte[] decodedBytes = Base64.getDecoder().decode(ciphertext);

            // Extract nonce and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] nonce = new byte[GCM_IV_LENGTH];
            byteBuffer.get(nonce);
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);

            // Get secret key
            SecretKey secretKey = getSecretKey();

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
            String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

            log.debug("Successfully decrypted data");
            return Optional.of(plaintext);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption operation failed", e);
        }
    }

    private Optional<String> executeKeyGeneration() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, secureRandom);
            SecretKey secretKey = keyGenerator.generateKey();

            String keyBase64 = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            log.info("Generated new AES-256 encryption key");
            return Optional.of(keyBase64);

        } catch (Exception e) {
            log.error("Key generation failed", e);
            throw new RuntimeException("Key generation failed", e);
        }
    }

    private SecretKey getSecretKey() {
        System.out.println("=== DEBUG getSecretKey - masterKeyBase64: " + (masterKeyBase64 == null ? "NULL" : "PRESENT (length: " + masterKeyBase64.length() + ")"));
        log.debug("getSecretKey - masterKeyBase64 value: {}", masterKeyBase64 == null ? "NULL" : masterKeyBase64.substring(0, Math.min(10, masterKeyBase64.length())) + "...");

        if (masterKeyBase64 == null || masterKeyBase64.trim().isEmpty()) {
            System.out.println("=== ERROR: masterKeyBase64 is NULL or EMPTY!");
            log.error("getSecretKey - masterKeyBase64 is null or empty!");
            throw new IllegalStateException("Encryption key not configured - trademaster.security.encryption.key must be set");
        }

        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        System.out.println("=== DEBUG getSecretKey - Decoded key length: " + keyBytes.length + " bytes (expected: 32)");
        log.debug("getSecretKey - Decoded key length: {} bytes (expected: 32 for AES-256)", keyBytes.length);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // Validation methods using pattern matching

    private EncryptionValidation validateEncryptionInput(String plaintext) {
        if (plaintext == null || plaintext.trim().isEmpty()) {
            return EncryptionValidation.INVALID_INPUT;
        }
        return EncryptionValidation.VALID;
    }

    private DecryptionValidation validateDecryptionInput(String ciphertext) {
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            return DecryptionValidation.INVALID_INPUT;
        }
        return DecryptionValidation.VALID;
    }

    // Result handlers

    private Optional<String> handleEncryptionResult(Optional<String> result, Throwable throwable) {
        return Optional.ofNullable(throwable)
            .map(t -> {
                log.error("Encryption operation failed", t);
                return Optional.<String>empty();
            })
            .orElse(result);
    }

    private Optional<String> handleDecryptionResult(Optional<String> result, Throwable throwable) {
        return Optional.ofNullable(throwable)
            .map(t -> {
                log.error("Decryption operation failed", t);
                return Optional.<String>empty();
            })
            .orElse(result);
    }

    private Optional<String> handleKeyGenerationResult(Optional<String> result, Throwable throwable) {
        return Optional.ofNullable(throwable)
            .map(t -> {
                log.error("Key generation operation failed", t);
                return Optional.<String>empty();
            })
            .orElse(result);
    }

    // Validation enums

    private enum EncryptionValidation {
        VALID, INVALID_INPUT
    }

    private enum DecryptionValidation {
        VALID, INVALID_INPUT
    }
}
