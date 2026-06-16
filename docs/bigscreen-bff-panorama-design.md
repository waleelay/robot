# 大屏 BFF 与全景地图接口设计

## 1. 背景与目标

当前前端大屏已经通过 Nginx 访问后端接口，页面数据主要来自控制服务、媒体服务以及后续中心端的管理、任务、告警等服务。

当控制代码和媒体代码移动到中心端后，大屏不应直接感知中心端内部服务拆分，而应抽出一层独立的大屏 BFF：

```text
浏览器大屏前端
  -> Nginx HTTPS/WSS 入口
  -> Bigscreen BFF
  -> 中心端管理/控制/媒体/任务/告警服务
```

目标：

- 大屏前端只面向稳定的 `/api/bigscreen/**` 与 `/ws/bigscreen`。
- 中心端服务拆分、路径变化、DTO 变化尽量收敛在 BFF adapter 内。
- BFF 聚合页面所需数据，减少前端多接口拼装。
- BFF 不承载媒体流，只转发业务数据、页面模型、短期 token 或播放地址。

## 2. 部署与请求链路

### 2.1 当前本地部署

当前本地只需要一个 Nginx，作为浏览器入口网关：

```text
浏览器
  -> https://192.168.124.77:4443
  -> Nginx

Nginx /                 -> frontend/dist
Nginx /api/*            -> bigscreen-bff:8090
Nginx /ws/control       -> bigscreen-bff:8090
Nginx /ws/bigscreen     -> bigscreen-bff:8090
Nginx /livekit/*        -> LiveKit:7880

bigscreen-bff:8090
  -> center/backend:8088
```

前端到 BFF 建议继续走 Nginx，因为浏览器需要 HTTPS/WSS 安全上下文，尤其是麦克风、对讲和 WebRTC 场景。

BFF 到中心端不建议走 Nginx，应直接使用内部 HTTP 地址：

```text
CENTER_MANAGE_BASE_URL=http://localhost:8088
CENTER_CONTROL_BASE_URL=http://localhost:8088
CENTER_MEDIA_BASE_URL=http://localhost:8088
CENTER_CONTROL_WS_URL=ws://localhost:8088/ws/control
```

不要让 BFF 再调用：

```text
https://192.168.124.77:4443/api/...
```

否则链路会变成：

```text
浏览器 -> Nginx -> BFF -> Nginx -> 中心端
```

会增加延迟，并引入证书、Host、转发头和 WebSocket upgrade 等额外问题。

### 2.2 URL 变化

当前：

```text
前端 -> /api/control/*
前端 -> /ws/control
```

抽层后目标形态：

```text
前端 -> /api/bigscreen/*
前端 -> /ws/bigscreen
BFF  -> /api/control/*、/api/media/*、/api/manage/*、/internal/media/*
```

第一阶段为了兼容现有前端，BFF 仍可代理部分历史控制接口：

```text
/api/control/**，但不再对外提供 /api/control/robots
/ws/control
```

机器人列表数据统一进入 `/api/bigscreen/panorama/overview.devices`，前端全景地图页面不再直接调用 `/api/control/robots`。

## 3. BFF 职责边界

BFF 负责：

- 页面模型聚合，例如全景地图首屏数据。
- 接口适配，将中心端多个服务返回转换为大屏稳定 DTO。
- WebSocket 事件桥接与页面事件模型转换。
- 页面级权限、按钮可用状态、降级展示字段。
- 短缓存、限流、超时、熔断等面向页面体验的保护。

BFF 不负责：

- 不保存权威业务状态。
- 不复制控制服务、媒体服务核心业务逻辑。
- 不中转 LiveKit 音视频流。
- 不中转大体积录像/HLS 分片/图片文件，除非后续明确需要安全代理。

推荐权威状态归属：

| 数据 | 权威来源 |
|---|---|
| 设备档案、类型、所属区域 | 中心管理服务 |
| 在线、离线、故障、电量、控制模式 | 中心控制服务 |
| 视频会话、LiveKit token、抓拍、录像 | 中心媒体服务 |
| 任务名称、状态、时间、执行装备 | 中心任务服务 |
| 告警等级、状态、处置结果 | 中心告警服务 |
| 页面布局、选中状态、临时筛选 | 前端或 BFF 展示态 |

## 4. 性能影响与约束

抽 BFF 后会多一跳：

