#!/usr/bin/env bash
set -euo pipefail

export EASTAPP_DATABASE_RESET_ON_START=false
export EASTAPP_TRANSLATION_PROVIDER_ENABLED="${EASTAPP_TRANSLATION_PROVIDER_ENABLED:-true}"
export EASTAPP_CLOUDFLARE_ACCOUNT_ID="${EASTAPP_CLOUDFLARE_ACCOUNT_ID:-31a1bfe35e1108f953b35c50a82e4424}"
export EASTAPP_CLOUDFLARE_API_TOKEN="${EASTAPP_CLOUDFLARE_API_TOKEN:-cfut_hBwBAaqhvA8dtxC4rcbSkNqnhHFjTBTyw9QU1eeA53809508}"

echo "EastApp v090 local start: database reset environment gate is false; data will be preserved."
echo "EastApp local translation provider: ${EASTAPP_TRANSLATION_PROVIDER_ENABLED}."
docker compose up -d postgres
./mvnw spring-boot:run
