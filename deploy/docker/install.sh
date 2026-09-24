#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
INTERNAL_DIR="$SCRIPT_DIR/scripts/internal"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
STANDARD_INSTALL_DIR="${ROBOT_INSTALL_DIR:-/home/robot-mediaserver-installer}"

usage() {
  cat <<'EOF'
用法：
  ./install.sh [选项] [服务名...]

不带参数并在终端执行时进入交互式安装向导。

选项：
  --server-ip <IP>       内外网使用同一个服务器 IP
  --internal-ip <IP>     容器访问中间件和内部服务使用的 IP
  --external-ip <IP>     下发给浏览器和机器人使用的 IP
  --overwrite            覆盖运行目录中的程序和渲染配置
  -h, --help             显示帮助

示例：
  ./install.sh --server-ip 192.168.124.77
  ./install.sh --internal-ip 10.222.123.5 --external-ip 175.155.35.79 --overwrite
EOF
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
esac

if [ "$(uname -s 2>/dev/null || true)" = "Linux" ] \
  && [ -d "$SCRIPT_DIR/packages" ] \
  && [ "$SCRIPT_DIR" != "$STANDARD_INSTALL_DIR" ]; then
  echo "提示：服务器安装目录建议统一为 $STANDARD_INSTALL_DIR" >&2
  echo "当前目录为 $SCRIPT_DIR，请按 README 的迁移步骤整理后再进行长期维护。" >&2
fi

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

prompt_value() {
  label=$1
  default_value=$2
  if [ -n "$default_value" ]; then
    printf '%s [%s]: ' "$label" "$default_value" >&2
  else
    printf '%s: ' "$label" >&2
  fi
  IFS= read -r answer || answer=
  printf '%s' "${answer:-$default_value}"
}

prompt_yes_no() {
  label=$1
  default_answer=$2
  if [ "$default_answer" = "yes" ]; then
    printf '%s [Y/n]: ' "$label" >&2
  else
    printf '%s [y/N]: ' "$label" >&2
  fi
  IFS= read -r answer || answer=
  case "$answer" in
    y|Y|yes|YES) return 0 ;;
    n|N|no|NO) return 1 ;;
    "") [ "$default_answer" = "yes" ] ;;
    *) echo "请输入 y 或 n" >&2; return 1 ;;
  esac
}

server_ip=
internal_ip=
external_ip=
overwrite=false
interactive=false
install_mode_override=
if [ "$#" -eq 0 ] && [ -t 0 ] && [ -t 1 ]; then
  interactive=true
  echo "=== Robot Media Server 安装向导 ==="
  echo "提示：方括号内为默认值，直接回车采用默认值；Y/n 默认是，y/N 默认否。"
  echo "示例仅用于说明格式，请填写目标服务器的真实地址。"
  echo

  selected_network_mode=$(prompt_value "部署网络模式（示例：bridge；可选 bridge/host）" "$(env_value DEPLOY_NETWORK_MODE bridge)")
  case "$selected_network_mode" in
    bridge|host) ;;
    *) echo "部署网络模式只能是 bridge 或 host" >&2; exit 2 ;;
  esac
  selected_workspace=$(prompt_value "宿主机运行目录（示例：/home/mounts/media）" "$(env_value APP_WORKSPACE_ROOT /home/mounts/media)")

  if prompt_yes_no "是否配置服务器访问 IP（首次安装示例：y）" yes; then
    current_internal_url=$(env_value LIVEKIT_INTERNAL_URL "")
    current_internal_ip=${current_internal_url#ws://}
    current_internal_ip=${current_internal_ip%%:*}
    case "$current_internal_ip" in host.docker.internal|127.0.0.1) current_internal_ip= ;; esac
    current_external_ip=$(env_value LIVEKIT_NODE_IP "")
    internal_ip=$(prompt_value "内部访问 IP（示例：10.222.123.5）" "$current_internal_ip")
    [ -n "$internal_ip" ] || { echo "内部访问 IP 不能为空" >&2; exit 2; }
    external_ip=$(prompt_value "外部下发 IP（示例：175.155.35.79；无公网时填内网 IP）" "${current_external_ip:-$internal_ip}")
  fi

  if prompt_yes_no "是否覆盖已有程序和渲染配置（首次安装示例：n；全量更新示例：y）" no; then
    overwrite=true
  else
    install_mode_override=skip_existing
  fi

  echo
  echo "安装参数："
  echo "  安装目录：$SCRIPT_DIR"
  echo "  网络模式：$selected_network_mode"
  echo "  运行目录：$selected_workspace"
  [ -z "$internal_ip" ] || echo "  内部 IP： $internal_ip"
  [ -z "$external_ip" ] || echo "  外部 IP： $external_ip"
  echo "  覆盖安装：$overwrite"
  prompt_yes_no "确认开始安装（核对以上参数无误后输入 y）" no || { echo "已取消安装"; exit 0; }

  set_env_value DEPLOY_NETWORK_MODE "$selected_network_mode"
  set_env_value APP_WORKSPACE_ROOT "$selected_workspace"
