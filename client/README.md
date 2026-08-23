# Robot Media Client（Go）

## 1. 文档范围

`client/` 是机器人侧 Go 客户端。本 README 说明模块职责、代码结构、核心运行流程、配置和构建方式。

`client/` 是部署在机器人侧的 Go 云接入客户端，主要负责：

1. 启动后连接 MQTT，并周期性上报机器人与摄像头在线状态。
2. 接收实时视频 start/stop/switch 指令，探测 RTSP 后启动本地推流进程，将视频发布到 LiveKit。
3. 接收对讲 start/stop 指令，通过 LiveKit SDK 和本地 GStreamer 音频管线建立双向音频桥。
4. 后台扫描本地文件，按通用文件 multipart 协议续传到 Media Service/MinIO，并维护本地上传清单。
5. 消费多合一设备控制指令，通过 TCP `8519/12345` 和 HTTP `8222` 连接机器人局域网内的真实设备。

## 启动、构建与测试

```bash
cd client
go run -tags nolibopusfile ./cmd/robot-media-client
go test -tags nolibopusfile ./...
go build -tags nolibopusfile -o robot-media-client ./cmd/robot-media-client
```

完整文件上传调用示例见[任务视频上传接口对接指南](../docs/03-接口与协议/对接指南/任务视频上传接口对接指南.md)。

## 2. 顶层目录结构

```text
client/
  cmd/
    robot-media-client/
      main.go
    multi-function-test/
      main.go
  internal/
    config/
      config.go
    intercom/
      intercom.go
    model/
      model.go
    mqtt/
      client.go
    multifunction/
      client.go
      parser.go
      protocol.go
    publisher/
      publisher.go
    recordingupload/
      client.go
      manifest.go
      retention.go
      runner.go
      uploader.go
    rtsp/
      probe.go
  recordings/
  scripts/
    ffmpeg-livekit-publisher.sh
    install-gstreamer-publisher.sh
  Dockerfile
  go.mod
  go.sum
  recording-upload-manifest-001.json
  recording-upload-manifest-002.json
```

| 路径 | 职责 |
|---|---|
| `cmd/robot-media-client/main.go` | 程序入口，加载配置，初始化 RTSP 探测、视频 publisher、对讲 manager、录像上传 runner 和 MQTT client |
| `cmd/fixed-camera-gateway/main.go` | 固定摄像头 Gateway 入口，只订阅固定摄像头 MQTT 视频命令，查询管理端摄像头档案并推流到 LiveKit |
| `internal/config/config.go` | 从环境变量加载机器人、MQTT、RTSP、GStreamer、文件上传和本地缓存配置 |
| `internal/model/model.go` | MQTT 指令、状态消息、机器人在线消息、摄像头信息等 JSON 数据模型 |
| `internal/mqtt/client.go` | MQTT 连接、订阅、心跳、指令分发、状态发布 |
| `internal/fixedcamera/gateway.go` | 固定摄像头 MQTT 命令处理、管理端档案查询、RTSP 选流和状态上报 |
| `internal/rtsp/probe.go` | 使用 `ffprobe` 检查 RTSP 视频流是否可达 |
| `internal/publisher/publisher.go` | 按 `sessionId` 管理外部视频推流进程，支持默认 GStreamer publisher、FFmpeg fallback 或自定义命令 |
| `internal/intercom/intercom.go` | 使用 LiveKit SDK 管理对讲会话，桥接本地麦克风、扬声器与 LiveKit 音频 Track |
| `internal/multifunction/` | 多合一真实设备 TCP/HTTP 适配、灯光 CRC、状态流拆包和 Opus 帧输入输出 |
| `cmd/multi-function-test/main.go` | 不依赖 MQTT 的现场设备测试工具；危险动作必须显式增加 `-execute` |
| `internal/recordingupload/` | 本地文件发现、上传清单、断点续传、分片上传、完成上传和本地缓存清理 |
| `recordings/` | 默认本地文件扫描目录 |
| `scripts/` | 客户端部署或依赖安装脚本 |
| `Dockerfile` | 构建机器人侧客户端镜像 |

## 3. 启动入口

机器人客户端入口文件为 `cmd/robot-media-client/main.go`。

启动流程：

