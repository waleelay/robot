#!/usr/bin/env sh
set -eu
tmp="${TMPDIR:-/tmp}/gstreamer-publisher"
src="${GSTREAMER_PUBLISHER_SRC:-}"
repo="${GSTREAMER_PUBLISHER_REPO:-https://github.com/livekit/gstreamer-publisher.git}"
go_proxy="${GOPROXY:-https://goproxy.cn,direct}"
go_gst_version="${GO_GST_VERSION:-}"
go_glib_version="${GO_GLIB_VERSION:-${go_gst_version}}"
patch_gst_debug_message_id="${PATCH_GST_DEBUG_MESSAGE_ID:-false}"
git_http_version="${GIT_HTTP_VERSION:-HTTP/1.1}"

command -v go >/dev/null 2>&1 || {
  echo "go not found. Install it first: sudo apt-get install -y golang-go" >&2
  exit 127
}

if [ -n "$src" ]; then
  [ -d "$src" ] || {
    echo "GSTREAMER_PUBLISHER_SRC does not exist: $src" >&2
    exit 1
  }
  build_dir="$src"
  echo "use existing source: $build_dir"
else
  command -v git >/dev/null 2>&1 || {
    echo "git not found. Install it first: sudo apt-get install -y git" >&2
    exit 127
  }

  rm -rf "$tmp"
  echo "clone repo: $repo"
  git -c "http.version=${git_http_version}" clone --depth 1 "$repo" "$tmp" || {
    echo "clone failed: $repo"
    echo "retry with:"
    echo "GIT_HTTP_VERSION=HTTP/1.1 sh client/scripts/install-gstreamer-publisher.sh"
    echo "or set HTTP_PROXY/HTTPS_PROXY before running this script if your network needs a proxy."
    echo "or manually download https://github.com/livekit/gstreamer-publisher, copy it to this machine, then run:"
    echo "GSTREAMER_PUBLISHER_SRC=/path/to/gstreamer-publisher sh client/scripts/install-gstreamer-publisher.sh"
    exit 1
  }
  build_dir="$tmp"
fi

echo "build with GOPROXY=${go_proxy}"
if [ -n "$go_gst_version" ]; then
  echo "pin go-gst=${go_gst_version} go-glib=${go_glib_version}"
  (cd "$build_dir" && GOPROXY="$go_proxy" go get \
    "github.com/go-gst/go-gst@${go_gst_version}" \
    "github.com/go-gst/go-glib@${go_glib_version}") || {
    echo "failed to pin go-gst/go-glib versions." >&2
    exit 1
  }
fi

if [ "$patch_gst_debug_message_id" = "true" ]; then
  command -v python3 >/dev/null 2>&1 || {
    echo "python3 not found. It is required for PATCH_GST_DEBUG_MESSAGE_ID=true." >&2
    exit 127
  }

  (cd "$build_dir" && GOPROXY="$go_proxy" go mod download github.com/go-gst/go-gst) || {
    echo "failed to download go-gst before patching." >&2
    exit 1
  }
  gst_module_dir="$(cd "$build_dir" && GOPROXY="$go_proxy" go list -m -f '{{.Dir}}' github.com/go-gst/go-gst)"
  [ -n "$gst_module_dir" ] || {
    echo "go-gst module directory not found." >&2
    exit 1
  }
  gst_debug_file="${gst_module_dir}/gst/gst_debug.go"
  [ -f "$gst_debug_file" ] || {
    echo "go-gst gst_debug.go not found: $gst_debug_file" >&2
    exit 1
  }

  chmod -R u+w "$gst_module_dir" || {
    echo "failed to make go-gst module cache writable: $gst_module_dir" >&2
    exit 1
  }

  echo "patch legacy GStreamer debug API: $gst_debug_file"
  python3 - "$gst_debug_file" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
lines = text.splitlines(keepends=True)
changed = False
patched = []

for line in lines:
    stripped = line.lstrip()
    if "gst_debug_message_get_id" in line and stripped.startswith("return "):
        indent = line[: len(line) - len(stripped)]
        patched.append(f'{indent}return ""\n')
        changed = True
    else:
        patched.append(line)

if not changed:
    raise SystemExit(f"gst_debug_message_get_id return line not found in {path}")

backup = path.with_suffix(path.suffix + ".bak")
if not backup.exists():
    backup.write_text(text)
path.write_text("".join(patched))
PY
fi

(cd "$build_dir" && GOPROXY="$go_proxy" go build -o gstreamer-publisher .) || {
  echo "go build failed."
  echo "If the error mentions gst_debug_message_get_id, your system GStreamer headers are older than go-gst v1.4.0."
  echo "Retry with: PATCH_GST_DEBUG_MESSAGE_ID=true GOPROXY=https://goproxy.cn,direct sh client/scripts/install-gstreamer-publisher.sh"
  echo "If dependency downloads time out, retry with another GOPROXY, for example:"
  echo "GOPROXY=https://goproxy.cn,direct sh client/scripts/install-gstreamer-publisher.sh"
  echo "GOPROXY=https://proxy.golang.com.cn,direct sh client/scripts/install-gstreamer-publisher.sh"
  echo "GOPROXY=direct sh client/scripts/install-gstreamer-publisher.sh"
  exit 1
}

mkdir -p "${HOME}/.local/bin"
cp "$build_dir/gstreamer-publisher" "${HOME}/.local/bin/gstreamer-publisher"
chmod +x "${HOME}/.local/bin/gstreamer-publisher"
echo "${HOME}/.local/bin/gstreamer-publisher"
