package com.trademaster.brokerauth.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessToken Value Object Tests
 *
 * MANDATORY: Rule #20 - >80% unit test coverage
 * MANDATORY: Rule #9 - Test immutability
 *
 * Tests value object validation, business logic, and immutable operations.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@DisplayName("AccessToken Value Object Tests")
class AccessTokenTest {

    @Nested
    @DisplayName("Construction & Validation Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create valid access token")
        void shouldCreateValidAccessToken() {
            // Given
            String token = "test-access-token-123";
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(8);

            // When
            AccessToken accessToken = new AccessToken(token, expiresAt);

            // Then
            assertNotNull(accessToken);
            assertEquals(token, accessToken.token());
            assertEquals(expiresAt, accessToken.expiresAt());
        }

        @Test
        @DisplayName("Should reject null token")
        void shouldRejectNullToken() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new AccessToken(null, LocalDateTime.now().plusHours(8))
            );
        }

        @Test
        @DisplayName("Should reject blank token")
        void shouldRejectBlankToken() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new AccessToken("  ", LocalDateTime.now().plusHours(8))
            );
        }

        @Test
        @DisplayName("Should reject null expiry time")
        void shouldRejectNullExpiryTime() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new AccessToken("test-token", null)
            );
        }
    }

    @Nested
    @DisplayName("Validity Check Tests")
    class ValidityCheckTests {

        @Test
        @DisplayName("Should return true for valid token with future expiry")
        void shouldReturnTrueForValidToken() {
            // Given
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().plusHours(8));

            // When
            boolean isValid = token.isValid();

            // Then
            assertTrue(isValid, "Token with future expiry should be valid");
        }

        @Test
        @DisplayName("Should return false for expired token")
        void shouldReturnFalseForExpiredToken() {
            // Given
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().minusHours(2));

            // When
            boolean isValid = token.isValid();

            // Then
            assertFalse(isValid, "Token with past expiry should be invalid");
        }

        @Test
        @DisplayName("Should detect expired token")
        void shouldDetectExpiredToken() {
            // Given
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().minusMinutes(5));

            // When
            boolean isExpired = token.isExpired();

            // Then
            assertTrue(isExpired, "Past expiry time should be detected as expired");
        }
    }

    @Nested
    @DisplayName("Expiry Threshold Tests")
    class ExpiryThresholdTests {

        @Test
        @DisplayName("Should detect token expiring within threshold")
        void shouldDetectTokenExpiringWithinThreshold() {
            // Given - Token expiring in 3 minutes
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().plusMinutes(3));

            // When
            boolean expiresWithin5Min = token.expiresWithin(5);

            // Then
            assertTrue(expiresWithin5Min, "Token expiring in 3 minutes should be within 5 minute threshold");
        }

        @Test
        @DisplayName("Should not detect token expiring beyond threshold")
        void shouldNotDetectTokenExpiringBeyondThreshold() {
            // Given - Token expiring in 10 minutes
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().plusMinutes(10));

            // When
            boolean expiresWithin5Min = token.expiresWithin(5);

            // Then
            assertFalse(expiresWithin5Min, "Token expiring in 10 minutes should be beyond 5 minute threshold");
        }

        @Test
        @DisplayName("Should calculate minutes until expiry for valid token")
        void shouldCalculateMinutesUntilExpiryForValidToken() {
            // Given - Token expiring in approximately 60 minutes
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().plusMinutes(60));

            // When
            var minutesUntilExpiry = token.getMinutesUntilExpiry();

            // Then
            assertTrue(minutesUntilExpiry.isPresent());
            assertTrue(minutesUntilExpiry.get() >= 59 && minutesUntilExpiry.get() <= 60,
                "Minutes until expiry should be approximately 60");
        }

        @Test
        @DisplayName("Should return empty for expired token minutes")
        void shouldReturnEmptyForExpiredTokenMinutes() {
            // Given - Expired token
            AccessToken token = new AccessToken("test-token", LocalDateTime.now().minusHours(1));

            // When
            var minutesUntilExpiry = token.getMinutesUntilExpiry();

            // Then
            assertTrue(minutesUntilExpiry.isEmpty(), "Expired token should have no minutes until expiry");
        }
    }

    @Nested
    @DisplayName("Immutable Operations Tests (Rule #9)")
    class ImmutableOperationsTests {

        @Test
        @DisplayName("Should renew token with new expiry immutably")
        void shouldRenewTokenWithNewExpiryImmutably() {
            // Given
            AccessToken original = new AccessToken("test-token", LocalDateTime.now().plusHours(1));
            LocalDateTime newExpiry = LocalDateTime.now().plusHours(8);

            // When
            AccessToken renewed = original.renew(newExpiry);

            // Then
            assertNotSame(original, renewed, "Should return new instance");
            assertNotEquals(original.expiresAt(), renewed.expiresAt());
            assertEquals(newExpiry, renewed.expiresAt());
            assertEquals(original.token(), renewed.token(), "Token value should remain same");
        }

        @Test
        @DisplayName("Should extend token expiry immutably")
        void shouldExtendTokenExpiryImmutably() {
            // Given
            LocalDateTime originalExpiry = LocalDateTime.now().plusHours(1);
            AccessToken original = new AccessToken("test-token", originalExpiry);

            // When
            AccessToken extended = original.extend(120); // Extend by 2 hours

            // Then
            assertNotSame(original, extended, "Should return new instance");
            assertEquals(originalExpiry, original.expiresAt(), "Original should be unchanged");
            assertTrue(extended.expiresAt().isAfter(original.expiresAt()),
                "Extended token should expire later");
        }
    }

    @Nested
    @DisplayName("Security Tests (Rule #23)")
    class SecurityTests {

        @Test
        @DisplayName("Should mask token in toString")
        void shouldMaskTokenInToString() {
            // Given
            AccessToken token = new AccessToken("very-long-access-token-12345678",
                LocalDateTime.now().plusHours(8));

            // When
            String str = token.toString();

            // Then
            assertFalse(str.contains("very-long-access-token"),
                "Full token should not be exposed in toString");
            assertTrue(str.contains("...12345678"),
                "Should show only last 8 characters");
        }

        @Test
        @DisplayName("Should mask short tokens completely")
        void shouldMaskShortTokensCompletely() {
            // Given
            AccessToken token = new AccessToken("short", LocalDateTime.now().plusHours(8));

            // When
            String masked = token.getMaskedToken();

            // Then
            assertEquals("***", masked, "Short tokens should be completely masked");
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create token with duration from now")
        void shouldCreateTokenWithDurationFromNow() {
            // Given/When
            AccessToken token = AccessToken.withDuration("test-token", 480); // 8 hours

            // Then
            assertNotNull(token);
            assertTrue(token.isValid());
            var minutesUntilExpiry = token.getMinutesUntilExpiry();
            assertTrue(minutesUntilExpiry.isPresent());
            assertTrue(minutesUntilExpiry.get() >= 479 && minutesUntilExpiry.get() <= 480,
                "Should expire in approximately 480 minutes");
        }
    }
}
