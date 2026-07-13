#!/usr/bin/env bash
set -euo pipefail

# FFmpeg fallback publisher:
#
# 这个脚本用于 Python 客户端 PUBLISHER_MODE=auto/ffmpeg 时的备用推流链路。
# 输入 RTSP 先由 ffmpeg 拉取并转成低延迟 H264 elementary stream，再通过管道交给
# gstreamer-publisher 发布到 LiveKit。
#
# 参数：
#   1. rtsp_url：机器人本地摄像头 RTSP。
#   2. livekit_url：LiveKit 服务地址。
#   3. publisher_token：该房间的 publisher token。
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
  # 不同 FFmpeg 版本支持的 RTSP 超时参数不一致：
  # - 较新版本通常支持 rw_timeout。
  # - 一些旧版本支持 stimeout。
  # 自动探测可以减少在 Jetson/Ubuntu 现场部署时的参数兼容问题。
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

# ffmpeg 输出裸 H264 到 stdout；gstreamer-publisher 从 fdsrc fd=0 读取。
# 这里会重新编码为 baseline/yuv420p/zerolatency，牺牲一部分 CPU，换取更好的
# 浏览器/WebRTC 兼容性和更稳定的 fallback 行为。
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