```text
main
  -> config.Load()
  -> 创建根 context，监听 SIGINT/SIGTERM
  -> 初始化 rtsp.Probe
  -> 初始化 publisher.ProcessPublisher
  -> 初始化 intercom.SDKManager
  -> 如果启用录像上传，后台启动 recordingupload.Runner
  -> 创建 mqtt.Client
  -> 循环执行 mqtt.Client.Run(ctx)，断线后 5 秒退避重连
```

进程退出时，根 `context` 会统一通知 MQTT、推流进程、对讲音频桥和录像上传 runner 收尾。

固定摄像头 Gateway 入口文件为 `cmd/fixed-camera-gateway/main.go`。该入口不启动机器人在线心跳、对讲、多合一设备控制和录像上传，只复用 RTSP 探测与视频 publisher，用于中心侧固定监控摄像头推流。

## 4. 配置模块

`internal/config/config.go` 定义 `Config`，所有配置从环境变量读取，并提供默认值。

### 4.1 机器人与 MQTT

| 字段 | 环境变量 | 说明 |
|---|---|---|
| `RobotID` | `ROBOT_ID` | 机器人 ID，默认 `test111` |
| `RobotName` | `ROBOT_NAME` | 机器人名称 |
| `Type` | `ROBOT_TYPE` | 机器人类型 |
| `Battery` | `ROBOT_BATTERY` | 电量百分比，会限制在 0 到 100 |
| `MQTTBroker` | `MQTT_BROKER_URL` | MQTT broker 地址 |
| `MQTTUsername` | `MQTT_USERNAME` | MQTT 用户名 |
| `MQTTPassword` | `MQTT_PASSWORD` | MQTT 密码 |
| `ClientID` | `ROBOT_CLIENT_ID` | MQTT clientId |
| `FixedCameraGatewayID` | `FIXED_CAMERA_GATEWAY_ID` | 固定摄像头 Gateway 订阅命令使用的实例 ID，默认 `default` |
| `HeartbeatInterval` | `HEARTBEAT_INTERVAL_MS` | 在线心跳间隔 |

### 4.1.1 固定摄像头 Gateway

| 字段 | 环境变量 | 说明 |
|---|---|---|
| `FixedCameraGatewayID` | `FIXED_CAMERA_GATEWAY_ID` | 与 Control Service 下发 topic 中的 `{gatewayId}` 一致 |
| `ManagementServiceURL` | `MANAGEMENT_SERVICE_URL` / `CENTER_MANAGE_BASE_URL` | 管理端服务地址，用于查询 `/api/v1/management/fixed-cameras` |
| `ManagementToken` | `MANAGEMENT_SERVICE_TOKEN` | 调管理端接口时附加的 Bearer token，可为空 |
| `ManagementInsecureTLS` | `MANAGEMENT_INSECURE_SKIP_VERIFY` | 内网自签 HTTPS 证书兼容开关，默认关闭 |
| `FixedCameraHeartbeat` | `FIXED_CAMERA_HEARTBEAT_INTERVAL_SECONDS` | Gateway 心跳周期，默认 10 秒 |
| `FixedCameraHealthProbe` | `FIXED_CAMERA_HEALTH_PROBE_INTERVAL_SECONDS` | 全量 RTSP 健康扫描周期，默认 60 秒 |
| `FixedCameraProbeWorkers` | `FIXED_CAMERA_HEALTH_PROBE_CONCURRENCY` | 健康探测最大并发，默认 4 |

### 4.2 摄像头与 RTSP

| 字段 | 环境变量 | 说明 |
|---|---|---|
| `Cameras` | `CAMERA_{CAMERA_ID}_NAME` / `CAMERA_{CAMERA_ID}_GROUP_TYPE` / `CAMERA_{CAMERA_ID}_QUALITY` / `RTSP_{CAMERA_ID}` / `RTSP_{CAMERA_ID}_SUB` / `RTSP_{CAMERA_ID}_MAIN` | 摄像头列表，默认按 `test111` 或 `SN006` 生成三路摄像头 |
| `RTSPVisibleSub` | `RTSP_VISIBLE_SUB` | 兼容旧配置的可见光低码流 RTSP |
| `RTSPVisibleMain` | `RTSP_VISIBLE_MAIN` | 可见光高清 RTSP |
| `RTSPThermalSub` | `RTSP_THERMAL_SUB` | 热成像低码流 RTSP |
| `RTSPThermalMain` | `RTSP_THERMAL_MAIN` | 热成像高清 RTSP |
| `FFprobePath` | `FFPROBE_PATH` | `ffprobe` 路径 |
| `ProbeTimeout` | `PROBE_TIMEOUT_MS` | RTSP 探测超时时间 |

