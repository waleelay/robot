#!/usr/bin/env sh
set -eu

APP_NAME="@project.artifactId@"
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PID_FILE="$BASE_DIR/${APP_NAME}.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "$APP_NAME is already stopped"
  exit 0
fi

PID=$(cat "$PID_FILE")
if kill -0 "$PID" >/dev/null 2>&1; then
  kill "$PID"
  echo "$APP_NAME stopped, pid $PID"
else
  echo "$APP_NAME is already stopped"
fi

rm -f "$PID_FILE"
