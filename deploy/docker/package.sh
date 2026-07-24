#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
DIST_DIR="${DIST_DIR:-$PROJECT_DIR/dist}"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
ROBOT_UI_DIR="${ROBOT_UI_DIR:-$PROJECT_DIR/robot-ui}"
ROBOT_UI_DIST_DIR="${ROBOT_UI_DIST_DIR:-$ROBOT_UI_DIR/dist}"
ROBOT_UI_BUILD="${ROBOT_UI_BUILD:-true}"
TDT_DIR="${TDT_DIR:-$SCRIPT_DIR/config/nginx/html/tdt}"
TDT_TAR="${TDT_TAR:-$SCRIPT_DIR/config/nginx/html/tdt.tar.gz}"
TDT_ZIP="${TDT_ZIP:-$SCRIPT_DIR/config/nginx/html/tdt.zip}"

if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$SCRIPT_DIR/.env.example"
fi

env_value() {
  key=$1
  default_value=$2
  value=$(sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1)
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default_value"
  fi
}

TARGET_ARCH="${TARGET_ARCH:-$(env_value TARGET_ARCH amd64)}"
case "$TARGET_ARCH" in
  amd64|x86_64)
    TARGET_ARCH=amd64
    TARGET_PLATFORM=linux/amd64
    ;;
  arm64|aarch64)
    TARGET_ARCH=arm64
    TARGET_PLATFORM=linux/arm64
    ;;
  *)
    echo "unsupported TARGET_ARCH: $TARGET_ARCH, expected amd64 or arm64" >&2
    exit 1
    ;;
esac

TOOL_IMAGE_ROOT="${TOOL_IMAGE_ROOT:-$PROJECT_DIR/deploy/docker/tool-images}"
TOOL_IMAGE_DIR="${TOOL_IMAGE_DIR:-$TOOL_IMAGE_ROOT/$TARGET_ARCH}"
if [ ! -d "$TOOL_IMAGE_DIR" ] && [ -d "$TOOL_IMAGE_ROOT" ]; then
  TOOL_IMAGE_DIR="$TOOL_IMAGE_ROOT"
fi

PACKAGE_NAME="${PACKAGE_NAME:-robot-mediaserver-installer-$TARGET_ARCH-$(date +%Y%m%d%H%M%S)}"
STAGING_DIR="$DIST_DIR/$PACKAGE_NAME"

if command -v docker >/dev/null 2>&1; then
  DOCKER="docker"
else
  echo "docker is required" >&2
  exit 1
fi

if ! $DOCKER buildx version >/dev/null 2>&1; then
  echo "docker buildx is required for architecture-specific image builds" >&2
  exit 1
fi

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/packages" "$STAGING_DIR/images" "$STAGING_DIR/tools" "$STAGING_DIR/config/livekit" "$STAGING_DIR/config/nginx/html" "$STAGING_DIR/config/tts"

build_service() {
  service_dir=$1
  package_name=$2
  echo "building $service_dir"
  (cd "$PROJECT_DIR/$service_dir" && mvn -q -DskipTests clean package)
  cp "$PROJECT_DIR/$service_dir/target/$package_name" "$STAGING_DIR/packages/"
}

build_service "backend" "robot-mediaserver-dist.tar.gz"
build_service "control-service" "robot-control-service-dist.tar.gz"
build_service "bigscreen-bff" "bigscreen-bff-dist.tar.gz"

if [ "$ROBOT_UI_BUILD" != "false" ]; then
  echo "building robot-ui"
  (cd "$ROBOT_UI_DIR" && npm run build:prod)
fi

if [ ! -d "$ROBOT_UI_DIST_DIR" ]; then
  echo "missing robot-ui dist directory: $ROBOT_UI_DIST_DIR" >&2
  echo "run 'cd robot-ui && npm run build:prod' first, or do not set ROBOT_UI_BUILD=false" >&2
  exit 1
fi

echo "copying robot-ui dist"
cp -R "$ROBOT_UI_DIST_DIR" "$STAGING_DIR/config/nginx/html/dist"

if [ -f "$TDT_TAR" ]; then
  echo "copying tdt map tar.gz"
  cp "$TDT_TAR" "$STAGING_DIR/config/nginx/html/tdt.tar.gz"
elif [ -f "${TDT_TAR%.tar.gz}.tgz" ]; then
  echo "copying tdt map tgz"
  cp "${TDT_TAR%.tar.gz}.tgz" "$STAGING_DIR/config/nginx/html/tdt.tgz"
elif [ -f "$TDT_ZIP" ]; then
  echo "copying tdt map zip"
  cp "$TDT_ZIP" "$STAGING_DIR/config/nginx/html/tdt.zip"
elif [ -d "$TDT_DIR" ] && [ "$(find "$TDT_DIR" -mindepth 1 -maxdepth 1 | head -n 1)" ]; then
  echo "copying tdt map files"
  cp -R "$TDT_DIR" "$STAGING_DIR/config/nginx/html/tdt"
else
  echo "tdt map archive/directory is empty or not found, skip: $TDT_TAR $TDT_ZIP $TDT_DIR"
fi

