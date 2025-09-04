package com.trademaster.brokerauth.enums;

/**
 * Supported Broker Types
 * 
 * Enumeration of supported brokers for authentication.
 * 
 * MANDATORY: Records/Enums - Rule #9
 * MANDATORY: Pattern Matching - Rule #14
 */
public enum BrokerType {
    ZERODHA("Zerodha", "https://api.zerodha.com", "OAuth"),
    UPSTOX("Upstox", "https://api.upstox.com", "OAuth"),
    ANGEL_ONE("Angel One", "https://apiconnect.angelbroking.com", "JWT"),
    ICICI_DIRECT("ICICI Direct", "https://api.icicidirect.com/breezeapi", "SessionToken");
    
    private final String displayName;
    private final String apiBaseUrl;
    private final String authType;
    
    BrokerType(String displayName, String apiBaseUrl, String authType) {
        this.displayName = displayName;
        this.apiBaseUrl = apiBaseUrl;
        this.authType = authType;
    }
    
    public String getDisplayName() { return displayName; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public String getAuthType() { return authType; }
}