默认摄像头 ID：`test111` 为 `camera01/camera02/camera03`，`SN006` 为 `camera04/camera05/camera06`。每路摄像头会按 ID 生成环境变量前缀，例如 `camera01` 对应 `RTSP_CAMERA01`、`RTSP_CAMERA01_SUB`、`RTSP_CAMERA01_MAIN`。

当前实际选流逻辑优先按 `Cameras` 中的 `DeviceID/CameraID` 查找 RTSP URL。`quality=main` 时优先使用 `RTSP_{CAMERA_ID}_MAIN`，`quality=sub/auto/空` 时优先使用 `RTSP_{CAMERA_ID}_SUB`；如果没有命中摄像头配置，再回退到兼容旧配置的 `RTSPVisibleSub`。

### 4.3 推流与对讲

| 字段 | 环境变量 | 说明 |
|---|---|---|
| `PublisherCmd` | `PUBLISHER_CMD` | 自定义推流命令；为空时使用默认 GStreamer publisher |
| `PublisherMode` | `PUBLISHER_MODE` | `auto` 默认先 GStreamer，并在失败或短时间崩溃后切 FFmpeg；也可设为 `gstreamer` 或 `ffmpeg` |
| `PublisherFFmpegFirstIDs` | `PUBLISHER_FFMPEG_FIRST_DEVICE_IDS` | `auto` 模式下直接优先走 FFmpeg 的设备 ID 覆盖列表，默认空 |
| `PublisherFallbackWatch` | `PUBLISHER_FALLBACK_WATCH_SECONDS` | `auto` 模式下观察 GStreamer 启动后短时间退出的窗口，默认 8 秒 |
| `PublisherGStreamerRetry` | `PUBLISHER_GSTREAMER_RETRY_SECONDS` | GStreamer 失败后使用 FFmpeg 的冷却时间，默认 60 秒；到期重试直推 |
| `FFmpegPublisherCmd` | `FFMPEG_PUBLISHER_CMD` | GStreamer publisher 启动失败或观察窗口内退出时的 FFmpeg fallback 命令 |
| `GStreamerPublisherPath` | `GSTREAMER_PUBLISHER_PATH` | 默认 `gstreamer-publisher` |
| `GStreamerPipeline` | `GSTREAMER_PIPELINE` | RTSP 到 LiveKit publisher 的媒体 pipeline |
| `GSTLaunchPath` | `GST_LAUNCH_PATH` | 默认 `gst-launch-1.0` |
| `AudioCapturePipeline` | `AUDIO_CAPTURE_PIPELINE` | 本地麦克风采集 pipeline |
| `AudioPlaybackPipeline` | `AUDIO_PLAYBACK_PIPELINE` | 本地扬声器播放 pipeline |

### 4.4 文件上传

| 字段 | 环境变量 | 说明 |
|---|---|---|
| `RecordingUploadEnabled` | `RECORDING_UPLOAD_ENABLED` | 是否启用文件上传能力 |
| `MediaServiceURL` | `MEDIA_SERVICE_URL` | Media Service 地址 |
| `RecordingDirectory` | `RECORDING_DIRECTORY` | 本地文件扫描目录 |
| `RecordingManifestPath` | `RECORDING_MANIFEST_PATH` | 本地上传 manifest 路径 |
| `RecordingDeviceID` | `RECORDING_DEVICE_ID` | 文件所属设备 ID |
| `UploadScanInterval` | `RECORDING_UPLOAD_SCAN_INTERVAL_MS` | 扫描间隔 |
| `UploadPartConcurrency` | `RECORDING_UPLOAD_PART_CONCURRENCY` | 单文件分片上传并发 |
| `UploadPartURLBatchSize` | `RECORDING_UPLOAD_PART_URL_BATCH_SIZE` | 单批获取上传 URL 数量 |
| `UploadFileConcurrency` | `RECORDING_UPLOAD_FILE_CONCURRENCY` | 多文件上传并发 |
| `LocalCacheMaxBytes` | `RECORDING_LOCAL_CACHE_MAX_BYTES` | 本地文件缓存上限 |
| `LocalMinFreeBytes` | `RECORDING_LOCAL_MIN_FREE_BYTES` | 本地磁盘最小剩余空间 |
| `LocalRetentionAfterReady` | `RECORDING_LOCAL_RETENTION_AFTER_READY_HOURS` | 文件 READY 后本地保留时长 |