echo "building application runtime images"

image_prefix=$(sed -n 's/^IMAGE_PREFIX=//p' "$ENV_FILE" | tail -n 1)
image_tag=$(sed -n 's/^IMAGE_TAG=//p' "$ENV_FILE" | tail -n 1)
image_prefix=${image_prefix:-robot}
image_tag=${image_tag:-latest}
java_runtime_image=$(sed -n 's/^JAVA_RUNTIME_IMAGE=//p' "$ENV_FILE" | tail -n 1)
java_runtime_image=${JAVA_RUNTIME_IMAGE:-${java_runtime_image:-robot/java17-ffmpeg-runtime:latest}}

load_image_archive() {
  archive_file=$1
  echo "loading image archive: $archive_file"
  case "$archive_file" in
    *.tar.gz|*.tgz)
      gzip -dc "$archive_file" | $DOCKER load
      ;;
    *.tar.xz|*.txz)
      xz -dc "$archive_file" | $DOCKER load
      ;;
    *.tar.zst|*.tzst)
      zstd -dc "$archive_file" | $DOCKER load
      ;;
    *.tar)
      $DOCKER load -i "$archive_file"
      ;;
    *)
      echo "unsupported image archive format: $archive_file" >&2
      exit 1
      ;;
  esac
}

ensure_java_runtime_image() {
  if $DOCKER image inspect "$java_runtime_image" >/dev/null 2>&1; then
    return
  fi

  for archive in \
    "$TOOL_IMAGE_DIR/java-runtime.tar" \
    "$TOOL_IMAGE_DIR/java-runtime.tar.gz" \
    "$TOOL_IMAGE_DIR/java-runtime.tgz" \
    "$TOOL_IMAGE_DIR/java-runtime.tar.xz" \
    "$TOOL_IMAGE_DIR/java-runtime.txz" \
    "$TOOL_IMAGE_DIR/java-runtime.tar.zst" \
    "$TOOL_IMAGE_DIR/java-runtime.tzst"
  do
    if [ -f "$archive" ]; then
      load_image_archive "$archive"
      break
    fi
  done

  if ! $DOCKER image inspect "$java_runtime_image" >/dev/null 2>&1; then
    echo "missing Java runtime image: $java_runtime_image" >&2
    echo "Prepare it before packaging, for example:" >&2
    echo "  docker buildx build --platform $TARGET_PLATFORM --load -t $java_runtime_image $SCRIPT_DIR/java-runtime" >&2
    echo "  docker image save --platform $TARGET_PLATFORM $java_runtime_image | gzip -9 > $TOOL_IMAGE_DIR/java-runtime.tar.gz" >&2
    echo "Or set JAVA_RUNTIME_IMAGE in .env to an existing local Java 17 runtime image." >&2
    exit 1
  fi

  if ! $DOCKER run --rm --platform "$TARGET_PLATFORM" "$java_runtime_image" sh -c 'java -version >/dev/null && ffmpeg -version >/dev/null && ffprobe -version >/dev/null' >/dev/null 2>&1; then
    echo "Java runtime image must contain java, ffmpeg, and ffprobe: $java_runtime_image" >&2
    echo "Rebuild it before packaging, for example:" >&2
    echo "  docker buildx build --platform $TARGET_PLATFORM --load -t $java_runtime_image $SCRIPT_DIR/java-runtime" >&2
    echo "  docker image save --platform $TARGET_PLATFORM $java_runtime_image | gzip -9 > $TOOL_IMAGE_DIR/java-runtime.tar.gz" >&2
    exit 1
  fi
}

ensure_java_runtime_image

build_image() {
  service_dir=$1
  image=$2
  echo "building $image for $TARGET_PLATFORM with $java_runtime_image"
  $DOCKER buildx build --platform "$TARGET_PLATFORM" --build-arg "JAVA_RUNTIME_IMAGE=$java_runtime_image" --load -t "$image" "$PROJECT_DIR/$service_dir"
}

build_image "backend" "$image_prefix/media-service:$image_tag"
build_image "control-service" "$image_prefix/control-service:$image_tag"
build_image "bigscreen-bff" "$image_prefix/bigscreen-bff:$image_tag"

save_image() {
  image=$1
  file=$2
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "saving $image"
    if docker save --help 2>/dev/null | grep -q -- '--platform'; then
      docker save --platform "$TARGET_PLATFORM" "$image" | gzip -9 > "$STAGING_DIR/images/$file"
    else
      docker save "$image" | gzip -9 > "$STAGING_DIR/images/$file"
    fi
  else
    echo "image not found, skip: $image"
  fi
}

save_image "$image_prefix/media-service:$image_tag" "media-service-image.tar.gz"
save_image "$image_prefix/control-service:$image_tag" "control-service-image.tar.gz"
save_image "$image_prefix/bigscreen-bff:$image_tag" "bigscreen-bff-image.tar.gz"

