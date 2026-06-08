# Backend 代码结构说明

本文档说明 `backend/` 目录下 Java 后端的代码组织、模块职责和主要调用关系。当前后端是一个 Java 17 + Spring Boot 3.3.x 服务，负责机器人实时视频会话编排、LiveKit Token/Room 管理、MQTT 指令桥接、WebSocket 事件推送、抓拍、录像上传与 HLS 回放等媒体能力。

## 1. 工程概览

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/robot/mediaserver/
    │   │   ├── RobotMediaServerApplication.java
    │   │   ├── auth/
    │   │   ├── config/
    │   │   ├── control/
    │   │   ├── livekit/
    │   │   ├── recording/
    │   │   ├── robot/
    │   │   ├── storage/
    │   │   ├── video/
    │   │   └── ws/
    │   └── resources/
    │       ├── application.yml
    │       └── application-dev.yml
    └── test/
```

入口类是 `RobotMediaServerApplication`，通过 `@SpringBootApplication` 启动 Spring Boot，并通过 `@EnableScheduling` 开启定时任务。

`pom.xml` 中主要依赖如下：

- Spring Web：提供 REST API。
- Spring WebSocket：提供业务 WebSocket 事件推送。
- Spring Validation：请求参数校验。
- Spring Data JPA：MySQL 数据访问。
- Spring Data Redis：预留或支持缓存/状态能力。
- Spring Data Elasticsearch：当前配置中关闭 repository，但保留依赖。
- MySQL Driver：运行时数据库驱动。
- Eclipse Paho MQTT：机器人媒体指令和状态通信。
- MinIO SDK：抓拍和录像对象存储。
- JJWT：LiveKit Token 和录像播放 Token 相关签名能力。

## 2. 配置文件

### `application.yml`

基础配置和媒体能力配置集中在 `media.*` 下：

- `server.port`：默认 `8088`。
- `control.media-service-base-url`：Control 层访问 Media 内部接口的基础地址。
- `media.auth`：当前用户模拟开关。
- `media.livekit`：LiveKit 地址、API key、Token TTL、Room API 开关。
- `media.mqtt`：MQTT broker、账号、clientId、启用开关。
- `media.minio`：MinIO endpoint、bucket、启用开关。
- `media.rtsp`：RTSP 探测工具和默认地址。
- `media.robot`：机器人心跳超时。
- `media.snapshot-worker`：抓拍 Worker 的 ffmpeg、调度间隔、超时。
- `media.recording`：录像上传、分片、回放、HLS 转码、保留策略、机器人可信网段。
- `media.session`：视频会话 Track 发布超时、空闲释放、viewer 心跳、视频墙数量和清晰度限制。

### `application-dev.yml`

开发环境配置：

- MySQL 数据源，JPA `ddl-auto=update`。
- Redis 连接地址。
- Elasticsearch 地址，repository 默认关闭。
- `com.robot.mediaserver` 日志级别为 `debug`。

## 3. 顶层包职责

```text
com.robot.mediaserver
├── auth       当前用户解析和用户上下文
├── config     Spring Bean、配置属性、WebSocket 配置
├── control    面向前端/管理端的控制入口和媒体服务客户端
├── livekit    LiveKit Room 与 Token 能力
├── recording  录像上传、转码、回放、清理
├── robot      机器人设备注册、心跳、设备列表
├── storage    MinIO/对象存储封装
├── video      实时视频会话、Track、抓拍、事件、状态机
└── ws         WebSocket 连接管理和事件广播
```

后端代码整体按业务域拆分，每个业务域内部通常继续按 `api`、`dto`、`model`、`repository`、`service`、`scheduler` 分层。

## 4. 通用基础模块

### `auth/`

- `CurrentUser`：当前请求用户上下文，包含 `userId`、`orgId`、`roles`、`clientId`。
- `CurrentUserResolver`：Spring MVC 参数解析器，用于在 Controller 方法中直接注入 `CurrentUser`。当 `media.auth.mock-enabled=true` 时，可支持开发环境模拟用户。

### `config/`

- `AppConfig`：注册 MVC 相关配置，例如参数解析器。
- `WebSocketConfig`：注册业务 WebSocket endpoint。
- `MediaProperties`：绑定 `media.*` 配置，是 LiveKit、MQTT、MinIO、录像、会话等配置的统一入口。
- `ControlProperties`：绑定 `control.*` 配置。

### `ws/`

- `MediaWebSocketHandler`：维护 WebSocket 连接，接收客户端连接/断开。
- `MediaWebSocketPublisher`：封装事件广播能力，业务模块通过它向前端推送设备、视频会话、抓拍等事件。

## 5. 实时视频模块 `video/`

`video` 是后端最核心的媒体会话模块，负责视频会话生命周期、LiveKit 房间和 Token、机器人状态回写、Track 记录、抓拍任务、事件日志。

### 目录结构

```text
video/
├── api/          REST API 和异常处理
├── dto/          请求/响应 DTO
├── event/        媒体事件日志服务
├── internal/     内部 Worker 回调接口
├── messaging/    MQTT/内部指令和状态消息模型
├── model/        JPA 实体和枚举
├── repository/   JPA Repository
├── scheduler/    会话超时、viewer 清理、抓拍 Worker
└── service/      核心业务服务
```

### API 入口

- `VideoSessionController`
  - 路径：`/api/media/video-sessions` 和 `/internal/media/video-sessions`。
  - 负责创建/查询/停止/重启视频会话、签发 viewer token、处理机器人状态、对讲状态、切换通道、创建抓拍。
  - 同一个 Controller 同时暴露外部 media API 和内部 media API，便于 Control 层复用。

- `SnapshotWorkerController`
  - 路径：`/api/internal/media/snapshots` 和 `/internal/media/snapshots`。
  - 供内部抓拍 Worker 回调抓拍完成、上传文件、标记失败，或供 Control 层代理读取抓拍图片。

- `ApiExceptionHandler`
  - 全局 REST 异常处理，统一返回错误响应。

### 核心 Service

- `VideoSessionService`
  - 视频会话状态机核心。
  - 负责创建或复用会话、创建 LiveKit Room、签发 publisher/viewer token、维护 viewer 心跳和人数、停止/重启会话、处理机器人上报的 streaming/interrupted/failed 状态、生成 MQTT start/stop/switch 指令 payload。

- `MediaTrackService`
  - 记录 LiveKit Track 发布和取消发布。
  - 提供按会话查询最近 Track 的能力。

- `SnapshotService`
  - 创建抓拍记录、保存前端当前画面抓拍结果、上传图片到 MinIO、查询抓拍列表、读取图片、标记抓拍失败。

- `MediaEventLogService`
  - 记录媒体事件日志，并配合 WebSocket 推送关键事件。

### 数据模型

- `VideoSession`
  - 表示一路机器人摄像头视频会话。
  - 关键字段：`sessionId`、`robotId`、`deviceId`、`channel`、`quality`、`roomName`、`status`、`viewerCount`、`trackSid`、`commandId`、`idleSince`、错误信息等。

- `MediaSessionViewer`
  - 表示观看者会话。
  - 用于 viewer 心跳、离开时间、并发观看人数统计。

- `MediaTrack`
  - 记录 LiveKit Track 发布信息。

- `MediaSnapshot`
  - 记录抓拍任务、图片对象 key、抓拍状态、失败原因等。

- `MediaEventLog`
  - 记录会话事件和 payload，用于前端查询事件流水。

### 主要枚举

- `VideoSessionStatus`：视频会话状态。
- `VideoChannel`：视频通道，如可见光/热成像等。
- `VideoQuality`：视频质量。
- `IntercomStatus`：对讲状态。
- `SnapshotStatus`：抓拍状态。

### 定时任务

- `VideoSessionTimeoutScheduler`
  - 清理过期 viewer。
  - 检测等待客户端发布 Track 超时的会话并标记失败。

- `ViewerStartupCleaner`
  - 应用启动后关闭遗留活跃 viewer，避免服务重启后 viewer 计数不一致。

- `SnapshotWorkerScheduler`
  - 定时领取待处理抓拍任务，通过 ffmpeg 截帧并回写结果。

## 6. Control 模块 `control/`

`control` 是面向前端或管理端的业务控制层。它将前端请求转成媒体服务内部调用，并负责向机器人侧下发 MQTT 指令。

### 目录结构

```text
control/
├── api/          前端/管理端 REST API
├── client/       调用 Media 内部接口的 HTTP Client
├── dto/          Control 请求 DTO
├── messaging/    MQTT 指令发送和状态订阅
├── scheduler/    Control 侧会话恢复/释放调度
└── service/      Control 编排服务
```

### API 入口

- `ControlRobotController`
  - 路径：`/api/control/robots`。
  - 查询机器人列表、启动摄像头视频、启动对讲。

- `ControlVideoSessionController`
  - 路径：`/api/control/video-sessions`。
  - 查询会话、查询活跃会话、获取事件和 Track、签发 token、对讲心跳/停止、viewer 心跳、停止/重启视频、切换通道、创建/查询抓拍。

- `ControlRecordingController`
  - 路径：`/api/control/recordings`。
  - 查询录像、生成播放 URL、读取 HLS 对象。

### 核心类

- `ControlVideoCommandService`
  - 管理端视频操作编排层。
  - 对前端请求进行业务编排，调用 Media 内部接口准备会话和指令，再通过 MQTT 下发机器人命令。

- `ControlMediaServiceClient`
  - 通过 `RestClient` 调用当前 Media Service 的内部接口。
  - 当前工程中 Control 和 Media 在同一个 Spring Boot 应用内，但代码保留了“Control Server 调 Media Service”的边界。

- `RobotMediaCommandService`
  - MQTT 指令发送封装。
  - 负责发送 start、stop、switch-channel、intercom 等机器人媒体命令。

- `RobotMediaStatusSubscriber`
  - MQTT 状态订阅。
  - 接收机器人端状态上报，并转交 Control/Media 业务服务处理。

- `ControlVideoSessionScheduler`
  - 周期性处理异常恢复、空闲释放、对讲超时等控制侧后台任务。

## 7. 机器人模块 `robot/`

`robot` 模块维护机器人和摄像头的在线状态、设备列表和心跳。

### API 入口

- `RobotDeviceController`
  - 路径：`/api/media/robots` 和 `/internal/media/robots`。
  - `GET` 查询机器人设备列表。
  - `POST /client-status` 接收机器人客户端状态和摄像头列表上报。

### 核心类

- `RobotRegistryService`
  - 以内存注册表维护机器人设备状态。
  - 处理机器人上线/离线、摄像头列表、最后心跳时间。
  - 设备状态变化时通过 WebSocket 推送。

- `RobotHeartbeatScheduler`
  - 定时扫描超时未上报的机器人，并标记离线。

## 8. 录像模块 `recording/`

`recording` 模块负责机器人巡逻录像上传、分片上传 URL 生成、上传完成确认、HLS 转码、播放 URL、录像列表和保留清理。

### 目录结构

```text
recording/
├── api/          机器人上传 API、内部/控制端录像 API、过滤器
├── dto/          录像上传、分片、列表、播放 URL DTO
├── model/        录像和上传会话 JPA 实体/状态枚举
├── repository/   JPA Repository
├── scheduler/    上传过期清理、HLS 处理、保留期清理
└── service/      录像上传、存储、播放和 HLS 处理服务
```

### API 入口

- `RobotRecordingController`
  - 路径：`/api/media`。
  - 提供机器人侧录像上传接口：
    - `POST /recording-uploads` 创建或恢复上传。
    - `POST /recording-uploads/{uploadId}/part-urls` 获取分片上传 URL。
    - `POST /recording-uploads/{uploadId}/complete` 完成上传。
    - `GET /recordings/{recordingId}/status` 查询上传/处理状态。

- `RecordingInternalController`
  - 路径：`/internal/media/recordings`。
  - 提供内部录像列表、播放 URL、HLS 对象读取。

- `TrustedRobotNetworkFilter`
  - 可选的机器人可信网段过滤，用于限制机器人上传接口来源。

- `RecordingApiException`
  - 录像模块业务异常。

### 核心 Service

- `RecordingService`
  - 录像上传和回放主服务。
  - 负责创建/恢复上传会话、签发分片上传 URL、完成 multipart 上传、查询状态、分页查询录像、生成播放 URL、读取播放资产、过期上传处理、删除录像资产。

- `HlsPlaybackAssetService`
  - 领取待处理录像，使用 ffprobe/ffmpeg 转 HLS，上传 HLS playlist 和 segment，回写处理结果。

### 数据模型

- `MediaRecording`
  - 录像主表。
  - 关键字段：`recordingId`、`orgId`、`robotId`、`deviceId`、源文件信息、对象存储 key、HLS playlist key、时长、状态、错误信息、处理时间等。

- `MediaRecordingUpload`
  - 录像上传会话表。
  - 关键字段：`uploadId`、`recordingId`、`storageUploadId`、`partSize`、`partCount`、`status`、`expiresAt`、`lastActiveAt`。

- `RecordingStatus`
  - 录像处理状态。

- `UploadStatus`
  - 上传会话状态。

### 定时任务

- `ExpiredUploadCleanupScheduler`
  - 清理过期上传会话并中止对象存储 multipart 上传。

- `HlsPlaybackProcessingScheduler`
  - 定时领取待处理录像并生成 HLS 播放资产。

- `RecordingRetentionCleanupScheduler`
  - 按保留天数删除过期录像及对象存储资产。

## 9. LiveKit 模块 `livekit/`

- `LiveKitTokenService`
  - 签发 LiveKit token。
  - 支持 viewer、interactive viewer、operator、publisher、robot intercom、admin 等身份。

- `LiveKitRoomService`
  - 通过 LiveKit Room API 创建和删除房间。
  - 当 Room API 关闭时，可按配置跳过实际 Room 管理。

该模块被 `VideoSessionService` 调用，是实时视频房间和鉴权能力的基础。

## 10. 存储模块 `storage/`

- `MinioStorageService`
  - 抓拍图片上传封装。

- `RecordingObjectStorageService`
  - 录像对象存储封装。
  - 支持 multipart 初始化、分片预签名 URL、列出分片、完成/中止 multipart、读取对象、上传文件、下载文件、按前缀删除。

存储模块屏蔽 MinIO SDK 细节，业务层只处理 object key 和业务状态。

## 11. 接口路径分层

后端接口大致分为三类：

### 面向前端/管理端：`/api/control/*`

由 `control/api` 提供，前端主要调用这些接口：

- `/api/control/robots`
- `/api/control/robots/{robotId}/cameras/{deviceId}/video/start`
- `/api/control/video-sessions`
- `/api/control/video-sessions/{sessionId}/token`
- `/api/control/video-sessions/{sessionId}/heartbeat`
- `/api/control/video-sessions/{sessionId}/stop`
- `/api/control/video-sessions/{sessionId}/restart`
- `/api/control/video-sessions/{sessionId}/switch-channel`
- `/api/control/video-sessions/{sessionId}/snapshots`
- `/api/control/video-sessions/{sessionId}/snapshots/file`
- `/api/control/robots/{robotId}/cameras/{deviceId}/snapshots`
- `/api/control/robots/{robotId}/cameras/{deviceId}/snapshots/{snapshotId}/image`
- `/api/control/recordings`

### 面向媒体能力/机器人端：`/api/media/*`

由 `video/api`、`robot/api`、`recording/api` 提供：

- `/api/media/video-sessions`
- `/api/media/robots`
- `/api/media/robots/client-status`
- `/api/media/recording-uploads`
- `/api/media/recording-uploads/{uploadId}/part-urls`
- `/api/media/recording-uploads/{uploadId}/complete`
- `/api/media/recordings/{recordingId}/status`

### 内部调用：`/internal/media/*` 和 `/api/internal/media/*`

用于 Control 层、内部 Worker 或服务间调用：

- `/internal/media/video-sessions`
- `/internal/media/video-sessions/{sessionId}/snapshots/file`
- `/internal/media/video-sessions/snapshots`
- `/internal/media/snapshots/{snapshotId}/image`
- `/internal/media/robots`
- `/internal/media/recordings`
- `/api/internal/media/snapshots`

## 12. 典型调用链路

### 启动一路实时视频

```text
前端
  -> ControlRobotController
  -> ControlVideoCommandService
  -> ControlMediaServiceClient
  -> VideoSessionController / VideoSessionService
  -> LiveKitRoomService 创建 Room
  -> LiveKitTokenService 签发 publisher/viewer token
  -> 返回 VideoStartCommand
  -> RobotMediaCommandService 通过 MQTT 下发 start
  -> 机器人 Go 客户端推流到 LiveKit
  -> RobotMediaStatusSubscriber 接收 streaming 状态
  -> VideoSessionService 更新 session/track
  -> MediaWebSocketPublisher 推送事件给前端
```

### viewer 停止观看

```text
前端
  -> ControlVideoSessionController
  -> ControlVideoCommandService
  -> VideoSessionService stop viewer
  -> 更新 MediaSessionViewer / viewerCount
  -> 若 viewerCount 为 0，进入 IDLE_WAIT
  -> ControlVideoSessionScheduler 到期后 release idle session
  -> MQTT 下发 stop
  -> LiveKitRoomService 删除 Room
```

### 机器人录像上传

```text
机器人 Go 客户端
  -> RobotRecordingController 创建/恢复上传
  -> RecordingService 创建 MediaRecording / MediaRecordingUpload
  -> RecordingObjectStorageService 初始化 multipart
  -> RobotRecordingController 获取 part upload URLs
  -> 客户端直传 MinIO 分片
  -> RobotRecordingController complete
  -> RecordingService 完成 multipart 并标记待处理
  -> HlsPlaybackProcessingScheduler / HlsPlaybackAssetService 转 HLS
  -> 前端通过 ControlRecordingController 获取播放 URL 和 HLS 资源
```

## 13. 数据持久化

当前后端使用 Spring Data JPA + MySQL。主要 Repository：

- `VideoSessionRepository`
- `MediaSessionViewerRepository`
- `MediaTrackRepository`
- `MediaSnapshotRepository`
- `MediaEventLogRepository`
- `MediaRecordingRepository`
- `MediaRecordingUploadRepository`

机器人设备注册当前由 `RobotRegistryService` 以内存方式维护，不是 JPA 实体。

## 14. 外部系统依赖

- LiveKit：实时媒体 Room、Track、Token。
- MQTT/EMQX：机器人媒体指令下发和状态上报。
- MinIO：抓拍图片、录像源文件、HLS 播放资产。
- MySQL：视频会话、Track、抓拍、事件、录像等持久化。
- Redis：已引入并配置，当前代码结构中不是核心业务主路径。
- ffmpeg/ffprobe：抓拍和录像 HLS 处理。

## 15. 开发时定位建议

- 查前端调用入口：先看 `control/api`。
- 查媒体会话状态机：看 `video/service/VideoSessionService.java`。
- 查机器人 MQTT 指令：看 `control/messaging/RobotMediaCommandService.java`。
- 查机器人状态上报：看 `control/messaging/RobotMediaStatusSubscriber.java` 和 `video/service/VideoSessionService.java`。
- 查抓拍：看 `video/service/SnapshotService.java` 和 `video/scheduler/SnapshotWorkerScheduler.java`。
- 查录像上传/回放：看 `recording/service/RecordingService.java` 和 `recording/service/HlsPlaybackAssetService.java`。
- 查配置项含义：看 `config/MediaProperties.java` 和 `src/main/resources/application.yml`。

## 16. 扩展规则建议

- 新增前端业务接口时，优先放在 `control/api`，由 `control/service` 编排，再调用媒体内部能力。
- 新增媒体核心能力时，优先放在对应业务域的 `service`，Controller 只做参数接收和响应转换。
- 新增持久化表时，按 `model` + `repository` + `dto` + `service` 的方式落地。
- 新增定时后台任务时，放在业务域 `scheduler`，调度类只负责触发，复杂逻辑放入 Service。
- 新增外部系统调用时，优先封装到独立基础模块，避免 Controller 或业务 Service 直接依赖 SDK 细节。
