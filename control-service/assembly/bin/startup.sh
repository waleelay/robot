#!/usr/bin/env sh
set -eu

APP_NAME="@project.artifactId@"
APP_JAR="@build.finalName@.jar"
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
LOG_DIR="$BASE_DIR/logs"
PID_FILE="$BASE_DIR/${APP_NAME}.pid"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx512m -Duser.timezone=Asia/Shanghai}"
SPRING_ARGS="${SPRING_ARGS:---spring.config.additional-location=$BASE_DIR/config/}"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" >/dev/null 2>&1; then
  echo "$APP_NAME is already running, pid $(cat "$PID_FILE")"
  exit 0
fi

nohup java $JAVA_OPTS -jar "$BASE_DIR/boot/$APP_JAR" $SPRING_ARGS > "$LOG_DIR/$APP_NAME.log" 2>&1 &
echo "$!" > "$PID_FILE"
echo "$APP_NAME started, pid $(cat "$PID_FILE")"
