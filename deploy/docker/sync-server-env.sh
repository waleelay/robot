#!/usr/bin/env sh
# 将标准输入中的 "SERVICE VAR VALUE" 幂等同步到服务器 .env 和 Compose。
set -eu

if [ "$#" -ne 2 ]; then
  echo "用法: sync-server-env.sh <env_path> <compose_path>" >&2
  exit 2
fi

env_file=$1
compose_file=$2
entries_file=$(mktemp "${TMPDIR:-/tmp}/robot-env-entries.XXXXXX")
trap 'rm -f "$entries_file"' EXIT
cat > "$entries_file"

if [ ! -s "$entries_file" ]; then
  echo "没有需要同步的环境变量" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d%H%M%S)-$$"
cp "$env_file" "$env_file.bak-$timestamp"
cp "$compose_file" "$compose_file.bak-$timestamp"
echo "已备份: .bak-$timestamp"

while IFS=' ' read -r service variable value; do
  [ -n "$service" ] && [ -n "$variable" ] || continue
  if ! printf '%s\n' "$variable" | grep -Eq '^[A-Za-z_][A-Za-z0-9_]*$'; then
    echo "非法环境变量名: $variable" >&2
    exit 1
  fi

  env_tmp="$env_file.tmp.$$"
  awk -v key="$variable" -v replacement="$variable=$value" '
    BEGIN { prefix = key "="; replaced = 0 }
    index($0, prefix) == 1 {
      if (!replaced) {
        print replacement
        replaced = 1
      }
      next
    }
    { print }
    END { if (!replaced) print replacement }
  ' "$env_file" > "$env_tmp"
  mv "$env_tmp" "$env_file"

  if awk -v header="  $service:" -v key="      $variable:" '
    $0 == header { inside = 1; next }
    inside && /^  [^ ]/ { inside = 0 }
    inside && index($0, key) == 1 { found = 1 }
    END { exit found ? 0 : 1 }
  ' "$compose_file"; then
    echo "已接线，跳过: $service $variable"
    continue
  fi

  if ! awk -v header="  $service:" '
    $0 == header { inside = 1; found_service = 1; next }
    inside && /^  [^ ]/ { inside = 0 }
    inside && /^    environment:[[:space:]]*$/ { found_environment = 1 }
    END { exit found_service && found_environment ? 0 : 1 }
  ' "$compose_file"; then
    echo "服务不存在或没有 environment 段: $service" >&2
    exit 1
  fi

  compose_line=$(printf '      %s: ${%s:-%s}' "$variable" "$variable" "$value")
  compose_tmp="$compose_file.tmp.$$"
  awk -v header="  $service:" -v addition="$compose_line" '
    $0 == header { inside = 1 }
    {
      print
      if (inside && !inserted && /^    environment:[[:space:]]*$/) {
        print addition
        inserted = 1
      }
    }
    inside && /^  [^ ]/ && $0 != header { inside = 0 }
  ' "$compose_file" > "$compose_tmp"
  mv "$compose_tmp" "$compose_file"
  echo "已接线: $service $variable"
done < "$entries_file"

echo "环境变量同步完成"
