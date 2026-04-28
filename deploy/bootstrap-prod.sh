#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROD_ENV="$ROOT/deploy/prod.env"
PROD_ENV_EXAMPLE="$ROOT/deploy/prod.env.example"
AI_ENV="$ROOT/deploy/ai-service.env"
AI_ENV_EXAMPLE="$ROOT/deploy/ai-service.env.example"
LIGHTRAG_ENV="$ROOT/deploy/lightrag.env"

ensure_file() {
  local target="$1" example="$2"
  if [[ ! -f "$target" ]]; then
    cp "$example" "$target"
    echo "Created $target from template."
  fi
}

require_no_placeholder() {
  local file="$1"
  if grep -Eq 'change_me|replace_with_' "$file"; then
    echo "Placeholder values still exist in $file" >&2
    exit 1
  fi
}

ensure_file "$PROD_ENV" "$PROD_ENV_EXAMPLE"
ensure_file "$AI_ENV" "$AI_ENV_EXAMPLE"

if [[ ! -f "$LIGHTRAG_ENV" ]]; then
  "$ROOT/deploy/prepare_lightrag_env.sh"
fi

require_no_placeholder "$PROD_ENV"
require_no_placeholder "$AI_ENV"
require_no_placeholder "$LIGHTRAG_ENV"

cd "$ROOT"
docker compose --env-file deploy/prod.env -f docker-compose.prod.yml up -d --build

echo
echo "Deployment started."
echo "Frontend:  http://SERVER_IP/"
echo "Admin KB:  http://SERVER_IP/admin/kb"
echo "Products:  http://SERVER_IP/api/v1/products"
