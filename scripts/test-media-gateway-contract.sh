#!/usr/bin/env sh
# 先由真实 Java 命令发布器生成 MQTT 样例，再交给 Go 消费，避免各语言手写样例自行通过。
set -eu
cd "$(dirname "$0")/.."
mvn -q -f media-common/pom.xml install -DskipTests
mvn -q -f control-service/pom.xml -Dtest=MediaGatewayCommandContractTest test
gateway_contract_root=$(pwd)
(
  cd media-gateway
  MEDIA_GATEWAY_CONTRACT_FIXTURE="$gateway_contract_root/control-service/target/media-gateway-contract.json" \
    go test -race ./internal/gateway -run TestControlContractFixture -count=1
)
