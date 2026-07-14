#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$SCRIPT_DIR/.env"
PACKAGE_DIR="${PACKAGE_DIR:-$SCRIPT_DIR/packages}"

if [ ! -f "$ENV_FILE" ]; then
  cp "$SCRIPT_DIR/.env.example" "$ENV_FILE"
  echo "created $ENV_FILE from .env.example"
fi

env_value() {
  key=$1
  default_value=$2
  value=$(sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1)
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

APP_WORKSPACE_ROOT=$(env_value APP_WORKSPACE_ROOT /opt/qihang/robot/dockerWorkspace)
INSTALL_MODE=$(env_value INSTALL_MODE skip_existing)

extract_service() {
  package_file=$1
  target_name=$2
  target_dir="$APP_WORKSPACE_ROOT/$target_name"

  if [ ! -f "$package_file" ]; then
    echo "missing package: $package_file" >&2
    exit 1
  fi

  if [ "$INSTALL_MODE" != "overwrite" ] && [ -d "$target_dir" ] && [ "$(find "$target_dir" -mindepth 1 -maxdepth 1 | head -n 1)" ]; then
    echo "skip existing $target_name at $target_dir"
    return
  fi

  mkdir -p "$target_dir"
  tar -xzf "$package_file" -C "$target_dir" --strip-components=1
  echo "installed $target_name to $target_dir"
}

extract_service "$PACKAGE_DIR/robot-mediaserver-dist.tar.gz" "media-service"
extract_service "$PACKAGE_DIR/robot-control-service-dist.tar.gz" "control-service"
extract_service "$PACKAGE_DIR/bigscreen-bff-dist.tar.gz" "bigscreen-bff"

livekit_dir="$APP_WORKSPACE_ROOT/livekit-server"
egress_dir="$APP_WORKSPACE_ROOT/livekit-egress"
mkdir -p "$livekit_dir" "$egress_dir"

livekit_api_key=$(env_value LIVEKIT_API_KEY devkey)
livekit_api_secret=$(env_value LIVEKIT_API_SECRET dev-secret-dev-secret-dev-secret-32)
livekit_log_level=$(env_value LIVEKIT_LOG_LEVEL info)
livekit_redis_address=$(env_value LIVEKIT_REDIS_ADDRESS host.docker.internal:6379)
livekit_port_start=$(env_value LIVEKIT_RTC_PORT_RANGE_START 50000)
livekit_port_end=$(env_value LIVEKIT_RTC_PORT_RANGE_END 50100)
livekit_use_external_ip=$(env_value LIVEKIT_USE_EXTERNAL_IP false)
livekit_egress_ws_url=$(env_value LIVEKIT_EGRESS_WS_URL ws://livekit-server:7880)
livekit_egress_redis_address=$(env_value LIVEKIT_EGRESS_REDIS_ADDRESS host.docker.internal:6379)
minio_endpoint=$(env_value MINIO_ENDPOINT http://host.docker.internal:9000)
minio_access_key=$(env_value MINIO_ACCESS_KEY minioadmin)
minio_secret_key=$(env_value MINIO_SECRET_KEY minioadmin)
minio_bucket=$(env_value MINIO_BUCKET robot-media)

if [ "$INSTALL_MODE" = "overwrite" ] || [ ! -f "$livekit_dir/livekit.yaml" ]; then
  cat > "$livekit_dir/livekit.yaml" <<EOF
port: 7880
log_level: $livekit_log_level
rtc:
  tcp_port: 7881
  port_range_start: $livekit_port_start
  port_range_end: $livekit_port_end
  use_external_ip: $livekit_use_external_ip
redis:
  address: $livekit_redis_address
keys:
  $livekit_api_key: $livekit_api_secret
EOF
  echo "prepared $livekit_dir/livekit.yaml"
else
  echo "skip existing $livekit_dir/livekit.yaml"
fi

if [ "$INSTALL_MODE" = "overwrite" ] || [ ! -f "$egress_dir/egress.yaml" ]; then
  cat > "$egress_dir/egress.yaml" <<EOF
log_level: $livekit_log_level
api_key: $livekit_api_key
api_secret: $livekit_api_secret
ws_url: $livekit_egress_ws_url
insecure: true
redis:
  address: $livekit_egress_redis_address
s3:
  access_key: $minio_access_key
  secret: $minio_secret_key
  region: us-east-1
  endpoint: $minio_endpoint
  bucket: $minio_bucket
  force_path_style: true
EOF
  echo "prepared $egress_dir/egress.yaml"
else
  echo "skip existing $egress_dir/egress.yaml"
fi

echo "workspace prepared at $APP_WORKSPACE_ROOT"
