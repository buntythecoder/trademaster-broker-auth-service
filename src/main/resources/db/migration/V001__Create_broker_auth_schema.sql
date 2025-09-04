-- TradeMaster Broker Authentication Service Database Schema
-- Version: V001 - Initial schema creation
-- MANDATORY: Production-ready database design - Rule #26

-- Enable UUID extension for better ID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Brokers table - Supported trading brokers configuration
CREATE TABLE brokers (
    id BIGSERIAL PRIMARY KEY,
    broker_type VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    api_url VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    session_validity_seconds INTEGER NOT NULL DEFAULT 86400,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization
CREATE INDEX idx_broker_type ON brokers(broker_type);
CREATE INDEX idx_broker_active ON brokers(is_active);

-- Broker rate limits table - Rate limiting configuration per broker
CREATE TABLE broker_rate_limits (
    broker_id BIGINT NOT NULL REFERENCES brokers(id) ON DELETE CASCADE,
    limit_type VARCHAR(50) NOT NULL,
    limit_value INTEGER NOT NULL,
    PRIMARY KEY (broker_id, limit_type)
);

-- Broker accounts table - User credentials for each broker
CREATE TABLE broker_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    broker_type VARCHAR(50) NOT NULL,
    broker_user_id VARCHAR(100) NOT NULL,
    encrypted_password TEXT,
    encrypted_api_key TEXT,
    encrypted_api_secret TEXT,
    encrypted_totp_secret TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint: one account per user per broker
    CONSTRAINT uk_user_broker UNIQUE (user_id, broker_type)
);

-- Indexes for performance optimization
CREATE INDEX idx_user_id ON broker_accounts(user_id);
CREATE INDEX idx_broker_type_accounts ON broker_accounts(broker_type);
CREATE INDEX idx_user_broker ON broker_accounts(user_id, broker_type);
CREATE INDEX idx_active_accounts ON broker_accounts(is_active);
CREATE INDEX idx_last_login ON broker_accounts(last_login_at);

-- Broker sessions table - Active authentication sessions
CREATE TABLE broker_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    broker_type VARCHAR(50) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    request_token VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key to broker_accounts
    CONSTRAINT fk_session_account 
        FOREIGN KEY (user_id, broker_type) 
        REFERENCES broker_accounts(user_id, broker_type) 
        ON DELETE CASCADE
);

-- Indexes for performance optimization
CREATE INDEX idx_session_id ON broker_sessions(session_id);
CREATE INDEX idx_user_sessions ON broker_sessions(user_id);
CREATE INDEX idx_broker_sessions ON broker_sessions(broker_type);
CREATE INDEX idx_session_status ON broker_sessions(status);
CREATE INDEX idx_session_expires ON broker_sessions(expires_at);
CREATE INDEX idx_session_user_broker ON broker_sessions(user_id, broker_type);
CREATE INDEX idx_access_token ON broker_sessions(access_token);