```text
原来：前端 -> 控制/媒体后端
现在：前端 -> BFF -> 中心端
```

影响：

- 每个接口多一次内网服务调用。
- 聚合接口如果串行调用中心端，会拖慢首屏。
- WebSocket 事件需要经过 BFF fan-out。
- 如果 BFF 透明转发且前端高频轮询，中心端压力可能增加。

约束与优化：

- BFF 内部聚合中心端接口时应并发调用。
- 设备档案、设备能力、组织信息可做 1 到 5 秒短缓存。
- 实时状态优先走 WebSocket 或事件总线，减少高频轮询。
- 高频控制帧走 WebSocket。
- 视频、音频、HLS、录像文件、图片大文件不经 BFF 中转。
- BFF 应设置中心端调用超时，例如 300ms 到 1s，避免单个慢服务拖垮首屏。
- 某个中心服务异常时，BFF 可以返回部分数据和降级标识。

## 5. 全景地图接口设计

### 5.1 第一版接口

第一版先只开发 BFF 接口，不改前端。数据可以 mock 返回，后续替换为中心端聚合。

```text
GET /api/bigscreen/panorama/overview
GET /api/bigscreen/panorama/devices/{deviceId}
GET /api/bigscreen/panorama/device-groups
GET /api/bigscreen/panorama/tasks
GET /api/bigscreen/panorama/alarms
```

`/api/control/robots` 的机器人基础字段合并到 `/api/bigscreen/panorama/overview.devices` 中；冲突字段以 `/api/control/robots` 的字段名和语义为准，地图展示、任务、告警等非冲突字段作为大屏扩展字段合并。

### 5.2 首屏聚合接口

```http
GET /api/bigscreen/panorama/overview
```

用途：一次返回全景地图 tab 页首屏需要的数据。

返回结构：

```json
{
  "serverTime": "2026-06-12 11:31:02",
  "deviceStats": {
    "total": 22,
    "online": 18,
    "fault": 2,
    "offline": 2
  },
  "deviceTypeStats": [
    {
      "type": "ROBOT_DOG",
      "name": "机器狗",
      "count": 8
    },
    {
      "type": "HUMANOID_ROBOT",
      "name": "机器人",
      "count": 6
    },
    {
      "type": "WHEELED_ROBOT",
      "name": "轮式车",
      "count": 8
    }
  ],
  "deviceGroups": {
    "total": 4,
    "online": {
      "status": "online",
      "statusName": "在线",
      "count": 2,
      "items": [
        {
          "robotId": "robot-001",
          "clientId": "robot-media-client",
          "name": "松灵四轮机器人",
          "type": "轮式机器人",
          "typeCode": "WHEELED_ROBOT",
          "vendor": "SONGLING",
          "model": "SCOUT",
          "status": "online",
          "battery": 100,
          "fault": false,
          "alarmLevel": null,
          "alarmText": "-",
          "locationText": "A区主干道",
          "currentTaskId": "task-001",
          "currentTaskName": "A区-夜间巡逻"
        }
      ]
    },
    "offline": {
      "status": "offline",
      "statusName": "离线",
      "count": 2,
      "items": []
    }
  },
  "devices": [
    {
      "robotId": "robot-001",
      "clientId": "robot-media-client",
      "name": "松灵四轮机器人",
      "type": "轮式机器人",
      "typeCode": "WHEELED_ROBOT",
      "vendor": "SONGLING",
      "model": "SCOUT",
      "status": "online",
      "battery": 100,
      "lastHeartbeatAt": "2026-06-12 11:30:58",
      "cameras": [
        {
          "cameraId": "camera01",
          "deviceId": "camera01",
          "name": "前向双光云台",
          "groupType": "dual_gimbal",
          "channel": "visible",
          "quality": "sub",
          "status": "online"
        }
      ],
      "stateSeq": 1,
      "fault": false,
      "alarmLevel": null,
      "controlMode": "MANUAL",
      "mountedDeviceCount": 3,
      "speed": null,
      "location": {
        "lng": 106.03655278081857,
        "lat": 30.7478613352993,
        "altitude": null,
        "address": "A区主干道",
        "updatedAt": "2026-06-12 11:30:58"
      },
      "mapDisplay": {
        "icon": "wheeled_robot",
        "label": "松灵四轮机器人",
        "badgeText": "空闲中",
        "badgeStatus": "idle"
      },
      "task": {
        "taskId": "task-001",
        "name": "A区-夜间巡逻",
        "status": "running",
        "timeRange": "20:00-22:00"
      }
    }
  ],
  "tasks": [
    {
      "taskId": "task-001",
      "name": "A区-夜间巡逻",
      "status": "running",
      "statusName": "执行中",
      "startTime": "2026-06-12 20:00:00",
      "endTime": "2026-06-12 22:00:00",
      "timeRange": "20:00-22:00",
      "currentLocation": "A区主干道",
      "equipmentList": [
        {
          "robotId": "robot-001",
          "name": "松灵四轮机器人",
          "type": "WHEELED_ROBOT",
          "status": "online"
        }
      ]
    }
  ],
  "alarms": {
    "total": 15,
    "high": {
      "level": "HIGH",
      "levelName": "高风险",
      "count": 5,
      "items": [
        {
          "alarmId": "alarm-001",
          "title": "发生火灾",
          "category": "BUSINESS",
          "categoryName": "业务告警",
          "level": "HIGH",
          "levelName": "高风险",
          "eventTime": "2023-08-01 10:00:00",
          "location": "A区仓库",
          "robotId": "robot-003",
          "deviceName": "巡检机器人一号",
          "taskId": "task-002",
          "taskName": "A区-仓库复核",
          "status": "unhandled",
          "snapshotUrl": null
        }
      ]
    },
    "medium": {
      "level": "MEDIUM",
      "levelName": "中风险",
      "count": 5,
      "items": []
    },
    "low": {
      "level": "LOW",
      "levelName": "低风险",
      "count": 5,
      "items": []
    }
  },
  "map": {
    "center": {
      "lng": 106.03655278081857,
      "lat": 30.7478613352993
    },
    "zoom": 17,
    "defaultLayer": "dark-vector",
    "updatedAt": "2026-06-12 11:31:02"
  }
}
```

