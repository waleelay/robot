# Fixed Camera Gateway

固定摄像头 Gateway 部署在可访问现场 RTSP 摄像头的网络中。它只负责固定摄像头目录、RTSP 健康探测，以及把 RTSP 流发布到 LiveKit；不包含机器人状态、控制、对讲、录像上传或机器人模拟逻辑。

机器人演示与模拟统一由 [python-client](../python-client/) 承担。

## 目录

```text
fixed-camera-gateway/
├── cmd/fixed-camera-gateway/  # 进程入口与健康指标
├── internal/fixedcamera/      # MQTT 目录、指令、状态和健康探测
├── internal/publisher/        # GStreamer 优先、FFmpeg 回退的推流进程管理
├── internal/rtsp/             # RTSP 探测
└── scripts/                   # 推流与 gstreamer-publisher 安装脚本
```

## 构建与运行

```bash
cd fixed-camera-gateway
go test ./...
go build -o fixed-camera-gateway ./cmd/fixed-camera-gateway

env \
  FIXED_CAMERA_GATEWAY_ID=default \
  MQTT_BROKER_URL=tcp://127.0.0.1:1883 \
  ./fixed-camera-gateway
```

默认使用 `gstreamer-publisher` 推流；同一路 RTSP 的 GStreamer 推流异常后，在冷却周期内自动使用 FFmpeg 回退。生产镜像已包含两者。宿主机运行时可执行 `sh scripts/install-gstreamer-publisher.sh` 安装前者。

视频会话启动不再额外执行 RTSP 预探测，不同视频流可并行启动；后台健康检查对相同 RTSP 每轮只
建立一次探测连接。Gateway 上报 `streaming` 仅表示 Publisher 进程已启动，真实 LiveKit 视频 Track
由 Media Service 统一确认。

## 必要配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `FIXED_CAMERA_GATEWAY_ID` | `default` | Gateway 实例标识，必须与控制端下发 topic 一致 |
| `FIXED_CAMERA_CLIENT_ID` | `fixed-camera-gateway` | MQTT 客户端标识；默认运行时追加 Gateway ID |
| `MQTT_BROKER_URL` | `tcp://127.0.0.1:1883` | MQTT Broker 地址 |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | 空 | MQTT 认证信息 |
| `FIXED_CAMERA_HTTP_ADDR` | `:9091` | 内部健康与指标监听地址 |
| `PUBLISHER_MODE` | `auto` | `auto`、`gstreamer` 或 `ffmpeg`；默认 GStreamer 管道会重建 H264 时间戳，启动失败时回退 FFmpeg |

完整协议见 [固定监控摄像头实时视频设计说明书](../docs/02-设计/实时视频/固定监控摄像头实时视频设计说明书.md)。

## MQTT 主题

| 方向 | 主题 |
| --- | --- |
| 控制端到 Gateway | `gateway/fixed-camera/{gatewayId}/catalog/sync` |
| 控制端到 Gateway | `gateway/fixed-camera/{gatewayId}/video/start` |
| 控制端到 Gateway | `gateway/fixed-camera/{gatewayId}/video/stop` |
| 控制端到 Gateway | `gateway/fixed-camera/{gatewayId}/video/restart` |
| Gateway 到控制端 | `gateway/fixed-camera/{gatewayId}/status` |
| Gateway 到控制端 | `gateway/fixed-camera/{gatewayId}/camera/{cameraId}/status` |
| Gateway 到控制端 | `gateway/fixed-camera/{gatewayId}/video/status` |

HTTP 仅提供内部 `/health` 和 `/metrics`，业务命令不通过 HTTP 下发。
