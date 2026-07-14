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

if [ ! -f "$SCRIPT_DIR/.env" ]; then
  cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
  echo "created $SCRIPT_DIR/.env from .env.example"
fi

sh "$SCRIPT_DIR/load-images.sh"
sh "$SCRIPT_DIR/prepare-workspace.sh"

cd "$SCRIPT_DIR"
$COMPOSE -f "$COMPOSE_FILE" up -d "$@"
