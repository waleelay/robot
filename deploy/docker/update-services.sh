#!/usr/bin/env bash
# 增量更新 Java 服务到远程服务器（media-service / control-service / bigscreen-bff）
#
# 职责（一条命令完成）：
#   1. 上传本地 target 打包好的 dist tar.gz 到服务器
#   2. 用仓库 Compose 模板覆盖并备份服务器安装包 Compose，避免运行定义漂移
#   3. 同步 update-services.env 中声明的环境变量到服务器 .env（新增/改值，幂等）
#   4. 校验仓库模板与安装包渲染后的关键服务环境变量和挂载一致
#   5. 备份并替换服务器工作区各服务的 bin/boot/lib
#   6. docker compose up -d --force-recreate 重建对应容器（必须 force-recreate 才会重新加载卷内新 jar）
#   7. 核验：容器状态、容器内环境变量、三个服务启动日志（Started / ERROR）
#
# 用法：
#   # 先按本次发布编辑 deploy/docker/update-services.env（SERVICE VAR=VALUE 格式）
#   sh deploy/docker/update-services.sh
#
# 可覆盖配置（环境变量）：
#   UPDATE_SERVER        服务器地址，默认 192.168.124.234
#   UPDATE_SSH_USER      SSH 用户，默认 root
#   UPDATE_SSH_PORT      SSH 端口，默认 22
#   UPDATE_INSTALL_DIR   compose 安装目录，默认 /data/robot-mediaserver-installer-amd64-20260719220656
#   UPDATE_WORKSPACE     服务运行目录，默认 /root/mounts/media
#   UPDATE_ENV_FILE      环境变量增量文件，默认 deploy/docker/update-services.env
#   UPDATE_SERVICES      本次更新的服务列表，默认 "media-service control-service bigscreen-bff"
#   DIST_MEDIA/DIST_CONTROL/DIST_BIGSCREEN  三个 dist 包路径（默认指向各模块 target）
#
# 环境变量增量文件格式（update-services.env）：
#   # 注释行以 # 开头
#   control-service MANAGEMENT_DEVICE_CACHE_TTL_SECONDS=30
#   bigscreen-bff    STATISTICS_REPORT_CLEANUP_INTERVAL_MS=3600000

set -euo pipefail

# ---------- 配置 ----------
UPDATE_SERVER="${UPDATE_SERVER:-192.168.124.234}"
UPDATE_SSH_USER="${UPDATE_SSH_USER:-root}"
UPDATE_SSH_PORT="${UPDATE_SSH_PORT:-22}"
UPDATE_INSTALL_DIR="${UPDATE_INSTALL_DIR:-/data/robot-mediaserver-installer-amd64-20260719220656}"
UPDATE_WORKSPACE="${UPDATE_WORKSPACE:-/root/mounts/media}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
UPDATE_ENV_FILE="${UPDATE_ENV_FILE:-$SCRIPT_DIR/update-services.env}"
UPDATE_SERVICES="${UPDATE_SERVICES:-media-service control-service bigscreen-bff}"

DIST_MEDIA="${DIST_MEDIA:-$REPO_ROOT/backend/target/robot-mediaserver-dist.tar.gz}"
DIST_CONTROL="${DIST_CONTROL:-$REPO_ROOT/control-service/target/robot-control-service-dist.tar.gz}"
DIST_BIGSCREEN="${DIST_BIGSCREEN:-$REPO_ROOT/bigscreen-bff/target/bigscreen-bff-dist.tar.gz}"

SSH_BASE=(ssh -4 -p "$UPDATE_SSH_PORT" -o ConnectTimeout=10 -o BatchMode=yes)
SCP_BASE=(scp -4 -P "$UPDATE_SSH_PORT" -o ConnectTimeout=10)
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

# ---------- 0. 前置检查 ----------
[ -f "$UPDATE_ENV_FILE" ] || { log "找不到环境变量增量文件：$UPDATE_ENV_FILE"; exit 1; }
for svc in $UPDATE_SERVICES; do
  dist="$(dist_for_service "$svc")"
  [ -n "$dist" ] && [ -f "$dist" ] || { log "服务 $svc 的 dist 包不存在：$dist"; exit 1; }
done
log "服务器: $HOST:$UPDATE_SSH_PORT"
log "安装目录: $UPDATE_INSTALL_DIR  工作区: $UPDATE_WORKSPACE"
log "更新服务: $UPDATE_SERVICES"

