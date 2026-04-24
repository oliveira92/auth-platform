#!/bin/bash
# Generate RSA key pairs for JWT signing.
# Output:
#   private_key_pkcs8.pem       private key in PKCS#8 format, consumed by Java
#   public_key.pem              public key published through JWKS
#   jwt-keys.secret.json        Secrets Manager payload for auth-service
#   jwt-public-key.secret.json  Secrets Manager payload for token validators
#   key-metadata.json           non-secret metadata for audit/checks

set -euo pipefail

OUTPUT_DIR="./keys"
OUTPUT_DIR_SET=false
ENVIRONMENT=""
KEY_SIZE="2048"
FORCE=false
CREATE_SECRET_PAYLOADS=true

usage() {
  cat <<'EOF'
Usage:
  scripts/keys/generate-rsa-keys.sh [options]

Options:
  --env <name>             Environment name, for example dev, hom or prod.
                           When --output-dir is not provided, keys are written
                           to ./keys/<env>.
  --output-dir <path>      Final output directory. Defaults to ./keys or
                           ./keys/<env> when --env is provided.
  --key-size <bits>        RSA key size. Defaults to 2048.
  --force                  Overwrite existing key files in the output directory.
  --no-secret-payloads     Generate only PEM files and metadata.
  -h, --help               Show this help message.

Examples:
  scripts/keys/generate-rsa-keys.sh --env dev
  scripts/keys/generate-rsa-keys.sh --env hom --output-dir ./secure-keys/hom
  scripts/keys/generate-rsa-keys.sh --env prod --force
EOF
}

require_value() {
  local option="$1"
  local value="${2:-}"
  if [ -z "$value" ] || [[ "$value" == --* ]]; then
    echo "ERROR: missing value for $option"
    exit 1
  fi
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --env)
      require_value "$1" "${2:-}"
      ENVIRONMENT="$2"
      shift 2
      ;;
    --output-dir)
      require_value "$1" "${2:-}"
      OUTPUT_DIR="$2"
      OUTPUT_DIR_SET=true
      shift 2
      ;;
    --key-size)
      require_value "$1" "${2:-}"
      KEY_SIZE="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    --no-secret-payloads)
      CREATE_SECRET_PAYLOADS=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: unknown param: $1"
      usage
      exit 1
      ;;
  esac
done

if [ -n "$ENVIRONMENT" ]; then
  case "$ENVIRONMENT" in
    *[!a-zA-Z0-9._-]*)
      echo "ERROR: --env accepts only letters, numbers, dot, dash and underscore"
      exit 1
      ;;
  esac

  if [ "$OUTPUT_DIR_SET" = "false" ]; then
    OUTPUT_DIR="./keys/$ENVIRONMENT"
  fi
fi

case "$KEY_SIZE" in
  *[!0-9]*|"")
    echo "ERROR: --key-size must be numeric"
    exit 1
    ;;
esac

if [ "$KEY_SIZE" -lt 2048 ]; then
  echo "ERROR: key size must be at least 2048 bits"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR" 2>/dev/null || true

TEMP_PRIVATE_KEY="$OUTPUT_DIR/private_key.pem"
PRIVATE_KEY_FILE="$OUTPUT_DIR/private_key_pkcs8.pem"
PUBLIC_KEY_FILE="$OUTPUT_DIR/public_key.pem"
JWT_KEYS_SECRET_FILE="$OUTPUT_DIR/jwt-keys.secret.json"
JWT_PUBLIC_KEY_SECRET_FILE="$OUTPUT_DIR/jwt-public-key.secret.json"
METADATA_JSON_FILE="$OUTPUT_DIR/key-metadata.json"
METADATA_ENV_FILE="$OUTPUT_DIR/key-metadata.env"
GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

if [ "$FORCE" = "false" ]; then
  for existing_file in \
    "$TEMP_PRIVATE_KEY" \
    "$PRIVATE_KEY_FILE" \
    "$PUBLIC_KEY_FILE" \
    "$JWT_KEYS_SECRET_FILE" \
    "$JWT_PUBLIC_KEY_SECRET_FILE" \
    "$METADATA_JSON_FILE" \
    "$METADATA_ENV_FILE"
  do
    if [ -e "$existing_file" ]; then
      echo "ERROR: $existing_file already exists. Use --force to overwrite."
      exit 1
    fi
  done
fi

echo "Generating RSA-$KEY_SIZE key pair..."
if [ -n "$ENVIRONMENT" ]; then
  echo "Environment: $ENVIRONMENT"
fi
echo "Output dir: $OUTPUT_DIR"

openssl genrsa -out "$TEMP_PRIVATE_KEY" "$KEY_SIZE"