当前 mock 的 4 台机器人定位：

| robotId | lat | lng |
|---|---:|---:|
| `robot-001` | 30.7478613352993 | 106.03655278081857 |
| `robot-002` | 30.746587087515316 | 106.03824884204943 |
| `robot-unitree-001` | 30.7469491 | 106.0344109 |
| `robot-003` | 30.745330 | 106.039428 |

### 5.3 设备详情接口

```http
GET /api/bigscreen/panorama/devices/{deviceId}
```

用途：点击地图设备点位后，返回右侧设备弹窗数据。路径变量可传 `robotId`；`deviceId` 命名保留是为了兼容页面语义。

返回结构：

```json
{
  "robotId": "robot-001",
  "clientId": "robot-media-client",
  "name": "松灵四轮机器人",
  "type": "轮式机器人",
  "typeCode": "WHEELED_ROBOT",
  "vendor": "SONGLING",
  "status": "online",
  "model": "SCOUT",
  "battery": 100,
  "lastHeartbeatAt": "2026-06-12 11:30:58",
  "cameras": [],
  "stateSeq": 1,
  "alarmStatus": "none",
  "alarmText": "-",
  "controlMode": "MANUAL",
  "speed": null,
  "location": {
    "lng": 106.03655278081857,
    "lat": 30.7478613352993,
    "altitude": null,
    "address": "A区主干道",
    "updatedAt": "2026-06-12 11:30:58"
  },
  "mountedDeviceCount": 3,
  "mountedDevices": [
    {
      "deviceId": "camera01",
      "name": "前向双光云台",
      "type": "DUAL_GIMBAL",
      "status": "online"
    }
  ],
  "currentTask": {
    "taskId": "task-001",
    "name": "A区-夜间巡逻",
    "status": "running",
    "timeRange": "20:00-22:00"
  },
  "actions": {
    "remoteControl": true,
    "slamMap": true,
    "returnHome": true,
    "returnChargingPile": true,
    "showPath": true,
    "showArea": true
  }
}
```

`actions` 由 BFF 返回，前端可直接控制按钮显示和启用状态。

### 5.4 设备分组接口

```http
GET /api/bigscreen/panorama/device-groups
```

用途：按在线、离线分组展示各机器人摘要集合，适合大屏侧边栏列表直接渲染。单设备完整详情继续使用 `GET /api/bigscreen/panorama/devices/{deviceId}`。

分组规则：

- `online` 组包含状态为 `online` 和 `fault` 的设备。故障设备仍然可能在线，因此进入在线组，同时通过 `fault` 和 `alarmLevel` 标识异常。
- `offline` 组包含状态为 `offline` 的设备。

