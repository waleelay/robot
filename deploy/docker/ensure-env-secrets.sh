#!/usr/bin/env sh
# 为部署环境补齐必须持久化的随机密钥。已有非空值不会被覆盖。
set -eu

if [ "$#" -ne 1 ]; then
  echo "用法: ensure-env-secrets.sh <env_path>" >&2
  exit 2
fi

ENV_FILE=$1

if [ ! -f "$ENV_FILE" ]; then
  echo "环境变量文件不存在: $ENV_FILE" >&2
  exit 1
fi

env_value() {
  key=$1
  sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1
}

generate_token() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
    return
  fi
  if [ -r /dev/urandom ] && command -v od >/dev/null 2>&1; then
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
    return
  fi
  echo "无法生成随机密钥: 需要 openssl，或可读取 /dev/urandom 且已安装 od" >&2
  exit 1
}

set_env_value() {
  key=$1
  value=$2
  tmp_file="$ENV_FILE.tmp.$$"
  trap 'rm -f "$tmp_file"' EXIT HUP INT TERM
  umask 077
  awk -v key="$key" -v value="$value" '
    BEGIN { prefix = key "="; replaced = 0 }
    index($0, prefix) == 1 {
      if (!replaced) {
        print key "=" value
        replaced = 1
      }
      next
    }
    { print }
    END { if (!replaced) print key "=" value }
  ' "$ENV_FILE" > "$tmp_file"
  mv "$tmp_file" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  trap - EXIT HUP INT TERM
}

remove_obsolete_key() {
  key=$1
  if ! grep -q "^$key=" "$ENV_FILE"; then
    return
  fi
  tmp_file="$ENV_FILE.tmp.$$"
  trap 'rm -f "$tmp_file"' EXIT HUP INT TERM
  umask 077
  awk -v key="$key" '
    BEGIN { prefix = key "=" }
    index($0, prefix) != 1 { print }
  ' "$ENV_FILE" > "$tmp_file"
  mv "$tmp_file" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  trap - EXIT HUP INT TERM
  echo "已移除废弃部署变量: $key"
}

ensure_secret() {
  key=$1
  if [ -n "$(env_value "$key")" ]; then
    echo "已保留现有部署密钥: $key"
    return
  fi
  set_env_value "$key" "$(generate_token)"
  echo "已自动生成部署密钥: $key"
}

remove_obsolete_key MEDIA_FILE_PROGRESS_MANAGEMENT_TOKEN
ensure_secret MEDIA_FILE_PROGRESS_WEBHOOK_TOKEN
