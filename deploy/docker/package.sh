#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
DIST_DIR="${DIST_DIR:-$PROJECT_DIR/dist}"
TOOL_IMAGE_DIR="${TOOL_IMAGE_DIR:-$PROJECT_DIR/deploy/docker/tool-images}"
PACKAGE_NAME="${PACKAGE_NAME:-robot-mediaserver-installer-$(date +%Y%m%d%H%M%S)}"
STAGING_DIR="$DIST_DIR/$PACKAGE_NAME"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"

if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE="$SCRIPT_DIR/.env.example"
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "docker compose or docker-compose is required" >&2
  exit 1
fi

rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/packages" "$STAGING_DIR/images" "$STAGING_DIR/tools"

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

echo "building application runtime images"
(cd "$SCRIPT_DIR" && $COMPOSE --env-file "$ENV_FILE" -f docker-compose.build.yml build media-service control-service bigscreen-bff)

save_image() {
  image=$1
  file=$2
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "saving $image"
    docker save "$image" | gzip -9 > "$STAGING_DIR/images/$file"
  else
    echo "image not found, skip: $image"
  fi
}

image_prefix=$(sed -n 's/^IMAGE_PREFIX=//p' "$ENV_FILE" | tail -n 1)
image_tag=$(sed -n 's/^IMAGE_TAG=//p' "$ENV_FILE" | tail -n 1)
image_prefix=${image_prefix:-robot}
image_tag=${image_tag:-latest}

save_image "$image_prefix/media-service:$image_tag" "media-service-image.tar.gz"
save_image "$image_prefix/control-service:$image_tag" "control-service-image.tar.gz"
save_image "$image_prefix/bigscreen-bff:$image_tag" "bigscreen-bff-image.tar.gz"

if [ -d "$TOOL_IMAGE_DIR" ]; then
  echo "copying tool images from $TOOL_IMAGE_DIR"
  find "$TOOL_IMAGE_DIR" -maxdepth 1 -type f \( -name '*.tar' -o -name '*.tar.gz' -o -name '*.tgz' -o -name '*.tar.xz' -o -name '*.txz' -o -name '*.tar.zst' -o -name '*.tzst' \) -exec cp {} "$STAGING_DIR/images/" \;
else
  echo "tool image directory not found, skip: $TOOL_IMAGE_DIR"
fi

cp "$SCRIPT_DIR/docker-compose.yml" "$STAGING_DIR/docker-compose.yml"
cp "$SCRIPT_DIR/.env.example" "$STAGING_DIR/.env.example"
cp "$SCRIPT_DIR/install.sh" "$STAGING_DIR/install.sh"
cp "$SCRIPT_DIR/uninstall.sh" "$STAGING_DIR/uninstall.sh"
cp "$SCRIPT_DIR/load-images.sh" "$STAGING_DIR/load-images.sh"
cp "$SCRIPT_DIR/prepare-workspace.sh" "$STAGING_DIR/prepare-workspace.sh"

cat > "$STAGING_DIR/README.md" <<'EOF'
# robot-mediaserver installer

1. Edit `.env.example` if needed, or copy it to `.env`.
2. Run `./install.sh`.
3. Run `./uninstall.sh` to stop services.

Java service packages are stored under `packages/`.
Docker image archives are stored under `images/`.
Application files are extracted to `/opt/qihang/robot/dockerWorkspace` by default.
Repeated installs use `INSTALL_MODE=skip_existing` by default, so existing runtime directories and LiveKit config files are left untouched. Set `INSTALL_MODE=overwrite` in `.env` to overwrite them during install.
EOF

chmod +x "$STAGING_DIR"/*.sh

(cd "$DIST_DIR" && tar -czf "$PACKAGE_NAME.tar.gz" "$PACKAGE_NAME")
echo "created $DIST_DIR/$PACKAGE_NAME.tar.gz"
