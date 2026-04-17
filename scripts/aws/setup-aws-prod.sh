#!/bin/bash
# Production AWS Setup Script
# Creates Secrets Manager and Parameter Store entries in AWS
# Usage: ./setup-aws-prod.sh --region us-east-1 --env production --keys-dir ./keys

set -e

REGION="us-east-1"
ENV="production"
KEYS_DIR="./keys"

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --region) REGION="$2"; shift ;;
    --env) ENV="$2"; shift ;;
    --keys-dir) KEYS_DIR="$2"; shift ;;
    *) echo "Unknown param: $1"; exit 1 ;;
  esac
  shift
done

echo "Setting up AWS resources for environment: $ENV in region: $REGION"

# Validate RSA keys exist
if [ ! -f "$KEYS_DIR/private_key_pkcs8.pem" ] || [ ! -f "$KEYS_DIR/public_key.pem" ]; then
  echo "ERROR: RSA keys not found in $KEYS_DIR"
  echo "Run: scripts/keys/generate-rsa-keys.sh --output-dir $KEYS_DIR"
  exit 1
fi

PRIVATE_KEY=$(cat "$KEYS_DIR/private_key_pkcs8.pem")
PUBLIC_KEY=$(cat "$KEYS_DIR/public_key.pem")

# Prompt for LDAP credentials
read -r -p "LDAP Service Account DN: " LDAP_DN
read -r -s -p "LDAP Service Account Password: " LDAP_PASSWORD
echo

# ─── Secrets Manager ───────────────────────────────────────────────────────

echo "Creating/updating Secrets Manager secrets..."

aws secretsmanager create-secret \
  --region "$REGION" \
  --name "auth-platform/auth-service/jwt-keys" \
  --description "JWT RSA Key Pair for auth-service [$ENV]" \
  --secret-string "{\"privateKey\":\"$PRIVATE_KEY\",\"publicKey\":\"$PUBLIC_KEY\"}" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --region "$REGION" \
  --secret-id "auth-platform/auth-service/jwt-keys" \
  --secret-string "{\"privateKey\":\"$PRIVATE_KEY\",\"publicKey\":\"$PUBLIC_KEY\"}"
echo "  [OK] JWT keys secret"

aws secretsmanager create-secret \
  --region "$REGION" \
  --name "auth-platform/shared/jwt-public-key" \
  --description "JWT RSA Public Key for token validation [$ENV]" \
  --secret-string "{\"publicKey\":\"$PUBLIC_KEY\"}" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --region "$REGION" \
  --secret-id "auth-platform/shared/jwt-public-key" \
  --secret-string "{\"publicKey\":\"$PUBLIC_KEY\"}"
echo "  [OK] JWT public key secret"

aws secretsmanager create-secret \
  --region "$REGION" \
  --name "auth-platform/auth-service/ldap-credentials" \
  --description "LDAP service account credentials [$ENV]" \
  --secret-string "{\"username\":\"$LDAP_DN\",\"password\":\"$LDAP_PASSWORD\"}" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --region "$REGION" \
  --secret-id "auth-platform/auth-service/ldap-credentials" \
  --secret-string "{\"username\":\"$LDAP_DN\",\"password\":\"$LDAP_PASSWORD\"}"
echo "  [OK] LDAP credentials secret"

# ─── Parameter Store ───────────────────────────────────────────────────────

echo "Creating/updating Parameter Store parameters..."

# Auth Service
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.ldap.url" \
  --value "${LDAP_URL}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.ldap.base-dn" \
  --value "${LDAP_BASE_DN}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.jwt.access-token-expiration-seconds" \
  --value "900" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.jwt.refresh-token-expiration-seconds" \
  --value "86400" --type String

# Authorization Service
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/spring.datasource.url" \
  --value "${DB_URL}" --type String

echo ""
echo "AWS setup complete for environment: $ENV"
