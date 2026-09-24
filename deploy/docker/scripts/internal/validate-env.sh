#!/usr/bin/env sh
# 内部脚本：校验已启用能力的条件配置，避免容器启动后才暴露缺项。
set -eu

if [ "$#" -ne 1 ]; then
  echo "用法: validate-env.sh <env_path>" >&2
  exit 2
fi

ENV_FILE=$1

if [ ! -f "$ENV_FILE" ]; then
  echo "配置预检失败：环境变量文件不存在: $ENV_FILE" >&2
  exit 1
fi

env_value() {
  key=$1
  sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1
}

require_value() {
  key=$1
  value=$(env_value "$key")
  if [ -z "$value" ]; then
    echo "  - $key 未配置" >&2
    VALIDATION_FAILED=true
  fi
}

VALIDATION_FAILED=false
CENTER_STOMP_ENABLED=$(env_value CENTER_STOMP_ENABLED)
CENTER_STOMP_ENABLED=${CENTER_STOMP_ENABLED:-true}

case "$CENTER_STOMP_ENABLED" in
  true)
    require_value CENTER_STOMP_WS_URL
    require_value CENTER_STOMP_TOPIC

    if [ -z "$(env_value CENTER_STOMP_ACCESS_TOKEN)" ]; then
      require_value CENTER_STOMP_TOKEN_URL
      require_value CENTER_STOMP_CLIENT_ID
      require_value CENTER_STOMP_CLIENT_SECRET
    fi
    ;;
  false)
    ;;
  *)
    echo "  - CENTER_STOMP_ENABLED 只能是 true 或 false，当前值: $CENTER_STOMP_ENABLED" >&2
    VALIDATION_FAILED=true
    ;;
esac

if [ "$VALIDATION_FAILED" = "true" ]; then
  echo "配置预检失败。" >&2
  if [ "$CENTER_STOMP_ENABLED" = "true" ]; then
    echo "CENTER_STOMP_ENABLED=true 时必须选择一种认证方式：" >&2
    echo "  1. 配置 CENTER_STOMP_ACCESS_TOKEN" >&2
    echo "  2. 完整配置 CENTER_STOMP_TOKEN_URL、CENTER_STOMP_CLIENT_ID、CENTER_STOMP_CLIENT_SECRET" >&2
    echo "当前环境不需要中心端实时事件时，可显式设置 CENTER_STOMP_ENABLED=false。" >&2
  fi
  exit 1
fi

echo "配置预检通过"
