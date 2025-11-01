package com.trademaster.brokerauth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for ServiceApiKeyFilter - Kong Integration
 *
 * MANDATORY: Rule #20 - Unit Tests with >80% coverage
 * MANDATORY: Rule #9 - Functional test builders
 * MANDATORY: Rule #3 - Functional programming patterns
 *
 * Tests Cover:
 * 1. Kong consumer header authentication (X-Consumer-ID, X-Consumer-Username)
 * 2. Fallback API key validation
 * 3. ROLE_SERVICE assignment
 * 4. Internal API path filtering
 * 5. Bypass for external APIs
 * 6. Correlation ID tracking
 * 7. Unauthorized responses
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceApiKeyFilter - Kong Integration Tests")
class ServiceApiKeyFilterTest {

    private ServiceApiKeyFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ServiceApiKeyFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();

        // Set default values for filter properties
        ReflectionTestUtils.setField(filter, "fallbackServiceApiKey", "test-api-key-2024");
        ReflectionTestUtils.setField(filter, "serviceAuthEnabled", true);
    }

    @Nested
    @DisplayName("Kong Consumer Header Authentication")
    class KongConsumerHeaderTests {

        @Test
        @DisplayName("Should authenticate with valid Kong consumer headers")
        void shouldAuthenticateWithKongConsumerHeaders() throws IOException, ServletException {
            // Given: Internal API request with Kong consumer headers
            request.setRequestURI("/api/internal/v1/broker-auth/health");
            request.addHeader("X-Consumer-ID", "broker-auth-internal");
            request.addHeader("X-Consumer-Username", "broker-auth-service");
            request.addHeader("X-Correlation-ID", "test-correlation-123");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should proceed and ROLE_SERVICE should be assigned
            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_INTERNAL");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("broker-auth-service");
        }

        @Test
        @DisplayName("Should fail authentication when Kong consumer ID missing")
        void shouldFailWhenKongConsumerIdMissing() throws IOException, ServletException {
            // Given: Internal API request with only username header
            request.setRequestURI("/api/internal/v1/broker-auth/status");
            request.addHeader("X-Consumer-Username", "broker-auth-service");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should be rejected
            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("SERVICE_AUTHENTICATION_FAILED");
        }

        @Test
        @DisplayName("Should fail authentication when Kong consumer username missing")
        void shouldFailWhenKongConsumerUsernameMissing() throws IOException, ServletException {
            // Given: Internal API request with only consumer ID header
            request.setRequestURI("/api/internal/v1/broker-auth/status");
            request.addHeader("X-Consumer-ID", "broker-auth-internal");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should be rejected
            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Should use consumer username as service principal")
        void shouldUseConsumerUsernameAsPrincipal() throws IOException, ServletException {
            // Given: Internal API request with Kong headers
            request.setRequestURI("/api/internal/v1/broker-auth/sessions/123/validate");
            request.addHeader("X-Consumer-ID", "trading-service-id");
            request.addHeader("X-Consumer-Username", "trading-service");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Principal should match consumer username
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("trading-service");
        }
    }

    @Nested
    @DisplayName("Fallback API Key Authentication")
    class FallbackApiKeyTests {

        @Test
        @DisplayName("Should authenticate with valid fallback API key")
        void shouldAuthenticateWithFallbackApiKey() throws IOException, ServletException {
            // Given: Internal API request with valid API key
            request.setRequestURI("/api/internal/v1/broker-auth/health");
            request.addHeader("X-API-Key", "test-api-key-2024");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should proceed with direct-service-call principal
            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("direct-service-call");
        }

        @Test
        @DisplayName("Should fail with invalid API key")
        void shouldFailWithInvalidApiKey() throws IOException, ServletException {
            // Given: Internal API request with wrong API key
            request.setRequestURI("/api/internal/v1/broker-auth/status");
            request.addHeader("X-API-Key", "wrong-api-key");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should be rejected
            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Should prioritize Kong headers over API key")
        void shouldPrioritizeKongHeadersOverApiKey() throws IOException, ServletException {
            // Given: Request with both Kong headers and API key
            request.setRequestURI("/api/internal/v1/broker-auth/health");
            request.addHeader("X-Consumer-ID", "broker-auth-internal");
            request.addHeader("X-Consumer-Username", "broker-auth-service");
            request.addHeader("X-API-Key", "test-api-key-2024");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Kong headers should take precedence
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("broker-auth-service"); // Not "direct-service-call"
        }
    }

    @Nested
    @DisplayName("Internal API Path Filtering")
    class InternalApiPathTests {

        @Test
        @DisplayName("Should bypass authentication for external APIs")
        void shouldBypassExternalApis() throws IOException, ServletException {
            // Given: External API request
            request.setRequestURI("/api/v1/broker-auth/authenticate");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should proceed without authentication
            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Should enforce authentication for internal APIs")
        void shouldEnforceAuthForInternalApis() throws IOException, ServletException {
            // Given: Internal API request without credentials
            request.setRequestURI("/api/internal/v1/broker-auth/status");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should be rejected
            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Should handle various internal API paths")
        void shouldHandleVariousInternalPaths() throws IOException, ServletException {
            String[] internalPaths = {
                "/api/internal/v1/broker-auth/health",
                "/api/internal/v1/broker-auth/sessions/123/validate",
                "/api/internal/v1/broker-auth/sessions/user/user123"
            };

            for (String path : internalPaths) {
                setUp(); // Reset for each iteration
                request.setRequestURI(path);
                request.addHeader("X-Consumer-ID", "test-consumer");
                request.addHeader("X-Consumer-Username", "test-service");

                filter.doFilter(request, response, filterChain);

                verify(filterChain).doFilter(request, response);
            }
        }
    }

    @Nested
    @DisplayName("Correlation ID Tracking")
    class CorrelationIdTests {

        @Test
        @DisplayName("Should use provided correlation ID")
        void shouldUseProvidedCorrelationId() throws IOException, ServletException {
            // Given: Request with correlation ID header
            request.setRequestURI("/api/internal/v1/broker-auth/health");
            request.addHeader("X-Correlation-ID", "custom-correlation-456");
            request.addHeader("X-Consumer-ID", "test-consumer");
            request.addHeader("X-Consumer-Username", "test-service");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should succeed
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should generate correlation ID if not provided")
        void shouldGenerateCorrelationIdIfMissing() throws IOException, ServletException {
            // Given: Request without correlation ID
            request.setRequestURI("/api/internal/v1/broker-auth/health");
            request.addHeader("X-Consumer-ID", "test-consumer");
            request.addHeader("X-Consumer-Username", "test-service");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should succeed
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should include correlation ID in error response")
        void shouldIncludeCorrelationIdInError() throws IOException, ServletException {
            // Given: Failing request with correlation ID
            request.setRequestURI("/api/internal/v1/broker-auth/status");
            request.addHeader("X-Correlation-ID", "error-correlation-789");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Error response should include correlation ID
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("error-correlation-789");
        }
    }

    @Nested
    @DisplayName("ROLE_SERVICE Assignment")
    class RoleServiceTests {

        @Test
        @DisplayName("Should assign ROLE_SERVICE and ROLE_INTERNAL")
        void shouldAssignServiceAndInternalRoles() throws IOException, ServletException {
            // Given: Valid Kong authentication
            request.setRequestURI("/api/internal/v1/broker-auth/health");
            request.addHeader("X-Consumer-ID", "test-consumer");
            request.addHeader("X-Consumer-Username", "test-service");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Both roles should be assigned
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_INTERNAL");
        }

        @Test
        @DisplayName("Should allow @PreAuthorize with ROLE_SERVICE")
        void shouldAllowPreAuthorizeWithRoleService() throws IOException, ServletException {
            // Given: Valid authentication
            request.setRequestURI("/api/internal/v1/broker-auth/status");
            request.addHeader("X-Consumer-ID", "test-consumer");
            request.addHeader("X-Consumer-Username", "test-service");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: ROLE_SERVICE should be present for @PreAuthorize checks
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(auth -> "ROLE_SERVICE".equals(auth.getAuthority()));
        }
    }

    @Nested
    @DisplayName("Development Mode")
    class DevelopmentModeTests {

        @Test
        @DisplayName("Should bypass authentication when disabled")
        void shouldBypassWhenDisabled() throws IOException, ServletException {
            // Given: Service auth disabled
            ReflectionTestUtils.setField(filter, "serviceAuthEnabled", false);
            request.setRequestURI("/api/internal/v1/broker-auth/health");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Request should proceed
            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("development-service");
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseTests {

        @Test
        @DisplayName("Should return proper JSON error response")
        void shouldReturnProperJsonErrorResponse() throws IOException, ServletException {
            // Given: Invalid request
            request.setRequestURI("/api/internal/v1/broker-auth/status");

            // When: Filter processes request
            filter.doFilter(request, response, filterChain);

            // Then: Response should be proper JSON
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString())
                .contains("SERVICE_AUTHENTICATION_FAILED")
                .contains("broker-auth-service")
                .contains("timestamp")
                .contains("correlationId");
        }
    }
}
