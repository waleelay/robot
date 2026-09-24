#!/usr/bin/env bash
# 增量更新前后端服务与部署配置到远程服务器。
#
# 职责（一条命令完成）：
#   1. 重新编译所选 Java 服务和 Robot UI
#   2. 上传 dist 包和前端资源
#   3. 同步仓库 Compose 模板与新增环境变量
#   4. 备份并替换服务器工作区程序，重建对应容器
#   5. 核验容器状态、环境变量和启动日志
#
# 用法：
#   # 先按本次发布编辑 deploy/docker/update-services.env（SERVICE VAR=VALUE 格式）
#   ./deploy/docker/update-services.sh
#
# 可覆盖配置（环境变量）：
#   UPDATE_SERVER        服务器地址，默认 192.168.124.23
#   UPDATE_SSH_USER      SSH 用户，默认 root
#   UPDATE_SSH_PORT      SSH 端口，默认 22
#   UPDATE_INSTALL_DIR   Compose 安装目录，默认 /home/robot-mediaserver-installer
#   UPDATE_WORKSPACE     服务运行目录，默认 /home/mounts/media
#   UPDATE_ENV_FILE      环境变量增量文件，默认 deploy/docker/update-services.env
#   UPDATE_SERVICES      本次更新的服务列表，默认 "media-service control-service bigscreen-bff"
#   UPDATE_BUILD         是否重新编译所选 Java 服务，默认 true
#   UPDATE_FRONTEND      是否重新编译并更新 robot-ui，默认 true
#   DIST_MEDIA/DIST_CONTROL/DIST_BIGSCREEN  三个 dist 包路径（默认指向各模块 target）
#
# 环境变量增量文件格式（update-services.env）：
#   # 注释行以 # 开头
#   control-service MANAGEMENT_DEVICE_CACHE_TTL_SECONDS=30
#   bigscreen-bff    STATISTICS_REPORT_CLEANUP_INTERVAL_MS=3600000

set -euo pipefail

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
用法：./deploy/docker/update-services.sh

默认行为：重新编译并更新 media-service、control-service、bigscreen-bff 和 Robot UI，
同时把 update-services.env 中的新增配置同步到服务器。

常用可选变量：
  UPDATE_SERVER=<服务器IP>
  UPDATE_SSH_USER=<SSH用户>
  UPDATE_SSH_PORT=<SSH端口>
  UPDATE_INSTALL_DIR=<服务器安装包目录>
  UPDATE_WORKSPACE=<服务器运行目录>
  UPDATE_SERVICES="media-service control-service bigscreen-bff"
  UPDATE_BUILD=true|false
  UPDATE_FRONTEND=true|false
  UPDATE_NON_INTERACTIVE=true|false
EOF
  exit 0
fi

# ---------- 配置 ----------
UPDATE_SERVER="${UPDATE_SERVER:-192.168.124.23}"
UPDATE_SSH_USER="${UPDATE_SSH_USER:-root}"
UPDATE_SSH_PORT="${UPDATE_SSH_PORT:-22}"
UPDATE_INSTALL_DIR="${UPDATE_INSTALL_DIR:-/home/robot-mediaserver-installer}"
UPDATE_WORKSPACE="${UPDATE_WORKSPACE:-/home/mounts/media}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
INTERNAL_SCRIPT_DIR="$SCRIPT_DIR/scripts/internal"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
UPDATE_ENV_FILE="${UPDATE_ENV_FILE:-$SCRIPT_DIR/update-services.env}"
UPDATE_SERVICES="${UPDATE_SERVICES:-media-service control-service bigscreen-bff}"
UPDATE_BUILD="${UPDATE_BUILD:-true}"
UPDATE_FRONTEND="${UPDATE_FRONTEND:-true}"
UPDATE_NON_INTERACTIVE="${UPDATE_NON_INTERACTIVE:-false}"
ROBOT_UI_DIR="${ROBOT_UI_DIR:-$REPO_ROOT/robot-ui}"

DIST_MEDIA="${DIST_MEDIA:-$REPO_ROOT/backend/target/robot-mediaserver-dist.tar.gz}"
DIST_CONTROL="${DIST_CONTROL:-$REPO_ROOT/control-service/target/robot-control-service-dist.tar.gz}"
DIST_BIGSCREEN="${DIST_BIGSCREEN:-$REPO_ROOT/bigscreen-bff/target/bigscreen-bff-dist.tar.gz}"

