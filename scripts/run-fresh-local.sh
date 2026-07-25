#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

export EASTAPP_DATABASE_RESET_ON_START="${EASTAPP_DATABASE_RESET_ON_START:-true}"

echo "Deleting the local EastApp PostgreSQL volume..."
docker compose down -v --remove-orphans

echo "Starting a fresh PostgreSQL 18 container..."
docker compose up -d postgres

until docker compose exec -T postgres pg_isready -U eastapp -d eastapp >/dev/null 2>&1; do
  sleep 1
done

echo "Starting EastApp. Read the one-time setup code from this terminal."
./mvnw spring-boot:run
