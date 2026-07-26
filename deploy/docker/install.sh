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

env_value() {
  key=$1
  default_value=$2
  value=$(sed -n "s/^$key=//p" "$SCRIPT_DIR/.env" | tail -n 1)
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

set_env_value() {
  key=$1
  value=$2
  tmp_file="$SCRIPT_DIR/.env.tmp.$$"
  if grep -q "^$key=" "$SCRIPT_DIR/.env"; then
    awk -v key="$key" -v value="$value" '
      BEGIN { prefix = key "=" }
      index($0, prefix) == 1 { print key "=" value; next }
      { print }
    ' "$SCRIPT_DIR/.env" > "$tmp_file"
  else
    cp "$SCRIPT_DIR/.env" "$tmp_file"
    printf '%s=%s\n' "$key" "$value" >> "$tmp_file"
  fi
  mv "$tmp_file" "$SCRIPT_DIR/.env"
}

set_env_value_if_default() {
  key=$1
  default_value=$2
  new_value=$3
  current_value=$(env_value "$key" "$default_value")
  if [ "$current_value" = "$default_value" ]; then
    set_env_value "$key" "$new_value"
  fi
}

raw_workspace_root=$(env_value APP_WORKSPACE_ROOT ~/mounts/media)
APP_WORKSPACE_ROOT=$(expand_user_path "$raw_workspace_root")
if [ "$APP_WORKSPACE_ROOT" != "$raw_workspace_root" ]; then
  set_env_value APP_WORKSPACE_ROOT "$APP_WORKSPACE_ROOT"
fi
export APP_WORKSPACE_ROOT

DEPLOY_NETWORK_MODE=$(env_value DEPLOY_NETWORK_MODE bridge)
case "$DEPLOY_NETWORK_MODE" in
  bridge)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
    ;;
  host)
    COMPOSE_FILE="$SCRIPT_DIR/docker-compose.host.yml"
    if [ ! -f "$COMPOSE_FILE" ]; then
      echo "missing host network compose file: $COMPOSE_FILE" >&2
      exit 1
    fi
    set_env_value_if_default NGINX_MEDIA_SERVICE_UPSTREAM host.docker.internal:8088 127.0.0.1:8088
    set_env_value_if_default NGINX_BIGSCREEN_BFF_UPSTREAM host.docker.internal:8090 127.0.0.1:8090
    set_env_value_if_default NGINX_LIVEKIT_UPSTREAM host.docker.internal:7880 127.0.0.1:7880
    set_env_value_if_default NGINX_MEDIA_SERVICE_TEST_UPSTREAM host.docker.internal:28088 127.0.0.1:28088
    set_env_value_if_default NGINX_BIGSCREEN_BFF_TEST_UPSTREAM host.docker.internal:28090 127.0.0.1:28090
    set_env_value_if_default NGINX_LIVEKIT_TEST_UPSTREAM host.docker.internal:7880 127.0.0.1:7880
    set_env_value_if_default LIVEKIT_URL ws://host.docker.internal:7880 ws://127.0.0.1:7880
    set_env_value_if_default LIVEKIT_EGRESS_WS_URL ws://livekit-server:7880 ws://127.0.0.1:7880
    set_env_value_if_default MEDIA_SERVICE_BASE_URL http://media-service:8088 http://127.0.0.1:8088
    set_env_value_if_default CENTER_MANAGE_BASE_URL http://host.docker.internal:8866 http://127.0.0.1:8866
    set_env_value_if_default CENTER_CONTROL_BASE_URL http://control-service:8082 http://127.0.0.1:8082
    set_env_value_if_default CENTER_MEDIA_BASE_URL http://media-service:8088 http://127.0.0.1:8088
    set_env_value_if_default CENTER_CONTROL_WS_URL ws://control-service:8082/ws/control ws://127.0.0.1:8082/ws/control
    set_env_value_if_default TTS_ENGINE_URL http://tts:8080/api/tts http://127.0.0.1:8080/api/tts
    ;;
  *)
    echo "unsupported DEPLOY_NETWORK_MODE: $DEPLOY_NETWORK_MODE, expected bridge or host" >&2
    exit 1
    ;;
esac
export DEPLOY_NETWORK_MODE

sh "$SCRIPT_DIR/load-images.sh"
sh "$SCRIPT_DIR/prepare-workspace.sh"
sh "$SCRIPT_DIR/preflight-network.sh"

cd "$SCRIPT_DIR"
$COMPOSE -f "$COMPOSE_FILE" up -d "$@"
