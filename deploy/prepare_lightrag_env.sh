#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AI_ENV="$ROOT/deploy/ai-service.env"
TARGET_ENV="$ROOT/deploy/lightrag.env"
EXAMPLE_ENV="$ROOT/deploy/lightrag.env.example"
FORCE=0

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

require_file() {
  local file="$1"
  [[ -f "$file" ]] || { echo "Missing file: $file" >&2; exit 1; }
}

get_env_value() {
  local key="$1" file="$2"
  awk -F= -v search="$key" '
    $0 !~ /^[[:space:]]*#/ && $1 == search {
      sub(/^[^=]*=/, "", $0)
      print $0
      exit
    }
  ' "$file"
}

set_env_value() {
  local key="$1" value="$2" file="$3"
  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

require_file "$AI_ENV"
require_file "$EXAMPLE_ENV"

if [[ ! -f "$TARGET_ENV" || "$FORCE" == "1" ]]; then
  cp "$EXAMPLE_ENV" "$TARGET_ENV"
fi

AI_LLM_BASE_URL="$(get_env_value AI_LLM_BASE_URL "$AI_ENV")"
AI_LLM_API_KEY="$(get_env_value AI_LLM_API_KEY "$AI_ENV")"
AI_LLM_MODEL="$(get_env_value AI_LLM_MODEL "$AI_ENV")"
AI_EMBEDDING_GATEWAY_API_KEY="$(get_env_value AI_EMBEDDING_GATEWAY_API_KEY "$AI_ENV")"
AI_EMBEDDING_MODEL="$(get_env_value AI_EMBEDDING_MODEL "$AI_ENV")"
NEO4J_PASSWORD="$(get_env_value NEO4J_PASSWORD "$AI_ENV")"

[[ -n "$AI_LLM_BASE_URL" ]] && set_env_value "LLM_BINDING_HOST" "$AI_LLM_BASE_URL" "$TARGET_ENV"
[[ -n "$AI_LLM_API_KEY" ]] && set_env_value "LLM_BINDING_API_KEY" "$AI_LLM_API_KEY" "$TARGET_ENV"
[[ -n "$AI_LLM_MODEL" ]] && set_env_value "LLM_MODEL" "$AI_LLM_MODEL" "$TARGET_ENV"
[[ -n "$AI_EMBEDDING_GATEWAY_API_KEY" ]] && set_env_value "EMBEDDING_BINDING_API_KEY" "$AI_EMBEDDING_GATEWAY_API_KEY" "$TARGET_ENV"
[[ -n "$AI_EMBEDDING_MODEL" ]] && set_env_value "EMBEDDING_MODEL" "$AI_EMBEDDING_MODEL" "$TARGET_ENV"
[[ -n "$NEO4J_PASSWORD" ]] && set_env_value "NEO4J_PASSWORD" "$NEO4J_PASSWORD" "$TARGET_ENV"

echo "Prepared $TARGET_ENV"
echo "Check these keys before deployment:"
echo "- LIGHTRAG_API_KEY"
echo "- POSTGRES_PASSWORD"
echo "- NEO4J_PASSWORD"
echo "- LLM_BINDING_API_KEY"
