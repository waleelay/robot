#!/usr/bin/env bash
set -euo pipefail

rtsp_url="${1:?rtsp url is required}"
livekit_url="${2:?livekit url is required}"
publisher_token="${3:?publisher token is required}"

ffmpeg_bin="${FFMPEG_PATH:-ffmpeg}"
publisher_bin="${GSTREAMER_PUBLISHER_PATH:-gstreamer-publisher}"
bitrate="${FFMPEG_TRANSCODE_BITRATE:-1500k}"
gop="${FFMPEG_TRANSCODE_GOP:-25}"
preset="${FFMPEG_TRANSCODE_PRESET:-veryfast}"
transport="${FFMPEG_RTSP_TRANSPORT:-tcp}"
rtsp_timeout_us="${FFMPEG_RTSP_TIMEOUT_US:-8000000}"
rtsp_timeout_option="${FFMPEG_RTSP_TIMEOUT_OPTION:-auto}"

if ! command -v "${ffmpeg_bin}" >/dev/null 2>&1; then
  echo "ffmpeg not found: ${ffmpeg_bin}" >&2
  exit 127
fi

if ! command -v "${publisher_bin}" >/dev/null 2>&1; then
  echo "gstreamer-publisher not found: ${publisher_bin}" >&2
  echo "Set GSTREAMER_PUBLISHER_PATH to the absolute binary path, or install livekit/gstreamer-publisher." >&2
  exit 127
fi

input_args=(
  -hide_banner
  -loglevel warning
  -rtsp_transport "${transport}"
)

if [ "${rtsp_timeout_option}" = "auto" ]; then
  rtsp_help="$("${ffmpeg_bin}" -hide_banner -h demuxer=rtsp 2>/dev/null || true)"
  if printf '%s\n' "${rtsp_help}" | grep -q -- '-rw_timeout'; then
    rtsp_timeout_option="rw_timeout"
  elif printf '%s\n' "${rtsp_help}" | grep -q -- '-stimeout'; then
    rtsp_timeout_option="stimeout"
  else
    rtsp_timeout_option=""
  fi
fi

if [ -n "${rtsp_timeout_option}" ] && [ "${rtsp_timeout_us}" != "0" ]; then
  input_args+=("-${rtsp_timeout_option}" "${rtsp_timeout_us}")
fi

"${ffmpeg_bin}" \
  "${input_args[@]}" \
  -fflags nobuffer \
  -flags low_delay \
  -i "${rtsp_url}" \
  -map 0:v:0 \
  -an \
  -vf format=yuv420p \
  -c:v libx264 \
  -preset "${preset}" \
  -tune zerolatency \
  -profile:v baseline \
  -level:v 4.0 \
  -g "${gop}" \
  -keyint_min "${gop}" \
  -sc_threshold 0 \
  -b:v "${bitrate}" \
  -maxrate "${bitrate}" \
  -bufsize "${bitrate}" \
  -f h264 \
  - | "${publisher_bin}" \
  --url "${livekit_url}" \
  --token "${publisher_token}" \
  -- \
  fdsrc fd=0 \
  ! h264parse config-interval=1