### 4.5 多合一设备

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `MULTI_FUNCTION_ENABLED` | `false` | 是否启用真实设备连接 |
| `MULTI_FUNCTION_DEVICE_ID` | `broadcaster-001` | 必须与管理端注册及 MQTT target 一致 |
| `MULTI_FUNCTION_HOST` | `192.168.1.27` | 机器人局域网内设备 IP |
| `MULTI_FUNCTION_CONTROL_PORT` | `8519` | 控制、状态和 Opus TCP 长连接 |
| `MULTI_FUNCTION_TILT_PORT` | `12345` | 喊话器俯仰 TCP 端口 |
| `MULTI_FUNCTION_HTTP_PORT` | `8222` | 文件查询、上传和删除 HTTP 端口 |
| `MULTI_FUNCTION_DIAL_TIMEOUT_MS` | `3000` | 建连超时 |
| `MULTI_FUNCTION_WRITE_TIMEOUT_MS` | `3000` | 写入超时 |
| `MULTI_FUNCTION_HTTP_TIMEOUT_MS` | `5000` | HTTP 超时 |
| `MULTI_FUNCTION_KEEPALIVE_ENABLED` | `true` | 是否发送灯光协议保活 |
| `MULTI_FUNCTION_KEEPALIVE_INTERVAL_MS` | `2000` | 保活间隔 |

## 5. 数据模型

`internal/model/model.go` 定义 MQTT 交互模型。

| 类型 | 用途 |
|---|---|
| `StartCommand` | 实时视频启动或切换指令，包含 `sessionId`、`deviceId`、`channel`、`quality`、LiveKit URL/token 等 |
| `StopCommand` | 实时视频或对讲停止指令 |
| `IntercomStartCommand` | 对讲启动指令，包含 LiveKit room、机器人 token、音频发布/订阅开关和 `publishVideo` |
| `StatusMessage` | 实时视频状态上报，包含状态、Track SID、Track 名称、错误码 |
| `IntercomStatusMessage` | 对讲状态上报，包含机器人麦克风 Track 信息 |
| `OnlineMessage` | 媒体客户端在线/离线和周期心跳，携带摄像头清单与仍被消费的 `devices[]` 运行态；不携带本体业务状态 |
| `Camera` | 上报给后端的摄像头信息 |
| `Device` | 上报给后端的本体和上装设备信息，包含 `actions`、`status`、`controlProfile` |

## 6. MQTT 模块

`internal/mqtt/client.go` 是机器人实时控制链路的中心。固定摄像头 Gateway 使用 `internal/fixedcamera/gateway.go`，不订阅机器人控制或对讲 topic。

### 6.1 订阅 Topic

所有 topic 都按 `robotId` 分区。

| Topic | 处理函数 | 说明 |
|---|---|---|
| `robot/{robotId}/media/video/start` | `handleStart` | 启动一路实时视频 |
| `robot/{robotId}/media/video/stop` | `handleStop` | 停止指定 `sessionId` 的视频推流 |
| `robot/{robotId}/media/video/switch-channel` | `handleStart` | 切换通道，本地按重新 start 处理 |
| `robot/{robotId}/media/video/intercom/start` | `handleIntercomStart` | 启动对讲 |
| `robot/{robotId}/media/video/intercom/stop` | `handleIntercomStop` | 停止对讲 |
| `robot/{robotId}/control/#` | `handleControlCommand` | 接收本体、云台、扬声器、发射器、警示灯、车灯和多合一设备等装备控制命令 |

固定摄像头 Gateway 订阅：

| Topic | 处理函数 | 说明 |
|---|---|---|
| `gateway/fixed-camera/{gatewayId}/video/start` | `handleStart` | 启动固定摄像头实时视频 |
| `gateway/fixed-camera/{gatewayId}/video/stop` | `handleStop` | 停止指定 `sessionId` 的固定摄像头推流 |
| `gateway/fixed-camera/{gatewayId}/video/restart` | `handleStart` | 重启固定摄像头推流 |

