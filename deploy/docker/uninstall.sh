#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
ENV_FILE="$SCRIPT_DIR/.env"

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "docker compose or docker-compose is required" >&2
  exit 1
fi

env_value() {
  key=$1
  default_value=$2
  if [ -f "$ENV_FILE" ]; then
    value=$(sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1)
  else
    value=""
  fi
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

expand_user_path() {
  path_value=$1
  case "$path_value" in
    "~")
      printf '%s' "$HOME"
      ;;
    "~/"*)
      printf '%s/%s' "$HOME" "${path_value#~/}"
      ;;
    *)
      printf '%s' "$path_value"
      ;;
  esac
}

purge_workspace() {
  workspace_root=$(expand_user_path "$(env_value APP_WORKSPACE_ROOT ~/mounts/media)")

  case "$workspace_root" in
    ""|"/"|"$HOME"|"$HOME/")
      echo "refuse to purge unsafe APP_WORKSPACE_ROOT: $workspace_root" >&2
      exit 1
      ;;
  esac

  if [ -d "$workspace_root" ]; then
    echo "removing workspace: $workspace_root"
    rm -rf "$workspace_root"
  else
    echo "workspace not found, skip: $workspace_root"
  fi
}

cd "$SCRIPT_DIR"
remove_volumes=false
remove_workspace=false

while [ "$#" -gt 0 ]; do
  case "$1" in
    --volumes)
      remove_volumes=true
      ;;
    --purge-workspace|--remove-workspace)
      remove_workspace=true
      ;;
    *)
      echo "unknown option: $1" >&2
      echo "usage: ./uninstall.sh [--volumes] [--purge-workspace]" >&2
      exit 1
      ;;
  esac
  shift
done

if [ "$remove_volumes" = "true" ]; then
  $COMPOSE -f "$COMPOSE_FILE" down --remove-orphans --volumes
else
  $COMPOSE -f "$COMPOSE_FILE" down --remove-orphans
fi

if [ "$remove_workspace" = "true" ]; then
  purge_workspace
else
  echo "workspace kept. use ./uninstall.sh --purge-workspace to remove APP_WORKSPACE_ROOT."
fi
