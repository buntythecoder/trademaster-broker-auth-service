#!/bin/bash

# TradeMaster Broker Auth Service - HashiCorp Vault Development Setup
# This script sets up Vault for development with all required secrets

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
VAULT_TOKEN=""
VAULT_ROOT_TOKEN=""

echo -e "${BLUE}🔒 Setting up HashiCorp Vault for TradeMaster Broker Auth Service${NC}"
echo "=================================================================="

# Function to check if Vault is running
check_vault_running() {
    echo -e "${YELLOW}📋 Checking if Vault is running...${NC}"
    if ! curl -s "$VAULT_ADDR/v1/sys/health" >/dev/null 2>&1; then
        echo -e "${RED}❌ Vault is not running at $VAULT_ADDR${NC}"
        echo "Please start Vault first:"
        echo "  vault server -dev"
        exit 1
    fi
    echo -e "${GREEN}✅ Vault is running${NC}"
}

# Function to initialize Vault (if needed)
initialize_vault() {
    echo -e "${YELLOW}🔧 Checking Vault initialization...${NC}"
    
    # Check if Vault is already initialized
    if vault status >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Vault is already initialized${NC}"
        return
    fi
    
    echo -e "${YELLOW}🔄 Initializing Vault...${NC}"
    
    # Initialize Vault
    INIT_OUTPUT=$(vault operator init -key-shares=1 -key-threshold=1 -format=json)
    
    # Extract root token and unseal key
    VAULT_ROOT_TOKEN=$(echo "$INIT_OUTPUT" | jq -r '.root_token')
    UNSEAL_KEY=$(echo "$INIT_OUTPUT" | jq -r '.unseal_keys_b64[0]')
    
    # Save credentials for reference
    cat > vault-credentials.txt << EOF
# Vault Development Credentials
# KEEP THESE SECURE - DO NOT COMMIT TO VERSION CONTROL

VAULT_ROOT_TOKEN=$VAULT_ROOT_TOKEN
VAULT_UNSEAL_KEY=$UNSEAL_KEY
VAULT_ADDR=$VAULT_ADDR

# Usage:
# export VAULT_TOKEN=$VAULT_ROOT_TOKEN
# export VAULT_ADDR=$VAULT_ADDR
EOF
    
    echo -e "${GREEN}✅ Vault initialized successfully${NC}"
    echo -e "${YELLOW}⚠️  Credentials saved to vault-credentials.txt${NC}"
    
    # Unseal Vault
    vault operator unseal "$UNSEAL_KEY"
    
    # Set token for subsequent operations
    export VAULT_TOKEN="$VAULT_ROOT_TOKEN"
    VAULT_TOKEN="$VAULT_ROOT_TOKEN"
}

