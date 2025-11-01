-- Flyway Migration V2: Add Vault Integration to BrokerSession
-- MANDATORY: Secure token storage with HashiCorp Vault
--
-- Changes:
-- 1. Remove plain-text token columns (access_token, refresh_token)
-- 2. Add vault_path column for secure token retrieval
-- 3. Add is_encrypted flag for encryption status tracking
--
-- Security Impact: CRITICAL
-- - Tokens will no longer be stored in plain-text
-- - All tokens stored in HashiCorp Vault with AES-256-GCM encryption
-- - Database only stores Vault path references
--
-- Migration Strategy:
-- - This is a BREAKING CHANGE
-- - All existing sessions will be invalidated
-- - Users must re-authenticate with brokers
--
-- Author: TradeMaster Development Team
-- Date: 2025-01-28
-- Version: 2.0.0

-- Step 1: Add new Vault-related columns
ALTER TABLE broker_sessions
    ADD COLUMN vault_path VARCHAR(500),
    ADD COLUMN is_encrypted BOOLEAN DEFAULT true;

-- Step 2: Drop old plain-text token columns
-- WARNING: This will invalidate all existing sessions
ALTER TABLE broker_sessions
    DROP COLUMN IF EXISTS access_token,
    DROP COLUMN IF EXISTS refresh_token;

-- Step 3: Make vault_path NOT NULL after migration
ALTER TABLE broker_sessions
    ALTER COLUMN vault_path SET NOT NULL;

-- Step 4: Add index on vault_path for faster lookups
CREATE INDEX idx_broker_sessions_vault_path ON broker_sessions(vault_path);

-- Step 5: Add index on user_id and broker_type combination
CREATE INDEX idx_broker_sessions_user_broker ON broker_sessions(user_id, broker_type);

-- Step 6: Add comment for documentation
COMMENT ON COLUMN broker_sessions.vault_path IS 'HashiCorp Vault path where encrypted tokens are stored (format: secret/broker-auth/{userId}/{brokerType})';
COMMENT ON COLUMN broker_sessions.is_encrypted IS 'Flag indicating if tokens are encrypted with AES-256-GCM (always true in production)';

-- Verification Query (for manual testing):
-- SELECT id, session_id, user_id, broker_type, vault_path, is_encrypted, status, created_at, expires_at
-- FROM broker_sessions
-- WHERE status = 'ACTIVE' AND is_encrypted = true;
