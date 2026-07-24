#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$SCRIPT_DIR/.env"
PACKAGE_DIR="${PACKAGE_DIR:-$SCRIPT_DIR/packages}"
CONFIG_DIR="${CONFIG_DIR:-$SCRIPT_DIR/config}"

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
  tmp_file="$ENV_FILE.tmp.$$"
  if grep -q "^$key=" "$ENV_FILE"; then
    awk -v key="$key" -v value="$value" '
      BEGIN { prefix = key "=" }
      index($0, prefix) == 1 { print key "=" value; next }
      { print }
    ' "$ENV_FILE" > "$tmp_file"
  else
    cp "$ENV_FILE" "$tmp_file"
    printf '%s=%s\n' "$key" "$value" >> "$tmp_file"
  fi
  mv "$tmp_file" "$ENV_FILE"
}

template_default() {
  key=$1
  case "$key" in
    APP_WORKSPACE_ROOT) printf '%s' ~/mounts/media ;;
    NGINX_MEDIA_SERVICE_UPSTREAM) printf '%s' host.docker.internal:8088 ;;
    NGINX_BIGSCREEN_BFF_UPSTREAM) printf '%s' host.docker.internal:8090 ;;
    NGINX_LIVEKIT_UPSTREAM) printf '%s' host.docker.internal:7880 ;;
    NGINX_MEDIA_SERVICE_TEST_UPSTREAM) printf '%s' host.docker.internal:28088 ;;
    NGINX_BIGSCREEN_BFF_TEST_UPSTREAM) printf '%s' host.docker.internal:28090 ;;
    NGINX_LIVEKIT_TEST_UPSTREAM) printf '%s' host.docker.internal:7880 ;;
    NGINX_HTTPS_PORT) printf '%s' 4443 ;;
    NGINX_ALT_HTTPS_PORT) printf '%s' 24443 ;;
    LIVEKIT_HTTP_PORT) printf '%s' 7880 ;;
    LIVEKIT_RTC_TCP_PORT) printf '%s' 7881 ;;
    LIVEKIT_RTC_PORT_RANGE_START) printf '%s' 50000 ;;
    LIVEKIT_RTC_PORT_RANGE_END) printf '%s' 50100 ;;
    LIVEKIT_USE_EXTERNAL_IP) printf '%s' false ;;
    LIVEKIT_LOG_LEVEL) printf '%s' info ;;
    LIVEKIT_REDIS_ADDRESS) printf '%s' host.docker.internal:6379 ;;
    LIVEKIT_REDIS_USERNAME) printf '%s' "" ;;
    LIVEKIT_REDIS_PASSWORD) printf '%s' "" ;;
    LIVEKIT_REDIS_DB) printf '%s' 0 ;;
    LIVEKIT_NODE_IP) printf '%s' "" ;;
    LIVEKIT_API_KEY) printf '%s' devkey ;;
    LIVEKIT_API_SECRET) printf '%s' dev-secret-dev-secret-dev-secret-32 ;;
    LIVEKIT_EGRESS_WS_URL) printf '%s' ws://livekit-server:7880 ;;
    LIVEKIT_EGRESS_REDIS_ADDRESS) printf '%s' host.docker.internal:6379 ;;
    LIVEKIT_EGRESS_REDIS_USERNAME) printf '%s' "" ;;
    LIVEKIT_EGRESS_REDIS_PASSWORD) printf '%s' "" ;;
    LIVEKIT_EGRESS_REDIS_DB) printf '%s' 0 ;;
    MINIO_ENDPOINT) printf '%s' http://host.docker.internal:9000 ;;
    MINIO_ACCESS_KEY) printf '%s' minioadmin ;;
    MINIO_SECRET_KEY) printf '%s' minioadmin ;;
    MINIO_BUCKET) printf '%s' robot-media ;;
    *) printf '%s' "" ;;
  esac
}

