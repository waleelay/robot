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
sh ../client/scripts/install-gstreamer-publisher.sh
export PATH="$HOME/.local/bin:$PATH"
```

You can also override the fallback command explicitly:

```bash
export FFMPEG_PUBLISHER_CMD='/home/jetson/payload/demo/python-client/scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}'
```

If the install script is not available on the target machine, build `gstreamer-publisher` manually from `https://github.com/livekit/gstreamer-publisher` and set:

```bash
export GSTREAMER_PUBLISHER_PATH="$HOME/.local/bin/gstreamer-publisher"
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
