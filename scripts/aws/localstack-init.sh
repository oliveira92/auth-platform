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

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"

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

JWT_KEYS_SECRET_FILE="$KEYS_DIR/jwt-keys.json"
JWT_PUBLIC_KEY_SECRET_FILE="$KEYS_DIR/jwt-public-key.json"
LDAP_SECRET_FILE="$KEYS_DIR/ldap-credentials.json"

python3 - "$KEYS_DIR/private_key_pkcs8.pem" "$KEYS_DIR/public_key.pem" \
  "$JWT_KEYS_SECRET_FILE" "$JWT_PUBLIC_KEY_SECRET_FILE" "$LDAP_SECRET_FILE" <<'PY'
import json
import sys

private_key_path, public_key_path, jwt_keys_path, public_key_secret_path, ldap_secret_path = sys.argv[1:]

with open(private_key_path, encoding="utf-8") as private_key_file:
    private_key = private_key_file.read()

with open(public_key_path, encoding="utf-8") as public_key_file:
    public_key = public_key_file.read()

with open(jwt_keys_path, "w", encoding="utf-8") as jwt_keys_file:
    json.dump({"privateKey": private_key, "publicKey": public_key}, jwt_keys_file)

with open(public_key_secret_path, "w", encoding="utf-8") as public_key_secret_file:
    json.dump({"publicKey": public_key}, public_key_secret_file)

with open(ldap_secret_path, "w", encoding="utf-8") as ldap_secret_file:
    json.dump({
        "username": "cn=admin,dc=authplatform,dc=com",
        "password": "admin"
    }, ldap_secret_file)
PY

# ─────────────────────────────────────────────
# AWS Secrets Manager
# ─────────────────────────────────────────────
echo "Creating Secrets Manager entries..."

# JWT RSA Keys
aws secretsmanager create-secret $CLI_ARGS \
  --name "auth-platform/auth-service/jwt-keys" \
  --description "JWT RSA Key Pair for auth-service" \
  --secret-string "file://$JWT_KEYS_SECRET_FILE" \
  2>/dev/null || \
aws secretsmanager update-secret $CLI_ARGS \
  --secret-id "auth-platform/auth-service/jwt-keys" \
  --secret-string "file://$JWT_KEYS_SECRET_FILE"
echo "  [OK] auth-platform/auth-service/jwt-keys"

# JWT Public Key (shared for validation in other services)
aws secretsmanager create-secret $CLI_ARGS \
  --name "auth-platform/shared/jwt-public-key" \
  --description "JWT RSA Public Key for token validation" \
  --secret-string "file://$JWT_PUBLIC_KEY_SECRET_FILE" \
  2>/dev/null || \
aws secretsmanager update-secret $CLI_ARGS \
  --secret-id "auth-platform/shared/jwt-public-key" \
  --secret-string "file://$JWT_PUBLIC_KEY_SECRET_FILE"
echo "  [OK] auth-platform/shared/jwt-public-key"

# LDAP Credentials
aws secretsmanager create-secret $CLI_ARGS \
  --name "auth-platform/auth-service/ldap-credentials" \
  --description "LDAP service account credentials" \
  --secret-string "file://$LDAP_SECRET_FILE" \
  2>/dev/null || \
aws secretsmanager update-secret $CLI_ARGS \
  --secret-id "auth-platform/auth-service/ldap-credentials" \
  --secret-string "file://$LDAP_SECRET_FILE"
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
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/auth-service/auth.jwt.issuer" \
  --value "auth-platform" --type String --overwrite

# Authorization Service parameters
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/server.port" \
  --value "8082" --type String --overwrite
echo "  [OK] /config/auth-platform/authorization-service/server.port"
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/auth.jwt.issuer" \
  --value "auth-platform" --type String --overwrite
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/auth.platform.issuer" \
  --value "auth-platform" --type String --overwrite
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/auth.platform.jwks-uri" \
  --value "http://localhost:8081/.well-known/jwks.json" --type String --overwrite
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/auth.platform.introspection-url" \
  --value "http://localhost:8081/api/v1/auth/validate" --type String --overwrite
aws ssm put-parameter $CLI_ARGS \
  --name "/config/auth-platform/authorization-service/auth.platform.token-algorithm" \
  --value "RS256" --type String --overwrite
echo "  [OK] authorization-service auth platform metadata"

echo ""
echo "======================================================"
echo " LocalStack initialization COMPLETE"
echo "======================================================"
echo " RSA Public Key saved at: $KEYS_DIR/public_key.pem"
echo " Secrets Manager entries created"
echo " Parameter Store entries created"
echo "======================================================"
