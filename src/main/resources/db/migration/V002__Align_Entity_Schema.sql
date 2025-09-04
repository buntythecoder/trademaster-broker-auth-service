-- Migration V002: Align Entity Schema with Current Implementation
-- Fixes deltas between BrokerSession entity and migration script

-- Remove complex tables that don't have corresponding entities
DROP TABLE IF EXISTS broker_sessions CASCADE;
DROP TABLE IF EXISTS broker_accounts CASCADE;  
DROP TABLE IF EXISTS brokers CASCADE;
DROP VIEW IF EXISTS active_sessions_summary CASCADE;

-- Drop functions
DROP FUNCTION IF EXISTS check_max_sessions_limit(BIGINT, VARCHAR);
DROP FUNCTION IF EXISTS get_account_health_score(BIGINT);
DROP FUNCTION IF EXISTS cleanup_expired_sessions();
DROP FUNCTION IF EXISTS update_updated_at_column();

-- Create simplified broker_sessions table matching BrokerSession entity
CREATE TABLE broker_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    broker_type VARCHAR(50) NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP
);

-- Create essential indexes for performance
CREATE INDEX idx_broker_sessions_session_id ON broker_sessions(session_id);
CREATE INDEX idx_broker_sessions_user_id ON broker_sessions(user_id);
CREATE INDEX idx_broker_sessions_broker_type ON broker_sessions(broker_type);
CREATE INDEX idx_broker_sessions_status ON broker_sessions(status);
CREATE INDEX idx_broker_sessions_expires_at ON broker_sessions(expires_at);
CREATE INDEX idx_broker_sessions_active ON broker_sessions(status, expires_at) 
    WHERE status = 'ACTIVE';

-- Create update trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

-- Add updated_at column and trigger
ALTER TABLE broker_sessions ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TRIGGER update_broker_sessions_updated_at 
    BEFORE UPDATE ON broker_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to cleanup expired sessions
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    UPDATE broker_sessions 
    SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
    WHERE status = 'ACTIVE' 
      AND expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- Table documentation
COMMENT ON TABLE broker_sessions IS 'Simplified broker authentication sessions matching BrokerSession entity';
COMMENT ON COLUMN broker_sessions.access_token IS 'Access token from broker (stored as plain text for now)';
COMMENT ON COLUMN broker_sessions.refresh_token IS 'Refresh token from broker (stored as plain text for now)';