# 统一边缘媒体 Gateway

本模块仅构建 media-gateway。固定摄像头与无人机视频共用 RTSP 协议适配、MQTT、LiveKit 发布、健康与进程治理。
当前 Java 已完成固定摄像头统一编排；平台无人机权限与播放接口、其他协议和飞控适配仍待实现。
Go 模块路径、Compose 服务键、镜像与离线包名称均统一为 `media-gateway`，不保留第二套固定摄像头服务入口。

生产固定摄像头配置来自 BFF 授权目录：Control 合并短租约后向统一 Gateway 下发 sources[]。
没有有效租约不能启动或探测，租约撤销或过期后停止对应会话，不能回退本地固定源配置。
探测结束后及真正发布前重新确认授权与输入；输入已变化时返回 `SOURCE_INPUT_CHANGED`，不复用旧探测结果。
租约失效的终止状态先保存再清理，发送失败保留，重连后补报；连接中断不能覆盖已经保存的撤权原因。

构建入口为 `cmd/media-gateway`，运行核心为 `internal/gateway`，源注册与目录租约位于 `internal/source`。
业务命令仅通过 MQTT；HTTP 仅提供只读 /health 和 /metrics。

## 目录与依赖边界

```text
media-gateway/
  cmd/media-gateway/         # 唯一可执行入口
  internal/app/             # 组装配置、具体协议适配器、运行核心与观测
  internal/config/          # 环境变量与配置校验
  internal/model/           # MQTT 命令和状态载荷
  internal/media/           # Input、StreamInfo、Adapter 与安全错误契约
  internal/source/          # 业务源映射、授权租约与协议注册表
  internal/protocol/rtsp/   # RTSP 地址校验、ffprobe 探测与编码识别
  internal/gateway/         # MQTT、调度、状态、健康探测与终止水位
  internal/publisher/       # LiveKit 发布、转码选择与进程生命周期
  scripts/                  # RTSP 发布后端与构建辅助脚本
```

`source`、`gateway` 和 `publisher` 使用 `media.Input/StreamInfo`，不导入具体 RTSP 包；
`app` 负责注册 `protocol/rtsp.Adapter`。`Input.Address` 由输入协议解释，编码来自实际探测，
不写入业务命令。租约清理查当前源配置，不假定协议是 RTSP。

当前 Publisher 仍只实现 RTSP 后端。新增协议必须同时补齐输入适配器、监听或会话协商、
LiveKit 发布后端和资源释放测试；仅注册适配器不代表可播放。未实现的发布协议会在变更既有进程前被拒绝。

## 统一媒体入口

构建 `cmd/media-gateway`，使用同一 Publisher 与运行核心。首批仅实现 RTSP 协议，支持
`FIXED_CAMERA/DRONE_CAMERA`，H.264 优先 GStreamer，H.265/hevc 直接使用现有 FFmpeg 脚本转为 H.264。
MQTT 不接受命令地址覆盖。固定源地址来自有效授权目录，可选本地文件提供无人机输入。未配置源、协议不匹配、retained 命令、Token
过期或同一 session 跨源复用都被拒绝，未校验的命令不会污染已有会话状态。

```bash
go build -o /tmp/media-gateway ./cmd/media-gateway
env MEDIA_GATEWAY_ID=site-a \
    MEDIA_GATEWAY_SOURCES_FILE=/etc/media-gateway/sources.json \
    MQTT_BROKER_URL=tcp://<broker-host>:1883 \
    /tmp/media-gateway
```

在仓库根目录执行 `sh scripts/test-media-gateway-config.sh` 可验证两套 Compose 的服务身份、默认模板
以及 Control/Gateway 的非默认实例配置（需要 Node.js 与 Docker Compose CLI，不会启动容器）。
执行 `sh scripts/test-media-gateway-contract.sh` 可验证 Java 实际 MQTT 序列化与 Go 消费契约。

`<broker-host>` 必须替换为实际受控 MQTT 主机。可选的本地无人机 RTSP 源文件示例（尚未接入 Java 无人机观看权限）：

```json
{
  "sources": [
    {
      "sourceType": "DRONE_CAMERA",
      "sourceId": "drone-001",
      "deviceId": "camera01",
      "sourceProtocol": "RTSP",
      "mainStreamUrl": "rtsp://ground-station.example:8554/drone-001"
    }
  ]
}
```

示例域名不是默认设备地址。本地文件不能含未知字段、重复源或未实现协议，超过 1 MiB 时拒绝启动。固定摄像头仍必须有目录租约。
`main/sub/auto` 选择对应源，缺失时回退另一条已配置码流；`auto` 优先 sub。本地配置变更通过重启实例生效，平台目录通过租约快照动态更新。
源映射键为 `sourceType + sourceId + deviceId`；Publisher 同时按 Room、摄像头、通道和清晰度隔离。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `MEDIA_GATEWAY_ID` | `default` | MQTT Topic 所属实例 |
| `MEDIA_GATEWAY_CLIENT_ID` | `media-gateway-{gatewayId}` | 实例唯一 MQTT Client ID |
| `MEDIA_GATEWAY_SOURCES_FILE` | 无 | 可选的受控本地 JSON 文件；固定源无需文件，等待授权目录 |
| `MEDIA_GATEWAY_HTTP_ADDR` | `127.0.0.1:9092` | 只读健康与指标监听 |
| `MEDIA_GATEWAY_HEARTBEAT_INTERVAL_SECONDS` | `10` | 网关心跳周期 |
| `MEDIA_GATEWAY_HEALTH_PROBE_INTERVAL_SECONDS` | `60` | 视频源健康周期 |
| `MEDIA_GATEWAY_HEALTH_PROBE_CONCURRENCY` | `4` | 最大并行探测数 |

