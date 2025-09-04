package com.trademaster.brokerauth.dto;

import com.trademaster.brokerauth.enums.BrokerType;
import java.time.LocalDateTime;

/**
 * Authentication Response
 * 
 * MANDATORY: Records - Rule #9
 * MANDATORY: Immutability - Rule #9
 */
public record AuthResponse(
    String sessionId,
    String accessToken,
    String refreshToken,
    BrokerType brokerType,
    LocalDateTime expiresAt,
    boolean success,
    String message
) {}