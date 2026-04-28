#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="dev"
DRYRUN=0
SKIP_DB=0
INIT_DB=0
INSTALL_AI_DEPS=0
SEED_AI_KB=0

for arg in "$@"; do
  case "$arg" in
    dev|prod) MODE="$arg" ;;
    --dry-run|dry-run) DRYRUN=1 ;;
    --skip-db) SKIP_DB=1 ;;
    --init-db) INIT_DB=1 ;;
    --install-ai-deps) INSTALL_AI_DEPS=1 ;;
    --seed-ai-kb) SEED_AI_KB=1 ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

BACKEND_DIR="$ROOT/back"
[[ -d "$BACKEND_DIR" ]] || BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
AI_SERVICE_DIR="$ROOT/ai-service"
LOGS_DIR="$ROOT/logs"
PIDS_DIR="$ROOT/.pids"
mkdir -p "$LOGS_DIR" "$PIDS_DIR"

have() { command -v "$1" >/dev/null 2>&1; }
port_listening() {
  if have ss; then ss -ltn "sport = :$1" | grep -q ":$1"; return; fi
  if have netstat; then netstat -ltn | grep -q ":$1 "; return; fi
  return 1
}

run_bg() {
  local name="$1" workdir="$2" logfile="$LOGS_DIR/$name.log" pidfile="$PIDS_DIR/$name.pid"
  shift 2
  if [[ "$DRYRUN" == "1" ]]; then
    printf '[dry-run] (cd "%s" && %s) > "%s" 2>&1 &\n' "$workdir" "$*" "$logfile"
    return 0
  fi
  (cd "$workdir" && "$@") >"$logfile" 2>&1 &
  echo "$!" >"$pidfile"
  echo "Started $name (pid=$!), logs: $logfile"
}

start_db_if_needed() {
  [[ "$SKIP_DB" == "1" ]] && return 0
  if port_listening 3306; then
    echo "MySQL already listening on 3306, skip docker mysql."
  elif have docker; then
    if [[ "$DRYRUN" == "1" ]]; then echo '[dry-run] docker compose up -d mysql'; else (cd "$ROOT" && docker compose up -d mysql); fi
  else
    echo "WARN: MySQL is not listening on 3306 and docker is unavailable. Start MySQL manually before backend." >&2
  fi

  if [[ "$INIT_DB" == "1" ]]; then
    have mysql || { echo "WARN: mysql client not found, skip database import." >&2; return 0; }
    if [[ "$DRYRUN" == "1" ]]; then
      echo '[dry-run] mysql -uroot -p123456 web < back/sql/init_db.sql'
    else
      mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
      mysql -uroot -p123456 --default-character-set=utf8mb4 web < "$ROOT/back/sql/init_db.sql"
    fi
  fi
}

start_dev() {
  start_db_if_needed
  [[ -f "$BACKEND_DIR/pom.xml" ]] || { echo "Backend pom.xml not found: $BACKEND_DIR" >&2; exit 1; }
  [[ -f "$FRONTEND_DIR/package.json" ]] || { echo "Frontend package.json not found: $FRONTEND_DIR" >&2; exit 1; }
  [[ -f "$AI_SERVICE_DIR/app/main.py" ]] || { echo "AI service entry not found: $AI_SERVICE_DIR" >&2; exit 1; }
  have mvn || { echo "mvn not found" >&2; exit 1; }
  have npm || { echo "npm not found" >&2; exit 1; }
  have python || { echo "python not found" >&2; exit 1; }
  if [[ "$INSTALL_AI_DEPS" == "1" ]]; then
    if [[ "$DRYRUN" == "1" ]]; then
      echo '[dry-run] python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com'
    else
      (cd "$AI_SERVICE_DIR" && python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com)
    fi
  fi
  if [[ "$SEED_AI_KB" == "1" ]]; then
    if [[ "$DRYRUN" == "1" ]]; then
      echo '[dry-run] python app/ingest/sync_job.py'
    else
      (cd "$AI_SERVICE_DIR" && python app/ingest/sync_job.py)
    fi
  fi
  run_bg backend "$BACKEND_DIR" mvn spring-boot:run
  run_bg ai-service "$AI_SERVICE_DIR" python -m uvicorn app.main:app --host 127.0.0.1 --port 9000 --reload
  run_bg frontend "$FRONTEND_DIR" npm run dev -- --host 127.0.0.1
  echo "Frontend: http://127.0.0.1:5173/"
  echo "Backend:  http://localhost:8080/api"
  echo "AI:       http://127.0.0.1:9000/health"
}

start_prod() {
  have docker || { echo "docker not found; install/start Docker or use dev mode" >&2; exit 1; }
  local env_args=()
  [[ -f "$ROOT/deploy/prod.env" ]] && env_args=(--env-file deploy/prod.env)
  if [[ "$DRYRUN" == "1" ]]; then
    printf '[dry-run] docker compose %s -f docker-compose.prod.yml up -d --build\n' "${env_args[*]}"
  else
    (cd "$ROOT" && docker compose "${env_args[@]}" -f docker-compose.prod.yml up -d --build)
  fi
}

if [[ "$MODE" == "prod" ]]; then start_prod; else start_dev; fi
