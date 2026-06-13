#!/usr/bin/env bash
set -euo pipefail

BREW_BIN="${BREW_BIN:-/opt/homebrew/bin/brew}"
MYSQL_CLIENT="${MYSQL_CLIENT:-/opt/homebrew/opt/mysql@8.0/bin/mysql}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql@8.0}"
REDIS_CLI="${REDIS_CLI:-/opt/homebrew/bin/redis-cli}"

DB_NAME="${MYTHIC_DB_NAME:-mythicrealm}"
DB_USER="${MYTHIC_DB_USERNAME:-mythic}"
DB_PASSWORD="${MYTHIC_DB_PASSWORD:-mythic}"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"

if [[ ! -x "$BREW_BIN" ]]; then
  echo "Homebrew not found at $BREW_BIN"
  echo "Install Homebrew first, then rerun this script."
  exit 1
fi

if [[ ! -x "$MYSQL_CLIENT" ]]; then
  echo "MySQL client not found. Installing local mysql@8.4 via Homebrew."
  "$BREW_BIN" install mysql@8.4
  MYSQL_CLIENT="/opt/homebrew/opt/mysql@8.4/bin/mysql"
  MYSQL_SERVICE="mysql@8.4"
fi

if [[ ! -x "$REDIS_CLI" ]]; then
  echo "Redis not found. Installing local redis via Homebrew."
  "$BREW_BIN" install redis
fi

"$BREW_BIN" services start "$MYSQL_SERVICE"
"$BREW_BIN" services start redis

if ! "$REDIS_CLI" ping >/dev/null; then
  echo "Redis service did not respond to PING."
  exit 1
fi

MYSQL_PASSWORD_ARG=()
if [[ -n "${MYSQL_ADMIN_PASSWORD:-}" ]]; then
  MYSQL_PASSWORD_ARG=(-p"${MYSQL_ADMIN_PASSWORD}")
fi

"$MYSQL_CLIENT" \
  -u"$MYSQL_ADMIN_USER" "${MYSQL_PASSWORD_ARG[@]}" \
  --protocol=tcp \
  -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

"$MYSQL_CLIENT" \
  -u"$MYSQL_ADMIN_USER" "${MYSQL_PASSWORD_ARG[@]}" \
  --protocol=tcp \
  -e "CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}'; GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost'; FLUSH PRIVILEGES;"

echo "Local MySQL and Redis are ready."
echo "Database: ${DB_NAME}"
echo "App user: ${DB_USER}"
