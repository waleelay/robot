#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd media-common && mvn -q install -DskipTests)
(cd backend && mvn -q -DskipTests package)
(cd control-service && mvn -q -DskipTests package)
(cd bigscreen-bff && mvn -q -DskipTests package)
(cd frontend && npm run build)
mkdir -p target/dev-check
(cd fixed-camera-gateway && go build -o ../target/dev-check/fixed-camera-gateway ./cmd/fixed-camera-gateway)