prompt_value() {
  local label=$1
  local default_value=$2
  local answer
  if [ -n "$default_value" ]; then
    printf '%s [%s]: ' "$label" "$default_value" >&2
  else
    printf '%s: ' "$label" >&2
  fi
  IFS= read -r answer || answer=
  printf '%s' "${answer:-$default_value}"
}

prompt_yes_no() {
  local label=$1
  local default_answer=$2
  local answer
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

if [ "$UPDATE_NON_INTERACTIVE" != "true" ] && [ "$#" -eq 0 ] && [ -t 0 ] && [ -t 1 ]; then
  echo "=== Robot Media Server 增量更新向导 ==="
  echo "提示：方括号内为默认值，直接回车采用默认值；Y/n 默认是，y/N 默认否。"
  echo "示例仅用于说明格式，请填写目标服务器的真实 SSH 信息。"
  echo
  UPDATE_SERVER=$(prompt_value "服务器地址（示例：192.168.124.23 或 175.155.35.79）" "$UPDATE_SERVER")
  UPDATE_SSH_USER=$(prompt_value "SSH 用户（示例：root 或 jszn）" "$UPDATE_SSH_USER")
  UPDATE_SSH_PORT=$(prompt_value "SSH 端口（示例：22 或 32272）" "$UPDATE_SSH_PORT")
  UPDATE_INSTALL_DIR=$(prompt_value "服务器安装包目录（示例：/home/robot-mediaserver-installer）" "$UPDATE_INSTALL_DIR")
  UPDATE_WORKSPACE=$(prompt_value "服务器运行目录（示例：/home/mounts/media）" "$UPDATE_WORKSPACE")

  echo "选择需要更新的 Java 服务："
  echo "  1) 全部三个服务"
  echo "  2) media-service"
  echo "  3) control-service"
  echo "  4) bigscreen-bff"
  echo "  5) 自定义服务列表"
  service_choice=$(prompt_value "请选择编号（示例：全部更新选 1；自定义组合选 5）" "1")
  case "$service_choice" in
    1) UPDATE_SERVICES="media-service control-service bigscreen-bff" ;;
    2) UPDATE_SERVICES="media-service" ;;
    3) UPDATE_SERVICES="control-service" ;;
    4) UPDATE_SERVICES="bigscreen-bff" ;;
    5) UPDATE_SERVICES=$(prompt_value "服务列表（空格分隔，示例：media-service control-service）" "$UPDATE_SERVICES") ;;
    *) echo "无效选择：$service_choice" >&2; exit 2 ;;
  esac

  if prompt_yes_no "是否重新编译 Java 服务（代码有修改时示例：y）" yes; then UPDATE_BUILD=true; else UPDATE_BUILD=false; fi
  if prompt_yes_no "是否重新编译并更新 Robot UI（前端有修改时示例：y；仅后端更新示例：n）" yes; then UPDATE_FRONTEND=true; else UPDATE_FRONTEND=false; fi

  echo
  echo "更新参数："
  echo "  服务器：$UPDATE_SSH_USER@$UPDATE_SERVER:$UPDATE_SSH_PORT"
  echo "  安装目录：$UPDATE_INSTALL_DIR"
  echo "  运行目录：$UPDATE_WORKSPACE"
  echo "  Java 服务：$UPDATE_SERVICES"
  echo "  编译 Java：$UPDATE_BUILD"
  echo "  更新前端：$UPDATE_FRONTEND"
  echo "  配置增量：$UPDATE_ENV_FILE"
  prompt_yes_no "确认开始更新（核对以上参数无误后输入 y）" no || { echo "已取消更新"; exit 0; }
fi

UPDATE_SSH_BATCH_MODE="${UPDATE_SSH_BATCH_MODE:-no}"
SSH_CONTROL_PATH="${TMPDIR:-/tmp}/robot-update-ssh-$$"
SSH_COMMON=(-4 -o ConnectTimeout=10 -o BatchMode="$UPDATE_SSH_BATCH_MODE" -o ControlMaster=auto -o ControlPersist=120 -o ControlPath="$SSH_CONTROL_PATH")
SSH_BASE=(ssh "${SSH_COMMON[@]}" -p "$UPDATE_SSH_PORT")
SCP_BASE=(scp "${SSH_COMMON[@]}" -P "$UPDATE_SSH_PORT")
HOST="$UPDATE_SSH_USER@$UPDATE_SERVER"

