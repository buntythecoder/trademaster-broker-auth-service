package com.trademaster.brokerauth.constant;

/**
 * Broker Authentication Service Constants
 * 
 * MANDATORY: Constants for Magic Numbers - Rule #17
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Immutable Constants - Rule #9
 */
public final class BrokerAuthConstants {
    
    // Private constructor to prevent instantiation
    private BrokerAuthConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
    
    // ===== SESSION MANAGEMENT =====
    public static final String SESSION_KEY_PREFIX = "session:";
    public static final String RATE_LIMIT_KEY_PREFIX = "rate_limit_";
    
    // ===== SECURITY CONSTANTS =====
    public static final int MAX_SESSION_AGE_HOURS = 24;
    public static final int MIN_USER_ID_LENGTH = 3;
    public static final int MAX_REQUESTS_PER_MINUTE = 100;
    public static final int HIGH_RISK_THRESHOLD = 75;
    
    // ===== BROKER TOKEN PREFIXES =====
    public static final String ZERODHA_TOKEN_PREFIX = "ZERODHA_TOKEN_";
    public static final String UPSTOX_TOKEN_PREFIX = "UPSTOX_TOKEN_";
    public static final String ANGEL_TOKEN_PREFIX = "ANGEL_TOKEN_";
    public static final String ICICI_TOKEN_PREFIX = "ICICI_TOKEN_";
    public static final String REFRESH_TOKEN_PREFIX = "REFRESH_";
    
    // ===== SESSION EXPIRY =====
    public static final int DEFAULT_SESSION_EXPIRY_HOURS = 8;
    public static final int ZERODHA_SESSION_EXPIRY_HOURS = 24;
    public static final int UPSTOX_SESSION_EXPIRY_HOURS = 24;
    public static final int ANGEL_ONE_SESSION_EXPIRY_HOURS = 12;
    public static final int ICICI_SESSION_EXPIRY_HOURS = 8;
    
    // ===== RISK ASSESSMENT =====
    public static final int PRIVATE_IP_RISK_SCORE = 0;
    public static final int PUBLIC_IP_RISK_SCORE = 20;
    public static final int MISSING_USER_RISK_SCORE = 50;
    public static final int OLD_REQUEST_RISK_SCORE = 30;
    public static final int MISSING_USER_AGENT_RISK_SCORE = 25;
    public static final int MAX_RISK_SCORE = 100;
    public static final int MEDIUM_RISK_THRESHOLD = 40;
    public static final int TIMING_RISK_THRESHOLD_MINUTES = 5;
    
    // ===== HTTP HEADERS =====
    public static final String CLIENT_ID_HEADER = "X-Client-ID";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String UNKNOWN_CLIENT = "unknown";
    
    // ===== AUDIT LOGGING =====
    public static final String SECURITY_AUDIT_MARKER = "SECURITY_AUDIT";
    public static final String USER_ID_MASK = "***";
    public static final String SESSION_ID_MASK = "***";
    public static final String IP_MASK = "masked";
    public static final String UNKNOWN_IP = "unknown";
    public static final int MIN_MASK_LENGTH = 3;
    public static final int USER_ID_VISIBLE_CHARS = 2;
    public static final int SESSION_ID_VISIBLE_CHARS = 4;
    public static final int MIN_SESSION_ID_LENGTH = 8;
    
    // ===== CLIENT IDS =====
    public static final String TRADEMASTER_WEB_CLIENT = "trademaster-web";
    public static final String TRADEMASTER_MOBILE_CLIENT = "trademaster-mobile";
    public static final String TRADEMASTER_API_CLIENT = "trademaster-api";
}