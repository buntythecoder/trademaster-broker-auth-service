package com.trademaster.brokerauth.domain.model;

import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionDomain Unit Tests
 *
 * MANDATORY: Rule #20 - >80% unit test coverage
 * MANDATORY: Rule #3 - Functional test builders
 * MANDATORY: Rule #14 - Test pattern matching behavior
 *
 * Tests immutable domain model with business logic validation.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@DisplayName("SessionDomain Unit Tests")
class SessionDomainTest {

    private static final String TEST_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_USER_ID = "USER123";
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";
    private static final String TEST_VAULT_PATH = "secret/data/trademaster/sessions/USER123/zerodha";

    /**
     * Create valid test session
     */
    private SessionDomain createValidSession() {
        return new SessionDomain(
            1L,
            TEST_SESSION_ID,
            TEST_USER_ID,
            BrokerType.ZERODHA,
            TEST_ACCESS_TOKEN,
            TEST_REFRESH_TOKEN,
            SessionStatus.ACTIVE,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(8),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            TEST_VAULT_PATH
        );
    }

    @Nested
    @DisplayName("Construction & Validation Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create valid session domain")
        void shouldCreateValidSessionDomain() {
            // When
            SessionDomain session = createValidSession();

            // Then
            assertNotNull(session);
            assertEquals(TEST_SESSION_ID, session.sessionId());
            assertEquals(TEST_USER_ID, session.userId());
            assertEquals(BrokerType.ZERODHA, session.brokerType());
            assertEquals(SessionStatus.ACTIVE, session.status());
        }

