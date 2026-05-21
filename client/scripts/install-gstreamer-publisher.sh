#!/usr/bin/env sh
set -eu
tmp="${TMPDIR:-/tmp}/gstreamer-publisher"
rm -rf "$tmp"
repo="${GSTREAMER_PUBLISHER_REPO:-https://github.com/livekit/gstreamer-publisher.git}"
export HTTP_PROXY=http://127.0.0.1:7892
export HTTPS_PROXY=http://127.0.0.1:7892
git -c http.version=HTTP/1.1 clone --depth 1 "$repo" "$tmp" || {
  echo "clone failed: $repo"
  echo "retry with:"
  echo "GIT_HTTP_VERSION=HTTP/1.1 sh client/scripts/install-gstreamer-publisher.sh"
  echo "or manually download https://github.com/livekit/gstreamer-publisher and run go build -o gstreamer-publisher ."
  exit 1
}
(cd "$tmp" && go build -o gstreamer-publisher .)
mkdir -p "${HOME}/.local/bin"
cp "$tmp/gstreamer-publisher" "${HOME}/.local/bin/gstreamer-publisher"
echo "${HOME}/.local/bin/gstreamer-publisher"
