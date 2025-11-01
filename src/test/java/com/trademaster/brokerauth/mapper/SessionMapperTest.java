package com.trademaster.brokerauth.mapper;

import com.trademaster.brokerauth.domain.model.SessionDomain;
import com.trademaster.brokerauth.dto.SessionResponseDTO;
import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionMapper Unit Tests
 *
 * MANDATORY: Rule #20 - >80% unit test coverage
 * MANDATORY: Rule #3 - Functional test patterns
 *
 * Tests bidirectional transformation between Entity, Domain, and DTO layers.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@DisplayName("SessionMapper Unit Tests")
class SessionMapperTest {

    private SessionMapper sessionMapper;

    private static final Long TEST_ID = 1L;
    private static final String TEST_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_USER_ID = "USER123";
    private static final BrokerType TEST_BROKER = BrokerType.ZERODHA;
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";
    private static final String TEST_METADATA = "{\"ip\": \"192.168.1.1\"}";
    private static final String TEST_VAULT_PATH = "secret/data/trademaster/sessions/USER123/zerodha";

    @BeforeEach
    void setUp() {
        sessionMapper = new SessionMapper();
    }

    /**
     * Create test entity
     */
    private BrokerSession createTestEntity() {
        return BrokerSession.builder()
            .id(TEST_ID)
            .sessionId(TEST_SESSION_ID)
            .userId(TEST_USER_ID)
            .brokerType(TEST_BROKER)
            .accessToken(TEST_ACCESS_TOKEN)
            .refreshToken(TEST_REFRESH_TOKEN)
            .status(SessionStatus.ACTIVE)
            .createdAt(LocalDateTime.now().minusHours(1))
            .expiresAt(LocalDateTime.now().plusHours(8))
            .lastAccessedAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .metadata(TEST_METADATA)
            .vaultPath(TEST_VAULT_PATH)
            .build();
    }

    /**
     * Create test domain
     */
    private SessionDomain createTestDomain() {
        return new SessionDomain(
            TEST_ID,
            TEST_SESSION_ID,
            TEST_USER_ID,
            TEST_BROKER,
            TEST_ACCESS_TOKEN,
            TEST_REFRESH_TOKEN,
            SessionStatus.ACTIVE,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(8),
            LocalDateTime.now(),
            LocalDateTime.now(),
            TEST_METADATA,
            TEST_VAULT_PATH
        );
    }

    @Nested
    @DisplayName("Entity to Domain Transformation Tests")
    class EntityToDomainTests {

        @Test
        @DisplayName("Should convert entity to domain successfully")
        void shouldConvertEntityToDomain() {
            // Given
            BrokerSession entity = createTestEntity();

            // When
            SessionDomain domain = sessionMapper.toDomain(entity);

            // Then
            assertNotNull(domain);
            assertEquals(entity.getId(), domain.id());
            assertEquals(entity.getSessionId(), domain.sessionId());
            assertEquals(entity.getUserId(), domain.userId());
            assertEquals(entity.getBrokerType(), domain.brokerType());
            assertEquals(entity.getAccessToken(), domain.accessToken());
            assertEquals(entity.getRefreshToken(), domain.refreshToken());
            assertEquals(entity.getStatus(), domain.status());
            assertEquals(entity.getVaultPath(), domain.vaultPath());
        }

