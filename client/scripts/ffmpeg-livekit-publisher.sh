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

"${ffmpeg_bin}" \
  -hide_banner \
  -loglevel warning \
  -rtsp_transport "${transport}" \
  -fflags nobuffer \
  -flags low_delay \
  -timeout 8000000 \
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
