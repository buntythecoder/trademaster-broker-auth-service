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

    /**
     * Builder pattern for AuthRequest records - Rule #9
     */
    public static AuthRequestBuilder builder() {
        return new AuthRequestBuilder();
    }

    public static class AuthRequestBuilder {
        private BrokerType brokerType;
        private String apiKey;
        private String apiSecret;
        private String userId;
        private String password;
        private String totpCode;

        public AuthRequestBuilder brokerType(BrokerType brokerType) {
            this.brokerType = brokerType;
            return this;
        }

        public AuthRequestBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public AuthRequestBuilder apiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
            return this;
        }

        public AuthRequestBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AuthRequestBuilder password(String password) {
            this.password = password;
            return this;
        }

        public AuthRequestBuilder totpCode(String totpCode) {
            this.totpCode = totpCode;
            return this;
        }

        public AuthRequest build() {
            return new AuthRequest(brokerType, apiKey, apiSecret, userId, password, totpCode);
        }
    }
}