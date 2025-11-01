package com.trademaster.brokerauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Session Refresh Request DTO
 *
 * Immutable request record for session token refresh operations.
 *
 * MANDATORY: Records Usage - Rule #9
 * MANDATORY: Immutability - Rule #9
 * MANDATORY: Input Validation - Rule #11
 *
 * @param refreshToken The refresh token for session renewal
 */
@Schema(description = "Session refresh request for internal API")
public record SessionRefreshRequest(
    @Schema(description = "Refresh token for session renewal", example = "rt_abc123xyz")
    @NotBlank(message = "Refresh token cannot be blank")
    String refreshToken
) {
    /**
     * Compact constructor for validation - Rule #9
     */
    public SessionRefreshRequest {
        java.util.Objects.requireNonNull(refreshToken, "Refresh token cannot be null");

        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be blank");
        }
    }
}
