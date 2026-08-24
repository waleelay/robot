#!/usr/bin/env sh

# 生产节点时钟同步门禁：在每个应用、认证、MQTT、LiveKit 节点本机执行。
# 仅检查，不修改系统时间。退出码非 0 表示该节点不得通过 PROD-010 验收。

set -eu

MAX_OFFSET_SECONDS="${MAX_OFFSET_SECONDS:-1}"

command -v chronyc >/dev/null 2>&1 || {
  echo "时钟同步检查失败：未安装 chronyc" >&2
  exit 1
}

tracking="$(chronyc tracking 2>&1)" || {
  echo "时钟同步检查失败：无法读取 chrony 状态" >&2
  echo "$tracking" >&2
  exit 1
}

reference_id="$(printf '%s\n' "$tracking" | awk -F: '/^Reference ID/{gsub(/[[:space:]]/, "", $2); print toupper($2)}')"
stratum="$(printf '%s\n' "$tracking" | awk -F: '/^Stratum/{gsub(/[[:space:]]/, "", $2); print $2}')"
leap_status="$(printf '%s\n' "$tracking" | awk -F: '/^Leap status/{sub(/^[[:space:]]+/, "", $2); print $2}')"
offset="$(printf '%s\n' "$tracking" | awk '/^System time/{print $4}')"

case "$reference_id" in
  ""|00000000|7F7F0101)
    echo "时钟同步检查失败：chrony 使用空源或 127.127.1.1 本地伪时钟源" >&2
    exit 1
    ;;
esac

case "$stratum" in
  ''|*[!0-9]*)
    echo "时钟同步检查失败：无法解析 Stratum" >&2
    exit 1
    ;;
esac

if [ "$stratum" -ge 16 ] || [ "$leap_status" != "Normal" ]; then
  echo "时钟同步检查失败：Stratum=$stratum，Leap=$leap_status" >&2
  exit 1
fi

awk -v offset="$offset" -v limit="$MAX_OFFSET_SECONDS" 'BEGIN {
  if (offset < 0) offset = -offset
  exit(offset <= limit ? 0 : 1)
}' || {
  echo "时钟同步检查失败：系统偏差 ${offset}s，允许上限 ${MAX_OFFSET_SECONDS}s" >&2
  exit 1
}

echo "时钟同步检查通过：ReferenceID=$reference_id Stratum=$stratum Offset=${offset}s Leap=$leap_status"
