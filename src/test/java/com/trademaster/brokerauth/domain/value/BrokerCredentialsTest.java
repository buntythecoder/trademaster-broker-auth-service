package com.trademaster.brokerauth.domain.value;

import com.trademaster.brokerauth.enums.BrokerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BrokerCredentials Value Object Tests
 *
 * MANDATORY: Rule #20 - >80% unit test coverage
 * MANDATORY: Rule #9 - Test immutability
 * MANDATORY: Rule #23 - Test token masking security
 *
 * Tests value object validation, business logic, immutable operations, and security.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@DisplayName("BrokerCredentials Value Object Tests")
class BrokerCredentialsTest {

    private static final String TEST_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_USER_ID = "USER123";
    private static final BrokerType TEST_BROKER = BrokerType.ZERODHA;
    private static final String TEST_ACCESS_TOKEN = "test-access-token-12345678";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token-87654321";

    @Nested
    @DisplayName("Construction & Validation Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create valid broker credentials")
        void shouldCreateValidBrokerCredentials() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expires = now.plusHours(8);

            // When
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                expires, now
            );

            // Then
            assertNotNull(credentials);
            assertEquals(TEST_SESSION_ID, credentials.sessionId());
            assertEquals(TEST_USER_ID, credentials.userId());
            assertEquals(TEST_BROKER, credentials.brokerType());
            assertEquals(TEST_ACCESS_TOKEN, credentials.accessToken());
            assertEquals(TEST_REFRESH_TOKEN, credentials.refreshToken());
        }

        @Test
        @DisplayName("Should reject null session ID")
        void shouldRejectNullSessionId() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    null, TEST_USER_ID, TEST_BROKER,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                    LocalDateTime.now().plusHours(8), LocalDateTime.now()
                )
            );
        }

        @Test
        @DisplayName("Should reject blank session ID")
        void shouldRejectBlankSessionId() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    "  ", TEST_USER_ID, TEST_BROKER,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                    LocalDateTime.now().plusHours(8), LocalDateTime.now()
                )
            );
        }

        @Test
        @DisplayName("Should reject null user ID")
        void shouldRejectNullUserId() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    TEST_SESSION_ID, null, TEST_BROKER,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                    LocalDateTime.now().plusHours(8), LocalDateTime.now()
                )
            );
        }

        @Test
        @DisplayName("Should reject null broker type")
        void shouldRejectNullBrokerType() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    TEST_SESSION_ID, TEST_USER_ID, null,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                    LocalDateTime.now().plusHours(8), LocalDateTime.now()
                )
            );
        }

        @Test
        @DisplayName("Should reject null access token")
        void shouldRejectNullAccessToken() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                    null, TEST_REFRESH_TOKEN,
                    LocalDateTime.now().plusHours(8), LocalDateTime.now()
                )
            );
        }

        @Test
        @DisplayName("Should reject null expiry timestamp")
        void shouldRejectNullExpiryTimestamp() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                    null, LocalDateTime.now()
                )
            );
        }

        @Test
        @DisplayName("Should reject null created timestamp")
        void shouldRejectNullCreatedTimestamp() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new BrokerCredentials(
                    TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                    LocalDateTime.now().plusHours(8), null
                )
            );
        }
    }

    @Nested
    @DisplayName("Validity Check Tests")
    class ValidityCheckTests {

        @Test
        @DisplayName("Should return true for valid credentials with future expiry")
        void shouldReturnTrueForValidCredentials() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            boolean isValid = credentials.isValid();

            // Then
            assertTrue(isValid, "Credentials with future expiry should be valid");
        }

        @Test
        @DisplayName("Should return false for expired credentials")
        void shouldReturnFalseForExpiredCredentials() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().minusHours(2), LocalDateTime.now()
            );

            // When
            boolean isValid = credentials.isValid();

            // Then
            assertFalse(isValid, "Credentials with past expiry should be invalid");
        }

        @Test
        @DisplayName("Should detect expired credentials")
        void shouldDetectExpiredCredentials() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now()
            );

            // When
            boolean isExpired = credentials.isExpired();

            // Then
            assertTrue(isExpired, "Past expiry time should be detected as expired");
        }
    }

    @Nested
    @DisplayName("Refresh Logic Tests")
    class RefreshLogicTests {

        @Test
        @DisplayName("Should detect credentials needing refresh within threshold")
        void shouldDetectCredentialsNeedingRefreshWithinThreshold() {
            // Given - Credentials expiring in 3 minutes
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusMinutes(3), LocalDateTime.now()
            );

            // When
            boolean needsRefresh = credentials.needsRefresh(5);

            // Then
            assertTrue(needsRefresh, "Credentials expiring in 3 minutes should need refresh within 5 minute threshold");
        }

        @Test
        @DisplayName("Should not detect credentials needing refresh beyond threshold")
        void shouldNotDetectCredentialsNeedingRefreshBeyondThreshold() {
            // Given - Credentials expiring in 10 minutes
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now()
            );

            // When
            boolean needsRefresh = credentials.needsRefresh(5);

            // Then
            assertFalse(needsRefresh, "Credentials expiring in 10 minutes should not need refresh within 5 minute threshold");
        }

        @Test
        @DisplayName("Should use default threshold when not specified")
        void shouldUseDefaultThresholdWhenNotSpecified() {
            // Given - Credentials expiring in 3 minutes
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusMinutes(3), LocalDateTime.now()
            );

            // When
            boolean needsRefresh = credentials.needsRefresh();

            // Then
            assertTrue(needsRefresh, "Should use default 5 minute threshold");
        }

        @Test
        @DisplayName("Should calculate minutes until expiry for valid credentials")
        void shouldCalculateMinutesUntilExpiryForValidCredentials() {
            // Given - Credentials expiring in approximately 60 minutes
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusMinutes(60), LocalDateTime.now()
            );

            // When
            var minutesUntilExpiry = credentials.getMinutesUntilExpiry();

            // Then
            assertTrue(minutesUntilExpiry.isPresent());
            assertTrue(minutesUntilExpiry.get() >= 59 && minutesUntilExpiry.get() <= 60,
                "Minutes until expiry should be approximately 60");
        }

        @Test
        @DisplayName("Should return empty for expired credentials")
        void shouldReturnEmptyForExpiredCredentials() {
            // Given - Expired credentials
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().minusHours(1), LocalDateTime.now()
            );

            // When
            var minutesUntilExpiry = credentials.getMinutesUntilExpiry();

            // Then
            assertTrue(minutesUntilExpiry.isEmpty(), "Expired credentials should have no minutes until expiry");
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should detect credentials with refresh token")
        void shouldDetectCredentialsWithRefreshToken() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            boolean hasRefreshToken = credentials.hasRefreshToken();

            // Then
            assertTrue(hasRefreshToken, "Credentials should have refresh token");
        }

        @Test
        @DisplayName("Should detect credentials without refresh token")
        void shouldDetectCredentialsWithoutRefreshToken() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, null,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            boolean hasRefreshToken = credentials.hasRefreshToken();

            // Then
            assertFalse(hasRefreshToken, "Credentials should not have refresh token");
        }

        @Test
        @DisplayName("Should detect blank refresh token as not present")
        void shouldDetectBlankRefreshTokenAsNotPresent() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, "  ",
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            boolean hasRefreshToken = credentials.hasRefreshToken();

            // Then
            assertFalse(hasRefreshToken, "Blank refresh token should be detected as not present");
        }
    }

    @Nested
    @DisplayName("Time Calculation Tests")
    class TimeCalculationTests {

        @Test
        @DisplayName("Should calculate age in hours")
        void shouldCalculateAgeInHours() {
            // Given - Credentials created 3 hours ago
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(5), LocalDateTime.now().minusHours(3)
            );

            // When
            long ageInHours = credentials.getAgeInHours();

            // Then
            assertTrue(ageInHours >= 2 && ageInHours <= 3,
                "Age should be approximately 3 hours");
        }

        @Test
        @DisplayName("Should return zero age for newly created credentials")
        void shouldReturnZeroAgeForNewlyCreatedCredentials() {
            // Given - Just created
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            long ageInHours = credentials.getAgeInHours();

            // Then
            assertEquals(0, ageInHours, "Newly created credentials should have zero age");
        }
    }

    @Nested
    @DisplayName("Immutable Operations Tests (Rule #9)")
    class ImmutableOperationsTests {

        @Test
        @DisplayName("Should refresh credentials with new tokens immutably")
        void shouldRefreshCredentialsWithNewTokensImmutably() {
            // Given
            LocalDateTime originalExpiry = LocalDateTime.now().plusHours(1);
            BrokerCredentials original = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                originalExpiry, LocalDateTime.now()
            );

            LocalDateTime newExpiry = LocalDateTime.now().plusHours(8);

            // When
            BrokerCredentials refreshed = original.refresh(
                "new-access-token",
                "new-refresh-token",
                newExpiry
            );

            // Then
            assertNotSame(original, refreshed, "Should return new instance");
            assertEquals(TEST_ACCESS_TOKEN, original.accessToken(), "Original should be unchanged");
            assertEquals("new-access-token", refreshed.accessToken());
            assertEquals("new-refresh-token", refreshed.refreshToken());
            assertEquals(newExpiry, refreshed.expiresAt());
        }

        @Test
        @DisplayName("Should update access token immutably")
        void shouldUpdateAccessTokenImmutably() {
            // Given
            BrokerCredentials original = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            String newToken = "new-access-token";

            // When
            BrokerCredentials updated = original.withAccessToken(newToken);

            // Then
            assertNotSame(original, updated, "Should return new instance");
            assertEquals(TEST_ACCESS_TOKEN, original.accessToken(), "Original should be unchanged");
            assertEquals(newToken, updated.accessToken());
            assertEquals(TEST_REFRESH_TOKEN, updated.refreshToken(), "Refresh token should remain same");
        }
    }

    @Nested
    @DisplayName("Security Tests (Rule #23)")
    class SecurityTests {

        @Test
        @DisplayName("Should mask access token in getter")
        void shouldMaskAccessTokenInGetter() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            String masked = credentials.getMaskedAccessToken();

            // Then
            assertFalse(masked.contains("test-access-token"),
                "Full token should not be exposed");
            assertTrue(masked.contains("...12345678"),
                "Should show only last 8 characters");
        }

        @Test
        @DisplayName("Should mask refresh token in getter")
        void shouldMaskRefreshTokenInGetter() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            String masked = credentials.getMaskedRefreshToken();

            // Then
            assertFalse(masked.contains("test-refresh-token"),
                "Full token should not be exposed");
            assertTrue(masked.contains("...87654321"),
                "Should show only last 8 characters");
        }

        @Test
        @DisplayName("Should mask short tokens completely")
        void shouldMaskShortTokensCompletely() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                "short", null,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            String masked = credentials.getMaskedAccessToken();

            // Then
            assertEquals("***", masked, "Short tokens should be completely masked");
        }

        @Test
        @DisplayName("Should mask tokens in toString")
        void shouldMaskTokensInToString() {
            // Given
            BrokerCredentials credentials = new BrokerCredentials(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                LocalDateTime.now().plusHours(8), LocalDateTime.now()
            );

            // When
            String str = credentials.toString();

            // Then
            assertFalse(str.contains(TEST_ACCESS_TOKEN),
                "Full access token should not be exposed in toString");
            assertFalse(str.contains(TEST_REFRESH_TOKEN),
                "Full refresh token should not be exposed in toString");
            assertTrue(str.contains("...12345678"),
                "Should show masked access token");
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create credentials using factory method")
        void shouldCreateCredentialsUsingFactoryMethod() {
            // Given/When
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expires = now.plusHours(8);

            BrokerCredentials credentials = BrokerCredentials.of(
                TEST_SESSION_ID, TEST_USER_ID, TEST_BROKER,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN,
                expires, now
            );

            // Then
            assertNotNull(credentials);
            assertEquals(TEST_SESSION_ID, credentials.sessionId());
            assertEquals(TEST_USER_ID, credentials.userId());
            assertEquals(TEST_BROKER, credentials.brokerType());
            assertEquals(TEST_ACCESS_TOKEN, credentials.accessToken());
        }
    }
}
