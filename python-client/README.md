# robot-media-client Python

Python implementation of the robot media client, kept alongside the original Go client.

It follows the same responsibilities as the Go implementation:

- subscribe to robot-scoped MQTT media and control topics
- resolve local RTSP URLs from environment configuration
- probe RTSP streams through `ffprobe`
- publish RTSP video to LiveKit through an external publisher process
- upload local `.mp4` recordings to the media service with resumable parts
- bridge LiveKit intercom audio between robot microphone/speaker GStreamer pipelines
- report online/offline, video session, intercom, and equipment-control status

The Go client remains under `client/cmd` and `client/internal`.

## Reported Device State

The Python client reports the same `devices[]` structure as the Go client in `robot/{robotId}/media/client/status`.

`devices[].status` is refreshed after local equipment-control commands so the frontend can update stateful controls, including `volume/muted`, launcher `safetySwitchEnabled`, warning-light `enabled`, PTZ `autoRotateEnabled`, and vehicle-light `front/rear`.

## Install

```bash
cd client/python-client
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
```

`ffprobe` and `gstreamer-publisher` or the configured `FFMPEG_PUBLISHER_CMD` must be available in `PATH`, matching the Go client runtime requirements.

On Ubuntu/Jetson, if the default `gstreamer-publisher` path fails because the local GStreamer stack cannot create an EGL display, the client falls back to `scripts/ffmpeg-livekit-publisher.sh`. Keep the `scripts/` directory with `python-client/`, and make sure both `ffmpeg` and `gstreamer-publisher` are installed:

```bash
chmod +x scripts/ffmpeg-livekit-publisher.sh
sudo apt-get install -y ffmpeg
GOPROXY=https://goproxy.cn,direct sh ../client/scripts/install-gstreamer-publisher.sh
export PATH="$HOME/.local/bin:$PATH"
```

You can also override the fallback command explicitly:

```bash
export FFMPEG_PUBLISHER_CMD='/home/jetson/payload/demo/python-client/scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}'
```

In `PUBLISHER_MODE=auto`, streams first try the direct GStreamer RTSP path. If a GStreamer publisher fails or exits during the fallback watch window, the same session is restarted through the FFmpeg fallback path, and that RTSP URL is remembered so later starts use FFmpeg first. You can also force specific device IDs to use FFmpeg first.

```bash
export PUBLISHER_MODE=auto
export PUBLISHER_FFMPEG_FIRST_DEVICE_IDS=
export PUBLISHER_FALLBACK_WATCH_SECONDS=8
```

Use `PUBLISHER_MODE=ffmpeg` only when you want every stream to skip direct GStreamer RTSP publishing.

If the install script is not available on the target machine, build `gstreamer-publisher` manually from `https://github.com/livekit/gstreamer-publisher` and set:

```bash
export GSTREAMER_PUBLISHER_PATH="$HOME/.local/bin/gstreamer-publisher"
```

If Go dependency downloads time out on Ubuntu/Jetson, retry the install script with a reachable module proxy:

```bash
GOPROXY=https://goproxy.cn,direct sh ../client/scripts/install-gstreamer-publisher.sh
GOPROXY=https://proxy.golang.com.cn,direct sh ../client/scripts/install-gstreamer-publisher.sh
GOPROXY=direct sh ../client/scripts/install-gstreamer-publisher.sh
```

If the build fails with `gst_debug_message_get_id`, the Jetson system GStreamer headers are older than the `go-gst` dependency expects. Patch that optional debug-message ID call while building:

```bash
PATCH_GST_DEBUG_MESSAGE_ID=true GOPROXY=https://goproxy.cn,direct sh ../client/scripts/install-gstreamer-publisher.sh
```

If the fallback FFmpeg publisher fails with `Option rw_timeout not found` or `Unrecognized option 'stimeout'`, use the updated fallback script. It auto-detects the supported RTSP timeout option. You can also override or disable the timeout option:

```bash
export FFMPEG_RTSP_TIMEOUT_OPTION=auto
export FFMPEG_RTSP_TIMEOUT_OPTION=rw_timeout
export FFMPEG_RTSP_TIMEOUT_OPTION=stimeout
export FFMPEG_RTSP_TIMEOUT_OPTION=
```

## Run

```bash
cd client/python-client
python -m robot_media_client
```

The environment variables are intentionally the same as the Go client, for example:

```bash
ROBOT_ID='test001' \
ROBOT_CLIENT_ID='robot-media-client-test001' \
RTSP_CAMERA01_MAIN='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA01_SUB='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA02_MAIN='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA02_SUB='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA03_MAIN='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA03_SUB='rtsp://192.168.124.204:8554/camera03' \
PUBLISHER_MODE='ffmpeg' \
python -m robot_media_client
```

## Docker

```bash
cd client/python-client
docker build -t robot-media-client-python:dev .
docker run --rm \
  -e ROBOT_ID='test001' \
  -e ROBOT_CLIENT_ID='robot-media-client-test001' \
  -e RTSP_CAMERA01_MAIN='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA01_SUB='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA02_MAIN='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA02_SUB='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA03_MAIN='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA03_SUB='rtsp://192.168.124.204:8554/camera03' \
  robot-media-client-python:dev
```

## Intercom

Intercom is enabled by default and uses the Python LiveKit RTC SDK plus the same `GST_LAUNCH_PATH`, `AUDIO_CAPTURE_PIPELINE`, and `AUDIO_PLAYBACK_PIPELINE` environment variables as the Go client.

Set `INTERCOM_AUDIO_ENABLED=false` only when you want to run video without the audio bridge.
