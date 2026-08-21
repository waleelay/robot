# Media Service

`backend/` 是 Java 17 + Spring Boot 3 媒体服务，默认端口 `8088`。它负责视频会话、LiveKit、通用文件和 OpenTTS 接入，不负责机器人 MQTT、设备档案或控制会话。

接口权威定义见 [媒体服务接口文档](../docs/03-接口与协议/媒体服务/媒体服务接口文档.md)；实时视频状态机和命令载荷见 [实时视频接口与协议文档](../docs/03-接口与协议/实时视频/实时视频接口与协议文档.md)。

## 1. 启动、构建与测试

```bash
cd backend
mvn spring-boot:run
mvn test
mvn -q -DskipTests package
```

运行依赖 MySQL、LiveKit 和 MinIO；视频文件 HLS 处理还需要 `ffmpeg/ffprobe`，TTS 需要可访问的 OpenTTS HTTP 服务。Redis 和 Elasticsearch 依赖仍保留，但不是当前业务主链路的权威存储。

## 2. 代码结构

```text
src/main/java/com/robot/mediaserver/
├── auth/      可信用户 Header 解析
├── config/    配置属性、时间序列化、MVC 与 WebSocket 配置
├── video/     视频会话、viewer、Track、对讲、录像及状态机
├── livekit/   Room、Token 和 Egress SDK 封装
├── file/      文件上传、MinIO、HLS、播放和保留期清理
├── tts/       OpenTTS 调用、缓存和音频广播
└── ws/        `/ws/media` 连接与事件/二进制广播
```

### `video/`

- `VideoSessionController`：提供 `/internal/media/video-sessions/**`，供 Control 调用。
- `VideoSessionService`：创建/复用 Room、签发 Token、维护 viewer 和对讲占用、生成 start/stop 命令并处理客户端状态。
- `MediaTrackService`：维护 `MediaTrack` 发布记录。
- `VideoSessionTimeoutScheduler`：处理发布超时；`ViewerStartupCleaner` 在启动时关闭遗留 viewer。
- 主要实体：`VideoSession`、`MediaSessionViewer`、`MediaTrack`。

会话复用键为 `sourceType + sourceId + deviceId + channel + quality`，只复用 `ROOM_READY`、`STREAMING`、`IDLE_WAIT`。固定摄像头用 `sourceType=FIXED_CAMERA`、`sourceId=cameraId`；`robotId` 当前仍传 `cameraId` 仅为兼容非空约束。

### `file/`

- `FileController` 同时注册 `/api/media/files/**` 和 `/internal/media/files/**`。
- `FileService` 处理简单上传、multipart、列表、扩展绑定、删除、下载和播放。
- `FileObjectStorageService` 封装 MinIO/S3 操作。
- `FileHlsProcessingService` 使用 ffprobe/ffmpeg 生成 fMP4 HLS。
- 三个 Scheduler 分别处理上传过期、HLS 任务和文件保留期。
- 主要实体：`MediaFile`、`MediaFileUpload`、`MediaVideoFile`。

`/api/media/files` 主要用于机器人直传，`/internal/media/files` 供 Control 代理。可信网段过滤器按配置启用，只检查 TCP 对端地址，不信任 `X-Forwarded-For`。

### `tts/`

`RobotTtsController` 提供 `/api/media/tts/generate-file` 和 `/generate-and-play`。`TtsAudioService` 按文本、voice、format 计算缓存键，并把生成文件放在按机器人隔离的本地目录。`generate-and-play` 会向所有 `/ws/media` 连接先发 `tts.audio.meta`，再发二进制音频，不按 robotId 定向。

### `livekit/` 与 `ws/`

- `LiveKitRoomService`：创建、查询和删除 Room。
- `LiveKitTokenService`：签发 viewer、operator、publisher 和机器人对讲 Token。
- `LiveKitEgressService`：开始/停止录像并与通用文件记录关联。
- `MediaWebSocketHandler`：只管理 `/ws/media` 连接，不处理文本控制请求。
- `MediaWebSocketPublisher`：广播 `{event,timestamp,data}` 与 TTS 二进制帧。

## 3. 接口边界

| 路径 | 调用方 | 说明 |
| --- | --- | --- |
| `/internal/media/video-sessions/**` | Control Service | 视频、对讲、Track、Token、调度候选和录像 |
| `/internal/media/files/**` | Control Service | 文件能力内部别名 |
| `/api/media/files/**` | 机器人或受控调用方 | 文件上传、状态、查询、下载与播放 |
| `/api/media/tts/**` | 内部调用方/调试端 | TTS 生成与 WebSocket 音频广播 |
| `/ws/media` | 内部调试/TTS 客户端 | 事件及二进制音频广播 |

Media 不存在媒体源 CRUD、LiveKit Webhook、专用 Snapshot Controller，也不直接发布 MQTT。Control 从 Media 获取命令载荷后决定下发 Topic。

## 4. 配置

配置入口为 `src/main/resources/application.yml` 和 `config/MediaProperties.java`：

| 前缀 | 说明 |
| --- | --- |
| `media.livekit.*` | LiveKit 地址、Key/Secret、Token、Room 与 Egress |
| `media.file.live-recording-max-duration-seconds` | 单次手动录像最长持续时间，默认 14400 秒 |
| `media.minio.*` | 对象存储地址、凭据、bucket 和开关 |
| `media.file.*` | 文件大小、multipart、播放 Token、HLS、保留期与可信网段 |
| `media.tts.*` | OpenTTS 地址、voice、format、缓存目录和超时 |
| `media.session.*` | 发布超时、中断宽限、空闲释放、viewer 超时和视频墙上限 |

开发 Profile 还配置 `spring.datasource`、Redis 与 Elasticsearch 地址。生产必须替换 LiveKit Secret、MinIO 凭据和文件播放 Token Secret。

Media/Control JSON 时间统一显示为上海时区 `yyyy-MM-dd HH:mm:ss`。Media 通过 `X-User-Id`、`X-Org-Id`、`X-Roles`、`X-Client-Id` 解析调用者；生产应只允许 BFF/Control 等受控上游访问。

## 5. 典型调用链

```text
Control 创建视频
  -> VideoSessionController / VideoSessionService
  -> LiveKitRoomService + LiveKitTokenService
  -> 返回 VideoStartCommand
  -> Control 通过 MQTT 下发
  -> Control 转发客户端 status
  -> Media 更新 VideoSession/MediaTrack 并广播事件
```

```text
机器人创建 multipart
  -> Media 创建 MediaFile/MediaFileUpload
  -> 客户端凭预签名 URL 直传 MinIO
  -> complete 后 VIDEO 进入 HLS 处理，其他文件进入 READY
  -> 前端经 BFF/Control 获取下载或播放地址
```

修改接口、状态、配置或模型含义时，同时更新本 README 和对应接口/协议文档。
