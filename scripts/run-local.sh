#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

export EASTAPP_DATABASE_RESET_ON_START=false

echo "Starting PostgreSQL 18 without deleting the existing EastApp volume..."
docker compose up -d postgres

until docker compose exec -T postgres pg_isready -U eastapp -d eastapp >/dev/null 2>&1; do
  sleep 1
done

echo "Starting EastApp and applying pending Flyway migrations..."
./mvnw spring-boot:run