if [ -d "$TOOL_IMAGE_DIR" ]; then
  echo "copying tool images from $TOOL_IMAGE_DIR"
  find "$TOOL_IMAGE_DIR" -maxdepth 1 -type f \( -name '*.tar' -o -name '*.tar.gz' -o -name '*.tgz' -o -name '*.tar.xz' -o -name '*.txz' -o -name '*.tar.zst' -o -name '*.tzst' \) -exec cp {} "$STAGING_DIR/images/" \;
else
  echo "tool image directory not found, skip: $TOOL_IMAGE_DIR"
fi

if [ -d "$SCRIPT_DIR/config/livekit" ]; then
  find "$SCRIPT_DIR/config/livekit" -maxdepth 1 -type f \( -name 'livekit.yaml' -o -name 'livekit-egress.yaml' -o -name 'egress.yaml' \) -exec cp {} "$STAGING_DIR/config/livekit/" \;
fi

if [ -f "$SCRIPT_DIR/config/nginx/nginx.conf" ]; then
  cp "$SCRIPT_DIR/config/nginx/nginx.conf" "$STAGING_DIR/config/nginx/nginx.conf"
elif [ -f "$PROJECT_DIR/deploy/nginx/robot-mediaserver.conf" ]; then
  cp "$PROJECT_DIR/deploy/nginx/robot-mediaserver.conf" "$STAGING_DIR/config/nginx/nginx.conf"
fi

if [ -f "$SCRIPT_DIR/config/tts/app.py" ]; then
  cp "$SCRIPT_DIR/config/tts/app.py" "$STAGING_DIR/config/tts/app.py"
fi

cp "$SCRIPT_DIR/docker-compose.yml" "$STAGING_DIR/docker-compose.yml"
cp "$SCRIPT_DIR/docker-compose.host.yml" "$STAGING_DIR/docker-compose.host.yml"
awk -v target_arch="$TARGET_ARCH" -v target_platform="$TARGET_PLATFORM" '
  BEGIN { arch_written = 0; platform_written = 0 }
  /^TARGET_ARCH=/ {
    print "TARGET_ARCH=" target_arch
    arch_written = 1
    next
  }
  /^TARGET_PLATFORM=/ {
    print "TARGET_PLATFORM=" target_platform
    platform_written = 1
    next
  }
  { print }
  END {
    if (!arch_written) {
      print "TARGET_ARCH=" target_arch
    }
    if (!platform_written) {
      print "TARGET_PLATFORM=" target_platform
    }
  }
' "$SCRIPT_DIR/.env.example" > "$STAGING_DIR/.env.example"
cp "$SCRIPT_DIR/install.sh" "$STAGING_DIR/install.sh"
cp "$SCRIPT_DIR/uninstall.sh" "$STAGING_DIR/uninstall.sh"
cp "$SCRIPT_DIR/load-images.sh" "$STAGING_DIR/load-images.sh"
cp "$SCRIPT_DIR/prepare-workspace.sh" "$STAGING_DIR/prepare-workspace.sh"
cp "$SCRIPT_DIR/preflight-network.sh" "$STAGING_DIR/preflight-network.sh"
cp "$SCRIPT_DIR/configure-env-ip.sh" "$STAGING_DIR/configure-env-ip.sh"

cat > "$STAGING_DIR/README.md" <<'EOF'
# robot-mediaserver installer

1. Edit `.env.example` if needed, or copy it to `.env`.
2. Run `./configure-env-ip.sh <server-ip>` when internal and external IP are the same.
   Run `./configure-env-ip.sh --internal-ip <internal-ip> --external-ip <external-ip>` when clients need a public IP.
3. Run `./install.sh`.
4. Run `./uninstall.sh` to stop services.

Java service packages are stored under `packages/`.
Docker image archives are stored under `images/`.
Runtime config templates are stored under `config/`.
Robot UI dist is stored under `config/nginx/html/dist`.
TDT map files are stored under `config/nginx/html/tdt.tar.gz`, `config/nginx/html/tdt.zip`, or `config/nginx/html/tdt` when provided.
Java runtime image defaults to `robot/java17-ffmpeg-runtime:latest`; it must contain Java 17, ffmpeg, and ffprobe for HLS.
Application files are extracted to `~/mounts/media` by default. `~` means the current user's home directory.
Runtime IPs, ports, accounts, and secrets should be configured in `.env`.
For OpenStack/Linux servers that are sensitive to Docker bridge routes, set `DEPLOY_NETWORK_MODE=host` in `.env` before running `./install.sh`.
Repeated installs use `INSTALL_MODE=skip_existing` by default, so existing runtime directories and LiveKit config files are left untouched. Set `INSTALL_MODE=overwrite` in `.env` to overwrite them during install.
EOF

{
  echo
  echo "Target architecture: $TARGET_ARCH"
  echo "Target platform: $TARGET_PLATFORM"
} >> "$STAGING_DIR/README.md"

chmod +x "$STAGING_DIR"/*.sh

if command -v xattr >/dev/null 2>&1; then
  xattr -cr "$STAGING_DIR" 2>/dev/null || true
fi

(cd "$DIST_DIR" && COPYFILE_DISABLE=1 tar -czf "$PACKAGE_NAME.tar.gz" "$PACKAGE_NAME")
echo "created $DIST_DIR/$PACKAGE_NAME.tar.gz"
