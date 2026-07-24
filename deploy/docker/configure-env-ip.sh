#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_TARGET="${ENV_TARGET:-$SCRIPT_DIR/.env}"
ENV_EXAMPLE="${ENV_EXAMPLE:-$SCRIPT_DIR/.env.example}"

INTERNAL_IP="${DEPLOY_INTERNAL_IP:-}"
EXTERNAL_IP="${DEPLOY_EXTERNAL_IP:-}"
LEGACY_IP="${DEPLOY_HOST_IP:-}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --internal-ip)
      INTERNAL_IP="${2:-}"
      shift 2
      ;;
    --external-ip)
      EXTERNAL_IP="${2:-}"
      shift 2
      ;;
    --help|-h)
      echo "usage:" >&2
      echo "  $0 <server-ip>" >&2
      echo "  $0 --internal-ip <internal-ip> --external-ip <external-ip>" >&2
      echo "examples:" >&2
      echo "  $0 192.168.124.77" >&2
      echo "  $0 --internal-ip 10.222.123.5 --external-ip 175.155.35.79" >&2
      echo "set ENV_TARGET=/path/to/.env.example if you really want to update .env.example." >&2
      exit 0
      ;;
    -*)
      echo "unknown option: $1" >&2
      exit 1
      ;;
    *)
      LEGACY_IP="$1"
      shift
      ;;
  esac
done

if [ -n "$LEGACY_IP" ]; then
  INTERNAL_IP="${INTERNAL_IP:-$LEGACY_IP}"
  EXTERNAL_IP="${EXTERNAL_IP:-$LEGACY_IP}"
fi

if [ -z "$INTERNAL_IP" ] && [ -z "$EXTERNAL_IP" ]; then
  echo "usage:" >&2
  echo "  $0 <server-ip>" >&2
  echo "  $0 --internal-ip <internal-ip> --external-ip <external-ip>" >&2
  echo "examples:" >&2
  echo "  $0 192.168.124.77" >&2
  echo "  $0 --internal-ip 10.222.123.5 --external-ip 175.155.35.79" >&2
  echo "set ENV_TARGET=/path/to/.env.example if you really want to update .env.example." >&2
  exit 1
fi

INTERNAL_IP="${INTERNAL_IP:-$EXTERNAL_IP}"
EXTERNAL_IP="${EXTERNAL_IP:-$INTERNAL_IP}"

if [ ! -f "$ENV_TARGET" ]; then
  cp "$ENV_EXAMPLE" "$ENV_TARGET"
  echo "created $ENV_TARGET from $ENV_EXAMPLE"
fi

replace_all_host() {
  tmp_file="$ENV_TARGET.tmp.$$"
  sed "s/host\.docker\.internal/$INTERNAL_IP/g" "$ENV_TARGET" > "$tmp_file"
  mv "$tmp_file" "$ENV_TARGET"
}

set_env_value() {
  key=$1
  value=$2
  tmp_file="$ENV_TARGET.tmp.$$"

  if grep -q "^$key=" "$ENV_TARGET"; then
    awk -v key="$key" -v value="$value" '
      BEGIN { prefix = key "=" }
      index($0, prefix) == 1 {
        print key "=" value
        next
      }
      { print }
    ' "$ENV_TARGET" > "$tmp_file"
  else
    cp "$ENV_TARGET" "$tmp_file"
    printf '%s=%s\n' "$key" "$value" >> "$tmp_file"
  fi

  mv "$tmp_file" "$ENV_TARGET"
}

add_csv_value() {
  key=$1
  value=$2
  current_value=$(sed -n "s/^$key=//p" "$ENV_TARGET" | tail -n 1)
  case ",$current_value," in
    *",$value,"*) return ;;
  esac
  if [ -n "$current_value" ]; then
    set_env_value "$key" "$current_value,$value"
  else
    set_env_value "$key" "$value"
  fi
}

replace_all_host
set_env_value "LIVEKIT_URL" "ws://$EXTERNAL_IP:7880"
set_env_value "LIVEKIT_NODE_IP" "$EXTERNAL_IP"
set_env_value "NGINX_TLS_HOST" "$EXTERNAL_IP"
add_csv_value "BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS" "https://$EXTERNAL_IP:4443"

echo "updated $ENV_TARGET"
echo "  internal ip: $INTERNAL_IP"
echo "  external ip: $EXTERNAL_IP"
echo "remember to run INSTALL_MODE=overwrite ./install.sh when LiveKit/Nginx rendered configs already exist."
