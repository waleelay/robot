#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "用法：$0 <IAM runtime/infra 目录> <大屏 HTTPS Origin> [--apply]" >&2
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 2
fi

infra_dir="$1"
origin="$2"
mode="${3:-}"
if [[ -n "$mode" && "$mode" != "--apply" ]]; then
  usage
  exit 2
fi
if [[ ! "$origin" =~ ^https://[^/?#@*[:space:],]+$ ]]; then
  echo "大屏 Origin 必须是完整的 HTTPS 来源，不能包含路径、查询、通配符或凭据。" >&2
  exit 2
fi

env_file="$infra_dir/.env.runtime"
compose_file="$infra_dir/docker-compose.prod.yml"
if [[ ! -f "$env_file" || ! -f "$compose_file" ]]; then
  echo "IAM 运行环境文件或 Compose 文件不存在：$infra_dir" >&2
  exit 1
fi

env_value() {
  local key="$1"
  awk -v key="$key" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "$env_file"
}

contains_origin() {
  local values="$1"
  local item
  IFS=',' read -r -a entries <<< "$values"
  for item in "${entries[@]}"; do
    [[ "$item" == "$origin" ]] && return 0
  done
  return 1
}

configured_origins="$(env_value GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS)"
admin_origins="$(env_value ADMIN_WEB_PUBLIC_ORIGINS)"
if [[ "$(grep -c '^GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS=' "$env_file")" -ne 1 ]]; then
  echo "IAM 环境文件的 Gateway 来源配置必须恰好出现一次。" >&2
  exit 1
fi
if [[ -z "$configured_origins" ]]; then
  echo "IAM 未配置 GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS。" >&2
  exit 1
fi

needs_update=true
if contains_origin "$configured_origins" || {
  [[ "$configured_origins" == *'${ADMIN_WEB_PUBLIC_ORIGINS}'* ]] && contains_origin "$admin_origins"
}; then
  needs_update=false
fi

project="${IAM_COMPOSE_PROJECT:-iam-auth}"
compose=(docker compose --env-file "$env_file" -p "$project" -f "$compose_file")
container_has_origin() {
  local container
  container="$("${compose[@]}" ps -q api-gateway)"
  [[ -n "$container" ]] || return 1
  docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$container" \
    | awk -v origin="$origin" '
      index($0, "GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS=") == 1 {
        value = substr($0, length("GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS=") + 1)
        count = split(value, entries, ",")
        for (i = 1; i <= count; i++) if (entries[i] == origin) found = 1
      }
      END { exit !found }
    '
}

if [[ "$needs_update" == false ]]; then
  "${compose[@]}" config --quiet
  if container_has_origin; then
    echo "IAM 运行配置和 Gateway 容器均已包含大屏来源。"
    exit 0
  fi
  if [[ "$mode" != "--apply" ]]; then
    echo "IAM 环境文件已有大屏来源，但 Gateway 容器尚未加载；传入 --apply 可仅重建 api-gateway。" >&2
    exit 2
  fi
  "${compose[@]}" up -d --no-build --pull never --no-deps --force-recreate api-gateway
  container_has_origin || { echo "Gateway 重建后仍未加载大屏来源。" >&2; exit 1; }
  echo "IAM API Gateway 已加载大屏来源。"
  exit 0
fi
if [[ "$mode" != "--apply" ]]; then
  echo "IAM 运行配置缺少大屏来源；传入 --apply 可备份配置并仅重建 api-gateway。" >&2
  exit 2
fi

backup="${env_file}.pre-bigscreen-origin-$(date +%Y%m%dT%H%M%S)"
tmp="$(mktemp "${env_file}.tmp.XXXXXX")"
cp -p "$env_file" "$backup"
trap 'rm -f "$tmp"' EXIT
awk -v origin="$origin" '
  index($0, "GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS=") == 1 {
    print $0 "," origin
    next
  }
  { print }
' "$env_file" > "$tmp"
chmod --reference="$env_file" "$tmp" 2>/dev/null || chmod 600 "$tmp"
cat "$tmp" > "$env_file"

restore() {
  cp -p "$backup" "$env_file"
  "${compose[@]}" up -d --no-build --pull never --no-deps --force-recreate api-gateway || true
}

if ! "${compose[@]}" config --quiet; then
  cp -p "$backup" "$env_file"
  echo "Compose 配置校验失败，已恢复 IAM 环境文件。" >&2
  exit 1
fi
if ! "${compose[@]}" up -d --no-build --pull never --no-deps --force-recreate api-gateway; then
  restore
  echo "IAM API Gateway 重建失败，已尝试恢复原环境。" >&2
  exit 1
fi

if ! container_has_origin; then
  restore
  echo "Gateway 容器未加载大屏来源，已尝试恢复原环境。" >&2
  exit 1
fi

echo "IAM API Gateway 已加载大屏来源；IAM 环境文件备份：$backup"
