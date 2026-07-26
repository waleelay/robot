#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$SCRIPT_DIR/.env"

env_value() {
  key=$1
  default_value=$2
  if [ -f "$ENV_FILE" ]; then
    value=$(sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1)
  else
    value=
  fi
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

DEPLOY_NETWORK_MODE=$(env_value DEPLOY_NETWORK_MODE bridge)
DOCKER_NETWORK_SUBNET=$(env_value DOCKER_NETWORK_SUBNET 10.253.10.0/24)

if [ "$DEPLOY_NETWORK_MODE" = "host" ]; then
  echo "network preflight: host mode, skip Docker bridge subnet checks"
  exit 0
fi

if [ "$DEPLOY_NETWORK_MODE" != "bridge" ]; then
  echo "unsupported DEPLOY_NETWORK_MODE: $DEPLOY_NETWORK_MODE, expected bridge or host" >&2
  exit 1
fi

if ! command -v ip >/dev/null 2>&1; then
  echo "network preflight: ip command not found, skip route overlap check"
  exit 0
fi

routes_file=$(mktemp "${TMPDIR:-/tmp}/robot-routes.XXXXXX")
docker_networks_file=$(mktemp "${TMPDIR:-/tmp}/robot-docker-networks.XXXXXX")
trap 'rm -f "$routes_file" "$docker_networks_file"' EXIT

ip route show > "$routes_file"

if command -v docker >/dev/null 2>&1; then
  docker network inspect $(docker network ls -q) --format '{{range .IPAM.Config}}{{.Subnet}}{{"\n"}}{{end}}' > "$docker_networks_file" 2>/dev/null || true
fi

if command -v python3 >/dev/null 2>&1; then
  python3 - "$DOCKER_NETWORK_SUBNET" "$routes_file" "$docker_networks_file" <<'PY'
import ipaddress
import re
import sys

target = ipaddress.ip_network(sys.argv[1], strict=False)
route_file = sys.argv[2]
docker_file = sys.argv[3]

networks = []

with open(route_file, "r", encoding="utf-8") as f:
    for line in f:
        first = line.split()[0] if line.split() else ""
        if first == "default":
            continue
        if "/" in first:
            try:
                networks.append(("route", first, ipaddress.ip_network(first, strict=False)))
            except ValueError:
                pass

with open(docker_file, "r", encoding="utf-8") as f:
    for raw in f:
        subnet = raw.strip()
        if not subnet or ":" in subnet:
            continue
        try:
            networks.append(("docker-network", subnet, ipaddress.ip_network(subnet, strict=False)))
        except ValueError:
            pass

conflicts = [(kind, text) for kind, text, net in networks if target.overlaps(net)]
if conflicts:
    print(f"network preflight failed: DOCKER_NETWORK_SUBNET {target} overlaps existing network:", file=sys.stderr)
    for kind, text in conflicts:
        print(f"  - {kind}: {text}", file=sys.stderr)
    print("Choose another DOCKER_NETWORK_SUBNET, or set DEPLOY_NETWORK_MODE=host on OpenStack/Linux servers that cannot use Docker bridge safely.", file=sys.stderr)
    sys.exit(1)

print(f"network preflight passed: bridge subnet {target}")
PY
else
  if grep -q "$DOCKER_NETWORK_SUBNET" "$routes_file" "$docker_networks_file"; then
    echo "network preflight failed: DOCKER_NETWORK_SUBNET appears in current routes or Docker networks: $DOCKER_NETWORK_SUBNET" >&2
    exit 1
  fi
  echo "network preflight passed: bridge subnet $DOCKER_NETWORK_SUBNET"
fi
