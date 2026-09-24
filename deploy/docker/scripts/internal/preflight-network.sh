#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"

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
DOCKER_NETWORK=$(env_value DOCKER_NETWORK robot-mediaserver)

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

project_bridge=
if command -v docker >/dev/null 2>&1; then
  project_network_id=$(docker network ls --format '{{.ID}} {{.Name}}' 2>/dev/null \
    | awk -v name="$DOCKER_NETWORK" '$2 == name { print $1; exit }')
  if [ -n "$project_network_id" ]; then
    project_subnets=$(docker network inspect "$project_network_id" \
      --format '{{range .IPAM.Config}}{{.Subnet}}{{"\n"}}{{end}}' 2>/dev/null || true)
    if ! printf '%s\n' "$project_subnets" | grep -Fqx "$DOCKER_NETWORK_SUBNET"; then
      echo "network preflight failed: existing project network $DOCKER_NETWORK does not use DOCKER_NETWORK_SUBNET $DOCKER_NETWORK_SUBNET" >&2
      echo "existing subnets:" >&2
      printf '%s\n' "$project_subnets" | sed '/^$/d; s/^/  - /' >&2
      echo "Run docker compose down before changing the project subnet, or restore the subnet used by the existing network." >&2
      exit 1
    fi
    project_bridge=$(docker network inspect "$project_network_id" \
      --format '{{index .Options "com.docker.network.bridge.name"}}' 2>/dev/null || true)
    if [ -z "$project_bridge" ]; then
      project_bridge="br-$(printf '%s' "$project_network_id" | cut -c1-12)"
    fi
  fi

  for network_id in $(docker network ls -q 2>/dev/null); do
    [ "$network_id" = "$project_network_id" ] && continue
    docker network inspect "$network_id" \
      --format '{{range .IPAM.Config}}{{.Subnet}}{{"\n"}}{{end}}' \
      >> "$docker_networks_file" 2>/dev/null || true
  done
fi

if [ -n "$project_bridge" ]; then
  ip route show | awk -v bridge="$project_bridge" '$0 !~ (" dev " bridge "([[:space:]]|$)")' > "$routes_file"
else
  ip route show > "$routes_file"
fi

awk -v target="$DOCKER_NETWORK_SUBNET" '
function power_of_two(exponent, result, i) {
  result = 1
  for (i = 0; i < exponent; i++) {
    result *= 2
  }
  return result
}

function ipv4_number(ip, octets, count, i, value) {
  count = split(ip, octets, ".")
  if (count != 4) {
    return -1
  }
  value = 0
  for (i = 1; i <= 4; i++) {
    if (octets[i] !~ /^[0-9]+$/ || octets[i] < 0 || octets[i] > 255) {
      return -1
    }
    value = value * 256 + octets[i]
  }
  return value
}

function parse_cidr(cidr, parts, count, prefix, address, block_size) {
  count = split(cidr, parts, "/")
  if (count != 2 || parts[2] !~ /^[0-9]+$/) {
    return 0
  }
  prefix = parts[2] + 0
  address = ipv4_number(parts[1])
  if (address < 0 || prefix < 0 || prefix > 32) {
    return 0
  }
  block_size = power_of_two(32 - prefix)
  parsed_start = int(address / block_size) * block_size
  parsed_end = parsed_start + block_size - 1
  return 1
}

BEGIN {
  if (!parse_cidr(target)) {
    print "network preflight failed: invalid IPv4 CIDR: " target > "/dev/stderr"
    exit 2
  }
  target_start = parsed_start
  target_end = parsed_end
}

{
  cidr = $1
  if (cidr == "" || cidr == "default" || index(cidr, ":") > 0 || !parse_cidr(cidr)) {
    next
  }
  if (target_start <= parsed_end && parsed_start <= target_end) {
    if (!found) {
      print "network preflight failed: DOCKER_NETWORK_SUBNET " target " overlaps existing network:" > "/dev/stderr"
    }
    kind = (FILENAME == ARGV[1]) ? "route" : "docker-network"
    print "  - " kind ": " cidr > "/dev/stderr"
    found = 1
  }
}

END {
  if (found) {
    print "Choose another DOCKER_NETWORK_SUBNET, or set DEPLOY_NETWORK_MODE=host on OpenStack/Linux servers that cannot use Docker bridge safely." > "/dev/stderr"
    exit 1
  }
}
' "$routes_file" "$docker_networks_file"

echo "network preflight passed: bridge subnet $DOCKER_NETWORK_SUBNET"