# Function to enable AppRole authentication
setup_approle() {
    echo -e "${YELLOW}🔐 Setting up AppRole authentication...${NC}"
    
    # Enable AppRole auth method
    vault auth enable approle || echo "AppRole already enabled"
    
    # Create policy for broker-auth service
    vault policy write broker-auth-policy - << EOF
path "secret/data/broker-auth" {
  capabilities = ["read", "update", "create"]
}

path "secret/metadata/broker-auth" {
  capabilities = ["read", "list"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}

path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "sys/health" {
  capabilities = ["read"]
}
EOF

    # Create AppRole
    vault write auth/approle/role/broker-auth-service \
        token_policies="broker-auth-policy" \
        token_ttl=1h \
        token_max_ttl=4h \
        bind_secret_id=true
    
    # Get RoleID
    ROLE_ID=$(vault read -field=role_id auth/approle/role/broker-auth-service/role-id)
    
    # Generate SecretID
    SECRET_ID=$(vault write -field=secret_id auth/approle/role/broker-auth-service/secret-id)
    
    # Save AppRole credentials
    cat > vault-approle-credentials.txt << EOF
# Vault AppRole Credentials for TradeMaster Broker Auth Service
# Use these credentials for application authentication

VAULT_ROLE_ID=$ROLE_ID
VAULT_SECRET_ID=$SECRET_ID
VAULT_ADDR=$VAULT_ADDR

# Add these to your application properties or environment:
# vault.app-role.role-id=$ROLE_ID
# vault.app-role.secret-id=$SECRET_ID
EOF
    
    echo -e "${GREEN}✅ AppRole authentication configured${NC}"
    echo -e "${YELLOW}⚠️  AppRole credentials saved to vault-approle-credentials.txt${NC}"
    
    # Test AppRole authentication
    echo -e "${YELLOW}🧪 Testing AppRole authentication...${NC}"
    APP_TOKEN=$(vault write -field=token auth/approle/login \
        role_id="$ROLE_ID" \
        secret_id="$SECRET_ID")
    
    if [ -n "$APP_TOKEN" ]; then
        echo -e "${GREEN}✅ AppRole authentication test successful${NC}"
    else
        echo -e "${RED}❌ AppRole authentication test failed${NC}"
        exit 1
    fi
}

# Function to generate secure secrets
generate_secure_secrets() {
    echo -e "${YELLOW}🔑 Generating secure secrets...${NC}"
    
    # Generate encryption key (64 characters for production-grade security)
    ENCRYPTION_KEY=$(openssl rand -base64 48 | tr -d '\n')
    
    # Generate JWT secret (64 characters)
    JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
    
    # Generate database password (32 characters)
    DB_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
    
    # Generate Redis password (24 characters)
    REDIS_PASSWORD=$(openssl rand -base64 18 | tr -d '\n')
    
    # Generate broker API keys (for development/testing)
    ZERODHA_API_KEY="dev_zerodha_$(openssl rand -hex 16)"
    ZERODHA_API_SECRET=$(openssl rand -base64 32 | tr -d '\n')
    
    UPSTOX_API_KEY="dev_upstox_$(openssl rand -hex 16)"
    UPSTOX_API_SECRET=$(openssl rand -base64 32 | tr -d '\n')
    
    ANGEL_ONE_API_KEY="dev_angel_$(openssl rand -hex 16)"
    ANGEL_ONE_API_SECRET=$(openssl rand -base64 32 | tr -d '\n')
    
    ICICI_DIRECT_API_KEY="dev_icici_$(openssl rand -hex 16)"
    ICICI_DIRECT_API_SECRET=$(openssl rand -base64 32 | tr -d '\n')
    
    echo -e "${GREEN}✅ Secure secrets generated${NC}"
}

# Function to store secrets in Vault
store_secrets() {
    echo -e "${YELLOW}💾 Storing secrets in Vault...${NC}"
    
    # Enable KV v2 secrets engine
    vault secrets enable -version=2 -path=secret kv || echo "KV secrets engine already enabled"
    
    # Store all secrets in a single path
    vault kv put secret/broker-auth \
        encryption-key="$ENCRYPTION_KEY" \
        jwt-secret="$JWT_SECRET" \
        database-password="$DB_PASSWORD" \
        redis-password="$REDIS_PASSWORD" \
        zerodha-api-key="$ZERODHA_API_KEY" \
        zerodha-api-secret="$ZERODHA_API_SECRET" \
        upstox-api-key="$UPSTOX_API_KEY" \
        upstox-api-secret="$UPSTOX_API_SECRET" \
        angel-one-api-key="$ANGEL_ONE_API_KEY" \
        angel-one-api-secret="$ANGEL_ONE_API_SECRET" \
        icici-direct-api-key="$ICICI_DIRECT_API_KEY" \
        icici-direct-api-secret="$ICICI_DIRECT_API_SECRET"
    
    echo -e "${GREEN}✅ Secrets stored in Vault at secret/broker-auth${NC}"
}

# Function to verify secrets
verify_secrets() {
    echo -e "${YELLOW}🔍 Verifying stored secrets...${NC}"
    
    # Test reading secrets
    if vault kv get -field=encryption-key secret/broker-auth >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Encryption key accessible${NC}"
    else
        echo -e "${RED}❌ Failed to read encryption key${NC}"
        exit 1
    fi
    
    if vault kv get -field=jwt-secret secret/broker-auth >/dev/null 2>&1; then
        echo -e "${GREEN}✅ JWT secret accessible${NC}"
    else
        echo -e "${RED}❌ Failed to read JWT secret${NC}"
        exit 1
    fi
    
    # List all secrets (metadata only)
    echo -e "${BLUE}📋 Available secrets:${NC}"
    vault kv metadata get secret/broker-auth | grep -A 20 "====== Metadata ======"
    
    echo -e "${GREEN}✅ All secrets verified successfully${NC}"
}

# Function to create development environment file
create_env_file() {
    echo -e "${YELLOW}📝 Creating development environment file...${NC}"
    
    # Read the AppRole credentials
    if [ -f "vault-approle-credentials.txt" ]; then
        source vault-approle-credentials.txt
    fi
    
    cat > .env.vault << EOF
# TradeMaster Broker Auth Service - Vault Development Environment
# Source this file: source .env.vault

# Vault Configuration
export VAULT_ADDR=$VAULT_ADDR
export VAULT_ROLE_ID=$ROLE_ID
export VAULT_SECRET_ID=$SECRET_ID

# Spring Boot Configuration
export SPRING_PROFILES_ACTIVE=vault,dev
export VAULT_URI=$VAULT_ADDR
export VAULT_ENABLED=true

# Application will retrieve all secrets from Vault automatically
# No need to set sensitive environment variables

echo "🔒 Vault development environment loaded"
echo "🚀 Start your application with: ./gradlew bootRun"
EOF
    
    chmod +x .env.vault
    echo -e "${GREEN}✅ Environment file created: .env.vault${NC}"
    echo -e "${BLUE}💡 Usage: source .env.vault${NC}"
}

# Function to show usage instructions
show_usage() {
    echo -e "${BLUE}📖 Vault Setup Complete - Usage Instructions${NC}"
    echo "=================================================="
    echo ""
    echo -e "${YELLOW}1. Load development environment:${NC}"
    echo "   source .env.vault"
    echo ""
    echo -e "${YELLOW}2. Start your application:${NC}"
    echo "   ./gradlew bootRun --args='--spring.profiles.active=vault,dev'"
    echo ""
    echo -e "${YELLOW}3. Verify Vault integration:${NC}"
    echo "   curl http://localhost:8080/actuator/health"
    echo ""
    echo -e "${YELLOW}4. Access secrets via Vault UI:${NC}"
    echo "   Open http://localhost:8200/ui"
    echo "   Token: $(cat vault-credentials.txt | grep VAULT_ROOT_TOKEN | cut -d'=' -f2)"
    echo ""
    echo -e "${YELLOW}5. Manual secret operations:${NC}"
    echo "   vault kv get secret/broker-auth"
    echo "   vault kv put secret/broker-auth new-key=new-value"
    echo ""
    echo -e "${RED}⚠️  Security Reminders:${NC}"
    echo "   - Never commit vault-credentials.txt to version control"
    echo "   - Never commit vault-approle-credentials.txt to version control"
    echo "   - Use different secrets for production"
    echo "   - Rotate secrets regularly"
    echo ""
}

# Main execution
main() {
    # Set Vault address
    export VAULT_ADDR="$VAULT_ADDR"
    
    check_vault_running
    initialize_vault
    setup_approle
    generate_secure_secrets
    store_secrets
    verify_secrets
    create_env_file
    show_usage
    
    echo -e "${GREEN}✅ Vault setup completed successfully!${NC}"
}

# Check if Vault CLI is available
if ! command -v vault &> /dev/null; then
    echo -e "${RED}❌ Vault CLI is not installed${NC}"
    echo "Please install Vault CLI: https://www.vaultproject.io/downloads"
    exit 1
fi

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo -e "${RED}❌ jq is not installed${NC}"
    echo "Please install jq: apt-get install jq or brew install jq"
    exit 1
fi

# Run main function
main "$@"