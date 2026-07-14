#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "docker compose or docker-compose is required" >&2
  exit 1
fi

cd "$SCRIPT_DIR"
if [ "${1:-}" = "--volumes" ]; then
  $COMPOSE -f "$COMPOSE_FILE" down --remove-orphans --volumes
else
  $COMPOSE -f "$COMPOSE_FILE" down --remove-orphans
fi
