-- TradeMaster Broker Authentication Service - Performance Indexes
-- Version: V003 - Create performance indexes

-- Create composite index for broker sessions performance
-- This index optimizes queries filtering by user, broker type, status, and expiry
CREATE INDEX IF NOT EXISTS idx_broker_sessions_composite 
    ON broker_sessions(user_id, broker_type, status, expires_at);

-- Create performance index for audit queries
-- This index optimizes audit log queries by user and timestamp with status filtering
CREATE INDEX IF NOT EXISTS idx_audit_performance 
    ON broker_auth_audit(user_id, timestamp DESC) 
    WHERE status IN ('SUCCESS', 'FAILURE');