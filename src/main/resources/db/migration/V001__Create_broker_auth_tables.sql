-- Broker Authentication Service Database Schema
-- Created for TradeMaster platform
-- Version: 1.0.0

-- Enable UUID extension if not exists
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Brokers table - Configuration for supported brokers
CREATE TABLE brokers (
    id BIGSERIAL PRIMARY KEY,
    broker_type VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    login_url VARCHAR(500),
    
    -- Rate limiting configuration
    rate_limit_per_second INTEGER,
    rate_limit_per_minute INTEGER,
    rate_limit_per_day INTEGER,
    
    -- Session configuration
    session_validity_seconds BIGINT,
    max_sessions_per_user INTEGER,
    supports_token_refresh BOOLEAN DEFAULT false,
    
    -- Status and maintenance
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    is_maintenance BOOLEAN NOT NULL DEFAULT false,
    maintenance_message TEXT,
    
    -- Additional configuration as JSON
    configuration TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Broker Accounts table - User's broker account configurations
CREATE TABLE broker_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    broker_id BIGINT NOT NULL REFERENCES brokers(id) ON DELETE CASCADE,
    broker_type VARCHAR(50) NOT NULL,
    
    -- Account identification
    broker_user_id VARCHAR(100),
    broker_username VARCHAR(100),
    account_name VARCHAR(100),
    
    -- Encrypted credentials
    encrypted_api_key TEXT,
    encrypted_api_secret TEXT,
    encrypted_password TEXT,
    encrypted_totp_secret TEXT,
    
    -- OAuth specific fields
    client_id VARCHAR(255),
    redirect_uri VARCHAR(500),
    
    -- Account status
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_verified BOOLEAN NOT NULL DEFAULT false,
    last_verified_at TIMESTAMP,
    
    -- Connection statistics
    total_connections INTEGER NOT NULL DEFAULT 0,
    successful_connections INTEGER NOT NULL DEFAULT 0,
    failed_connections INTEGER NOT NULL DEFAULT 0,
    last_connection_at TIMESTAMP,
    last_error_message TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_user_broker UNIQUE (user_id, broker_type)
);