        @Test
        @DisplayName("Should reject null session ID")
        void shouldRejectNullSessionId() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new SessionDomain(1L, null, TEST_USER_ID, BrokerType.ZERODHA,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                    LocalDateTime.now(), null, TEST_VAULT_PATH)
            );
        }

        @Test
        @DisplayName("Should reject blank session ID")
        void shouldRejectBlankSessionId() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new SessionDomain(1L, "  ", TEST_USER_ID, BrokerType.ZERODHA,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                    LocalDateTime.now(), null, TEST_VAULT_PATH)
            );
        }

        @Test
        @DisplayName("Should reject null user ID")
        void shouldRejectNullUserId() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new SessionDomain(1L, TEST_SESSION_ID, null, BrokerType.ZERODHA,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                    LocalDateTime.now(), null, TEST_VAULT_PATH)
            );
        }

        @Test
        @DisplayName("Should reject null broker type")
        void shouldRejectNullBrokerType() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                new SessionDomain(1L, TEST_SESSION_ID, TEST_USER_ID, null,
                    TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                    LocalDateTime.now(), null, TEST_VAULT_PATH)
            );
        }
    }

    @Nested
    @DisplayName("Status Check Tests - Pattern Matching (Rule #14)")
    class StatusCheckTests {

        @Test
        @DisplayName("Should return true for active session with future expiry")
        void shouldReturnTrueForActiveSession() {
            // Given
            SessionDomain session = createValidSession();

            // When
            boolean isActive = session.isActive();

            // Then
            assertTrue(isActive, "Active session with future expiry should be active");
        }

        @Test
        @DisplayName("Should return false for expired session")
        void shouldReturnFalseForExpiredSession() {
            // Given - Session with past expiry
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                LocalDateTime.now().minusHours(10),
                LocalDateTime.now().minusHours(2), // Expired 2 hours ago
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                TEST_VAULT_PATH
            );

            // When
            boolean isActive = session.isActive();

            // Then
            assertFalse(isActive, "Expired session should not be active");
        }

        @Test
        @DisplayName("Should return false for revoked status")
        void shouldReturnFalseForRevokedStatus() {
            // Given
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.REVOKED,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(8),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                TEST_VAULT_PATH
            );

            // When
            boolean isActive = session.isActive();

            // Then
            assertFalse(isActive, "Revoked session should not be active");
        }

        @Test
        @DisplayName("Should return false for invalid status")
        void shouldReturnFalseForInvalidStatus() {
            // Given
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.INVALID,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(8),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                TEST_VAULT_PATH
            );

            // When
            boolean isActive = session.isActive();

            // Then
            assertFalse(isActive, "Invalid session should not be active");
        }
    }

    @Nested
    @DisplayName("Refresh Logic Tests")
    class RefreshLogicTests {

        @Test
        @DisplayName("Should need refresh when expiring within 5 minutes")
        void shouldNeedRefreshWhenExpiringWithin5Minutes() {
            // Given - Session expiring in 3 minutes
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusMinutes(3),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                TEST_VAULT_PATH
            );

            // When
            boolean needsRefresh = session.needsRefresh(5);

            // Then
            assertTrue(needsRefresh, "Session expiring in 3 minutes should need refresh");
        }

        @Test
        @DisplayName("Should not need refresh when expiring in 10 minutes")
        void shouldNotNeedRefreshWhenExpiringIn10Minutes() {
            // Given - Session expiring in 10 minutes
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                TEST_VAULT_PATH
            );

            // When
            boolean needsRefresh = session.needsRefresh(5);

            // Then
            assertFalse(needsRefresh, "Session expiring in 10 minutes should not need refresh");
        }

        @Test
        @DisplayName("Should detect expired session")
        void shouldDetectExpiredSession() {
            // Given - Expired session
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, SessionStatus.ACTIVE,
                LocalDateTime.now().minusHours(10),
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                TEST_VAULT_PATH
            );

            // When
            boolean isExpired = session.isExpired();

            // Then
            assertTrue(isExpired, "Past expiry time should be expired");
        }
    }

    @Nested
    @DisplayName("Immutable Update Tests (Rule #9)")
    class ImmutableUpdateTests {

        @Test
        @DisplayName("Should create new instance when updating status")
        void shouldCreateNewInstanceWhenUpdatingStatus() {
            // Given
            SessionDomain original = createValidSession();

            // When
            SessionDomain updated = original.withStatus(SessionStatus.EXPIRED);

            // Then
            assertNotSame(original, updated, "Should return new instance");
            assertEquals(SessionStatus.ACTIVE, original.status(), "Original should be unchanged");
            assertEquals(SessionStatus.EXPIRED, updated.status(), "New instance should have updated status");
        }

        @Test
        @DisplayName("Should revoke session immutably")
        void shouldRevokeSessionImmutably() {
            // Given
            SessionDomain original = createValidSession();

            // When
            SessionDomain revoked = original.revoke();

            // Then
            assertNotSame(original, revoked);
            assertEquals(SessionStatus.ACTIVE, original.status());
            assertEquals(SessionStatus.REVOKED, revoked.status());
        }

        @Test
        @DisplayName("Should expire session immutably")
        void shouldExpireSessionImmutably() {
            // Given
            SessionDomain original = createValidSession();

            // When
            SessionDomain expired = original.expire();

            // Then
            assertNotSame(original, expired);
            assertEquals(SessionStatus.ACTIVE, original.status());
            assertEquals(SessionStatus.EXPIRED, expired.status());
        }

        @Test
        @DisplayName("Should invalidate session immutably")
        void shouldInvalidateSessionImmutably() {
            // Given
            SessionDomain original = createValidSession();

            // When
            SessionDomain invalidated = original.invalidate();

            // Then
            assertNotSame(original, invalidated);
            assertEquals(SessionStatus.ACTIVE, original.status());
            assertEquals(SessionStatus.INVALID, invalidated.status());
        }

        @Test
        @DisplayName("Should touch session with new timestamp")
        void shouldTouchSessionWithNewTimestamp() {
            // Given
            SessionDomain original = createValidSession();
            LocalDateTime originalAccessTime = original.lastAccessedAt();

            // Wait a bit to ensure timestamp difference
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // When
            SessionDomain touched = original.touch();

            // Then
            assertNotSame(original, touched);
            assertTrue(touched.lastAccessedAt().isAfter(originalAccessTime),
                "Touch should update last accessed time");
        }

        @Test
        @DisplayName("Should update access token immutably")
        void shouldUpdateAccessTokenImmutably() {
            // Given
            SessionDomain original = createValidSession();
            String newToken = "new-access-token";

            // When
            SessionDomain updated = original.withAccessToken(newToken);

            // Then
            assertNotSame(original, updated);
            assertEquals(TEST_ACCESS_TOKEN, original.accessToken());
            assertEquals(newToken, updated.accessToken());
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should detect session with refresh token")
        void shouldDetectSessionWithRefreshToken() {
            // Given
            SessionDomain session = createValidSession();

            // When
            boolean hasRefreshToken = session.hasRefreshToken();

            // Then
            assertTrue(hasRefreshToken, "Session should have refresh token");
        }

        @Test
        @DisplayName("Should detect session without refresh token")
        void shouldDetectSessionWithoutRefreshToken() {
            // Given
            SessionDomain session = new SessionDomain(
                1L, TEST_SESSION_ID, TEST_USER_ID, BrokerType.ZERODHA,
                TEST_ACCESS_TOKEN, null, SessionStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now().plusHours(8),
                LocalDateTime.now(), LocalDateTime.now(), null, TEST_VAULT_PATH
            );

            // When
            boolean hasRefreshToken = session.hasRefreshToken();

            // Then
            assertFalse(hasRefreshToken, "Session should not have refresh token");
        }
    }
}