openssl pkcs8 -topk8 -inform PEM -outform PEM \
  -in "$TEMP_PRIVATE_KEY" \
  -out "$PRIVATE_KEY_FILE" \
  -nocrypt

openssl rsa -in "$TEMP_PRIVATE_KEY" -pubout -out "$PUBLIC_KEY_FILE"
rm -f "$TEMP_PRIVATE_KEY"

chmod 600 "$PRIVATE_KEY_FILE"
chmod 644 "$PUBLIC_KEY_FILE"

python3 - "$PRIVATE_KEY_FILE" "$PUBLIC_KEY_FILE" \
  "$JWT_KEYS_SECRET_FILE" "$JWT_PUBLIC_KEY_SECRET_FILE" \
  "$METADATA_JSON_FILE" "$METADATA_ENV_FILE" \
  "${ENVIRONMENT:-}" "$KEY_SIZE" "$GENERATED_AT" "$CREATE_SECRET_PAYLOADS" <<'PY'
import base64
import hashlib
import json
import sys

(
    private_key_path,
    public_key_path,
    jwt_keys_secret_path,
    public_key_secret_path,
    metadata_json_path,
    metadata_env_path,
    environment,
    key_size,
    generated_at,
    create_secret_payloads,
) = sys.argv[1:]

with open(private_key_path, encoding="utf-8") as private_key_file:
    private_key = private_key_file.read()

with open(public_key_path, encoding="utf-8") as public_key_file:
    public_key = public_key_file.read()

public_key_der = base64.b64decode(
    "".join(
        line.strip()
        for line in public_key.splitlines()
        if not line.startswith("-----")
    )
)
public_key_digest = hashlib.sha256(public_key_der).digest()
key_id = base64.urlsafe_b64encode(public_key_digest).rstrip(b"=").decode("ascii")
public_key_sha256 = hashlib.sha256(public_key_der).hexdigest()

if create_secret_payloads == "true":
    with open(jwt_keys_secret_path, "w", encoding="utf-8") as jwt_keys_secret_file:
        json.dump({"privateKey": private_key, "publicKey": public_key}, jwt_keys_secret_file)

    with open(public_key_secret_path, "w", encoding="utf-8") as public_key_secret_file:
        json.dump({"publicKey": public_key}, public_key_secret_file)

metadata = {
    "environment": environment or None,
    "generatedAt": generated_at,
    "keySize": int(key_size),
    "algorithm": "RS256",
    "kid": key_id,
    "publicKeySha256": public_key_sha256,
    "privateKeyFile": private_key_path,
    "publicKeyFile": public_key_path,
}

with open(metadata_json_path, "w", encoding="utf-8") as metadata_json_file:
    json.dump(metadata, metadata_json_file, indent=2)
    metadata_json_file.write("\n")

with open(metadata_env_path, "w", encoding="utf-8") as metadata_env_file:
    metadata_env_file.write(f"KEY_ID={key_id}\n")
    metadata_env_file.write(f"PUBLIC_KEY_SHA256={public_key_sha256}\n")
    metadata_env_file.write(f"GENERATED_AT={generated_at}\n")
PY

chmod 600 "$JWT_KEYS_SECRET_FILE" 2>/dev/null || true
chmod 600 "$JWT_PUBLIC_KEY_SECRET_FILE" 2>/dev/null || true
chmod 644 "$METADATA_JSON_FILE"
chmod 644 "$METADATA_ENV_FILE"

# shellcheck disable=SC1090
. "$METADATA_ENV_FILE"

echo ""
echo "Keys generated successfully:"
echo "  Private key (PKCS#8): $PRIVATE_KEY_FILE"
echo "  Public key:           $PUBLIC_KEY_FILE"
if [ "$CREATE_SECRET_PAYLOADS" = "true" ]; then
  echo "  JWT secret payload:   $JWT_KEYS_SECRET_FILE"
  echo "  Public key payload:   $JWT_PUBLIC_KEY_SECRET_FILE"
fi
echo "  Metadata:             $METADATA_JSON_FILE"
echo ""
echo "kid:                 $KEY_ID"
echo "publicKey sha256:    $PUBLIC_KEY_SHA256"
echo ""
echo "SECURITY WARNING: never commit private keys or secret payload files."
echo "The repository already ignores keys/, *.pem and *.env."
echo ""
echo "To send these keys to an AWS environment, run:"
echo "  ./scripts/aws/setup-aws-prod.sh --region us-east-1 --env ${ENVIRONMENT:-production} --keys-dir $OUTPUT_DIR"
echo ""
echo "For local docker-compose without AWS, set:"
echo "  export JWT_PRIVATE_KEY=\$(cat $PRIVATE_KEY_FILE)"
echo "  export JWT_PUBLIC_KEY=\$(cat $PUBLIC_KEY_FILE)"
