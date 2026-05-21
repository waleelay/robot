#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd backend && mvn -q -DskipTests package)
(cd frontend && npm run build)
if command -v go >/dev/null 2>&1; then
  (cd client && go build -o robot-media-client ./cmd/robot-media-client)
fi
