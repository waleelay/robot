# robot-media-client Python 客户端

这是机器人媒体客户端的 Python 实现，功能边界与 Go 客户端保持一致，便于在 Jetson、Ubuntu 或其它 Python 运行环境中部署。

客户端主要负责：

- 连接 MQTT，并订阅机器人维度的实时视频、对讲和设备控制 Topic。
- 根据环境变量解析本地摄像头 RTSP 地址。
- 使用 `ffprobe` 探测 RTSP 视频流是否可用。
- 通过外部 publisher 进程把 RTSP 视频发布到 LiveKit。
- 扫描本地录像目录，并按通用文件 multipart 协议断点续传到媒体服务。
- 使用 LiveKit Python SDK 和 GStreamer 音频管线桥接机器人端对讲。
- 上报机器人在线、离线、实时视频、对讲和设备状态。

Go 客户端仍位于仓库 `client/` 目录，Python 客户端位于仓库根目录 `python-client/`。

## 目录结构

```text
python-client/
  robot_media_client/
    main.py
    config.py
    mqtt_client.py
    publisher.py
    rtsp.py
    intercom.py
    model.py
    timeutil.py
    recordingupload/
      client.py
      manifest.py
      runner.py
      uploader.py
  scripts/
    ffmpeg-livekit-publisher.sh
  Dockerfile
  requirements.txt
  代码结构说明.md
```

更详细的模块职责见 [代码结构说明.md](代码结构说明.md)。任务视频上传对接见 [任务视频上传对接文档.md](任务视频上传对接文档.md)。

## 安装

```bash
cd python-client
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
```

运行环境还需要安装以下外部命令：

- `ffprobe`：RTSP 探测。
- `gstreamer-publisher`：默认 RTSP 到 LiveKit 推流。
- `ffmpeg`：默认 GStreamer publisher 失败时的 fallback 推流。
- `gst-launch-1.0`：本地对讲音频采集和播放。

## GStreamer 与 FFmpeg fallback

默认 `PUBLISHER_MODE=auto`。客户端会优先尝试 GStreamer 直推 RTSP，如果 GStreamer 启动失败或在观察窗口内退出，会自动回退到 `FFMPEG_PUBLISHER_CMD`。

默认 fallback 脚本：

```bash
./scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}
```

Jetson/Ubuntu 环境可参考：

```bash
chmod +x scripts/ffmpeg-livekit-publisher.sh
sudo apt-get install -y ffmpeg
GOPROXY=https://goproxy.cn,direct sh ../client/scripts/install-gstreamer-publisher.sh
export PATH="$HOME/.local/bin:$PATH"
```

常用推流配置：

```bash
export PUBLISHER_MODE=auto
export PUBLISHER_FFMPEG_FIRST_DEVICE_IDS=
export PUBLISHER_FALLBACK_WATCH_SECONDS=8
```

如果希望所有视频都直接使用 FFmpeg：

```bash
export PUBLISHER_MODE=ffmpeg
export FFMPEG_PUBLISHER_CMD='/path/to/python-client/scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}'
```

如果 `ffmpeg` 报 `Option rw_timeout not found` 或 `Unrecognized option 'stimeout'`，可调整：

```bash
export FFMPEG_RTSP_TIMEOUT_OPTION=auto
export FFMPEG_RTSP_TIMEOUT_OPTION=rw_timeout
export FFMPEG_RTSP_TIMEOUT_OPTION=stimeout
export FFMPEG_RTSP_TIMEOUT_OPTION=
```

## 启动

```bash
cd python-client
python -m robot_media_client
```

示例：

```bash
ROBOT_ID='test111' \
ROBOT_CLIENT_ID='robot-media-client-test111' \
RTSP_CAMERA01_MAIN='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA01_SUB='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA02_MAIN='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA02_SUB='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA03_MAIN='rtsp://192.168.124.204:8554/camera03' \
RTSP_CAMERA03_SUB='rtsp://192.168.124.204:8554/camera03' \
MEDIA_SERVICE_URL='http://192.168.124.77:8088' \
RECORDING_DIRECTORY='./recordings' \
PUBLISHER_MODE='auto' \
python -m robot_media_client
```

## 文件上传

默认开启本地文件上传：

```text
RECORDING_UPLOAD_ENABLED=true
RECORDING_DIRECTORY=./recordings
MEDIA_SERVICE_URL=http://192.168.124.77:8088
```

客户端扫描 `RECORDING_DIRECTORY` 下的普通文件，按文件后缀识别 `VIDEO`、`IMAGE`、`LOG`、`CONFIG`、`MAP`、`DOCUMENT` 或 `OTHER`，再调用媒体服务通用文件接口：

```text
POST /api/media/files/multipart-uploads
POST /api/media/files/multipart-uploads/{uploadId}/part-urls
PUT uploadUrl
POST /api/media/files/multipart-uploads/{uploadId}/complete
GET /api/media/files/{fileId}/status
```

本地断点续传状态写入：

```text
RECORDING_MANIFEST_PATH=./recording-upload-manifest.json
```

## 对讲

对讲默认开启：

```text
INTERCOM_AUDIO_ENABLED=true
```

对讲使用 LiveKit Python SDK，并复用以下音频管线配置：

```text
GST_LAUNCH_PATH
AUDIO_CAPTURE_PIPELINE
AUDIO_PLAYBACK_PIPELINE
```

如果只希望运行视频能力，可设置：

```bash
export INTERCOM_AUDIO_ENABLED=false
```

### 机器人主动呼叫中心端

启动客户端时应确保 `MQTT_BROKER_URL` 与 Control Service 使用同一个 Broker，例如：

```bash
export MQTT_BROKER_URL='tcp://192.168.124.235:1884'
```

机器人本地按钮或业务模块可以调用 `RobotMQTTClient`：

```python
call_id = client.invite_intercom_call("camera01", "机器人请求人工对讲", 30)
# 振铃阶段由机器人取消
client.cancel_intercom_call(call_id, "用户取消呼叫")
# 或者中心端接听后，由机器人挂断
client.end_intercom_call(call_id)
```

可通过状态回调更新机器人屏幕或语音提示：

```python
client.set_intercom_call_state_handler(
    lambda state: print(state.call_id, state.status, state.message)
)
```

客户端只发起来电邀请；中心端接听后，仍由现有 `intercom/start` 启动 LiveKit 音频桥。

## Docker

```bash
cd python-client
docker build -t robot-media-client-python:dev .
docker run --rm \
  -e ROBOT_ID='test111' \
  -e ROBOT_CLIENT_ID='robot-media-client-test111' \
  -e RTSP_CAMERA01_MAIN='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA01_SUB='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA02_MAIN='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA02_SUB='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA03_MAIN='rtsp://192.168.124.204:8554/camera03' \
  -e RTSP_CAMERA03_SUB='rtsp://192.168.124.204:8554/camera03' \
  robot-media-client-python:dev
```

## 设备状态上报

客户端通过 `robot/{robotId}/media/client/status` 上报 `devices[]`。设备控制指令会更新本地可确认状态，并立即重新上报，例如：

- 音量、静音：`volume`、`volumePercent`、`muted`
- 发射器安全开关：`safetySwitchEnabled`
- 控制模式：`controlMode`
- 警示灯：`enabled`、`powerOn`、`mode`
- 云台自转：`autoRotateEnabled`、`panSpeed`
- 车灯：`front`、`rear`
