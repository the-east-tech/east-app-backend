#!/usr/bin/env bash
set -euo pipefail

case "${FLOW_DATABASE_RESET_ON_START:-false}" in
  true|TRUE|True)
    export FLOW_DATABASE_RESET_ON_START=true
    ;;
  *)
    export FLOW_DATABASE_RESET_ON_START=false
    ;;
esac

export FLOW_TRANSLATION_PROVIDER_ENABLED="${FLOW_TRANSLATION_PROVIDER_ENABLED:-false}"
export FLOW_CLOUDFLARE_ACCOUNT_ID="${FLOW_CLOUDFLARE_ACCOUNT_ID:-}"
export FLOW_CLOUDFLARE_API_TOKEN="${FLOW_CLOUDFLARE_API_TOKEN:-}"

echo "Flow local start."
echo "Database reset environment gate: ${FLOW_DATABASE_RESET_ON_START}."
echo "A reset occurs only when this gate and DATABASE_RESET_ALLOWED_BY_CODE are both true."
echo "Flow local translation provider: ${FLOW_TRANSLATION_PROVIDER_ENABLED}."
docker compose up -d postgres
./mvnw clean spring-boot:run