render_template_file() {
  target_file=$1
  keys="APP_WORKSPACE_ROOT NGINX_MEDIA_SERVICE_UPSTREAM NGINX_BIGSCREEN_BFF_UPSTREAM NGINX_LIVEKIT_UPSTREAM NGINX_MEDIA_SERVICE_TEST_UPSTREAM NGINX_BIGSCREEN_BFF_TEST_UPSTREAM NGINX_LIVEKIT_TEST_UPSTREAM NGINX_HTTPS_PORT NGINX_ALT_HTTPS_PORT LIVEKIT_HTTP_PORT LIVEKIT_RTC_TCP_PORT LIVEKIT_RTC_PORT_RANGE_START LIVEKIT_RTC_PORT_RANGE_END LIVEKIT_USE_EXTERNAL_IP LIVEKIT_LOG_LEVEL LIVEKIT_REDIS_ADDRESS LIVEKIT_REDIS_USERNAME LIVEKIT_REDIS_PASSWORD LIVEKIT_REDIS_DB LIVEKIT_NODE_IP LIVEKIT_API_KEY LIVEKIT_API_SECRET LIVEKIT_EGRESS_WS_URL LIVEKIT_EGRESS_REDIS_ADDRESS LIVEKIT_EGRESS_REDIS_USERNAME LIVEKIT_EGRESS_REDIS_PASSWORD LIVEKIT_EGRESS_REDIS_DB MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET"

  for key in $keys; do
    default_value=$(template_default "$key")
    value=$(env_value "$key" "$default_value")
    if [ "$key" = "APP_WORKSPACE_ROOT" ]; then
      value=$(expand_user_path "$value")
    fi
    escaped_value=$(printf '%s' "$value" | sed 's/[\/&|]/\\&/g')
    tmp_file="$target_file.tmp.$$"
    sed "s|{{$key}}|$escaped_value|g" "$target_file" > "$tmp_file"
    mv "$tmp_file" "$target_file"
  done
}

raw_workspace_root=$(env_value APP_WORKSPACE_ROOT ~/mounts/media)
APP_WORKSPACE_ROOT=$(expand_user_path "$raw_workspace_root")
if [ "$APP_WORKSPACE_ROOT" != "$raw_workspace_root" ]; then
  set_env_value APP_WORKSPACE_ROOT "$APP_WORKSPACE_ROOT"
fi
INSTALL_MODE=$(env_value INSTALL_MODE skip_existing)

ensure_workspace_writable() {
  check_file="$APP_WORKSPACE_ROOT/.write-check.$$"

  if ! mkdir -p "$APP_WORKSPACE_ROOT" 2>/dev/null; then
    echo "cannot create APP_WORKSPACE_ROOT: $APP_WORKSPACE_ROOT" >&2
    echo "Choose a user-writable APP_WORKSPACE_ROOT, for example: ~/mounts/media" >&2
    exit 1
  fi

  if ! : > "$check_file" 2>/dev/null; then
    echo "APP_WORKSPACE_ROOT is not writable: $APP_WORKSPACE_ROOT" >&2
    echo "Choose a user-writable APP_WORKSPACE_ROOT, for example: ~/mounts/media" >&2
    exit 1
  fi

  rm -f "$check_file"
}