返回结构：

```json
{
  "serverTime": "2026-06-12 11:31:02",
  "deviceGroups": {
    "total": 4,
    "online": {
      "status": "online",
      "statusName": "在线",
      "count": 2,
      "items": [
        {
          "robotId": "robot-001",
          "clientId": "robot-media-client",
          "name": "松灵四轮机器人",
          "type": "轮式机器人",
          "typeCode": "WHEELED_ROBOT",
          "vendor": "SONGLING",
          "model": "SCOUT",
          "status": "online",
          "battery": 100,
          "fault": false,
          "alarmLevel": null,
          "alarmText": "-",
          "locationText": "A区主干道",
          "currentTaskId": "task-001",
          "currentTaskName": "A区-夜间巡逻"
        }
      ]
    },
    "offline": {
      "status": "offline",
      "statusName": "离线",
      "count": 2,
      "items": []
    }
  }
}
```

### 5.5 任务列表接口

```http
GET /api/bigscreen/panorama/tasks
```

返回结构：

```json
{
  "serverTime": "2026-06-12 11:31:02",
  "total": 3,
  "items": [
    {
      "taskId": "task-001",
      "name": "A区-夜间巡逻",
      "status": "running",
      "statusName": "执行中",
      "startTime": "2026-06-12 20:00:00",
      "endTime": "2026-06-12 22:00:00",
      "timeRange": "20:00-22:00",
      "currentLocation": "A区主干道",
      "equipmentList": []
    }
  ]
}
```

### 5.6 告警列表接口

```http
GET /api/bigscreen/panorama/alarms
```

返回结构：

```json
{
  "serverTime": "2026-06-12 11:31:02",
  "alarms": {
    "total": 15,
    "high": {
      "level": "HIGH",
      "levelName": "高风险",
      "count": 5,
      "items": [
        {
          "alarmId": "alarm-001",
          "title": "发生火灾",
          "category": "BUSINESS",
          "categoryName": "业务告警",
          "level": "HIGH",
          "levelName": "高风险",
          "eventTime": "2023-08-01 10:00:00",
          "location": "A区仓库",
          "robotId": "robot-003",
          "deviceName": "巡检机器人一号",
          "taskId": "task-002",
          "taskName": "A区-仓库复核",
          "status": "unhandled",
          "snapshotUrl": null
        }
      ]
    },
    "medium": {
      "level": "MEDIUM",
      "levelName": "中风险",
      "count": 5,
      "items": []
    },
    "low": {
      "level": "LOW",
      "levelName": "低风险",
      "count": 5,
      "items": []
    }
  }
}
```

## 6. 动态数据回显

推荐采用：

```text
REST 首屏全量快照
  +
WebSocket 增量事件
  +
低频兜底刷新
```

WebSocket：

```text
WS /ws/bigscreen
```

统一事件结构：

```json
{
  "type": "panorama.device.status.changed",
  "eventId": "evt-001",
  "timestamp": "2026-06-12 11:31:10",
  "payload": {}
}
```

建议事件类型：

| 事件类型 | 用途 |
|---|---|
| `panorama.device.status.changed` | 设备在线、离线、故障、电量变化 |
| `panorama.device.location.changed` | 地图位置、速度、朝向变化 |
| `panorama.task.changed` | 任务创建、更新、删除、状态变化、设备任务关联变化 |
| `panorama.alarm.changed` | 告警创建、更新、处置状态变化 |
| `panorama.stats.changed` | 左侧统计卡片变化 |

设备状态事件示例：

```json
{
  "type": "panorama.device.status.changed",
  "eventId": "evt-001",
  "timestamp": "2026-06-12 11:31:10",
  "payload": {
    "robotId": "robot-001",
    "status": "online",
    "battery": 96,
    "fault": false,
    "alarmLevel": null
  }
}
```

设备位置事件示例：

```json
{
  "type": "panorama.device.location.changed",
  "eventId": "evt-002",
  "timestamp": "2026-06-12 11:31:11",
  "payload": {
    "robotId": "robot-001",
    "location": {
      "lng": 113.923556,
      "lat": 22.512385,
      "altitude": null,
      "address": "A区主干道",
      "updatedAt": "2026-06-12 11:31:11"
    },
    "speed": 0.6,
    "heading": 92
  }
}
```