# ---------- 小工具 ----------
log() { printf '[update-services] %s\n' "$*"; }

# 服务器网络偶发抖动，ssh 命令最多重试 3 次
ssh_run() {
  local n=0
  until "${SSH_BASE[@]}" "$HOST" "$@"; do
    n=$((n + 1))
    [ "$n" -ge 3 ] && { log "ssh 重试 3 次仍失败，退出"; return 1; }
    log "ssh 失败，2 秒后重试（$n/3）"
    sleep 2
  done
}

dist_for_service() {
  case "$1" in
    media-service)   echo "$DIST_MEDIA" ;;
    control-service) echo "$DIST_CONTROL" ;;
    bigscreen-bff)   echo "$DIST_BIGSCREEN" ;;
    *) echo "" ;;
  esac
}

module_for_service() {
  case "$1" in
    media-service) echo backend ;;
    control-service) echo control-service ;;
    bigscreen-bff) echo bigscreen-bff ;;
    *) echo "" ;;
  esac
}

# ---------- 0. 前置检查 ----------
[ -f "$UPDATE_ENV_FILE" ] || { log "找不到环境变量增量文件：$UPDATE_ENV_FILE"; exit 1; }
case "$UPDATE_BUILD" in true|false) ;; *) log "UPDATE_BUILD 只能是 true 或 false"; exit 1 ;; esac
case "$UPDATE_FRONTEND" in true|false) ;; *) log "UPDATE_FRONTEND 只能是 true 或 false"; exit 1 ;; esac

if [ "$UPDATE_BUILD" = "true" ]; then
  log "== 1/8 重新编译 Java 服务 =="
  for svc in $UPDATE_SERVICES; do
    module="$(module_for_service "$svc")"
    [ -n "$module" ] || { log "不支持的服务：$svc"; exit 1; }
    log "编译 $svc"
    (cd "$REPO_ROOT/$module" && mvn -q -DskipTests clean package)
  done
fi

frontend_archive=
cleanup() {
  [ -z "$frontend_archive" ] || rm -f "$frontend_archive"
  if [ -S "$SSH_CONTROL_PATH" ]; then
    "${SSH_BASE[@]}" -O exit "$HOST" >/dev/null 2>&1 || true
  fi
  rm -f "$SSH_CONTROL_PATH"
}
trap cleanup EXIT
if [ "$UPDATE_FRONTEND" = "true" ]; then
  log "== 2/8 重新编译 Robot UI =="
  (cd "$ROBOT_UI_DIR" && npm run build:prod)
  [ -f "$ROBOT_UI_DIR/dist/index.html" ] || { log "Robot UI 构建产物不存在：$ROBOT_UI_DIR/dist/index.html"; exit 1; }
  frontend_archive="$(mktemp "${TMPDIR:-/tmp}/robot-ui-dist.XXXXXX")"
  (cd "$ROBOT_UI_DIR/dist" && COPYFILE_DISABLE=1 tar -czf "$frontend_archive" .)
fi

for svc in $UPDATE_SERVICES; do
  dist="$(dist_for_service "$svc")"
  [ -n "$dist" ] && [ -f "$dist" ] || { log "服务 $svc 的 dist 包不存在：$dist"; exit 1; }
done
log "服务器: $HOST:$UPDATE_SSH_PORT"
log "安装目录: $UPDATE_INSTALL_DIR  工作区: $UPDATE_WORKSPACE"
log "更新服务: $UPDATE_SERVICES"

# ---------- 1. 上传 dist 包 ----------
log "== 3/8 上传后端与前端产物 =="
for svc in $UPDATE_SERVICES; do
  dist="$(dist_for_service "$svc")"
  log "上传 $svc <- $dist"
  "${SCP_BASE[@]}" "$dist" "$HOST:/tmp/$(basename "$dist")"