# ---------- 1. 上传 dist 包 ----------
log "== 1/6 上传 dist 包 =="
for svc in $UPDATE_SERVICES; do
  dist="$(dist_for_service "$svc")"
  log "上传 $svc <- $dist"
  "${SCP_BASE[@]}" "$dist" "$HOST:/tmp/$(basename "$dist")"
done

# ---------- 2+3. Compose 模板与环境变量同步 ----------
log "== 2/7 同步仓库 Compose 模板并更新服务器 .env =="
"${SCP_BASE[@]}" "$SCRIPT_DIR/docker-compose.yml" "$HOST:/tmp/update-services-compose.yml"
ssh_run "TS=\$(date +%Y%m%d%H%M%S); cp '$UPDATE_INSTALL_DIR/docker-compose.yml' '$UPDATE_INSTALL_DIR/docker-compose.yml.bak-template-\$TS'; cp /tmp/update-services-compose.yml '$UPDATE_INSTALL_DIR/docker-compose.yml'; cd '$UPDATE_INSTALL_DIR'; docker compose config --quiet"
# 上传服务器端幂等同步助手，并把增量文件解析为 "SERVICE VAR VALUE" 行通过 stdin 传入
"${SCP_BASE[@]}" "$SCRIPT_DIR/sync-server-env.py" "$HOST:/tmp/sync-server-env.py"
awk -F'[ =]' 'NF>=3 && $0 !~ /^#/ && $0 !~ /^$/ {print $1, $2, substr($0, index($0, $2) + length($2) + 1)}' \
  "$UPDATE_ENV_FILE" | "${SSH_BASE[@]}" "$HOST" "python3 /tmp/sync-server-env.py '$UPDATE_INSTALL_DIR/.env' '$UPDATE_INSTALL_DIR/docker-compose.yml'"

log "== 3/7 校验安装包 Compose 与仓库模板 =="
"${SCP_BASE[@]}" "$SCRIPT_DIR/verify-compose-drift.py" "$HOST:/tmp/verify-compose-drift.py"
ssh_run "cd '$UPDATE_INSTALL_DIR'; docker compose -f /tmp/update-services-compose.yml --env-file .env config --format json >/tmp/update-services-expected.json; docker compose config --format json >/tmp/update-services-actual.json; PYTHONIOENCODING=utf-8 python3 /tmp/verify-compose-drift.py /tmp/update-services-expected.json /tmp/update-services-actual.json"

# ---------- 4. 备份并替换 bin/boot/lib ----------
log "== 4/7 备份并替换服务运行目录 =="
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

# ---------- 5. 重建容器 ----------
log "== 5/7 重建容器 =="
ssh_run "cd '$UPDATE_INSTALL_DIR' && docker compose up -d --force-recreate $UPDATE_SERVICES 2>&1 | tail -8"

# ---------- 6. 核验 ----------
log "== 6/7 等待启动并核验 =="
sleep 30
ssh_run 'docker ps --format "{{.Names}}\t{{.Status}}" | grep robot-mediaserver;
echo "--- 容器内新环境变量 ---";
docker inspect robot-mediaserver-control-service --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -E "CONTROL_AUTH_ALLOW_DEFAULT_USER|MANAGEMENT_DEVICE_CACHE_TTL_SECONDS|CONTROL_DEVICE_CACHE_EVICT_DELAY_MS|ROBOT_HEARTBEAT_TIMEOUT_SECONDS|ROBOT_HEARTBEAT_SWEEP_DELAY_MS" || true;
docker inspect robot-mediaserver-bigscreen-bff --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -E "STATISTICS_REPORT_CLEANUP_INTERVAL_MS|BIGSCREEN_WS_AUTHORIZATION_MAX_STALENESS_MS|FIXED_CAMERA_CATALOG_LEASE" || true'

sleep 15
log "== 7/7 启动日志核验 =="
ssh_run 'for c in robot-mediaserver-media-service robot-mediaserver-control-service robot-mediaserver-bigscreen-bff; do
  echo "===== $c =====";
  docker logs --since 5m $c 2>&1 | grep -E "Started .*Application in|Tomcat started on port|APPLICATION FAILED|ERROR|BeanCreationException" | tail -5 || true;
done'

log "全部完成。如启动日志中有 ERROR 或启动顺序导致的瞬时 I/O 错误，请人工确认。"