MQTT、FFmpeg、GStreamer、探测超时和进程清理配置沿用本模块原有参数。统一入口不会回退固定摄像头的身份
或 HTTP 配置。`/health` 仅在 MQTT 已连接且主题订阅完成时返回 200，否则返回 503；`/metrics` 使用
`media_gateway_publisher_*` 指标。

统一 Topic：

```text
gateway/media/{gatewayId}/catalog/sync
gateway/media/{gatewayId}/video/start
gateway/media/{gatewayId}/video/stop
gateway/media/{gatewayId}/video/restart
gateway/media/{gatewayId}/video/status
gateway/media/{gatewayId}/status
gateway/media/{gatewayId}/source/{sourceType}/{sourceId}/{deviceId}/status
```

命令使用已有 `commandId/sessionId/sourceType/sourceId/deviceId/channel/quality/roomName/livekitUrl/
publisherToken/expiresAt`，新增必填 `sourceProtocol=RTSP`。首批仅允许 `channel=visible`。stop 必须携带
`sessionId/sourceType/sourceId/deviceId`；会话身份在第一次命令绑定后不可跨源修改。状态包含源类型、源 ID、
摄像头 ID；`streaming` 只表示进程已启动，`trackSid` 不伪造，仍须由 Media 核对真实 Track。

当前统一入口的原因码如下；飞控错误由独立适配器定义，不能混用视频源健康：

| 原因码 | 含义 |
| --- | --- |
| `MEDIA_COMMAND_INVALID` | 命令不完整、身份非法、Token 过期或携带未授权地址 |
| `MEDIA_SESSION_SOURCE_MISMATCH` | 同一 session 试图绑定另一业务源或摄像头 |
| `SOURCE_NOT_CONFIGURED` | 本地源映射缺失 |
| `SOURCE_LEASE_MISSING` | 固定源租约缺失、撤销或过期 |
| `SOURCE_PROTOCOL_MISMATCH` | 命令协议与受控源协议不一致 |
| `SOURCE_INPUT_CHANGED` | 探测后输入已变更，须重新探测与启动 |
| `SOURCE_PROTOCOL_UNSUPPORTED` | 输入适配器或发布后端尚未实现该协议 |
| `SOURCE_QUALITY_UNSUPPORTED` | 不支持的清晰度 |
| `SOURCE_URL_INVALID` | RTSP 地址格式无效 |
| `SOURCE_PROBE_FAILED` | RTSP 探测失败 |
| `SOURCE_VIDEO_EMPTY` | 未发现视频轨道 |
| `SOURCE_CODEC_UNSUPPORTED` | 非 H.264/H.265 编码 |
| `PUBLISH_FAILED` / `PUBLISH_PROCESS_EXITED` | 发布失败或发布进程异常退出 |

格式/身份无效和 retained 命令只记录安全拒绝日志，不用未校验 session 更新平台会话。已接受命令的
探测/发布失败通过 `video/status` 上报。输入协议注册表拒绝所有尚未实现协议。

镜像仅包含 media-gateway，入口及 Compose 已切换。打包会检查 robot.media-gateway.protocol=edge-media-v1，拒绝旧预构建镜像。容器中的 127.0.0.1 指向容器自身；RTSP 地址必须在目标网络内可达。

Control/Media 已使用统一编排和 EDGE_MEDIA_GATEWAY 枚举，BFF 的固定摄像头授权 HTTP 保持不变。
RTMP、GB28181、RTP/UDP、MPEG-TS、SRT 仅为后续适配方向，未实现协议在配置阶段即被拒绝。

## 升级边界

- 源目录、Go module/import、二进制、Compose service/profile/container 和镜像名统一为 `media-gateway`；离线包为 `media-gateway-image.tar.gz`。
- 旧 `fixed-camera-gateway` 实例不会因服务键更名而自动停止。上线时须先在目标节点确认并停止旧实例，再启动新实例，防止重复 MQTT 身份或双重推流；本轮未执行这些操作。
- 自定义 `PUBLISHER_CMD`、`FFMPEG_PUBLISHER_CMD` 和 `GSTREAMER_PIPELINE` 的地址占位符由 `{rtsp}` 改为 `{input}`；旧占位符会在启动前明确报错。其他占位符不变，当前默认后端仍使用 RTSP。
- MQTT `mainStreamUrl/subStreamUrl` 字段、源身份和 Topic 保持上批契约；`rtspUrl` 只用于检测并拒绝旧命令的地址覆盖，不参与发布。
- Control 与 Gateway 都使用 `MEDIA_GATEWAY_ID`，非默认实例也必须一致。Control 的固定摄像头授权、健康超时和发布切换参数仍使用其现有 `FIXED_CAMERA_*` 名称，不因服务目录更名而改成另一套变量。
- 上线还须审核并执行 [发布模式迁移脚本](../deploy/database/20261010-migrate-edge-media-gateway.sql)，协同更新管理端 `targetMode` 调用方并验证真实 MQTT/LiveKit 链路。SQL 仅提供脚本，本轮未执行数据库迁移、镜像构建或生产部署。
