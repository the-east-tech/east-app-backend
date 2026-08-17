#!/usr/bin/env bash
set -euo pipefail

export EASTAPP_DATABASE_RESET_ON_START=false

echo "EastApp local start: database reset disabled."
docker compose up -d postgres
./mvnw spring-boot:run