fi

while [ "$#" -gt 0 ]; do
  case "$1" in
    --server-ip)
      [ "$#" -ge 2 ] || { echo "--server-ip requires a value" >&2; exit 2; }
      server_ip=$2
      shift 2
      ;;
    --internal-ip)
      [ "$#" -ge 2 ] || { echo "--internal-ip requires a value" >&2; exit 2; }
      internal_ip=$2
      shift 2
      ;;
    --external-ip)
      [ "$#" -ge 2 ] || { echo "--external-ip requires a value" >&2; exit 2; }
      external_ip=$2
      shift 2
      ;;
    --overwrite)
      overwrite=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      break
      ;;
  esac
done

if [ -n "$server_ip" ] && { [ -n "$internal_ip" ] || [ -n "$external_ip" ]; }; then
  echo "--server-ip cannot be combined with --internal-ip or --external-ip" >&2
  exit 2
fi

if [ -n "$server_ip" ]; then
  ENV_TARGET="$SCRIPT_DIR/.env" sh "$INTERNAL_DIR/configure-env-ip.sh" "$server_ip"
elif [ -n "$internal_ip" ] || [ -n "$external_ip" ]; then
  ENV_TARGET="$SCRIPT_DIR/.env" sh "$INTERNAL_DIR/configure-env-ip.sh" \
    --internal-ip "${internal_ip:-$external_ip}" \
    --external-ip "${external_ip:-$internal_ip}"
fi

if [ "$overwrite" = "true" ]; then
  install_mode_override=overwrite
fi
if [ -n "$install_mode_override" ]; then
  export INSTALL_MODE="$install_mode_override"
fi

raw_workspace_root=$(env_value APP_WORKSPACE_ROOT /home/mounts/media)
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
    set_env_value_if_default LIVEKIT_INTERNAL_URL ws://host.docker.internal:7880 ws://127.0.0.1:7880
    set_env_value_if_default LIVEKIT_WEBHOOK_URL http://host.docker.internal:8088/internal/media/livekit/webhook http://127.0.0.1:8088/internal/media/livekit/webhook
    set_env_value_if_default LIVEKIT_EGRESS_WS_URL ws://host.docker.internal:7880 ws://127.0.0.1:7880
    set_env_value_if_default MEDIA_SERVICE_BASE_URL http://media-service:8088 http://127.0.0.1:8088
    set_env_value_if_default CENTER_MANAGE_BASE_URL http://host.docker.internal:8866 http://127.0.0.1:8866
    set_env_value_if_default CENTER_CONTROL_BASE_URL http://control-service:8082 http://127.0.0.1:8082
    set_env_value_if_default CENTER_MEDIA_BASE_URL http://media-service:8088 http://127.0.0.1:8088
    set_env_value_if_default CENTER_CONTROL_WS_URL ws://control-service:8082/ws/control ws://127.0.0.1:8082/ws/control
    set_env_value_if_default CENTER_FIELD_CALL_WS_URL ws://control-service:8082/ws/field-call ws://127.0.0.1:8082/ws/field-call
    set_env_value_if_default TTS_ENGINE_URL http://tts:8080/api/tts http://127.0.0.1:8080/api/tts
    ;;
  *)
    echo "unsupported DEPLOY_NETWORK_MODE: $DEPLOY_NETWORK_MODE, expected bridge or host" >&2
    exit 1
    ;;
esac
export DEPLOY_NETWORK_MODE

sh "$INTERNAL_DIR/preflight-network.sh"
sh "$INTERNAL_DIR/load-images.sh"
sh "$INTERNAL_DIR/prepare-workspace.sh"

cd "$SCRIPT_DIR"
$COMPOSE -f "$COMPOSE_FILE" up -d "$@"
