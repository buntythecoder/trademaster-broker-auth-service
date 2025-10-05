package com.trademaster.brokerauth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Request Filter for TradeMaster Golden Specification Compliance
 *
 * MANDATORY: Golden Specification Security Implementation
 * MANDATORY: Zero Trust Security Model - JWT Token Validation
 * MANDATORY: External API Authentication Filter
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Component
@Order(2) // Run after ServiceApiKeyFilter
@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EXTERNAL_API_PATH = "/api/v1/";

    @Value("${trademaster.security.jwt.secret}")
    private String jwtSecret;

    @Value("${trademaster.security.jwt.enabled:true}")
    private boolean jwtAuthEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only process external API requests (internal APIs use ServiceApiKeyFilter)
        if (!requestPath.startsWith(EXTERNAL_API_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT validation if disabled or for public endpoints
        if (!jwtAuthEnabled || isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwtToken = extractJwtToken(request);

            if (StringUtils.hasText(jwtToken)) {
                if (validateAndSetAuthentication(request, jwtToken)) {
                    log.debug("JWT authentication successful for path: {}", requestPath);
                } else {
                    log.warn("JWT validation failed for path: {}", requestPath);
                }
            } else {
                log.debug("No JWT token found in request headers for path: {}", requestPath);
            }

        } catch (Exception e) {
            log.error("JWT authentication error for path {}: {}", requestPath, e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        return null;
    }

    /**
     * Validate JWT token and set authentication context
     */
    private boolean validateAndSetAuthentication(HttpServletRequest request, String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

            String username = claims.getSubject();
            String userId = claims.get("user_id", String.class);
            List<String> roles = claims.get("roles", List.class);

            if (StringUtils.hasText(username) && roles != null && !roles.isEmpty()) {
                List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Add custom claims to authentication details
                authentication.setDetails(createAuthenticationDetails(request, userId, claims));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.info("JWT authentication successful for user: {} (ID: {}) with roles: {}",
                        username, userId, roles);

                return true;
            }

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token invalid: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * Create enhanced authentication details with JWT claims
     */
    private Object createAuthenticationDetails(HttpServletRequest request, String userId, Claims claims) {
        return new JwtAuthenticationDetails(
            new WebAuthenticationDetailsSource().buildDetails(request),
            userId,
            claims.get("email", String.class),
            claims.getIssuedAt(),
            claims.getExpiration()
        );
    }

    /**
     * Check if the endpoint is public (no authentication required)
     */
    private boolean isPublicEndpoint(String path) {
        List<String> publicPaths = List.of(
            "/api/v1/broker-auth/authenticate",
            "/api/v1/broker-auth/health",
            "/api/v1/broker-auth/status",
            "/v3/api-docs",
            "/swagger-ui"
        );

        return publicPaths.stream().anyMatch(path::startsWith);
    }

    /**
     * Custom authentication details to include JWT claims
     */
    public static class JwtAuthenticationDetails {
        private final Object webAuthDetails;
        private final String userId;
        private final String email;
        private final java.util.Date issuedAt;
        private final java.util.Date expiration;

        public JwtAuthenticationDetails(Object webAuthDetails, String userId, String email,
                                      java.util.Date issuedAt, java.util.Date expiration) {
            this.webAuthDetails = webAuthDetails;
            this.userId = userId;
            this.email = email;
            this.issuedAt = issuedAt;
            this.expiration = expiration;
        }

        // Getters
        public Object getWebAuthDetails() { return webAuthDetails; }
        public String getUserId() { return userId; }
        public String getEmail() { return email; }
        public java.util.Date getIssuedAt() { return issuedAt; }
        public java.util.Date getExpiration() { return expiration; }
    }
}