### 6.2 发布 Topic

| Topic | 消息类型 | 说明 |
|---|---|---|
| `robot/{robotId}/media/client/status` | `OnlineMessage` | 媒体客户端上线、下线和周期心跳；机器人 ID 以 topic 为准，仅携带摄像头清单和 `devices[].deviceId/deviceType/onlineStatus/status` 运行态 |
| `robot/{robotId}/media/video/status` | `StatusMessage` | 实时视频状态 |
| `robot/{robotId}/media/video/intercom/status` | `IntercomStatusMessage` | 对讲状态 |
| `gateway/fixed-camera/{gatewayId}/video/status` | `StatusMessage` | 固定摄像头实时视频状态 |
| `gateway/fixed-camera/{gatewayId}/status` | `FixedCameraGatewayStatus` | Gateway 上线、离线和周期心跳 |
| `gateway/fixed-camera/{gatewayId}/camera/{cameraId}/status` | `FixedCameraHealthStatus` | 固定摄像头最近 RTSP 健康 |

Gateway 连接后每 10 秒上报心跳，并设置异常断连 `OFFLINE` 遗嘱。固定摄像头列表按每页
500 条完整读取，每 60 秒以默认最多 4 个并发执行 RTSP 探测；上一轮未结束时跳过重叠轮次。
两类健康 Topic 均不携带 RTSP URL 或 Token，也不替代按 `sessionId` 上报的会话状态。

### 6.3 指令处理流程

实时视频 start/switch：

```text
收到 MQTT 指令
  -> 反序列化 StartCommand
  -> 按 sessionId + commandId 去重
  -> 解析 RTSP URL
  -> ffprobe 探测 RTSP
  -> 上报 publishing
  -> publisher.Start 启动推流进程
  -> 上报 streaming 或 failed
```

实时视频 stop：

```text
收到 StopCommand
  -> publisher.Stop(sessionId)
  -> 上报 stopped
```

装备控制命令：

```text
收到 robot/{robotId}/control/# 指令
  -> 反序列化 ControlCommand
  -> 多合一 action 调用真实 TCP/HTTP 适配器
  -> 解析设备状态并更新本地 deviceState
  -> 立即通过 media/client/status 上报 devices[].status
```

当前客户端会回写的设备状态包括：扬声器音量/静音 `volume`、`volumePercent`、`muted`，发射器连接状态 `connected`、安全开关 `safetySwitchEnabled`、弹筒数量 `tubeCount`、弹筒状态 `tubes[]`，警示灯 `enabled`，云台自转 `autoRotateEnabled`、`panSpeed`，车灯 `front`、`rear`，以及多合一设备音量和 `audioSession`。模拟客户端收到 `LAUNCHER/fire` 后会把对应弹筒从 `LOADED` 改为 `EMPTY` 并通过下一次 `media/client/status` 回写；真实客户端应以设备查询结果为准。车灯命令以平台通用 `params.front/rear.mode/brightness` 为准；客户端兼容旧 ROS 结构 `params.msg.front_mode/rear_mode`，但最终统一回写为 `devices[].status.front/rear.mode/brightness`。控制模式不再由媒体心跳上报，以边缘状态 topic 为准。

多合一客户端消费 `robot/{robotId}/control/multi-function/command`，并调用 TCP `8519/12345`、HTTP `8222`。设备主动上报的音量和温度、HTTP 查询的文件列表以及客户端媒体会话状态会写入统一状态；`audioPlayback` 记录客户端已成功写入的播放命令和文件名，不等同于设备查询状态；照明和警报不伪装为设备真实状态。

`upload_audio_file` 携带 `fileId`、`fileName`、`fileSize`、`orgId` 和 `transferId`。
客户端通过 `MEDIA_SERVICE_URL` 请求 Media Service `8088` 下载完整文件并校验大小，
再以原文件名调用设备 `POST /upload-file`；完成后重新查询文件列表。MQTT 不承载文件内容或分片。

现场先在能访问设备网段的工控机运行只读测试：

