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

read_required() {
  local var_name="$1"
  local prompt="$2"
  local current_value="${!var_name:-}"
  local new_value

  if [ -n "$current_value" ]; then
    read -r -p "$prompt [$current_value]: " new_value
    new_value="${new_value:-$current_value}"
  else
    read -r -p "$prompt: " new_value
  fi

  if [ -z "$new_value" ]; then
    echo "ERROR: $var_name is required"
    exit 1
  fi

  printf -v "$var_name" '%s' "$new_value"
}

read_with_default() {
  local var_name="$1"
  local prompt="$2"
  local default_value="$3"
  local current_value="${!var_name:-$default_value}"
  local new_value

  read -r -p "$prompt [$current_value]: " new_value
  new_value="${new_value:-$current_value}"

  printf -v "$var_name" '%s' "$new_value"
}

# Prompt for runtime parameters
read_required LDAP_URL "LDAP URL (ex: ldaps://ad.empresa.com:636)"
read_required LDAP_BASE_DN "LDAP Base DN (ex: dc=empresa,dc=com)"
read_with_default LDAP_USER_SEARCH_BASE "LDAP user search base" "ou=Users"
read_with_default LDAP_USER_SEARCH_FILTER "LDAP user search filter" "(sAMAccountName={0})"
read_with_default LDAP_GROUP_SEARCH_BASE "LDAP group search base" "ou=Groups"
read_with_default LDAP_GROUP_SEARCH_FILTER "LDAP group search filter" "(member={0})"
read_required DB_URL "Authorization DB JDBC URL (ex: jdbc:postgresql://db:5432/authplatform)"
read_with_default JWT_ISSUER "JWT issuer/public auth base URL" "https://auth.empresa.com"
AUTH_BASE_URL="${JWT_ISSUER%/}"
read_with_default AUTH_JWKS_URI "JWKS URI exposed to consumer applications" "$AUTH_BASE_URL/.well-known/jwks.json"
read_with_default AUTH_INTROSPECTION_URL "Introspection URL exposed to consumer applications" "$AUTH_BASE_URL/api/v1/auth/validate"
read_with_default AUTH_TOKEN_ALGORITHM "JWT token algorithm" "RS256"

# Prompt for LDAP credentials
read -r -p "LDAP Service Account DN: " LDAP_DN
read -r -s -p "LDAP Service Account Password: " LDAP_PASSWORD
echo

if [ -z "$LDAP_DN" ] || [ -z "$LDAP_PASSWORD" ]; then
  echo "ERROR: LDAP service account DN and password are required"
  exit 1
fi

TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

JWT_KEYS_SECRET_FILE="$TEMP_DIR/jwt-keys.json"
JWT_PUBLIC_KEY_SECRET_FILE="$TEMP_DIR/jwt-public-key.json"
LDAP_SECRET_FILE="$TEMP_DIR/ldap-credentials.json"

python3 - "$KEYS_DIR/private_key_pkcs8.pem" "$KEYS_DIR/public_key.pem" \
  "$JWT_KEYS_SECRET_FILE" "$JWT_PUBLIC_KEY_SECRET_FILE" "$LDAP_SECRET_FILE" \
  "$LDAP_DN" "$LDAP_PASSWORD" <<'PY'
import json
import sys

private_key_path, public_key_path, jwt_keys_path, public_key_secret_path, ldap_secret_path, ldap_dn, ldap_password = sys.argv[1:]

with open(private_key_path, encoding="utf-8") as private_key_file:
    private_key = private_key_file.read()

with open(public_key_path, encoding="utf-8") as public_key_file:
    public_key = public_key_file.read()

with open(jwt_keys_path, "w", encoding="utf-8") as jwt_keys_file:
    json.dump({"privateKey": private_key, "publicKey": public_key}, jwt_keys_file)

with open(public_key_secret_path, "w", encoding="utf-8") as public_key_secret_file:
    json.dump({"publicKey": public_key}, public_key_secret_file)

with open(ldap_secret_path, "w", encoding="utf-8") as ldap_secret_file:
    json.dump({"username": ldap_dn, "password": ldap_password}, ldap_secret_file)
PY

# ─── Secrets Manager ───────────────────────────────────────────────────────

echo "Creating/updating Secrets Manager secrets..."

aws secretsmanager create-secret \
  --region "$REGION" \
  --name "auth-platform/auth-service/jwt-keys" \
  --description "JWT RSA Key Pair for auth-service [$ENV]" \
  --secret-string "file://$JWT_KEYS_SECRET_FILE" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --region "$REGION" \
  --secret-id "auth-platform/auth-service/jwt-keys" \
  --secret-string "file://$JWT_KEYS_SECRET_FILE"
echo "  [OK] JWT keys secret"

aws secretsmanager create-secret \
  --region "$REGION" \
  --name "auth-platform/shared/jwt-public-key" \
  --description "JWT RSA Public Key for token validation [$ENV]" \
  --secret-string "file://$JWT_PUBLIC_KEY_SECRET_FILE" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --region "$REGION" \
  --secret-id "auth-platform/shared/jwt-public-key" \
  --secret-string "file://$JWT_PUBLIC_KEY_SECRET_FILE"
echo "  [OK] JWT public key secret"

aws secretsmanager create-secret \
  --region "$REGION" \
  --name "auth-platform/auth-service/ldap-credentials" \
  --description "LDAP service account credentials [$ENV]" \
  --secret-string "file://$LDAP_SECRET_FILE" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --region "$REGION" \
  --secret-id "auth-platform/auth-service/ldap-credentials" \
  --secret-string "file://$LDAP_SECRET_FILE"
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
  --name "/config/auth-platform/auth-service/auth.ldap.user-search-base" \
  --value "${LDAP_USER_SEARCH_BASE}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.ldap.user-search-filter" \
  --value "${LDAP_USER_SEARCH_FILTER}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.ldap.group-search-base" \
  --value "${LDAP_GROUP_SEARCH_BASE}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.ldap.group-search-filter" \
  --value "${LDAP_GROUP_SEARCH_FILTER}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.jwt.access-token-expiration-seconds" \
  --value "900" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.jwt.refresh-token-expiration-seconds" \
  --value "86400" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/auth-service/auth.jwt.issuer" \
  --value "${JWT_ISSUER}" --type String

# Authorization Service
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/spring.datasource.url" \
  --value "${DB_URL}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/auth.jwt.issuer" \
  --value "${JWT_ISSUER}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/auth.platform.issuer" \
  --value "${JWT_ISSUER}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/auth.platform.jwks-uri" \
  --value "${AUTH_JWKS_URI}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/auth.platform.introspection-url" \
  --value "${AUTH_INTROSPECTION_URL}" --type String
aws ssm put-parameter --region "$REGION" --overwrite \
  --name "/config/auth-platform/authorization-service/auth.platform.token-algorithm" \
  --value "${AUTH_TOKEN_ALGORITHM}" --type String

echo ""
echo "AWS setup complete for environment: $ENV"