-- Broker Sessions table - Active authentication sessions
CREATE TABLE broker_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    broker_account_id BIGINT NOT NULL REFERENCES broker_accounts(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    broker_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    
    -- Token information (encrypted)
    encrypted_access_token TEXT,
    encrypted_refresh_token TEXT,
    token_type VARCHAR(50) DEFAULT 'Bearer',
    
    -- Session timing
    expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    last_refreshed_at TIMESTAMP,
    
    -- Request information
    request_token VARCHAR(500),
    authorization_code VARCHAR(500),
    redirect_uri VARCHAR(500),
    
    -- Session metadata
    client_ip VARCHAR(45),
    user_agent TEXT,
    login_method VARCHAR(50),
    
    -- Usage statistics
    api_calls_count INTEGER NOT NULL DEFAULT 0,
    rate_limit_hits INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    
    -- Status information
    error_message TEXT,
    revocation_reason VARCHAR(255),
    revoked_at TIMESTAMP,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization

-- Brokers indexes
CREATE INDEX idx_brokers_type ON brokers(broker_type);
CREATE INDEX idx_brokers_enabled ON brokers(is_enabled, is_maintenance);

-- Broker Accounts indexes
CREATE INDEX idx_broker_accounts_user_id ON broker_accounts(user_id);
CREATE INDEX idx_broker_accounts_broker_type ON broker_accounts(broker_type);
CREATE INDEX idx_broker_accounts_active ON broker_accounts(is_active, is_verified);
CREATE INDEX idx_broker_accounts_last_connection ON broker_accounts(last_connection_at);

-- Broker Sessions indexes
CREATE INDEX idx_broker_sessions_session_id ON broker_sessions(session_id);
CREATE INDEX idx_broker_sessions_user_broker ON broker_sessions(user_id, broker_type);
CREATE INDEX idx_broker_sessions_status ON broker_sessions(status);
CREATE INDEX idx_broker_sessions_expires_at ON broker_sessions(expires_at);
CREATE INDEX idx_broker_sessions_user_id ON broker_sessions(user_id);
CREATE INDEX idx_broker_sessions_broker_account ON broker_sessions(broker_account_id);
CREATE INDEX idx_broker_sessions_last_used ON broker_sessions(last_used_at);

-- Composite indexes for common queries
CREATE INDEX idx_broker_sessions_active ON broker_sessions(status, expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_broker_sessions_expired ON broker_sessions(status, expires_at) WHERE status = 'ACTIVE' AND expires_at < CURRENT_TIMESTAMP;
CREATE INDEX idx_broker_sessions_refresh_needed ON broker_sessions(status, expires_at, encrypted_refresh_token) 
    WHERE status = 'ACTIVE' AND encrypted_refresh_token IS NOT NULL;

-- Update timestamp function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_brokers_updated_at BEFORE UPDATE ON brokers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_broker_accounts_updated_at BEFORE UPDATE ON broker_accounts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_broker_sessions_updated_at BEFORE UPDATE ON broker_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default broker configurations
INSERT INTO brokers (broker_type, display_name, base_url, login_url, rate_limit_per_second, rate_limit_per_minute, rate_limit_per_day, 
                     session_validity_seconds, max_sessions_per_user, supports_token_refresh) VALUES 
('ZERODHA', 'Zerodha Kite', 'https://api.kite.trade', 'https://kite.zerodha.com/connect/login', 10, 3000, 200000, 86400, 1, false),
('UPSTOX', 'Upstox Pro', 'https://api.upstox.com/v2', 'https://api.upstox.com/v2/login/authorization/dialog', 25, 250, 25000, 86400, 3, true),
('ANGEL_ONE', 'Angel One SmartAPI', 'https://apiconnect.angelbroking.com', NULL, 25, 200, 100000, 43200, 1, true),
('ICICI_DIRECT', 'ICICI Direct', 'https://api.icicidirect.com', NULL, 10, 100, 10000, 28800, 2, false);

-- Functions for business logic

-- Function to check if user has reached max sessions for a broker
CREATE OR REPLACE FUNCTION check_max_sessions_limit(p_user_id BIGINT, p_broker_type VARCHAR(50))
RETURNS BOOLEAN AS $$
DECLARE
    active_sessions_count INTEGER;
    max_sessions_allowed INTEGER;
BEGIN
    -- Get max sessions allowed for this broker
    SELECT max_sessions_per_user INTO max_sessions_allowed
    FROM brokers 
    WHERE broker_type = p_broker_type AND is_enabled = true;
    
    IF max_sessions_allowed IS NULL THEN
        RETURN false; -- Broker not found or not enabled
    END IF;
    
    -- Count active sessions for user and broker
    SELECT COUNT(*) INTO active_sessions_count
    FROM broker_sessions
    WHERE user_id = p_user_id 
      AND broker_type = p_broker_type 
      AND status = 'ACTIVE'
      AND expires_at > CURRENT_TIMESTAMP;
    
    RETURN active_sessions_count < max_sessions_allowed;
END;
$$ LANGUAGE plpgsql;

-- Function to get broker account health score
CREATE OR REPLACE FUNCTION get_account_health_score(p_account_id BIGINT)
RETURNS DECIMAL AS $$
DECLARE
    success_rate DECIMAL;
    recent_activity_score DECIMAL;
    total_score DECIMAL;
    total_connections INTEGER;
    successful_connections INTEGER;
    days_since_last_connection INTEGER;
BEGIN
    SELECT total_connections, successful_connections,
           COALESCE(EXTRACT(DAY FROM (CURRENT_TIMESTAMP - last_connection_at)), 999) 
    INTO total_connections, successful_connections, days_since_last_connection
    FROM broker_accounts
    WHERE id = p_account_id;
    
    -- Calculate success rate (0-100)
    IF total_connections > 0 THEN
        success_rate := (successful_connections::DECIMAL / total_connections) * 100;
    ELSE
        success_rate := 100; -- No connections yet, assume healthy
    END IF;
    
    -- Calculate recent activity score (penalize old connections)
    CASE 
        WHEN days_since_last_connection <= 1 THEN recent_activity_score := 100;
        WHEN days_since_last_connection <= 7 THEN recent_activity_score := 80;
        WHEN days_since_last_connection <= 30 THEN recent_activity_score := 60;
        WHEN days_since_last_connection <= 90 THEN recent_activity_score := 40;
        ELSE recent_activity_score := 0;
    END CASE;
    
    -- Combined score (70% success rate, 30% recent activity)
    total_score := (success_rate * 0.7) + (recent_activity_score * 0.3);
    
    RETURN ROUND(total_score, 2);
END;
$$ LANGUAGE plpgsql;

-- Function to cleanup expired sessions
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    -- Update expired active sessions
    UPDATE broker_sessions 
    SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
    WHERE status = 'ACTIVE' 
      AND expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- Comments for documentation
COMMENT ON TABLE brokers IS 'Configuration for supported brokers in the system';
COMMENT ON TABLE broker_accounts IS 'User broker account configurations with encrypted credentials';
COMMENT ON TABLE broker_sessions IS 'Active authentication sessions with brokers';

COMMENT ON COLUMN broker_accounts.encrypted_api_key IS 'AES encrypted API key for broker authentication';
COMMENT ON COLUMN broker_accounts.encrypted_api_secret IS 'AES encrypted API secret for broker authentication';
COMMENT ON COLUMN broker_accounts.encrypted_password IS 'AES encrypted password for broker authentication';
COMMENT ON COLUMN broker_accounts.encrypted_totp_secret IS 'AES encrypted TOTP secret for 2FA authentication';

COMMENT ON COLUMN broker_sessions.encrypted_access_token IS 'AES encrypted access token from broker';
COMMENT ON COLUMN broker_sessions.encrypted_refresh_token IS 'AES encrypted refresh token from broker';

-- Create view for active sessions summary
CREATE VIEW active_sessions_summary AS
SELECT 
    bs.broker_type,
    ba.user_id,
    COUNT(*) as active_sessions,
    MAX(bs.expires_at) as latest_expiry,
    MIN(bs.expires_at) as earliest_expiry,
    SUM(bs.api_calls_count) as total_api_calls,
    AVG(bs.api_calls_count) as avg_api_calls_per_session
FROM broker_sessions bs
JOIN broker_accounts ba ON bs.broker_account_id = ba.id
WHERE bs.status = 'ACTIVE' 
  AND bs.expires_at > CURRENT_TIMESTAMP
GROUP BY bs.broker_type, ba.user_id;

COMMENT ON VIEW active_sessions_summary IS 'Summary of active sessions per user and broker type';