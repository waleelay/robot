#!/usr/bin/env sh
set -eu

APP_NAME="@project.artifactId@"
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PID_FILE="$BASE_DIR/${APP_NAME}.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "$APP_NAME is not running"
  exit 1
fi

PID=$(cat "$PID_FILE")
DUMP_DIR="$BASE_DIR/logs/dump/$(date +%Y%m%d%H%M%S)"
mkdir -p "$DUMP_DIR"

jstack "$PID" > "$DUMP_DIR/jstack-$PID.dump" 2>&1 || true
jcmd "$PID" VM.flags > "$DUMP_DIR/vm-flags-$PID.dump" 2>&1 || true
jcmd "$PID" GC.heap_info > "$DUMP_DIR/heap-$PID.dump" 2>&1 || true

echo "dump written to $DUMP_DIR"