```bash
go build -o multi-function-test ./cmd/multi-function-test
./multi-function-test -host 192.168.1.27 -action status
./multi-function-test -host 192.168.1.27 -action list_audio_files
```

改变设备状态的动作必须显式增加 `-execute`：

```bash
./multi-function-test -host 192.168.1.27 -action set_volume \
  -params '{"volumePercent":30}' -execute
./multi-function-test -host 192.168.1.27 -action light.set \
  -params '{"enabled":true,"brightness":20}' -execute
```

实时喊话/收音的 `[10]+Opus` 写入与 `[40]+Opus` 读取接口已经提供，但多合一 LiveKit Track、重采样和 Opus 编解码尚未接通，不能仅凭离散控制测试视为大屏实时音频验收通过。

连接丢失时：

```text
MQTT ConnectionLost
  -> publisher.StopAll()
  -> intercom.StopAll()
```

正常退出时：

```text
context 取消
  -> 停止全部视频推流
  -> 停止全部对讲
  -> 上报 offline
  -> MQTT Disconnect
```

## 7. RTSP 探测模块

`internal/rtsp/probe.go` 封装 `ffprobe`。

探测命令主要参数：

```text
ffprobe -v error
  -rtsp_transport tcp
  -select_streams v:0
  -show_entries stream=codec_name,width,height
  -of json
  {rtspUrl}
```

该模块只返回成功或错误，不解析探测结果。它的作用是让客户端在启动推流前快速判断 RTSP 是否可达，并在失败时立即向后端上报 `RTSP_PROBE_FAILED`。

## 8. 视频推流模块

`internal/publisher/publisher.go` 通过 `Publisher` 接口抽象视频发布能力。

```go
type Publisher interface {
    Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error)
    Stop(sessionID string) error
    StopAll() error
}
```

当前实现为 `ProcessPublisher`，内部用：

```text
map[sessionId]*exec.Cmd
```

管理每个视频会话对应的外部进程。

### 8.1 默认 GStreamer publisher

当 `PUBLISHER_CMD` 为空时，客户端执行：

```text
gstreamer-publisher --url {livekitUrl} --token {publisherToken} -- {pipeline}
```

其中 `pipeline` 来自 `GSTREAMER_PIPELINE`，默认包含：

```text
rtspsrc location={rtsp} protocols=tcp latency=100
  drop-on-latency=true
  ! queue max-size-buffers=0 max-size-bytes=0 max-size-time=200000000 leaky=downstream
  ! rtph264depay
  ! h264parse config-interval=1
```

`PUBLISHER_MODE=auto` 时，客户端先启动默认 GStreamer publisher；如果启动失败，并且 `FFMPEG_PUBLISHER_CMD` 非空，客户端会记录 `publisher fallback ffmpeg` 并执行 FFmpeg fallback 命令。若 GStreamer 已启动但在 `PUBLISHER_FALLBACK_WATCH_SECONDS` 窗口内退出，则记录 `publisher auto fallback ffmpeg` 并用 FFmpeg fallback 重启同一个 session。失败的 RTSP URL 在 `PUBLISHER_GSTREAMER_RETRY_SECONDS` 冷却期内优先走 FFmpeg，到期后重新尝试 GStreamer 直推；`PUBLISHER_FFMPEG_FIRST_DEVICE_IDS` 仍作为永久人工覆盖项。默认 fallback 脚本为：

```text
./scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}
```

如果 `PUBLISHER_CMD` 非空，客户端优先执行自定义命令，不再进入默认 GStreamer 和 FFmpeg fallback 流程。

### 8.2 自定义 publisher 命令

当 `PUBLISHER_CMD` 非空时，客户端按空格拆分命令，并替换以下占位符：

| 占位符 | 含义 |
|---|---|
| `{rtsp}` | RTSP URL |
| `{livekitUrl}` | LiveKit URL |
| `{token}` | LiveKit publisher token |
| `{room}` | LiveKit room name |
| `{track}` | Track 名称 |

### 8.3 启动判定

外部进程启动后，客户端等待 2 秒：

1. 如果进程在 2 秒内退出，认为启动失败。
2. 如果进程运行超过 2 秒，认为启动成功，返回伪造的 `TR_{sessionId}` 和 Track 名称。

Track 名称规则：

```text
video.{channel}.{quality}
```

## 9. 对讲模块

