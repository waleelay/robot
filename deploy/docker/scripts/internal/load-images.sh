#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
IMAGE_DIR="${IMAGE_DIR:-$DEPLOY_DIR/images}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"

if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$DEPLOY_DIR/.env.example"
fi

env_value() {
  key=$1
  default_value=$2
  value=""
  if [ -f "$ENV_FILE" ]; then
    value=$(sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1)
  fi
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

if [ ! -d "$IMAGE_DIR" ]; then
  echo "image directory not found: $IMAGE_DIR"
fi

found=0
for image in "$IMAGE_DIR"/*.tar "$IMAGE_DIR"/*.tar.gz "$IMAGE_DIR"/*.tgz "$IMAGE_DIR"/*.tar.xz "$IMAGE_DIR"/*.txz "$IMAGE_DIR"/*.tar.zst "$IMAGE_DIR"/*.tzst; do
  [ -f "$image" ] || continue
  found=1
  echo "loading docker image $image"
  docker load -i "$image"
done

if [ "$found" -eq 0 ]; then
  echo "no docker image tar files found in $IMAGE_DIR"
fi

ensure_image() {
  desired=$1
  shift

  if docker image inspect "$desired" >/dev/null 2>&1; then
    return
  fi

  expected_version=${desired##*:}
  for source in "$@"; do
    [ -n "$source" ] || continue
    if ! docker image inspect "$source" >/dev/null 2>&1; then
      continue
    fi

    case "$expected_version" in
      v*)
        actual_version=$(docker image inspect \
          --format '{{ index .Config.Labels "org.opencontainers.image.version" }}' \
          "$source" 2>/dev/null || true)
        if [ -z "$actual_version" ]; then
          echo "cannot verify image version: $source has no org.opencontainers.image.version label, required $desired" >&2
          continue
        fi
        if [ "$actual_version" != "$expected_version" ]; then
          echo "image version mismatch: $source is $actual_version, required $desired" >&2
          continue
        fi
        ;;
    esac

    echo "tagging offline image $source as $desired"
    docker tag "$source" "$desired"
    return
  done

  echo "required offline image not found: $desired" >&2
  echo "check the image archive under $IMAGE_DIR and rebuild the installer; online pull is disabled by the installer preflight" >&2
  exit 1
}

image_prefix=$(env_value IMAGE_PREFIX robot)
image_tag=$(env_value IMAGE_TAG latest)
compose_profiles=$(env_value COMPOSE_PROFILES "")
compose_profiles=$(printf '%s' "$compose_profiles" | tr -d '[:space:]')

ensure_image "$(env_value NGINX_IMAGE nginx:alpine)" nginx:alpine nginx:latest
ensure_image "$(env_value LIVEKIT_SERVER_IMAGE livekit/livekit-server:v1.13.3)" livekit/livekit-server:latest
ensure_image "$(env_value LIVEKIT_INGRESS_IMAGE livekit/ingress:v1.5.0)" livekit/ingress:latest
ensure_image "$(env_value LIVEKIT_EGRESS_IMAGE livekit/egress:v1.13.0)" livekit/egress:latest
ensure_image "$image_prefix/media-service:$image_tag"
ensure_image "$image_prefix/control-service:$image_tag"
ensure_image "$image_prefix/bigscreen-bff:$image_tag"

case ",$compose_profiles," in
  *,tts,*)
    ensure_image "$(env_value TTS_IMAGE local/sherpa-tts-http:zh-en)"
    ;;
esac

case ",$compose_profiles," in
  *,fixed-camera-gateway,*)
    ensure_image "$image_prefix/fixed-camera-gateway:$image_tag"
    ;;
esac
