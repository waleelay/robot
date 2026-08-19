# Control Service

`control-service/` 是 Java 17 + Spring Boot 3 控制编排服务，默认端口 `8082`。它负责 `/api/control/**`、机器人运行时状态、控制租约、设备动作、实时视频编排和 MQTT；媒体文件、LiveKit Room/Token 与视频状态持久化由 Media Service 负责。

接口权威定义见 [控制服务接口文档](../docs/03-接口与协议/统一控制/控制服务接口文档.md)。

## 1. 启动、构建与测试

```bash
cd control-service
mvn spring-boot:run
mvn test
mvn -q -DskipTests package
```

本地不连接 MQTT：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--control.mqtt.enabled=false
```

运行时需要可访问 Media Service 和 MySQL；控制画像、设备动作和固定摄像头还依赖 Management Service。启用 MQTT 时需要 EMQX；任务失效通知的上游 STOMP 连接默认启用，可通过 `CENTER_STOMP_ENABLED=false` 显式关闭。

## 2. 代码结构

```text
src/main/java/com/robot/control/
├── api/        机器人、固定摄像头、视频会话和文件 REST API
├── auth/       用户上下文与 Bearer Token 透传
├── call/       机器人主动对讲呼叫占用与超时
├── client/     Media/Management HTTP 客户端
├── dto/        跨服务请求、响应、枚举与命令模型
├── messaging/  MQTT 控制发布、媒体命令和状态订阅
├── mileage/    里程增量计算、持久化与统计查询
├── robot/      内存注册表、设备状态与心跳超时
├── scheduler/  视频恢复、空闲释放和对讲超时调度
├── service/    控制、视频和多合一音频文件编排
└── ws/         WebSocket 请求/广播及上游 STOMP 桥接
```

### REST 入口

- `ControlRobotController`：注册表、控制画像、控制会话、命令、多合一音频文件传输和机器人摄像头启动。
- `ControlFixedCameraController`：单路/批量固定摄像头启动。
- `ControlVideoSessionController`：查询、Token、viewer、对讲、切换、重启和录像。
- `ControlFileController`：把文件请求代理到 Media，并改写播放路径。
- `MileageController`：按时间范围和机器人批量查询持久化里程。

`GET /api/control/robots` 已移除；内存注册表只通过 `/api/control/robots/registry` 供 BFF 聚合。

### 核心服务

- `EquipmentControlService`：合并管理端能力和运行时状态，维护 30 秒排他租约，校验 action/params 并生成 MQTT 命令。
- `ControlVideoCommandService`：调用 Media 准备会话/命令，再按机器人或固定摄像头选择 Topic。
- `MultiFunctionAudioTransferService`：校验 Media 文件归属、类型、状态、大小和扩展名，再发布 `audio_file_transfer`。
- `IntercomCallService`：维护机器人主动呼叫邀请、接听/拒绝/取消和占用超时。
- `RobotRegistryService`：以内存状态维护机器人在线、摄像头和设备状态，并广播 `robot.state`。
- `ControlVideoSessionScheduler`：处理中断恢复、空闲释放和对讲心跳超时。

### 外部连接

- `ControlMediaServiceClient`：调用 `/internal/media/**`，保留 Media 非 2xx 状态和错误正文。
- `ControlManagementClient`：查询机器人档案、设备动作和固定摄像头；设备详情缓存 30 秒，HTTP 请求链路透传 Bearer Token。
- `RobotMediaCommandService`：发布机器人视频、对讲和固定摄像头 Gateway 命令。
- `RobotMediaStatusSubscriber`：订阅客户端在线、视频、对讲、主动呼叫和设备状态。
- `EquipmentControlCommandPublisher`：按设备类型发布通用控制命令。
- `CenterStompTaskEventBridge`：把上游 `task.changed.v1` 转成 `management.task.invalidated` 失效通知。

## 3. 关键业务语义

### 控制会话

- 租约默认 30 秒；同一 `userId + clientId` 重复申请会续期。
- 冲突按机器人和 `deviceIds` 交集判断；冲突当前返回 HTTP 200 的 `CONTROL_LOCKED` 响应体。
- `drive.velocity` 要求机器人在线、手动模式、当前终端持有含 `base` 的租约。
- `takeover`/模式切换只发布请求，最终状态以机器人上报为准。
- `confirm-token` 当前会生成 30 秒 Token 信息，但普通 `/commands` 尚未校验它。

### 设备命令

服务端优先把 Management `components[].capabilities[].actions` 映射为对外 action；如果一个组件没有任何可识别的映射结果，当前代码会按已知 `deviceType` 使用生产兼容动作表。命令必须属于控制画像最终返回的 `devices[].actions`，再按设备类型路由至 body、ptz、audio、launcher、net-gun、warning-light、vehicle-light 或 multi-function Topic。Control 返回 MQTT 发布结果，不持久化命令，也不等待 ACK。

多合一动作包括音量、广播/收音、TTS、音频文件、报警、灯光及扬声器/灯光俯仰。音频文件必须先上传 Media；Control 只下发文件标识和元数据。

### 实时视频与固定摄像头

机器人视频 Topic 使用 `robot/{robotId}/media/video/**`。固定摄像头使用：

```text
gateway/fixed-camera/{gatewayId}/video/start
gateway/fixed-camera/{gatewayId}/video/stop
gateway/fixed-camera/{gatewayId}/video/restart
gateway/fixed-camera/{gatewayId}/video/status
```

固定摄像头仅支持 `visible`。Control 查询 Management 档案，校验 `enabled` 和码流，并在主/子码流缺失时回退到另一条可用码流。首次 start 命令含内部 `rtspUrl`；手工重启、自动恢复或切换命令可能不含该字段，由 Gateway 按摄像头 ID 回查 Management。`rtspUrl` 和 LiveKit Token 都是敏感数据；当前 MQTT 发布日志会序列化完整载荷，这是生产前必须关闭或脱敏的缺口。

## 4. WebSocket

`/ws/control` 和兼容路径 `/ws/media` 使用同一 Handler：

- 广播事件：`{event,timestamp,data}`。
- 请求消息：`{type,requestId,payload}`。
- 请求响应：`{type,requestId,timestamp,payload}`。

支持 `control.command` 与主动对讲的 accept/reject/query；未知 `type` 静默忽略。WebSocket 只维护进程内连接，服务重启后客户端需要重连。

## 5. 配置

| 配置 | 环境变量 | 说明 |
| --- | --- | --- |
| `control.media-service-base-url` | `MEDIA_SERVICE_BASE_URL` | Media 内部地址 |
| `control.management-service-base-url` | `CENTER_MANAGE_BASE_URL` | Management 地址 |
| `control.mqtt.*` | `MQTT_*`、`FIXED_CAMERA_GATEWAY_ID` | Broker、凭据、clientId、Gateway ID 和开关 |
| `control.center-stomp.*` | `CENTER_STOMP_*` | 上游任务事件连接 |
| `control.mileage.*` | `MILEAGE_*` | 异常速度阈值和统计刷新距离阈值 |
| `spring.datasource.*` | `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` | 里程检查点和分钟增量桶数据库 |
| `control.robot.*` | `ROBOT_HEARTBEAT_*`、`ROBOT_OFFLINE_RETENTION_SECONDS` | 心跳超时与扫描周期、离线注册表清理 |
| `control.session.*` | `INTERRUPTED_*`、`IDLE_*`、`VIEWER_*` | 视频恢复和释放参数 |

Control 信任 BFF 注入的用户 Header，缺失时使用开发身份；生产不应无防护直连。Java 时间统一输出 `yyyy-MM-dd HH:mm:ss`（`Asia/Shanghai`）。

修改跨服务协议时应同步检查 Media DTO、MQTT 生产者/消费者、WebSocket 事件和对应协议文档。