`internal/intercom/intercom.go` 通过 LiveKit SDK 建立机器人端对讲。

核心资源按 `sessionId` 管理：

```text
session
  cancel context
  LiveKit Room
  robot microphone PCMLocalTrack
  GStreamer capture process
  GStreamer playback process
  playback stdin writer
  subscribed remote PCM tracks
```

### 9.1 机器人麦克风上行

```text
本地麦克风
  -> AUDIO_CAPTURE_PIPELINE
  -> gst-launch-1.0 stdout 输出 48k 单声道 S16LE PCM
  -> copyCapturePCM 以 20ms frame 写入 PCMLocalTrack
  -> LiveKit Track: audio.robot.mic
```

每帧大小：

```text
48000Hz * 20ms = 960 samples
```

### 9.2 操作员音频下行

```text
LiveKit remote Track: audio.operator.mic
  -> PCMRemoteTrack 转换为 48k 单声道 PCM
  -> pcmPlaybackWriter 写入 GStreamer playback stdin
  -> AUDIO_PLAYBACK_PIPELINE
  -> 本地扬声器播放
```

### 9.3 生命周期

启动对讲时，同一 `sessionId` 会先停止旧 session，再创建新的播放管线、连接 LiveKit、发布机器人麦克风 Track、启动采集管线。

停止对讲时，会：

1. 取消 context。
2. 关闭远端 PCM Track。
3. 关闭本地发布 Track。
4. 关闭 playback stdin。
5. 断开 LiveKit Room。

## 10. 文件上传模块

`internal/recordingupload/` 是独立于 MQTT 的后台轮询任务。

### 10.1 文件职责

| 文件 | 职责 |
|---|---|
| `runner.go` | 扫描本地文件、调度多文件上传、驱动单个任务状态流转 |
| `client.go` | Media Service HTTP API 客户端 |
| `uploader.go` | 按缺失 part 进行分片上传 |
| `manifest.go` | 本地上传清单读写，支持进程重启后续传 |
| `retention.go` | READY 后本地缓存清理和磁盘空间控制 |

### 10.2 任务发现

`Runner.discover()` 扫描 `RecordingDirectory` 下的普通文件，跳过目录、隐藏文件、读取失败或大小为 0 的文件。客户端按文件后缀映射 `VIDEO`、`AUDIO`、`IMAGE`、`LOG`、`CONFIG`、`MAP`、`DOCUMENT`、`OTHER`。

本地幂等键 `sourceFileId` 由以下信息组成：

```text
{recordingDeviceId}/{fileName}/{fileSize}/{modTimeUnix}
```

同名文件如果被覆盖，只要大小或修改时间变化，就会被识别为新的源文件。

### 10.3 上传状态流

```text
PENDING
  -> createOrResume
  -> UPLOADING
  -> uploadMissingParts
  -> complete
  -> PROCESSING 或 READY
  -> 视频 status polling
  -> READY
  -> LOCAL_DELETED
```

说明：

1. `createOrResume` 会向 Media Service 注册或恢复上传会话。
2. 服务端返回已上传 part 后，客户端只上传缺失部分。
3. 分片 URL 由 `/part-urls` 批量获取。
4. 所有缺失 part 上传完成后调用 `/complete`。
5. `complete` 后非视频通常直接进入 `READY`；视频进入 `PROCESSING`，客户端轮询文件状态，等待服务端生成 HLS。

### 10.4 HTTP API

`recordingupload.Client` 调用的 Media Service API：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/media/files/multipart-uploads` | 创建或恢复上传 |
| `POST` | `/api/media/files/multipart-uploads/{uploadId}/part-urls` | 批量获取 part 上传 URL |
| `PUT` | `uploadUrl` | 将单个 part 直传到对象存储签名 URL |
| `POST` | `/api/media/files/multipart-uploads/{uploadId}/complete` | 通知服务端完成 multipart |
| `GET` | `/api/media/files/{fileId}/status` | 查询文件处理状态 |

所有 JSON API 请求都会带：

```http
X-Robot-Id: {robotId}
```

### 10.5 本地 manifest

manifest 是本地断点续传索引，默认路径为：

```text
./recording-upload-manifest.json
```

核心结构：

```text
Manifest
  path
  tasks[sourceFileId]Task

