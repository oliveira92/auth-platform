#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_FILE="$ROOT_DIR/scripts/mysql/seed-authorization-xpto.sql"

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
MYSQL_DATABASE="${MYSQL_DATABASE:-authplatform}"
MYSQL_USER="${MYSQL_USER:-authplatform}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-authplatform}"

cd "$ROOT_DIR"

echo "Loading Authorization Service sample data into database '$MYSQL_DATABASE'..."
docker compose exec -T "$MYSQL_SERVICE" \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$SQL_FILE"

echo "Sample data loaded successfully."