done
if [ "$UPDATE_FRONTEND" = "true" ]; then
  log "上传 Robot UI"
  "${SCP_BASE[@]}" "$frontend_archive" "$HOST:/tmp/robot-ui-dist.tar.gz"
  "${SCP_BASE[@]}" "$INTERNAL_SCRIPT_DIR/install-robot-ui-dist.sh" "$HOST:/tmp/install-robot-ui-dist.sh"
fi

# ---------- 2+3. Compose 模板与环境变量同步 ----------
log "== 4/8 校验并同步仓库 Compose 模板与服务器 .env =="
"${SCP_BASE[@]}" "$SCRIPT_DIR/docker-compose.yml" "$HOST:/tmp/update-services-compose.yml"
"${SCP_BASE[@]}" "$SCRIPT_DIR/docker-compose.host.yml" "$HOST:/tmp/update-services-compose.host.yml"
# 上传服务器端幂等同步助手，并把增量文件解析为 "SERVICE VAR VALUE" 行通过 stdin 传入
"${SCP_BASE[@]}" "$INTERNAL_SCRIPT_DIR/sync-server-env.sh" "$HOST:/tmp/sync-server-env.sh"
"${SCP_BASE[@]}" "$INTERNAL_SCRIPT_DIR/ensure-env-secrets.sh" "$HOST:/tmp/ensure-env-secrets.sh"
awk -F'[ =]' 'NF>=3 && $0 !~ /^#/ && $0 !~ /^$/ {print $1, $2, substr($0, index($0, $2) + length($2) + 1)}' \
  "$UPDATE_ENV_FILE" | "${SSH_BASE[@]}" "$HOST" "set -e; cp '$UPDATE_INSTALL_DIR/.env' /tmp/update-services.env; sh /tmp/sync-server-env.sh /tmp/update-services.env /tmp/update-services-compose.yml /tmp/update-services-compose.host.yml; sh /tmp/ensure-env-secrets.sh /tmp/update-services.env; docker compose -f /tmp/update-services-compose.yml --env-file /tmp/update-services.env config --quiet; docker compose -f /tmp/update-services-compose.host.yml --env-file /tmp/update-services.env config --quiet; TS=\$(date +%Y%m%d%H%M%S)-\$\$; cp '$UPDATE_INSTALL_DIR/.env' '$UPDATE_INSTALL_DIR/.env.bak-\$TS'; cp '$UPDATE_INSTALL_DIR/docker-compose.yml' '$UPDATE_INSTALL_DIR/docker-compose.yml.bak-template-\$TS'; cp '$UPDATE_INSTALL_DIR/docker-compose.host.yml' '$UPDATE_INSTALL_DIR/docker-compose.host.yml.bak-template-\$TS'; cp /tmp/update-services.env '$UPDATE_INSTALL_DIR/.env'; cp /tmp/update-services-compose.yml '$UPDATE_INSTALL_DIR/docker-compose.yml'; cp /tmp/update-services-compose.host.yml '$UPDATE_INSTALL_DIR/docker-compose.host.yml'"

log "== 5/8 校验安装包 Compose 与仓库模板 =="
ssh_run "cd '$UPDATE_INSTALL_DIR'; MODE=\$(sed -n 's/^DEPLOY_NETWORK_MODE=//p' .env | tail -n 1); if [ \"\$MODE\" = host ]; then EXPECTED=/tmp/update-services-compose.host.yml; ACTUAL=docker-compose.host.yml; else EXPECTED=/tmp/update-services-compose.yml; ACTUAL=docker-compose.yml; fi; docker compose -f \"\$EXPECTED\" --env-file .env config >/tmp/update-services-expected.yml; docker compose -f \"\$ACTUAL\" --env-file .env config >/tmp/update-services-actual.yml; if ! cmp -s /tmp/update-services-expected.yml /tmp/update-services-actual.yml; then echo '安装包 Compose 与仓库模板存在漂移' >&2; exit 1; fi; echo \"Compose 渲染结果校验通过: \$ACTUAL\""