-- Audit log table - Security and compliance audit trail
CREATE TABLE broker_auth_audit (
    id BIGSERIAL PRIMARY KEY,
    correlation_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    broker_type VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    request_details JSONB,
    response_details JSONB,
    error_details JSONB,
    execution_time_ms INTEGER,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit queries
CREATE INDEX idx_audit_correlation ON broker_auth_audit(correlation_id);
CREATE INDEX idx_audit_user ON broker_auth_audit(user_id);
CREATE INDEX idx_audit_broker ON broker_auth_audit(broker_type);
CREATE INDEX idx_audit_action ON broker_auth_audit(action);
CREATE INDEX idx_audit_status ON broker_auth_audit(status);
CREATE INDEX idx_audit_timestamp ON broker_auth_audit(timestamp);
CREATE INDEX idx_audit_user_broker ON broker_auth_audit(user_id, broker_type);

-- Rate limiting table - Track API call rates per user/broker
CREATE TABLE rate_limit_tracking (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    broker_type VARCHAR(50) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    call_count INTEGER NOT NULL DEFAULT 1,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint for rate limiting window
    CONSTRAINT uk_rate_limit_window UNIQUE (user_id, broker_type, endpoint, window_start)
);

-- Indexes for rate limiting performance
CREATE INDEX idx_rate_limit_user ON rate_limit_tracking(user_id);
CREATE INDEX idx_rate_limit_broker ON rate_limit_tracking(broker_type);
CREATE INDEX idx_rate_limit_endpoint ON rate_limit_tracking(endpoint);
CREATE INDEX idx_rate_limit_window ON rate_limit_tracking(window_start, window_end);
CREATE INDEX idx_rate_limit_composite ON rate_limit_tracking(user_id, broker_type, endpoint);

-- Insert default broker configurations
INSERT INTO brokers (broker_type, name, api_url, session_validity_seconds) VALUES
('ZERODHA', 'Zerodha Kite', 'https://api.kite.trade', 86400),
('UPSTOX', 'Upstox Pro', 'https://api.upstox.com/v2', 86400),
('ANGEL_ONE', 'Angel One SmartAPI', 'https://apiconnect.angelbroking.com', 43200),
('ICICI_DIRECT', 'ICICI Direct Breeze', 'https://api.icicidirect.com/breezeapi', 86400);

-- Insert default rate limits for each broker
INSERT INTO broker_rate_limits (broker_id, limit_type, limit_value) VALUES
-- Zerodha rate limits
(1, 'per_second', 10),
(1, 'per_minute', 3000),
(1, 'per_day', 200000),

-- Upstox rate limits
(2, 'per_second', 25),
(2, 'per_minute', 250),
(2, 'per_day', 25000),

-- Angel One rate limits
(3, 'per_second', 25),
(3, 'per_minute', 200),
(3, 'per_day', 100000),

-- ICICI Direct rate limits
(4, 'per_second', 2),
(4, 'per_minute', 100),
(4, 'per_day', 5000);

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_brokers_updated_at BEFORE UPDATE ON brokers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_broker_accounts_updated_at BEFORE UPDATE ON broker_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_broker_sessions_updated_at BEFORE UPDATE ON broker_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_rate_limit_tracking_updated_at BEFORE UPDATE ON rate_limit_tracking
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create database user for the application (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_user WHERE usename = 'trademaster_broker_auth') THEN
        CREATE USER trademaster_broker_auth WITH PASSWORD 'secure_password_change_in_production';
    END IF;
END $$;

-- Grant appropriate permissions
GRANT CONNECT ON DATABASE trademaster_broker_auth TO trademaster_broker_auth;
GRANT USAGE ON SCHEMA public TO trademaster_broker_auth;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO trademaster_broker_auth;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO trademaster_broker_auth;

-- Grant permissions for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO trademaster_broker_auth;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE ON SEQUENCES TO trademaster_broker_auth;

-- Create indexes for better performance on frequently queried columns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_broker_sessions_composite 
    ON broker_sessions(user_id, broker_type, status, expires_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_performance 
    ON broker_auth_audit(user_id, timestamp DESC) 
    WHERE status IN ('SUCCESS', 'FAILURE');

-- Comments for documentation
COMMENT ON TABLE brokers IS 'Configuration for supported trading brokers';
COMMENT ON TABLE broker_accounts IS 'User credentials for each broker (encrypted)';
COMMENT ON TABLE broker_sessions IS 'Active authentication sessions with brokers';
COMMENT ON TABLE broker_auth_audit IS 'Audit trail for security and compliance';
COMMENT ON TABLE rate_limit_tracking IS 'Rate limiting tracking per user/broker/endpoint';

COMMENT ON COLUMN broker_accounts.encrypted_password IS 'AES-256 encrypted broker password';
COMMENT ON COLUMN broker_accounts.encrypted_api_key IS 'AES-256 encrypted broker API key';
COMMENT ON COLUMN broker_accounts.encrypted_api_secret IS 'AES-256 encrypted broker API secret';
COMMENT ON COLUMN broker_sessions.access_token IS 'Broker API access token (may be encrypted)';
COMMENT ON COLUMN broker_sessions.refresh_token IS 'Broker API refresh token (may be encrypted)';