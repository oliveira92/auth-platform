#!/bin/bash
# LocalStack initialization script
# Creates AWS Secrets Manager and Parameter Store entries for local development

set -e

echo "======================================================"
echo " Auth Platform - LocalStack AWS Resource Initialization"
echo "======================================================"

AWS_REGION="us-east-1"
ENDPOINT="http://localhost:4566"
CLI_ARGS="--endpoint-url $ENDPOINT --region $AWS_REGION"

# Wait for LocalStack to be ready
echo "Waiting for LocalStack services..."
sleep 5

# ─────────────────────────────────────────────
# Generate RSA Key Pair (if not exists)
# ─────────────────────────────────────────────
KEYS_DIR="/tmp/auth-platform-keys"
mkdir -p "$KEYS_DIR"

if [ ! -f "$KEYS_DIR/private_key.pem" ]; then
  echo "Generating RSA-2048 key pair..."
  openssl genrsa -out "$KEYS_DIR/private_key.pem" 2048
  openssl rsa -in "$KEYS_DIR/private_key.pem" -pubout -out "$KEYS_DIR/public_key.pem"
  # Convert to PKCS8 for Java compatibility
  openssl pkcs8 -topk8 -inform PEM -outform PEM -in "$KEYS_DIR/private_key.pem" \
    -out "$KEYS_DIR/private_key_pkcs8.pem" -nocrypt
  echo "RSA key pair generated."
fi

PRIVATE_KEY=$(cat "$KEYS_DIR/private_key_pkcs8.pem")
PUBLIC_KEY=$(cat "$KEYS_DIR/public_key.pem")

# ─────────────────────────────────────────────
# AWS Secrets Manager
# ─────────────────────────────────────────────
echo "Creating Secrets Manager entries..."

# JWT RSA Keys
aws secretsmanager create-secret $CLI_ARGS \
  --name "auth-platform/auth-service/jwt-keys" \
  --description "JWT RSA Key Pair for auth-service" \
  --secret-string "{\"privateKey\":\"$PRIVATE_KEY\",\"publicKey\":\"$PUBLIC_KEY\"}" \
  2>/dev/null || \
aws secretsmanager update-secret $CLI_ARGS \
  --secret-id "auth-platform/auth-service/jwt-keys" \
  --secret-string "{\"privateKey\":\"$PRIVATE_KEY\",\"publicKey\":\"$PUBLIC_KEY\"}"
echo "  [OK] auth-platform/auth-service/jwt-keys"

# JWT Public Key (shared for validation in other services)
aws secretsmanager create-secret $CLI_ARGS \
  --name "auth-platform/shared/jwt-public-key" \
  --description "JWT RSA Public Key for token validation" \
  --secret-string "{\"publicKey\":\"$PUBLIC_KEY\"}" \
  2>/dev/null || \
aws secretsmanager update-secret $CLI_ARGS \
  --secret-id "auth-platform/shared/jwt-public-key" \
  --secret-string "{\"publicKey\":\"$PUBLIC_KEY\"}"
echo "  [OK] auth-platform/shared/jwt-public-key"

# LDAP Credentials
aws secretsmanager create-secret $CLI_ARGS \
  --name "auth-platform/auth-service/ldap-credentials" \
  --description "LDAP service account credentials" \
  --secret-string '{"username":"cn=admin,dc=authplatform,dc=com","password":"admin"}' \
  2>/dev/null || \
aws secretsmanager update-secret $CLI_ARGS \
  --secret-id "auth-platform/auth-service/ldap-credentials" \
  --secret-string '{"username":"cn=admin,dc=authplatform,dc=com","password":"admin"}'
echo "  [OK] auth-platform/auth-service/ldap-credentials"

# ─────────────────────────────────────────────
# AWS Systems Manager Parameter Store
# ─────────────────────────────────────────────
echo "Creating Parameter Store entries..."

# Auth Service parameters
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/auth-service/server.port" \
  --value "8081" --type String --overwrite
echo "  [OK] /config/auth-platform/auth-service/server.port"

aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/auth-service/auth.jwt.access-token-expiration-seconds" \
  --value "900" --type String --overwrite
echo "  [OK] auth-service jwt expiration"

aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/auth-service/auth.jwt.refresh-token-expiration-seconds" \
  --value "86400" --type String --overwrite

# Authorization Service parameters
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/server.port" \
  --value "8082" --type String --overwrite
echo "  [OK] /config/auth-platform/authorization-service/server.port"

echo ""
echo "======================================================"
echo " LocalStack initialization COMPLETE"
echo "======================================================"
echo " RSA Public Key saved at: $KEYS_DIR/public_key.pem"
echo " Secrets Manager entries created"
echo " Parameter Store entries created"
echo "======================================================"
