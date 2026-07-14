#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IMAGE_DIR="${IMAGE_DIR:-$SCRIPT_DIR/images}"

if [ ! -d "$IMAGE_DIR" ]; then
  echo "image directory not found, skip docker load: $IMAGE_DIR"
  exit 0
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
