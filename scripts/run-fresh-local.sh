#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if [[ "${EASTAPP_CONFIRM_DATABASE_RESET:-}" != "YES" ]]; then
  echo "Refusing to delete the EastApp database."
  echo "This script is destructive and removes all local data."
  echo "Run scripts/run-local.sh for normal development."
  echo "To confirm a deliberate reset, set EASTAPP_CONFIRM_DATABASE_RESET=YES."
  exit 1
fi

export EASTAPP_DATABASE_RESET_ON_START=true

echo "Deleting the local EastApp PostgreSQL volume..."
docker compose down -v --remove-orphans

echo "Starting a fresh PostgreSQL 18 container..."
docker compose up -d postgres

until docker compose exec -T postgres pg_isready -U eastapp -d eastapp >/dev/null 2>&1; do
  sleep 1
done

echo "Starting EastApp on a newly recreated database..."
./mvnw spring-boot:run