ensure_workspace_writable

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
  if [ -d "$target_dir/bin" ]; then
    chmod +x "$target_dir"/bin/*.sh 2>/dev/null || true
  fi
  echo "installed $target_name to $target_dir"
}

extract_service "$PACKAGE_DIR/robot-mediaserver-dist.tar.gz" "media-service"
extract_service "$PACKAGE_DIR/robot-control-service-dist.tar.gz" "control-service"
extract_service "$PACKAGE_DIR/bigscreen-bff-dist.tar.gz" "bigscreen-bff"

livekit_dir="$APP_WORKSPACE_ROOT/livekit-server"
egress_dir="$APP_WORKSPACE_ROOT/livekit-egress"
livekit_file="$APP_WORKSPACE_ROOT/livekit.yaml"
egress_file="$APP_WORKSPACE_ROOT/livekit-egress.yaml"
mkdir -p "$livekit_dir" "$egress_dir"

install_config_file() {
  source_file=$1
  target_file=$2
  label=$3

  if [ ! -f "$source_file" ]; then
    return 1
  fi

  if [ -d "$target_file" ]; then
    if [ "$INSTALL_MODE" = "overwrite" ]; then
      rm -rf "$target_file"
    else
      echo "target file path is a directory: $target_file" >&2
      echo "This usually happens when docker compose starts before the source file exists and creates a bind-mount directory." >&2
      echo "Remove the directory manually or set INSTALL_MODE=overwrite and run install.sh again." >&2
      exit 1
    fi
  fi

  if [ "$INSTALL_MODE" != "overwrite" ] && [ -f "$target_file" ]; then
    echo "skip existing $target_file"
    return 0
  fi

  mkdir -p "$(dirname "$target_file")"
  cp "$source_file" "$target_file"
  render_template_file "$target_file"
  echo "installed $label config to $target_file"
  return 0
}

install_config_dir() {
  source_dir=$1
  target_dir=$2
  label=$3

  if [ ! -d "$source_dir" ]; then
    return 1
  fi

  if [ "$INSTALL_MODE" != "overwrite" ] && [ -d "$target_dir" ] && [ "$(find "$target_dir" -mindepth 1 -maxdepth 1 | head -n 1)" ]; then
    echo "skip existing $target_dir"
    return 0
  fi

  mkdir -p "$target_dir"
  if [ "$INSTALL_MODE" = "overwrite" ]; then
    find "$target_dir" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  fi
  cp -R "$source_dir"/. "$target_dir"/
  echo "installed $label files to $target_dir"
  return 0
}

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

if install_config_file "$CONFIG_DIR/livekit/livekit.yaml" "$livekit_file" "livekit"; then
  :
elif [ "$INSTALL_MODE" = "overwrite" ] || [ ! -f "$livekit_file" ]; then
  cat > "$livekit_file" <<EOF
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
  echo "prepared $livekit_file"
else
  echo "skip existing $livekit_file"
fi

if install_config_file "$CONFIG_DIR/livekit/livekit-egress.yaml" "$egress_file" "livekit egress"; then
  :
elif install_config_file "$CONFIG_DIR/livekit/egress.yaml" "$egress_file" "livekit egress"; then
  :
elif [ "$INSTALL_MODE" = "overwrite" ] || [ ! -f "$egress_file" ]; then
  cat > "$egress_file" <<EOF
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
  echo "prepared $egress_file"
else
  echo "skip existing $egress_file"
fi

nginx_dir="$APP_WORKSPACE_ROOT/nginx"
nginx_conf_dir="$nginx_dir/conf"
nginx_ssl_dir="$nginx_dir/ssl"
nginx_html_dir="$nginx_dir/html"
nginx_logs_dir="$nginx_dir/logs"
mkdir -p "$nginx_conf_dir" "$nginx_ssl_dir" "$nginx_html_dir" "$nginx_logs_dir"

if ! install_config_file "$CONFIG_DIR/nginx/nginx.conf" "$nginx_conf_dir/nginx.conf" "nginx"; then
  install_config_file "$CONFIG_DIR/nginx/robot-mediaserver.conf" "$nginx_conf_dir/nginx.conf" "nginx" || true
fi

if [ ! -f "$nginx_conf_dir/nginx.conf" ]; then
  echo "missing nginx config: $nginx_conf_dir/nginx.conf" >&2
  exit 1
fi

install_config_dir "$CONFIG_DIR/nginx/html/dist" "$nginx_html_dir/dist" "robot-ui dist" || true
install_tdt_archive() {
  archive_file=$1
  archive_type=$2

  if [ "$INSTALL_MODE" != "overwrite" ] && [ -d "$nginx_html_dir/tdt" ] && [ "$(find "$nginx_html_dir/tdt" -mindepth 1 -maxdepth 1 | head -n 1)" ]; then
    echo "skip existing $nginx_html_dir/tdt"
  else
    tmp_tdt_dir="$nginx_html_dir/.tdt-unpack.$$"
    rm -rf "$tmp_tdt_dir"
    mkdir -p "$tmp_tdt_dir"

    case "$archive_type" in
      tar)
        tar -xzf "$archive_file" -C "$tmp_tdt_dir"
        ;;
      zip)
        if command -v unzip >/dev/null 2>&1; then
          unzip -q "$archive_file" -d "$tmp_tdt_dir"
        else
          echo "unzip is required to extract $archive_file" >&2
          rm -rf "$tmp_tdt_dir"
          exit 1
        fi
        ;;
      *)
        echo "unsupported tdt archive type: $archive_type" >&2
        rm -rf "$tmp_tdt_dir"
        exit 1
        ;;
    esac

    if [ -d "$tmp_tdt_dir/tdt" ]; then
      source_tdt_dir="$tmp_tdt_dir/tdt"
    else
      source_tdt_dir="$tmp_tdt_dir"
    fi

    rm -rf "$nginx_html_dir/tdt"
    mkdir -p "$nginx_html_dir/tdt"
    cp -R "$source_tdt_dir"/. "$nginx_html_dir/tdt"/
    rm -rf "$tmp_tdt_dir"
    echo "installed tdt map files to $nginx_html_dir/tdt"
  fi
}

if [ -f "$CONFIG_DIR/nginx/html/tdt.tar.gz" ]; then
  install_tdt_archive "$CONFIG_DIR/nginx/html/tdt.tar.gz" tar
elif [ -f "$CONFIG_DIR/nginx/html/tdt.tgz" ]; then
  install_tdt_archive "$CONFIG_DIR/nginx/html/tdt.tgz" tar
elif [ -f "$CONFIG_DIR/nginx/html/tdt.zip" ]; then
  install_tdt_archive "$CONFIG_DIR/nginx/html/tdt.zip" zip
else
  install_config_dir "$CONFIG_DIR/nginx/html/tdt" "$nginx_html_dir/tdt" "tdt map files" || true
fi

tts_dir="$APP_WORKSPACE_ROOT/tts"
mkdir -p "$tts_dir"
if install_config_file "$CONFIG_DIR/tts/app.py" "$tts_dir/app.py" "tts app"; then
  :
elif [ ! -f "$tts_dir/app.py" ]; then
  echo "missing tts app override: $tts_dir/app.py"
  echo "put app.py in config/tts/app.py before packaging, or create $tts_dir/app.py before starting tts"
  exit 1
fi

if [ ! -f "$nginx_ssl_dir/server.crt" ] || [ ! -f "$nginx_ssl_dir/server.key" ]; then
  nginx_tls_host=$(env_value NGINX_TLS_HOST localhost)
  if command -v openssl >/dev/null 2>&1; then
    tmp_config=$(mktemp)
    trap 'rm -f "$tmp_config"' EXIT
    case "$nginx_tls_host" in
      *[!0-9.]*)
        subject_alt_name="DNS:$nginx_tls_host"
        ;;
      *)
        subject_alt_name="IP:$nginx_tls_host"
        ;;
    esac
    cat > "$tmp_config" <<EOF
[req]
distinguished_name = dn
x509_extensions = ext
prompt = no

[dn]
CN = $nginx_tls_host

[ext]
subjectAltName = $subject_alt_name
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
basicConstraints = critical, CA:FALSE
EOF
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
      -keyout "$nginx_ssl_dir/server.key" \
      -out "$nginx_ssl_dir/server.crt" \
      -config "$tmp_config"
    echo "prepared nginx self-signed certificate for $nginx_tls_host"
  else
    echo "missing nginx certificate and openssl is unavailable: $nginx_ssl_dir/server.crt $nginx_ssl_dir/server.key" >&2
    exit 1
  fi
else
  echo "skip existing nginx certificate"
fi

echo "workspace prepared at $APP_WORKSPACE_ROOT"
