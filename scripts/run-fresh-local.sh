#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if [[ -z "${EASTAPP_BOOTSTRAP_PHONE_E164:-}" ]]; then
  read -r -p "Enter Jenssen phone number for the local seed: " EASTAPP_BOOTSTRAP_PHONE_E164
fi

if [[ -z "${EASTAPP_BOOTSTRAP_PHONE_E164}" ]]; then
  echo "EASTAPP_BOOTSTRAP_PHONE_E164 is required." >&2
  exit 1
fi

export EASTAPP_BOOTSTRAP_PHONE_E164
export EASTAPP_BOOTSTRAP_ENABLED=true
export EASTAPP_BOOTSTRAP_COMPANY_CODE=EAST
export EASTAPP_BOOTSTRAP_COMPANY_NAME="The East"
export EASTAPP_BOOTSTRAP_EMPLOYEE_ID=E0001
export EASTAPP_BOOTSTRAP_FULL_NAME=Jenssen
export EASTAPP_BOOTSTRAP_PASSWORD=1111

echo "Deleting the local EastApp PostgreSQL volume..."
docker compose down -v --remove-orphans

echo "Starting a fresh PostgreSQL 18 container..."
docker compose up -d postgres

until docker compose exec -T postgres pg_isready -U eastapp -d eastapp >/dev/null 2>&1; do
  sleep 1
done

echo "Starting EastApp with a clean Flyway V1 database..."
EASTAPP_DATABASE_RESET_ON_START=true ./mvnw spring-boot:run
