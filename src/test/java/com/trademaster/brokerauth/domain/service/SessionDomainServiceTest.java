package com.trademaster.brokerauth.domain.service;

import com.trademaster.brokerauth.constant.BrokerAuthConstants;
import com.trademaster.brokerauth.domain.model.SessionDomain;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import com.trademaster.brokerauth.mapper.SessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * SessionDomainService Unit Tests
 *
 * MANDATORY: Rule #20 - >80% unit test coverage
 * MANDATORY: Rule #3 - Functional test patterns
 * MANDATORY: Rule #12 - Test Redis operations
 *
 * Tests domain service layer with mocked Redis operations.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionDomainService Unit Tests")
class SessionDomainServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private SessionMapper sessionMapper;
    private SessionDomainService sessionDomainService;

    private static final String TEST_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_USER_ID = "USER123";
    private static final BrokerType TEST_BROKER = BrokerType.ZERODHA;

    @BeforeEach
    void setUp() {
        sessionMapper = new SessionMapper();
        sessionDomainService = new SessionDomainService(redisTemplate, sessionMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * Create test domain
     */
    private SessionDomain createTestDomain() {
        return new SessionDomain(
            1L,
            TEST_SESSION_ID,
            TEST_USER_ID,
            TEST_BROKER,
            "test-access-token",
            "test-refresh-token",
            SessionStatus.ACTIVE,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(8),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            "secret/data/trademaster/sessions/USER123/zerodha"
        );
    }

    /**
     * Create test entity
     */
    private BrokerSession createTestEntity() {
        return BrokerSession.builder()
            .id(1L)
            .sessionId(TEST_SESSION_ID)
            .userId(TEST_USER_ID)
            .brokerType(TEST_BROKER)
            .accessToken("test-access-token")
            .refreshToken("test-refresh-token")
            .status(SessionStatus.ACTIVE)
            .createdAt(LocalDateTime.now().minusHours(1))
            .expiresAt(LocalDateTime.now().plusHours(8))
            .lastAccessedAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .metadata(null)
            .vaultPath("secret/data/trademaster/sessions/USER123/zerodha")
            .build();
    }

    @Nested
    @DisplayName("Save Operation Tests")
    class SaveOperationTests {

        @Test
        @DisplayName("Should save session domain to Redis with correct key and TTL")
        void shouldSaveSessionDomainToRedisWithCorrectKeyAndTTL() {
            // Given
            SessionDomain domain = createTestDomain();
            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;

            // When
            sessionDomainService.save(domain);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

            verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

            assertEquals(expectedKey, keyCaptor.getValue());
            assertTrue(valueCaptor.getValue() instanceof BrokerSession);
            assertTrue(ttlCaptor.getValue().toHours() > 0, "TTL should be positive");
        }

        @Test
        @DisplayName("Should handle null domain gracefully")
        void shouldHandleNullDomainGracefully() {
            // When
            sessionDomainService.save(null);

            // Then
            verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Find Operation Tests")
    class FindOperationTests {

        @Test
        @DisplayName("Should find session by ID when exists and active")
        void shouldFindSessionByIdWhenExistsAndActive() {
            // Given
            BrokerSession entity = createTestEntity();
            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;
            when(valueOperations.get(expectedKey)).thenReturn(entity);

            // When
            Optional<SessionDomain> result = sessionDomainService.findById(TEST_SESSION_ID);

            // Then
            assertTrue(result.isPresent());
            assertEquals(TEST_SESSION_ID, result.get().sessionId());
            assertEquals(TEST_USER_ID, result.get().userId());
            verify(valueOperations).get(expectedKey);
        }

        @Test
        @DisplayName("Should return empty when session not found in Redis")
        void shouldReturnEmptyWhenSessionNotFoundInRedis() {
            // Given
            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;
            when(valueOperations.get(expectedKey)).thenReturn(null);

            // When
            Optional<SessionDomain> result = sessionDomainService.findById(TEST_SESSION_ID);

            // Then
            assertTrue(result.isEmpty());
            verify(valueOperations).get(expectedKey);
        }

        @Test
        @DisplayName("Should filter out inactive sessions")
        void shouldFilterOutInactiveSessions() {
            // Given - Revoked session
            BrokerSession entity = BrokerSession.builder()
                .id(1L)
                .sessionId(TEST_SESSION_ID)
                .userId(TEST_USER_ID)
                .brokerType(TEST_BROKER)
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .status(SessionStatus.REVOKED)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().plusHours(8))
                .lastAccessedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .metadata(null)
                .vaultPath("secret/data/test")
                .build();

            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;
            when(valueOperations.get(expectedKey)).thenReturn(entity);

            // When
            Optional<SessionDomain> result = sessionDomainService.findById(TEST_SESSION_ID);

            // Then
            assertTrue(result.isEmpty(), "Revoked session should be filtered out");
        }

        @Test
        @DisplayName("Should handle null session ID gracefully")
        void shouldHandleNullSessionIdGracefully() {
            // When
            Optional<SessionDomain> result = sessionDomainService.findById(null);

            // Then
            assertTrue(result.isEmpty());
            verify(valueOperations, never()).get(anyString());
        }
    }

    @Nested
    @DisplayName("Find Active Session Tests")
    class FindActiveSessionTests {

        @Test
        @DisplayName("Should find active session for user and broker")
        void shouldFindActiveSessionForUserAndBroker() {
            // Given
            BrokerSession entity = createTestEntity();
            Set<String> keys = Set.of(BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID);

            when(redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*")).thenReturn(keys);
            when(valueOperations.get(BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID))
                .thenReturn(entity);

            // When
            Optional<SessionDomain> result = sessionDomainService.findActiveSession(TEST_USER_ID, TEST_BROKER);

            // Then
            assertTrue(result.isPresent());
            assertEquals(TEST_SESSION_ID, result.get().sessionId());
            assertEquals(TEST_USER_ID, result.get().userId());
            assertEquals(TEST_BROKER, result.get().brokerType());
        }

        @Test
        @DisplayName("Should return empty when no matching broker session exists")
        void shouldReturnEmptyWhenNoMatchingBrokerSessionExists() {
            // Given - Session for different broker
            BrokerSession entity = createTestEntity();
            entity = entity.toBuilder().brokerType(BrokerType.UPSTOX).build();
            Set<String> keys = Set.of(BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID);

            when(redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*")).thenReturn(keys);
            when(valueOperations.get(BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID))
                .thenReturn(entity);

            // When
            Optional<SessionDomain> result = sessionDomainService.findActiveSession(TEST_USER_ID, TEST_BROKER);

            // Then
            assertTrue(result.isEmpty(), "Should not find session for different broker");
        }
    }

    @Nested
    @DisplayName("Session Lifecycle Tests")
    class SessionLifecycleTests {

        @Test
        @DisplayName("Should revoke session successfully")
        void shouldRevokeSessionSuccessfully() {
            // Given
            BrokerSession entity = createTestEntity();
            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;
            when(valueOperations.get(expectedKey)).thenReturn(entity);

            // When
            boolean result = sessionDomainService.revoke(TEST_SESSION_ID);

            // Then
            assertTrue(result, "Revoke should return true");
            verify(valueOperations).get(expectedKey);
            verify(valueOperations).set(eq(expectedKey), any(BrokerSession.class), any(Duration.class));
        }

        @Test
        @DisplayName("Should return false when revoking non-existent session")
        void shouldReturnFalseWhenRevokingNonExistentSession() {
            // Given
            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;
            when(valueOperations.get(expectedKey)).thenReturn(null);

            // When
            boolean result = sessionDomainService.revoke(TEST_SESSION_ID);

            // Then
            assertFalse(result, "Revoke non-existent session should return false");
        }

        @Test
        @DisplayName("Should touch session updating last accessed time")
        void shouldTouchSessionUpdatingLastAccessedTime() {
            // Given
            BrokerSession entity = createTestEntity();
            String expectedKey = BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID;
            when(valueOperations.get(expectedKey)).thenReturn(entity);

            // When
            boolean result = sessionDomainService.touch(TEST_SESSION_ID);

            // Then
            assertTrue(result, "Touch should return true");
            verify(valueOperations).get(expectedKey);
            verify(valueOperations).set(eq(expectedKey), any(BrokerSession.class), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Query Operation Tests")
    class QueryOperationTests {

        @Test
        @DisplayName("Should count active sessions correctly")
        void shouldCountActiveSessionsCorrectly() {
            // Given
            BrokerSession entity1 = createTestEntity();
            BrokerSession entity2 = createTestEntity();

            Set<String> keys = Set.of(
                BrokerAuthConstants.SESSION_KEY_PREFIX + "session1",
                BrokerAuthConstants.SESSION_KEY_PREFIX + "session2"
            );

            when(redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*")).thenReturn(keys);
            when(valueOperations.get(BrokerAuthConstants.SESSION_KEY_PREFIX + "session1"))
                .thenReturn(entity1);
            when(valueOperations.get(BrokerAuthConstants.SESSION_KEY_PREFIX + "session2"))
                .thenReturn(entity2);

            // When
            int count = sessionDomainService.countActiveSessions();

            // Then
            assertEquals(2, count, "Should count both active sessions");
        }

        @Test
        @DisplayName("Should find sessions expiring within threshold")
        void shouldFindSessionsExpiringWithinThreshold() {
            // Given - Session expiring in 3 minutes
            BrokerSession entity = BrokerSession.builder()
                .id(1L)
                .sessionId(TEST_SESSION_ID)
                .userId(TEST_USER_ID)
                .brokerType(TEST_BROKER)
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .status(SessionStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .lastAccessedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .metadata(null)
                .vaultPath("secret/data/test")
                .build();

            Set<String> keys = Set.of(BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID);

            when(redisTemplate.keys(BrokerAuthConstants.SESSION_KEY_PREFIX + "*")).thenReturn(keys);
            when(valueOperations.get(BrokerAuthConstants.SESSION_KEY_PREFIX + TEST_SESSION_ID))
                .thenReturn(entity);

            // When
            List<SessionDomain> result = sessionDomainService.findExpiringWithin(5);

            // Then
            assertEquals(1, result.size(), "Should find 1 session expiring within 5 minutes");
            assertEquals(TEST_SESSION_ID, result.get(0).sessionId());
        }
    }
}
