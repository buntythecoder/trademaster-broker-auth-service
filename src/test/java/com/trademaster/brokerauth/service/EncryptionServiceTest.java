package com.trademaster.brokerauth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for EncryptionService
 *
 * MANDATORY: Rule #20 - >80% test coverage with functional test builders
 * MANDATORY: Rule #12 - Test Virtual Threads functionality
 * MANDATORY: Rule #3 - Functional programming patterns
 *
 * Tests cover:
 * - AES-256-GCM encryption/decryption
 * - Input validation
 * - Error handling
 * - Key generation
 * - Security properties (nonce randomness, authentication)
 * - Virtual Threads execution
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@DisplayName("EncryptionService Unit Tests")
class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private static final String TEST_MASTER_KEY = generateTestKey();

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        // Inject test master key using reflection
        ReflectionTestUtils.setField(encryptionService, "masterKeyBase64", TEST_MASTER_KEY);
    }

    @Nested
    @DisplayName("Encryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("Should successfully encrypt plaintext")
        void shouldEncryptPlaintext() {
            // Given
            String plaintext = "test-access-token-123456";

            // When
            CompletableFuture<Optional<String>> result = encryptionService.encrypt(plaintext);

            // Then
            assertDoesNotThrow(() -> {
                Optional<String> encrypted = result.join();
                assertTrue(encrypted.isPresent(), "Encryption should return a value");
                assertNotEquals(plaintext, encrypted.get(), "Encrypted text should differ from plaintext");
                assertIsValidBase64(encrypted.get(), "Encrypted output should be Base64 encoded");
            });
        }

        @Test
        @DisplayName("Should reject null plaintext")
        void shouldRejectNullPlaintext() {
            // When
            CompletableFuture<Optional<String>> result = encryptionService.encrypt(null);

            // Then
            Optional<String> encrypted = result.join();
            assertTrue(encrypted.isEmpty(), "Encryption with null input should return empty Optional");
        }

        @Test
        @DisplayName("Should reject empty plaintext")
        void shouldRejectEmptyPlaintext() {
            // When
            CompletableFuture<Optional<String>> result = encryptionService.encrypt("");

            // Then
            Optional<String> encrypted = result.join();
            assertTrue(encrypted.isEmpty(), "Encryption with empty input should return empty Optional");
        }

        @Test
        @DisplayName("Should reject whitespace-only plaintext")
        void shouldRejectWhitespaceOnlyPlaintext() {
            // When
            CompletableFuture<Optional<String>> result = encryptionService.encrypt("   ");

            // Then
            Optional<String> encrypted = result.join();
            assertTrue(encrypted.isEmpty(), "Encryption with whitespace-only input should return empty Optional");
        }

        @Test
        @DisplayName("Should encrypt long plaintext successfully")
        void shouldEncryptLongPlaintext() {
            // Given
            String longPlaintext = "a".repeat(10000); // 10KB of text

            // When
            CompletableFuture<Optional<String>> result = encryptionService.encrypt(longPlaintext);

            // Then
            Optional<String> encrypted = result.join();
            assertTrue(encrypted.isPresent(), "Long plaintext should encrypt successfully");
            assertIsValidBase64(encrypted.get(), "Encrypted output should be Base64 encoded");
        }

        @Test
        @DisplayName("Should produce different ciphertexts for same plaintext (nonce randomness)")
        void shouldProduceDifferentCiphertexts() {
            // Given
            String plaintext = "same-plaintext";

            // When - Encrypt same plaintext twice
            CompletableFuture<Optional<String>> result1 = encryptionService.encrypt(plaintext);
            CompletableFuture<Optional<String>> result2 = encryptionService.encrypt(plaintext);

            // Then - Different ciphertexts due to random nonce
            String ciphertext1 = result1.join().orElseThrow();
            String ciphertext2 = result2.join().orElseThrow();

            assertNotEquals(ciphertext1, ciphertext2,
                "Same plaintext should produce different ciphertexts due to random nonce");
        }

        @Test
        @DisplayName("Should encrypt Unicode characters correctly")
        void shouldEncryptUnicodeCharacters() {
            // Given
            String unicodePlaintext = "Hello 世界! 🔒🔐 €£¥";

            // When
            CompletableFuture<Optional<String>> result = encryptionService.encrypt(unicodePlaintext);

            // Then
            Optional<String> encrypted = result.join();
            assertTrue(encrypted.isPresent(), "Unicode plaintext should encrypt successfully");
            assertIsValidBase64(encrypted.get(), "Encrypted output should be Base64 encoded");
        }
    }

    @Nested
    @DisplayName("Decryption Tests")
    class DecryptionTests {

        @Test
        @DisplayName("Should successfully decrypt ciphertext")
        void shouldDecryptCiphertext() {
            // Given
            String plaintext = "test-refresh-token-789";
            String ciphertext = encryptionService.encrypt(plaintext).join().orElseThrow();

            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt(ciphertext);

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isPresent(), "Decryption should return a value");
            assertEquals(plaintext, decrypted.get(), "Decrypted text should match original plaintext");
        }

        @Test
        @DisplayName("Should reject null ciphertext")
        void shouldRejectNullCiphertext() {
            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt(null);

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isEmpty(), "Decryption with null input should return empty Optional");
        }

        @Test
        @DisplayName("Should reject empty ciphertext")
        void shouldRejectEmptyCiphertext() {
            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt("");

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isEmpty(), "Decryption with empty input should return empty Optional");
        }

        @Test
        @DisplayName("Should reject invalid Base64 ciphertext")
        void shouldRejectInvalidBase64Ciphertext() {
            // Given
            String invalidBase64 = "not-valid-base64!@#$";

            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt(invalidBase64);

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isEmpty(), "Decryption with invalid Base64 should return empty Optional");
        }

        @Test
        @DisplayName("Should reject corrupted ciphertext")
        void shouldRejectCorruptedCiphertext() {
            // Given
            String plaintext = "original-token";
            String ciphertext = encryptionService.encrypt(plaintext).join().orElseThrow();

            // Corrupt the ciphertext by modifying a byte
            byte[] corruptedBytes = Base64.getDecoder().decode(ciphertext);
            corruptedBytes[corruptedBytes.length - 1] ^= 0xFF; // Flip bits in last byte
            String corruptedCiphertext = Base64.getEncoder().encodeToString(corruptedBytes);

            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt(corruptedCiphertext);

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isEmpty(), "Decryption with corrupted ciphertext should fail (GCM authentication)");
        }

        @Test
        @DisplayName("Should decrypt long ciphertext successfully")
        void shouldDecryptLongCiphertext() {
            // Given
            String longPlaintext = "x".repeat(10000);
            String ciphertext = encryptionService.encrypt(longPlaintext).join().orElseThrow();

            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt(ciphertext);

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isPresent(), "Long ciphertext should decrypt successfully");
            assertEquals(longPlaintext, decrypted.get(), "Decrypted long text should match original");
        }

        @Test
        @DisplayName("Should decrypt Unicode characters correctly")
        void shouldDecryptUnicodeCharacters() {
            // Given
            String unicodePlaintext = "Testing 中文 日本語 한글 العربية 🚀🔐";
            String ciphertext = encryptionService.encrypt(unicodePlaintext).join().orElseThrow();

            // When
            CompletableFuture<Optional<String>> result = encryptionService.decrypt(ciphertext);

            // Then
            Optional<String> decrypted = result.join();
            assertTrue(decrypted.isPresent(), "Unicode ciphertext should decrypt successfully");
            assertEquals(unicodePlaintext, decrypted.get(), "Decrypted Unicode text should match original");
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should successfully perform encrypt-decrypt round trip")
        void shouldPerformEncryptDecryptRoundTrip() {
            // Given
            String originalText = "round-trip-test-token-12345";

            // When - Encrypt then decrypt
            String ciphertext = encryptionService.encrypt(originalText).join().orElseThrow();
            String decryptedText = encryptionService.decrypt(ciphertext).join().orElseThrow();

            // Then
            assertEquals(originalText, decryptedText, "Round trip should preserve original text");
        }

        @Test
        @DisplayName("Should handle multiple round trips")
        void shouldHandleMultipleRoundTrips() {
            // Given
            String originalText = "multi-round-trip-token";

            // When - Perform 5 round trips
            String currentText = originalText;
            for (int i = 0; i < 5; i++) {
                String encrypted = encryptionService.encrypt(currentText).join().orElseThrow();
                currentText = encryptionService.decrypt(encrypted).join().orElseThrow();
            }

            // Then
            assertEquals(originalText, currentText, "Multiple round trips should preserve original text");
        }

        @Test
        @DisplayName("Should handle special characters in round trip")
        void shouldHandleSpecialCharactersRoundTrip() {
            // Given
            String specialText = "!@#$%^&*()_+-=[]{}|;:',.<>?/~`\"\\";

            // When
            String ciphertext = encryptionService.encrypt(specialText).join().orElseThrow();
            String decryptedText = encryptionService.decrypt(ciphertext).join().orElseThrow();

            // Then
            assertEquals(specialText, decryptedText, "Special characters should survive round trip");
        }
    }

    @Nested
    @DisplayName("Key Generation Tests")
    class KeyGenerationTests {

        @Test
        @DisplayName("Should generate valid AES-256 key")
        void shouldGenerateValidKey() {
            // When
            CompletableFuture<Optional<String>> result = encryptionService.generateKey();

            // Then
            Optional<String> key = result.join();
            assertTrue(key.isPresent(), "Key generation should return a value");
            assertIsValidBase64(key.get(), "Generated key should be Base64 encoded");

            // AES-256 key = 32 bytes = 44 characters in Base64
            byte[] keyBytes = Base64.getDecoder().decode(key.get());
            assertEquals(32, keyBytes.length, "AES-256 key should be 32 bytes (256 bits)");
        }

        @Test
        @DisplayName("Should generate different keys each time")
        void shouldGenerateDifferentKeys() {
            // When - Generate multiple keys
            String key1 = encryptionService.generateKey().join().orElseThrow();
            String key2 = encryptionService.generateKey().join().orElseThrow();
            String key3 = encryptionService.generateKey().join().orElseThrow();

            // Then - All keys should be different
            assertNotEquals(key1, key2, "Generated keys should be unique");
            assertNotEquals(key2, key3, "Generated keys should be unique");
            assertNotEquals(key1, key3, "Generated keys should be unique");
        }

        @Test
        @DisplayName("Generated key should be usable for encryption")
        void generatedKeyShouldBeUsableForEncryption() {
            // Given
            String newKey = encryptionService.generateKey().join().orElseThrow();
            EncryptionService newEncryptionService = new EncryptionService();
            ReflectionTestUtils.setField(newEncryptionService, "masterKeyBase64", newKey);

            String plaintext = "test-with-generated-key";

            // When
            String ciphertext = newEncryptionService.encrypt(plaintext).join().orElseThrow();
            String decrypted = newEncryptionService.decrypt(ciphertext).join().orElseThrow();

            // Then
            assertEquals(plaintext, decrypted, "Generated key should work for encryption/decryption");
        }
    }

    @Nested
    @DisplayName("Concurrency Tests - Virtual Threads")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent encryption operations")
        void shouldHandleConcurrentEncryption() {
            // Given
            int concurrentOperations = 100;

            // When - Execute 100 concurrent encryptions
            Set<String> ciphertexts = ConcurrentHashMap.newKeySet();
            CompletableFuture<?>[] futures = IntStream.range(0, concurrentOperations)
                .mapToObj(i -> encryptionService.encrypt("test-token-" + i)
                    .thenAccept(encrypted -> encrypted.ifPresent(ciphertexts::add)))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            // Then - All operations should complete successfully with unique ciphertexts
            assertEquals(concurrentOperations, ciphertexts.size(),
                "All concurrent encryptions should produce unique results");
        }

        @Test
        @DisplayName("Should handle concurrent decryption operations")
        void shouldHandleConcurrentDecryption() {
            // Given
            String plaintext = "concurrent-test-token";
            String ciphertext = encryptionService.encrypt(plaintext).join().orElseThrow();
            int concurrentOperations = 100;

            // When - Execute 100 concurrent decryptions of same ciphertext
            Set<String> decryptedTexts = ConcurrentHashMap.newKeySet();
            CompletableFuture<?>[] futures = IntStream.range(0, concurrentOperations)
                .mapToObj(i -> encryptionService.decrypt(ciphertext)
                    .thenAccept(decrypted -> decrypted.ifPresent(decryptedTexts::add)))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            // Then - All operations should produce same plaintext
            assertEquals(1, decryptedTexts.size(), "All decryptions should produce same plaintext");
            assertTrue(decryptedTexts.contains(plaintext), "Decrypted text should match original");
        }

        @Test
        @DisplayName("Should handle concurrent key generation")
        void shouldHandleConcurrentKeyGeneration() {
            // Given
            int concurrentOperations = 50;

            // When - Generate 50 keys concurrently
            Set<String> keys = ConcurrentHashMap.newKeySet();
            CompletableFuture<?>[] futures = IntStream.range(0, concurrentOperations)
                .mapToObj(i -> encryptionService.generateKey()
                    .thenAccept(key -> key.ifPresent(keys::add)))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            // Then - All generated keys should be unique
            assertEquals(concurrentOperations, keys.size(),
                "All concurrent key generations should produce unique keys");
        }
    }

    @Nested
    @DisplayName("Security Property Tests")
    class SecurityPropertyTests {

        @Test
        @DisplayName("Should include nonce in ciphertext")
        void shouldIncludeNonceInCiphertext() {
            // Given
            String plaintext = "nonce-test";

            // When
            String ciphertext = encryptionService.encrypt(plaintext).join().orElseThrow();
            byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);

            // Then - Ciphertext should be: nonce (12 bytes) + encrypted data + auth tag
            assertTrue(ciphertextBytes.length > 12,
                "Ciphertext should contain nonce + encrypted data + authentication tag");
        }

        @Test
        @DisplayName("Should fail decryption with tampered authentication tag")
        void shouldFailWithTamperedAuthTag() {
            // Given
            String plaintext = "auth-tag-test";
            String ciphertext = encryptionService.encrypt(plaintext).join().orElseThrow();

            // Tamper with authentication tag (last 16 bytes)
            byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
            int authTagStart = ciphertextBytes.length - 16;
            ciphertextBytes[authTagStart] ^= 0xFF; // Flip bits in first byte of auth tag
            String tamperedCiphertext = Base64.getEncoder().encodeToString(ciphertextBytes);

            // When
            Optional<String> decrypted = encryptionService.decrypt(tamperedCiphertext).join();

            // Then
            assertTrue(decrypted.isEmpty(),
                "Decryption should fail when authentication tag is tampered (GCM property)");
        }

        @Test
        @DisplayName("Should use UTF-8 encoding consistently")
        void shouldUseUtf8EncodingConsistently() {
            // Given
            String unicodeText = "UTF-8 test: 你好世界 مرحبا بالعالم";
            byte[] expectedBytes = unicodeText.getBytes(StandardCharsets.UTF_8);

            // When
            String ciphertext = encryptionService.encrypt(unicodeText).join().orElseThrow();
            String decrypted = encryptionService.decrypt(ciphertext).join().orElseThrow();

            // Then
            assertArrayEquals(expectedBytes, decrypted.getBytes(StandardCharsets.UTF_8),
                "UTF-8 encoding should be preserved through encryption/decryption");
        }
    }

    // Helper methods

    private void assertIsValidBase64(String value, String message) {
        assertDoesNotThrow(() -> Base64.getDecoder().decode(value), message);
    }

    private static String generateTestKey() {
        // Generate a deterministic test key (256-bit AES)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) (i * 7); // Deterministic pattern for testing
        }
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
