package com.trademaster.brokerauth.dto;

import com.trademaster.brokerauth.enums.BrokerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;

/**
 * Authentication Request
 * 
 * MANDATORY: Records - Rule #9
 * MANDATORY: Immutability - Rule #9
 */
public record AuthRequest(
    @NotNull BrokerType brokerType,
    @NotBlank String apiKey,
    @NotBlank String apiSecret,
    String userId,
    String password,
    String totpCode
) {
    public AuthRequest {
        Optional.ofNullable(brokerType)
            .orElseThrow(() -> new IllegalArgumentException("Broker type cannot be null"));
        Optional.ofNullable(apiKey)
            .filter(key -> !key.trim().isEmpty())
            .orElseThrow(() -> new IllegalArgumentException("API key cannot be null or empty"));
        Optional.ofNullable(apiSecret)
            .filter(secret -> !secret.trim().isEmpty())
            .orElseThrow(() -> new IllegalArgumentException("API secret cannot be null or empty"));
    }
}