# ---------- 4. 备份并替换 bin/boot/lib ----------
log "== 6/8 备份并替换服务运行目录 =="
ssh_run "TS=\$(date +%Y%m%d%H%M%S); echo \"TS=\$TS\"; rm -rf /tmp/update-services-extract; mkdir -p /tmp/update-services-extract;
for svc in $UPDATE_SERVICES; do
  case \$svc in
    media-service) tarball=/tmp/robot-mediaserver-dist.tar.gz ;;
    control-service) tarball=/tmp/robot-control-service-dist.tar.gz ;;
    bigscreen-bff) tarball=/tmp/bigscreen-bff-dist.tar.gz ;;
  esac
  rm -rf /tmp/update-services-extract/\$svc; mkdir -p /tmp/update-services-extract/\$svc
  tar -xzf \$tarball -C /tmp/update-services-extract/\$svc --strip-components=1
  ls -l /tmp/update-services-extract/\$svc/boot
  mv '$UPDATE_WORKSPACE'/\$svc/bin  '$UPDATE_WORKSPACE'/\$svc/bin.bak-\$TS
  mv '$UPDATE_WORKSPACE'/\$svc/boot '$UPDATE_WORKSPACE'/\$svc/boot.bak-\$TS
  mv '$UPDATE_WORKSPACE'/\$svc/lib  '$UPDATE_WORKSPACE'/\$svc/lib.bak-\$TS
  cp -a /tmp/update-services-extract/\$svc/bin  '$UPDATE_WORKSPACE'/\$svc/bin
  cp -a /tmp/update-services-extract/\$svc/boot '$UPDATE_WORKSPACE'/\$svc/boot
  cp -a /tmp/update-services-extract/\$svc/lib  '$UPDATE_WORKSPACE'/\$svc/lib
  echo \"replaced \$svc (backup .bak-\$TS)\"
done"

if [ "$UPDATE_FRONTEND" = "true" ]; then
  ssh_run "set -e; rm -rf /tmp/update-robot-ui; mkdir -p /tmp/update-robot-ui; tar -xzf /tmp/robot-ui-dist.tar.gz -C /tmp/update-robot-ui; RETENTION=\$(sed -n 's/^ROBOT_UI_ASSET_RETENTION_DAYS=//p' '$UPDATE_INSTALL_DIR/.env' | tail -n 1); sh /tmp/install-robot-ui-dist.sh /tmp/update-robot-ui '$UPDATE_WORKSPACE/nginx/html/dist' overwrite \${RETENTION:-7}"
fi

# ---------- 5. 重建容器 ----------
log "== 7/8 重建容器 =="
ssh_run "cd '$UPDATE_INSTALL_DIR'; MODE=\$(sed -n 's/^DEPLOY_NETWORK_MODE=//p' .env | tail -n 1); if [ \"\$MODE\" = host ]; then COMPOSE_FILE=docker-compose.host.yml; else COMPOSE_FILE=docker-compose.yml; fi; docker compose -f \"\$COMPOSE_FILE\" up -d --force-recreate --no-deps $UPDATE_SERVICES 2>&1 | tail -8"

# ---------- 6. 核验 ----------
log "== 8/8 等待启动并核验 =="
sleep 30
ssh_run 'docker ps --format "{{.Names}}\t{{.Status}}" | grep robot-mediaserver;
echo "--- 容器内新环境变量 ---";
docker inspect robot-mediaserver-control-service --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -E "CONTROL_AUTH_ALLOW_DEFAULT_USER|MANAGEMENT_DEVICE_CACHE_TTL_SECONDS|CONTROL_DEVICE_CACHE_EVICT_DELAY_MS|ROBOT_HEARTBEAT_TIMEOUT_SECONDS|ROBOT_HEARTBEAT_SWEEP_DELAY_MS" || true;
docker inspect robot-mediaserver-bigscreen-bff --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -E "STATISTICS_REPORT_CLEANUP_INTERVAL_MS|BIGSCREEN_WS_AUTHORIZATION_MAX_STALENESS_MS|FIXED_CAMERA_CATALOG_LEASE" || true'

sleep 15
log "启动日志核验"
ssh_run 'for c in robot-mediaserver-media-service robot-mediaserver-control-service robot-mediaserver-bigscreen-bff; do
  echo "===== $c =====";
  docker logs --since 5m $c 2>&1 | grep -E "Started .*Application in|Tomcat started on port|APPLICATION FAILED|ERROR|BeanCreationException" | tail -5 || true;
done'

log "全部完成。如启动日志中有 ERROR 或启动顺序导致的瞬时 I/O 错误，请人工确认。"
