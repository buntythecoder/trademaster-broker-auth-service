package com.trademaster.brokerauth.security;

import lombok.Getter;

/**
 * Security Level - Defines required security levels
 * 
 * MANDATORY: Enums for type safety - Rule #14
 * MANDATORY: Zero Trust - Rule #6
 */
@Getter
public enum SecurityLevel {
    
    PUBLIC(0, "Public access - no authentication required"),
    STANDARD(1, "Standard authentication required"),
    ELEVATED(2, "Elevated security - MFA required"),
    CRITICAL(3, "Critical operations - additional verification required");
    
    private final int level;
    private final String description;
    
    SecurityLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public boolean isHigherThan(SecurityLevel other) {
        return this.level > other.level;
    }
    
    public boolean isAtLeast(SecurityLevel other) {
        return this.level >= other.level;
    }
}