任务变化事件示例：

```json
{
  "type": "panorama.task.changed",
  "eventId": "evt-003",
  "timestamp": "2026-06-12 11:31:15",
  "payload": {
    "changeType": "status_changed",
    "scope": "both",
    "taskId": "task-001",
    "robotIds": ["robot-001"],
    "task": {
      "taskId": "task-001",
      "name": "A区-夜间巡逻",
      "status": "running",
      "statusName": "执行中",
      "timeRange": "20:00-22:00",
      "currentLocation": "A区主干道"
    }
  }
}
```

告警变化事件示例：

```json
{
  "type": "panorama.alarm.changed",
  "eventId": "evt-004",
  "timestamp": "2026-06-12 11:31:18",
  "payload": {
    "changeType": "created",
    "scope": "device",
    "alarmId": "alarm-001",
    "robotIds": ["robot-003"],
    "alarm": {
      "alarmId": "alarm-001",
      "title": "发生火灾",
      "category": "BUSINESS",
      "categoryName": "业务告警",
      "level": "HIGH",
      "levelName": "高风险",
      "eventTime": "2023-08-01 10:00:00",
      "location": "A区仓库",
      "robotId": "robot-003",
      "deviceName": "巡检机器人一号",
      "taskId": "task-002",
      "taskName": "A区-仓库复核",
      "status": "unhandled",
      "snapshotUrl": null
    }
  }
}
```

统计变化事件示例：

```json
{
  "type": "panorama.stats.changed",
  "eventId": "evt-005",
  "timestamp": "2026-06-12 11:31:20",
  "payload": {
    "deviceStats": {
      "total": 22,
      "online": 19,
      "fault": 1,
      "offline": 2
    },
    "deviceTypeStats": [
      {
        "type": "ROBOT_DOG",
        "name": "机器狗",
        "count": 8
      }
    ],
    "alarmStats": {
      "high": 5,
      "medium": 5,
      "low": 5
    }
  }
}
```

位置类事件需要限频，建议 1 秒最多推一次，或者仅在设备坐标、速度、方向发生明显变化时推送。

## 7. 实现与测试

当前已在 BFF 中实现 mock 版接口：

```text
bigscreen-bff/src/main/java/com/robot/bigscreen/panorama/PanoramaController.java
bigscreen-bff/src/main/java/com/robot/bigscreen/panorama/PanoramaMockService.java
```

启动 BFF：

```bash
cd bigscreen-bff
mvn spring-boot:run
```

本机测试：

```bash
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/overview | jq
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/devices/robot-001 | jq
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/device-groups | jq
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/tasks | jq
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/alarms | jq
```

通过 Nginx 测试：

```bash
curl -skS https://192.168.124.77:4443/api/bigscreen/panorama/overview | jq
```

如果 `127.0.0.1:8090` 通，而 Nginx 入口不通，优先检查 Nginx upstream 到 `host.docker.internal:8090`。

如果 Nginx 返回 `502`，检查 `/var/log/nginx/error.log`。已知代理响应需要过滤 hop-by-hop headers，例如：

```text
Transfer-Encoding
Connection
Content-Length
Upgrade
```

否则可能出现重复 `Transfer-Encoding: chunked` 导致 Nginx 502。

## 8. 后续演进

第一阶段：

- BFF 独立启动。
- `/api/control/robots` 在 BFF 对外移除，前端改从 `/api/bigscreen/panorama/overview.devices` 获取机器人列表。
- 其他 `/api/control/**` 可按迁移节奏继续短期兼容。
- `/api/bigscreen/panorama/**` 返回 mock 页面模型。
- 全景地图前端改调用 `/api/bigscreen/panorama/overview`。

第二阶段：

- BFF 接中心端已有机器人接口，填充设备数量、设备类型、地图点位。
- 任务、告警如中心端暂无接口，继续 mock 或返回空集合。
- 前端全景地图 tab 将静态数据替换为 `/api/bigscreen/panorama/overview`。

第三阶段：

- 中心端任务、告警、位置服务接入。
- BFF WebSocket 将中心端事件转换为 `panorama.*` 页面事件。
- 前端从轮询切到 REST 快照 + WebSocket 增量。

第四阶段：

- 前端路径全部收口到 `/api/bigscreen/**`。
- BFF 内部 adapter 分别对接 `center-manage`、`center-control`、`center-media`、`center-task`、`center-alarm`。