Task
  sourceFileId
  filePath
  fileSize
  createdAt
  fileId
  uploadId
  status
  error
  updatedAt
```

每次任务状态变化都会写回 manifest，文件权限为 `0600`。

### 10.6 本地缓存清理

`retention.go` 只清理已经 `READY` 的本地文件。

触发条件：

1. 本地缓存总大小超过 `LocalCacheMaxBytes`。
2. 录像目录所在磁盘剩余空间小于 `LocalMinFreeBytes`。
3. 文件已 READY 且超过 `LocalRetentionAfterReady`。

删除后任务状态改为 `LOCAL_DELETED`。

## 11. Docker 构建

`client/Dockerfile` 分两阶段构建：

1. `golang:1.24-alpine` 编译 Go 二进制，安装 `build-base`、`pkgconf`、`opus-dev`。
2. `alpine:3.20` 运行，安装 `bash`、`ffmpeg`、`ca-certificates`、`opus`、`gstreamer`、`gst-plugins-base`、`gst-plugins-good`、`gst-plugins-bad`。

构建命令：

```text
go build -tags nolibopusfile -o /out/robot-media-client ./cmd/robot-media-client
```

镜像入口：

```text
robot-media-client
```

## 12. 主要依赖

| 依赖 | 用途 |
|---|---|
| `github.com/eclipse/paho.mqtt.golang` | MQTT 客户端 |
| `github.com/livekit/server-sdk-go/v2` | LiveKit Room、Track 发布与订阅 |
| `github.com/livekit/media-sdk` | PCM 音频 sample 处理 |
| `github.com/pion/webrtc/v4` | WebRTC track 类型判断 |
| `ffprobe` | RTSP 探测 |
| `gstreamer-publisher` | 默认 RTSP 到 LiveKit 视频发布 |
| `ffmpeg` | 默认 GStreamer publisher 启动失败时的 fallback 视频发布 |
| `gst-launch-1.0` | 本地音频采集与播放 |

## 13. 模块关系总览

```mermaid
flowchart TD
    Main["cmd/robot-media-client/main.go"] --> Config["internal/config"]
    Main --> Probe["internal/rtsp"]
    Main --> Publisher["internal/publisher"]
    Main --> Intercom["internal/intercom"]
    Main --> MQTT["internal/mqtt"]
    Main --> UploadRunner["internal/recordingupload"]

    MQTT --> Model["internal/model"]
    MQTT --> Probe
    MQTT --> Publisher
    MQTT --> Intercom

    Publisher -->|"exec"| GstPublisher["gstreamer-publisher or custom command"]
    Probe -->|"exec"| FFprobe["ffprobe"]

    Intercom -->|"LiveKit SDK"| LiveKit["LiveKit"]
    Intercom -->|"exec"| GstAudio["gst-launch-1.0 audio pipelines"]

    UploadRunner --> UploadClient["recordingupload/client.go"]
    UploadRunner --> Manifest["recordingupload/manifest.go"]
    UploadRunner --> Retention["recordingupload/retention.go"]
    UploadClient --> MediaService["Media Service API"]
    UploadClient --> ObjectStore["MinIO signed part URLs"]
```

## 14. 一次完整实时视频链路

```text
Control/Media Service 创建 LiveKit Room 和 publisher token
  -> 后端通过 MQTT 下发 robot/{robotId}/media/video/start
  -> Go Client 收到 StartCommand
  -> 按 deviceId 找到本地 RTSP URL
  -> ffprobe 探测 RTSP
  -> 上报 publishing
  -> 启动 gstreamer-publisher
  -> RTSP 摄像头视频发布到 LiveKit
  -> 上报 streaming + track 信息
  -> 前端使用 viewer token 订阅 LiveKit Track
```

## 15. 一次完整文件上传链路

```text
机器人本地生成文件
  -> recordingupload.Runner 扫描到文件
  -> 写入 manifest，状态 PENDING
  -> 调 Media Service createOrResume
  -> 获取缺失 part 和上传 URL
  -> 并发 PUT part 到签名 URL
  -> 调 complete
  -> 非视频进入 READY，视频进入 PROCESSING
  -> 视频轮询服务端状态
  -> 服务端 HLS 处理完成后变为 READY
  -> 满足本地保留策略后删除本地文件，状态 LOCAL_DELETED
```
