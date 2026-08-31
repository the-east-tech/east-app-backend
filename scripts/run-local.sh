#!/usr/bin/env bash
set -euo pipefail

case "${EASTAPP_DATABASE_RESET_ON_START:-false}" in
  true|TRUE|True)
    export EASTAPP_DATABASE_RESET_ON_START=true
    ;;
  *)
    export EASTAPP_DATABASE_RESET_ON_START=false
    ;;
esac

export EASTAPP_TRANSLATION_PROVIDER_ENABLED="${EASTAPP_TRANSLATION_PROVIDER_ENABLED:-true}"
export EASTAPP_CLOUDFLARE_ACCOUNT_ID="${EASTAPP_CLOUDFLARE_ACCOUNT_ID:-31a1bfe35e1108f953b35c50a82e4424}"
export EASTAPP_CLOUDFLARE_API_TOKEN="${EASTAPP_CLOUDFLARE_API_TOKEN:-cfut_hBwBAaqhvA8dtxC4rcbSkNqnhHFjTBTyw9QU1eeA53809508}"

echo "EastApp v098 local start."
echo "Database reset environment gate: ${EASTAPP_DATABASE_RESET_ON_START}."
echo "A reset occurs only when this gate and DATABASE_RESET_ALLOWED_BY_CODE are both true."
echo "EastApp local translation provider: ${EASTAPP_TRANSLATION_PROVIDER_ENABLED}."
docker compose up -d postgres
./mvnw spring-boot:run
