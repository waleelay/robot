#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd media-common && mvn -q install -DskipTests)
(cd media-service && mvn -q -DskipTests package)
(cd control-service && mvn -q -DskipTests package)
(cd bigscreen-bff && mvn -q -DskipTests package)
mkdir -p target/dev-check
(cd fixed-camera-gateway && go build -o ../target/dev-check/fixed-camera-gateway ./cmd/fixed-camera-gateway)
