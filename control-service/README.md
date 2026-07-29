# Control Service

`control-service/` 是从 `backend/` 拆分出的独立控制服务。

主要职责：

- 提供 `/api/control/**` 和 `/ws/control`。
- 订阅机器人 MQTT 状态 Topic。
- 合并管理端设备登记信息与机器人运行时状态。
- 在 `com.robot.control.robot` 中维护机器人在线状态和设备状态。
- 通过 `/internal/media/**` 调用 Media Service 的视频会话和文件能力。

## 设备能力与状态

- 机器人与部件登记信息来自 `CENTER_MANAGE_BASE_URL`。
- 管理端部件 ID 是平台侧使用的 `devices[].deviceId`。
- 客户端 `devices[].status` 作为运行时真实状态保留；服务不生成虚假的在线、灯光、音量、发射器或弹筒状态。
- Driver ID 与平台 ID 不一致时，通过 `devices[].status.driverDeviceId` 上报。
- 管理端登记的 Action 优先；已集成设备类型保留生产兼容 Action 和参数限制，避免改变既有 MQTT `action/params` 契约。
- 多合一喊话设备使用 `MULTI_FUNCTION_BROADCASTER`，统一命令发布到 `robot/{robotId}/control/multi-function/command`；服务端只组装平台语义参数，不感知设备 IP、端口和二进制协议。
- 管理端设备数据缓存 30 秒；刷新失败时使用最近一次成功快照。
- 每条 `robot/{robotId}/media/client/status` 消息先完成合并，再统一发布一次 `robot.state`。

## 启动与构建

```bash
mvn spring-boot:run
```

默认端口：`8082`。

常用环境变量：

```bash
CONTROL_SERVER_PORT=8082
MEDIA_SERVICE_BASE_URL=http://localhost:8088
CENTER_MANAGE_BASE_URL=http://localhost:8866
MQTT_BROKER_URL=tcp://192.168.124.77:1883
MQTT_CLIENT_ID=robot-control-service-main
MQTT_ENABLED=true
```

本地不连接 MQTT 时：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--control.mqtt.enabled=false
```

服务通过 `/api/control/**` 和 `/ws/control` 对外提供控制能力，并通过 `/internal/media/**` 调用 Media Service。

构建与测试：

```bash
mvn test
mvn -q -DskipTests package
```

## 代码结构与实现说明

`control-service` 是独立 Spring Boot 服务，源码包为 `com.robot.control`。它将前端请求转成媒体服务内部调用，维护机器人在线/设备状态，并负责向机器人侧下发 MQTT 指令。

### 目录结构

```text
control-service/src/main/java/com/robot/control/
├── api/          前端/管理端 REST API
├── client/       调用 Media 内部接口的 HTTP Client
├── dto/          Control 请求 DTO
├── messaging/    MQTT 指令发送和状态订阅
├── robot/        机器人在线状态、心跳、摄像头和设备状态
├── scheduler/    Control 侧会话恢复/释放调度
└── service/      Control 编排服务
```

### API 入口

- `ControlRobotController`
  - 路径前缀：`/api/control/robots/{robotId}`。
  - 获取控制画像、控制会话、设备控制命令、启动摄像头视频和启动对讲；不再提供 `GET /api/control/robots` 列表接口。

- `ControlVideoSessionController`
  - 路径：`/api/control/video-sessions`。
  - 查询会话、查询活跃会话、获取事件和 Track、签发 token、对讲心跳/停止、viewer 心跳、停止/重启视频、切换通道、创建/查询抓拍。

- `ControlFileController`
  - 路径：`/api/control/files`。
  - 提供前端侧文件上传、列表、详情、下载 URL、播放 URL、正文读取和 HLS 对象读取。

### 核心类

- `ControlVideoCommandService`
  - 管理端视频操作编排层。
  - 对前端请求进行业务编排，调用 Media 内部接口准备会话和指令，再通过 MQTT 下发机器人命令。

- `ControlMediaServiceClient`
  - 通过 `RestClient` 调用 Media Service 的 `/internal/media/**` 内部接口。
  - Control 和 Media 已拆成独立服务，Java 包与部署进程均隔离。

- `RobotMediaCommandService`
  - MQTT 指令发送封装。
  - 负责发送 start、stop、switch-channel、intercom 等机器人媒体命令。

- `RobotMediaStatusSubscriber`
  - MQTT 状态订阅。
  - 接收机器人端状态上报：视频/对讲状态转调 Media；客户端在线状态写入 Control 本地 `RobotRegistryService`，上线时再向 Media 查询需要恢复的推流命令。

- `ControlVideoSessionScheduler`
  - 周期性处理异常恢复、空闲释放、对讲超时等控制侧后台任务。

### 机器人状态子模块 `robot/`

`control-service/src/main/java/com/robot/control/robot` 维护机器人在线状态、摄像头列表和心跳。该模块不属于 `backend` 媒体服务。

- `RobotRegistryService`
  - 以内存注册表维护机器人设备状态。
  - 处理机器人上线/离线、摄像头列表、设备能力、最后心跳时间。
  - 设备状态变化时通过 `/ws/control` 推送 `robot.state`。

- `RobotHeartbeatScheduler`
  - 定时扫描超时未上报的机器人，并标记离线。
