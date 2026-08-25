#!/usr/bin/env bash
set -euo pipefail

export EASTAPP_DATABASE_RESET_ON_START=true
export EASTAPP_TRANSLATION_PROVIDER_ENABLED="${EASTAPP_TRANSLATION_PROVIDER_ENABLED:-true}"
export EASTAPP_CLOUDFLARE_ACCOUNT_ID="${EASTAPP_CLOUDFLARE_ACCOUNT_ID:-31a1bfe35e1108f953b35c50a82e4424}"
export EASTAPP_CLOUDFLARE_API_TOKEN="${EASTAPP_CLOUDFLARE_API_TOKEN:-cfut_hBwBAaqhvA8dtxC4rcbSkNqnhHFjTBTyw9QU1eeA53809508}"

echo "EastApp v091 fresh start requested."
echo "EastApp local translation provider: ${EASTAPP_TRANSLATION_PROVIDER_ENABLED}."
echo "A database wipe occurs only when BOTH gates are true:"
echo "  1. DATABASE_RESET_ALLOWED_BY_CODE=true"
echo "  2. EASTAPP_DATABASE_RESET_ON_START=true (set by this script)"
echo
echo "Starting PostgreSQL and EastApp..."
docker compose up -d postgres
./mvnw clean spring-boot:run