        @Test
        @DisplayName("Should throw exception for null entity")
        void shouldThrowExceptionForNullEntity() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                sessionMapper.toDomain((BrokerSession) null)
            );
        }

        @Test
        @DisplayName("Should convert optional entity to optional domain")
        void shouldConvertOptionalEntityToOptionalDomain() {
            // Given
            Optional<BrokerSession> entityOpt = Optional.of(createTestEntity());

            // When
            Optional<SessionDomain> domainOpt = sessionMapper.toDomain(entityOpt);

            // Then
            assertTrue(domainOpt.isPresent());
            assertEquals(TEST_SESSION_ID, domainOpt.get().sessionId());
        }

        @Test
        @DisplayName("Should return empty optional when entity optional is empty")
        void shouldReturnEmptyOptionalWhenEntityOptionalIsEmpty() {
            // Given
            Optional<BrokerSession> entityOpt = Optional.empty();

            // When
            Optional<SessionDomain> domainOpt = sessionMapper.toDomain(entityOpt);

            // Then
            assertTrue(domainOpt.isEmpty());
        }
    }

    @Nested
    @DisplayName("Domain to Entity Transformation Tests")
    class DomainToEntityTests {

        @Test
        @DisplayName("Should convert domain to entity successfully")
        void shouldConvertDomainToEntity() {
            // Given
            SessionDomain domain = createTestDomain();

            // When
            BrokerSession entity = sessionMapper.toEntity(domain);

            // Then
            assertNotNull(entity);
            assertEquals(domain.id(), entity.getId());
            assertEquals(domain.sessionId(), entity.getSessionId());
            assertEquals(domain.userId(), entity.getUserId());
            assertEquals(domain.brokerType(), entity.getBrokerType());
            assertEquals(domain.accessToken(), entity.getAccessToken());
            assertEquals(domain.refreshToken(), entity.getRefreshToken());
            assertEquals(domain.status(), entity.getStatus());
            assertEquals(domain.vaultPath(), entity.getVaultPath());
        }

        @Test
        @DisplayName("Should throw exception for null domain")
        void shouldThrowExceptionForNullDomain() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                sessionMapper.toEntity(null)
            );
        }
    }

    @Nested
    @DisplayName("Domain to DTO Transformation Tests")
    class DomainToDTOTests {

        @Test
        @DisplayName("Should convert domain to DTO successfully")
        void shouldConvertDomainToDTO() {
            // Given
            SessionDomain domain = createTestDomain();

            // When
            SessionResponseDTO dto = sessionMapper.toDTO(domain);

            // Then
            assertNotNull(dto);
            assertEquals(domain.id(), dto.id());
            assertEquals(domain.sessionId(), dto.sessionId());
            assertEquals(domain.userId(), dto.userId());
            assertEquals(domain.brokerType(), dto.brokerType());
            assertEquals(domain.status(), dto.status());
            assertEquals(domain.vaultPath(), dto.vaultPath());
        }

        @Test
        @DisplayName("Should throw exception for null domain when converting to DTO")
        void shouldThrowExceptionForNullDomainWhenConvertingToDTO() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                sessionMapper.toDTO((SessionDomain) null)
            );
        }

        @Test
        @DisplayName("Should convert optional domain to optional DTO")
        void shouldConvertOptionalDomainToOptionalDTO() {
            // Given
            Optional<SessionDomain> domainOpt = Optional.of(createTestDomain());

            // When
            Optional<SessionResponseDTO> dtoOpt = sessionMapper.toDTO(domainOpt);

            // Then
            assertTrue(dtoOpt.isPresent());
            assertEquals(TEST_SESSION_ID, dtoOpt.get().sessionId());
        }

        @Test
        @DisplayName("Should return empty optional when domain optional is empty")
        void shouldReturnEmptyOptionalWhenDomainOptionalIsEmpty() {
            // Given
            Optional<SessionDomain> domainOpt = Optional.empty();

            // When
            Optional<SessionResponseDTO> dtoOpt = sessionMapper.toDTO(domainOpt);

            // Then
            assertTrue(dtoOpt.isEmpty());
        }
    }

    @Nested
    @DisplayName("Bidirectional Transformation Tests")
    class BidirectionalTransformationTests {

        @Test
        @DisplayName("Should maintain data integrity in entity->domain->entity transformation")
        void shouldMaintainDataIntegrityInEntityDomainEntityTransformation() {
            // Given
            BrokerSession originalEntity = createTestEntity();

            // When
            SessionDomain domain = sessionMapper.toDomain(originalEntity);
            BrokerSession recreatedEntity = sessionMapper.toEntity(domain);

            // Then
            assertEquals(originalEntity.getId(), recreatedEntity.getId());
            assertEquals(originalEntity.getSessionId(), recreatedEntity.getSessionId());
            assertEquals(originalEntity.getUserId(), recreatedEntity.getUserId());
            assertEquals(originalEntity.getBrokerType(), recreatedEntity.getBrokerType());
            assertEquals(originalEntity.getStatus(), recreatedEntity.getStatus());
        }

        @Test
        @DisplayName("Should maintain data integrity in domain->entity->domain transformation")
        void shouldMaintainDataIntegrityInDomainEntityDomainTransformation() {
            // Given
            SessionDomain originalDomain = createTestDomain();

            // When
            BrokerSession entity = sessionMapper.toEntity(originalDomain);
            SessionDomain recreatedDomain = sessionMapper.toDomain(entity);

            // Then
            assertEquals(originalDomain.id(), recreatedDomain.id());
            assertEquals(originalDomain.sessionId(), recreatedDomain.sessionId());
            assertEquals(originalDomain.userId(), recreatedDomain.userId());
            assertEquals(originalDomain.brokerType(), recreatedDomain.brokerType());
            assertEquals(originalDomain.status(), recreatedDomain.status());
        }
    }

    @Nested
    @DisplayName("Entity Update Tests")
    class EntityUpdateTests {

        @Test
        @DisplayName("Should update entity from domain preserving database ID")
        void shouldUpdateEntityFromDomainPreservingDatabaseId() {
            // Given
            BrokerSession existingEntity = createTestEntity();
            Long originalId = existingEntity.getId();

            SessionDomain updatedDomain = new SessionDomain(
                99L, // Different ID
                TEST_SESSION_ID,
                TEST_USER_ID,
                TEST_BROKER,
                "new-access-token",
                "new-refresh-token",
                SessionStatus.EXPIRED,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusHours(6),
                LocalDateTime.now(),
                LocalDateTime.now(),
                TEST_METADATA,
                TEST_VAULT_PATH
            );

            // When
            BrokerSession updatedEntity = sessionMapper.updateEntityFromDomain(existingEntity, updatedDomain);

            // Then
            assertEquals(originalId, updatedEntity.getId(), "Should preserve original database ID");
            assertEquals("new-access-token", updatedEntity.getAccessToken());
            assertEquals("new-refresh-token", updatedEntity.getRefreshToken());
            assertEquals(SessionStatus.EXPIRED, updatedEntity.getStatus());
        }

        @Test
        @DisplayName("Should throw exception when updating null entity")
        void shouldThrowExceptionWhenUpdatingNullEntity() {
            // Given
            SessionDomain domain = createTestDomain();

            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                sessionMapper.updateEntityFromDomain(null, domain)
            );
        }
    }
}
