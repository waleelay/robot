#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
ENV_FILE="$SCRIPT_DIR/.env"

case "${1:-}" in
  -h|--help)
    echo "usage: ./uninstall.sh [--volumes] [--purge-workspace]"
    echo "不带参数并在终端执行时进入交互式卸载向导。"
    exit 0
    ;;
esac

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

prompt_yes_no() {
  label=$1
  default_answer=$2
  if [ "$default_answer" = "yes" ]; then
    printf '%s [Y/n]: ' "$label"
  else
    printf '%s [y/N]: ' "$label"
  fi
  IFS= read -r answer || answer=
  case "$answer" in
    y|Y|yes|YES) return 0 ;;
    n|N|no|NO) return 1 ;;
    "") [ "$default_answer" = "yes" ] ;;
    *) echo "请输入 y 或 n" >&2; return 1 ;;
  esac
}

DEPLOY_NETWORK_MODE=$(env_value DEPLOY_NETWORK_MODE bridge)
case "$DEPLOY_NETWORK_MODE" in
  bridge) COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml" ;;
  host) COMPOSE_FILE="$SCRIPT_DIR/docker-compose.host.yml" ;;
  *)
    echo "unsupported DEPLOY_NETWORK_MODE: $DEPLOY_NETWORK_MODE, expected bridge or host" >&2
    exit 1
    ;;
esac

purge_workspace() {
  workspace_root=$(expand_user_path "$(env_value APP_WORKSPACE_ROOT /home/mounts/media)")

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

if [ "$#" -eq 0 ] && [ -t 0 ] && [ -t 1 ]; then
  workspace_root=$(expand_user_path "$(env_value APP_WORKSPACE_ROOT /home/mounts/media)")
  echo "=== Robot Media Server 卸载向导 ==="
  echo "提示：方括号内为默认值，直接回车采用默认值；Y/n 默认是，y/N 默认否。"
  echo "常规卸载建议两个删除选项都输入 n，以保留数据和运行目录。"
  echo
  echo "  网络模式：$DEPLOY_NETWORK_MODE"
  echo "  运行目录：$workspace_root"
  if prompt_yes_no "是否同时删除 Compose 数据卷（常规卸载示例：n）" no; then
    remove_volumes=true
  fi
  if prompt_yes_no "是否同时删除运行目录（不可恢复；常规卸载示例：n）" no; then
    remove_workspace=true
  fi
  prompt_yes_no "确认停止并删除项目容器（确认卸载示例：y）" no || { echo "已取消卸载"; exit 0; }
fi

while [ "$#" -gt 0 ]; do
  case "$1" in
    --volumes)
      remove_volumes=true
      ;;
    --purge-workspace|--remove-workspace)
      remove_workspace=true
      ;;
    -h|--help)
      echo "usage: ./uninstall.sh [--volumes] [--purge-workspace]"
      exit 0
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
