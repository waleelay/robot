# 大屏 BFF 全景地图设计说明书

设备数据权威来源、用户设备权限、MQTT 运行态、REST/WebSocket 过滤及缓存隔离的
统一约束见[大屏设备数据权限与缓存边界设计说明书](大屏设备数据权限与缓存边界设计说明书.md)。
本文只维护全景地图页面模型和接口流程；两份文档冲突时，以权限与缓存边界说明书为准。

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

### 2.1 当前部署边界

当前本地只需要一个 Nginx，作为浏览器入口网关：

```text
浏览器
  -> https://<gateway-host>:<https-port>
  -> Nginx

Nginx /                 -> frontend/dist
Nginx /api/*            -> bigscreen-bff:8090
Nginx /ws/control       -> bigscreen-bff:8090
Nginx /ws/bigscreen     -> bigscreen-bff:8090
Nginx /livekit/*        -> LiveKit:7880

bigscreen-bff:8090
  -> control-service:8082
  -> backend/media-service:8088
```

前端到 BFF 建议继续走 Nginx，因为浏览器需要 HTTPS/WSS 安全上下文，尤其是麦克风、对讲和 WebRTC 场景。

BFF 到 Control Service 与 Media Service 不建议走 Nginx，应直接使用内部 HTTP 地址：

```text
CENTER_MANAGE_BASE_URL=http://<management-host>:<port>
CENTER_CONTROL_BASE_URL=http://<control-host>:8082
CENTER_MEDIA_BASE_URL=http://<media-host>:8088
CENTER_CONTROL_WS_URL=ws://<control-host>:8082/ws/control
```

不要让 BFF 再调用：

```text
https://<gateway-host>:<https-port>/api/...
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
BFF  -> /api/control/*、/api/media/*、/api/manage/*
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
- 全景顶层编排与下游 I/O 使用隔离执行器；禁止任务向自身所在的等待型线程池提交子任务后再 `join()`。
- Overview 从进入 BFF 起共享 8 秒截止时间；必需设备数据超时返回明确失败，可选数据按降级语义返回，不能无限等待浏览器断开。
- 管理端分页优先按 `total` 结束；重复页或无进展必须快速中断，不能空转到安全页上限。
- 全量固定摄像头在单次请求内复用并按 `mapId` 分组，避免按地图重复查询；地图点位在地图列表返回后立即并发预取。
- 设备档案、设备能力、组织信息可做 1 到 5 秒短缓存。
- 实时状态优先走 WebSocket 或事件总线，减少高频轮询。
- 高频控制帧走 WebSocket。
- 视频、音频、HLS、录像文件、图片大文件不经 BFF 中转。
- BFF 应设置中心端调用超时，例如 300ms 到 1s，避免单个慢服务拖垮首屏。
- 某个中心服务异常时，BFF 可以返回部分数据和降级标识。

## 5. 全景地图接口设计

### 5.1 当前接口

当前 BFF 已实现以下聚合接口。设备集合严格以当前用户从 Management 查询到的
授权设备为准，旧版实时状态服务和 Control 内存注册表只补充这些设备的实时字段，
不得向集合追加管理端未授权设备。REST 不使用整套 mock 快照兜底；字段没有权威
来源时按字段类型返回 `null` 或空数组。仅 WebSocket 对三个明确的演示机器人保留
硬编码位置，详见 5.7 节。

```text
GET /api/bigscreen/panorama/overview
GET /api/bigscreen/panorama/devices/{deviceId}/mounted-device-count
GET /api/bigscreen/panorama/tasks
GET /api/bigscreen/panorama/alarms
```

机器人基础字段统一聚合到 `/api/bigscreen/panorama/overview.devices` 中；数据来源为平台管理设备和控制端实时状态。历史 `/api/control/robots` 列表接口已移除，不再作为字段语义来源。

### 5.2 首屏聚合接口

```http
GET /api/bigscreen/panorama/overview
```

用途：一次返回全景地图 tab 页首屏需要的数据。

`devices[]` 不再使用 mock 数据兜底；未查询到的标量字段返回 `null`，数组字段返回空数组。前端直接根据 `devices[].location.lng/lat` 筛选 GPS 设备，聚合响应不再重复返回 `gpsDevices[]`。

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
  "patrolOverview": {
    "durationToday": 32.6,
    "durationUnit": "小时",
    "mileageToday": 262.6,
    "mileageUnit": "KM"
  },
  "taskOverview": {
    "totalToday": 50,
    "completedRateText": "100%",
    "running": 48,
    "pending": 2
  },
  "devices": [
    {
      "robotId": "test111",
      "name": "R1轮式机器人",
      "type": "轮式机器人",
      "typeCode": "WHEELED_ROBOT",
      "model": "SCOUT",
      "status": "online",
      "battery": 100,
      "cameras": [
        {
          "cameraId": "camera01",
          "deviceId": "camera01",
          "groupType": "dual_gimbal",
          "name": "前向双光云台",
          "quality": "sub"
        }
      ],
      "stateSeq": 1,
      "fault": false,
      "alarmLevel": null,
      "controlMode": "手动模式",
      "mountedDeviceCount": 3,
      "speed": null,
      "location": {
        "lng": 106.03655278081857,
        "lat": 30.7478613352993,
        "x": 118.4,
        "y": 42.8,
        "z": 0.0,
        "address": "A区主干道"
      }
    }
  ],
  "tasks": [
	    {
	      "taskId": 1,
	      "workflowInstanceId": 9001,
	      "name": "A区-夜间巡逻",
	      "executionMode": "SCHEDULE",
	      "expectedDurationSeconds": 7200,
	      "status": "running",
      "statusName": "执行中",
      "startTime": "2026-06-12 20:00:00",
      "endTime": "2026-06-12 22:00:00",
      "timeRange": "20:00-22:00",
      "currentLocation": "A区主干道",
      "equipmentList": [
        {
          "robotId": "test111",
          "name": "R1轮式机器人",
          "type": "WHEELED_ROBOT",
          "status": "online"
        }
      ],
      "mapId": 1
    }
  ],
  "alarms": {
    "total": 15,
    "summary": {
      "totalToday": 50,
      "handled": 18,
      "unhandled": 0,
      "handleRateText": "100%"
    },
    "high": {
      "items": [
        {
          "alarmId": "alarm-001",
          "title": "发生火灾",
          "categoryName": "业务告警",
          "level": "HIGH",
          "levelName": "高风险",
          "eventTime": "2023-08-01 10:00:00",
          "location": {
            "lng": 106.03655278081857,
            "lat": 30.7478613352993,
            "x": 118.4,
            "y": 42.8,
            "z": 0.0,
            "address": "A区主干道"
          },
          "robotId": "test111",
          "deviceName": "R1轮式机器人",
          "taskId": "task-002",
          "taskName": "A区-仓库复核",
          "status": "unhandled",
          "snapshotUrl": {
            "visible": "/api/media/files/file_04a5e988115c40a68f1b697622375979/content",
            "thermal": "/api/media/files/file_3c13535973f94599b34b77e2fd5bf647/content",
            "front": "/api/media/files/file_1e82f88847c545579fd1850ce753b8c0/content"
          }
        }
      ]
    },
    "medium": {
      "items": []
    },
    "low": {
      "items": []
    }
  },
  "map": [
    {
      "id": 1,
      "mapName": "A区巡逻地图",
      "fileId": 1001,
      "previewWidth": 314,
      "previewHeight": 352,
      "resolution": 0.1,
      "originX": -21.7,
      "originY": -16.0,
      "originYaw": 0.0,
      "previewGeneratedAt": "2026-06-12 11:31:02"
    }
  ]
}
```

Overview 的 `map[]` 只返回地图摘要。当前地图的 `points/deviceIds/fixedCamares` 由
`GET /api/bigscreen/panorama/maps/{mapId}/resources` 按需返回，非当前地图不预取。

`patrolOverview` 字段说明：

| 字段 | 含义 | 页面显示 |
|---|---|---|
| `patrolOverview.durationToday` | 今日巡逻总时长数值 | 今日巡逻时长 |
| `patrolOverview.durationUnit` | 巡逻时长单位 | 小时 |
| `patrolOverview.mileageToday` | 今日巡逻总里程数值 | 今日巡逻里程 |
| `patrolOverview.mileageUnit` | 巡逻里程单位 | KM |

`taskOverview` 字段说明：

| 字段 | 含义 | 页面显示 |
|---|---|---|
| `taskOverview.totalToday` | 今日任务总数 | 今日任务 |
| `taskOverview.completedRateText` | 完成率展示文本 | 完成率 |
| `taskOverview.running` | 执行中的任务数 | 执行中 |
| `taskOverview.pending` | 待执行的任务数 | 待执行 |

`tasks[]` 来源于 `/api/v1/management/task-workflow-plans?pageNum={pageNum}&pageSize=100` 的完整分页任务计划列表。

`tasks[]` 不再使用 mock 数据兜底；`timeRange`、`mapId` 等未查询到的标量字段返回 `null`，
`equipmentList` 无数据时返回空数组。Overview 不返回 `currentLocation/mapPoints/pathPoints`。

| 字段 | 含义 | 数据来源 |
|---|---|---|
| `tasks[]` | 任务列表 | `/api/v1/management/task-workflow-plans?pageNum={pageNum}&pageSize=100`，读取完整分页 |
| `tasks[].taskId` | 任务计划 ID，number/int | `/api/v1/management/task-workflow-plans` 的 `id` |
| `tasks[].workflowInstanceId` | 当前任务实例 ID，number/int/null；用于暂停、恢复、终止任务实例 | `/api/v1/management/task-workflow-plans` 的 `activeWorkflowInstanceId/lastWorkflowInstanceId/workflowInstanceId` |
| `tasks[].mapId` | 任务关联地图 ID，number/int/null | 任务计划的 `mapId/mapID`；定义解析由按需路径/详情接口完成 |
| `map` | 可用地图数组 | `/api/v1/management/maps?pageNum=1&pageSize=500&enabled=true` 的 `data.records` |
| 当前地图渲染资源 | 点位、关联设备 ID、固定摄像头 | `/panorama/maps/{mapId}/resources` 按需返回 |

历史 REST mock 点位表不再是当前 REST 数据源。当前代码只在 WebSocket `robot.state` 不带定位时，对 `test111`、`SN005`、`SN006` 生成演示位置；以下旧表不得作为当前接口期望：

| robotId | lat | lng | x | y | z |
|---|---:|---:|---:|---:|---:|
| `test111` | 30.745330 | 106.039428 | 118.4 | 42.8 | 0.0 |
| `SN006` | 30.746587087515316 | 106.03824884204943 | 82.6 | 156.2 | 0.0 |
| `robot-unitree-001` | 30.7469491 | 106.0344109 | -64.3 | 198.5 | 0.0 |

`location.lng/lat/altitude` 用于地图经纬度定位；`location.x/y/z` 用于室内图、三维场景或局部坐标系定位。

### 5.3 上装设备计数接口

```http
GET /api/bigscreen/panorama/devices/{deviceId}/mounted-device-count
```

用途：右侧弹窗已从 Overview 取得主体数据，但普通机器人的上装设备数量未知时，按 `robotId` 补查计数。固定摄像头不调用。

返回结构：

```json
{
  "robotId": "test111",
  "mountedDeviceCount": 3
}
```

接口不返回设备档案、运行态、组件明细、地图、任务或操作能力。

### 5.4 任务列表接口

```http
GET /api/bigscreen/panorama/tasks
```

返回结构：

```json
{
  "serverTime": "2026-06-12 11:31:02",
  "total": 5,
  "items": [
	    {
	      "taskId": 1,
	      "workflowInstanceId": 9001,
	      "name": "A区-夜间巡逻",
      "executionMode": "SCHEDULE",
      "expectedDurationSeconds": 7200,
      "status": "running",
      "statusName": "执行中",
      "startTime": "2026-06-12 20:00:00",
      "endTime": "2026-06-12 22:00:00",
      "timeRange": "20:00-22:00",
      "currentLocation": "A区主干道",
      "equipmentList": [],
      "mapId": 1,
      "mapPoints": [
        {"id": 101, "pointId": "point-101", "name": "A区主干道", "lng": 106.03655278081857, "lat": 30.7478613352993, "x": 118.4, "y": 42.8, "z": 0.0, "sequence": 1}
      ],
      "pathPoints": [
        {"id": 101, "pointId": "point-101", "name": "A区主干道", "lng": 106.03655278081857, "lat": 30.7478613352993, "x": 118.4, "y": 42.8, "z": 0.0, "sequence": 1}
      ]
    },
    {
      "taskId": 4,
      "name": "北侧消防通道巡检",
      "status": "running",
      "statusName": "执行中",
      "startTime": "2026-06-12 16:00:00",
      "endTime": "2026-06-12 17:30:00",
      "timeRange": "16:00-17:30",
      "currentLocation": "A区北侧消防通道",
      "equipmentList": [
        {
          "robotId": "SN006",
          "name": "G1四足机器狗",
          "type": "ROBOT_DOG",
          "status": "offline"
        }
      ],
      "mapId": 1,
      "mapPoints": [
        {"id": 101, "pointId": "point-101", "name": "A区主干道", "lng": 106.03655278081857, "lat": 30.7478613352993, "x": 118.4, "y": 42.8, "z": 0.0, "sequence": 1}
      ],
      "pathPoints": [
        {"id": 101, "pointId": "point-101", "name": "A区主干道", "lng": 106.03655278081857, "lat": 30.7478613352993, "x": 118.4, "y": 42.8, "z": 0.0, "sequence": 1}
      ]
    },
    {
      "taskId": 5,
      "name": "东侧出入口值守巡检",
      "status": "pending",
      "statusName": "待执行",
      "startTime": "2026-06-12 18:00:00",
      "endTime": "2026-06-12 19:00:00",
      "timeRange": "18:00-19:00",
      "currentLocation": "A区东侧出入口",
      "equipmentList": [
        {
          "robotId": "test111",
          "name": "R1轮式机器人",
          "type": "WHEELED_ROBOT",
          "status": "online"
        }
      ],
      "mapId": 1,
      "mapPoints": [
        {"id": 101, "pointId": "point-101", "name": "A区主干道", "lng": 106.03655278081857, "lat": 30.7478613352993, "x": 118.4, "y": 42.8, "z": 0.0, "sequence": 1}
      ],
      "pathPoints": [
        {"id": 101, "pointId": "point-101", "name": "A区主干道", "lng": 106.03655278081857, "lat": 30.7478613352993, "x": 118.4, "y": 42.8, "z": 0.0, "sequence": 1}
      ]
    }
  ]
}
```

### 5.5 告警列表接口

```http
GET /api/bigscreen/panorama/alarms
```

`alarms` 不再使用 mock 数据兜底；管理端未提供或链路未查询到的标量字段返回 `null`，分组数组字段返回空数组。

返回结构：

```json
{
  "serverTime": "2026-06-12 11:31:02",
  "alarms": {
    "total": 15,
    "summary": {
      "totalToday": 50,
      "handled": 18,
      "unhandled": 0,
      "handleRate": 100,
      "handleRateText": "100%"
    },
    "high": {
      "items": [
        {
          "alarmId": "alarm-001",
          "title": "发生火灾",
          "categoryName": "业务告警",
          "level": "HIGH",
          "levelName": "高风险",
          "eventTime": "2023-08-01 10:00:00",
          "location": null,
          "robotId": "test111",
          "deviceName": "R1轮式机器人",
          "taskId": "task-002",
          "taskName": null,
          "status": "unhandled",
          "snapshotUrl": null
        }
      ]
    },
    "medium": {
      "items": []
    },
    "low": {
      "items": []
    }
  }
}
```

字段说明：

| 字段 | 含义 | 页面显示 |
|---|---|---|
| `alarms.summary.totalToday` | 今日告警总数 | 今日告警 |
| `alarms.summary.handled` | 已处理告警数 | 已处理 |
| `alarms.summary.unhandled` | 未处理告警数 | 未处理 |
| `alarms.summary.handleRate` | 处理率数值，范围 `0-100` | 处理率计算、排序或判断 |
| `alarms.summary.handleRateText` | 处理率展示文本 | 处理率 |

处理率建议由 BFF 计算，计算口径为：

```text
handled / max(totalToday, 1) * 100
```

当 `totalToday = 0` 时，`handleRate` 返回 `100`，表示当前没有待处理告警；如果业务希望显示 `0%`，可在 BFF 中按产品口径调整。

### 5.6 告警处置接口

```http
POST /api/bigscreen/panorama/alarms/{alarmId}/handled
```

用途：大屏侧对指定告警进行处置。BFF 内部转调中心端告警处置接口，并保持大屏接口路径和参数稳定。

请求参数：

```json
{
  "disposalStatus": "IMMEDIATE_DISPOSAL",
  "handleResult": null
}
```

`disposalStatus` 可选值：

| 值 | 含义 | 告警状态建议映射 |
|---|---|---|
| `IMMEDIATE_DISPOSAL` | 立即处置 | `handled` |
| `FALSE_ALARM` | 误报 | `false_alarm` |

BFF 分别映射为管理端 `handleAction=HANDLE_NOW` 和
`handleAction=FALSE_ALARM`，`handleResult` 保留前端传入的 `null`。普通告警调用管理端
`PATCH /api/v1/management/alarms/{id}/handled`。

返回结构：

```json
{
  "success": true,
  "serverTime": "2026-06-12 11:31:02",
  "alarmId": "alarm-001",
  "disposalStatus": "IMMEDIATE_DISPOSAL",
  "disposalStatusName": "立即处置",
  "status": "handled",
  "message": "告警处置状态已更新"
}
```

参数错误返回：

```json
{
  "success": false,
  "code": "BAD_REQUEST",
  "message": "disposalStatus must be one of IMMEDIATE_DISPOSAL, FALSE_ALARM"
}
```

### 5.7 工作流告警接口

```http
GET /api/bigscreen/panorama/alarms/actionable-workflow
POST /api/bigscreen/panorama/alarms/{alarmId}/handle-and-continue
```

查询接口只返回管理端工作流当前正等待该告警人工节点的记录。处置接口请求字段与
5.6 相同，BFF 将 `IMMEDIATE_DISPOSAL/FALSE_ALARM` 映射为管理端
`HANDLE_NOW/FALSE_ALARM`，调用管理端 `handle-and-continue`，在同一事务中更新告警并
继续对应工作流；`handleResult` 显式传 `null`。稍后处理只关闭前端当前弹窗，
不调用处置接口。

工作流告警返回 `items[]`，保留大屏告警展示字段，并增加
`workflowInstanceId`、`taskName`、`humanTaskId`、`humanTaskName`。管理端图片未携带通道
类型时，BFF 仅将第一张 `imageFileIds[]` 映射为 `snapshotUrl.visible`，不推断
`thermal/front`。

告警列表只加载进入滚动可视区及其前后 100 px 范围的缩略图，折叠分组和未滚动到的告警不请求
`/api/bigscreen/control/files/{fileId}/content`；打开告警详情后再加载该告警的多路原图。前端按
`fileId` 合并重复请求并将文件内容请求限制为最多 4 路并发，避免大量未处理告警放大首屏请求。

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
/ws/control
/ws/media
/ws/bigscreen
```

三个路径当前都由同一 BFF Handler 桥接 Control `/ws/control`。BFF 原样返回上游消息，并对设备、任务、告警变化派生 `panorama.*`；没有独立“模拟所有动态事件”调度器。

统一事件结构：

```json
{
  "event": "panorama.device.status.changed",
  "timestamp": "2026-06-12 11:31:10",
  "data": {}
}
```

建议事件类型：

| 事件类型 | 用途 |
|---|---|
| `panorama.device.status.changed` | 设备状态、电量、控制模式、速度变化 |
| `panorama.device.location.changed` | 地图定位变化 |
| `panorama.task.changed` | 任务数据或任务状态变化 |
| `panorama.alarm.changed` | 告警数据或处置状态变化 |
| `panorama.stats.changed` | 左侧统计卡片变化 |

各事件的当前推送机制：

| 事件 | 数据来源与触发条件 | BFF 推送策略 |
|---|---|---|
| `panorama.device.status.changed` | Control 收到设备状态 MQTT 上报后广播 `robot.state` | 仅当 `robot.state` 来源为边缘状态（`stateSource=EDGE_DEVICE_STATUS`）或离线扫描（`stateSource=OFFLINE_SCAN`）时即时派生并推送，不受位置限频影响；媒体客户端来源（`stateSource=MEDIA_CLIENT_STATUS`）不派生该事件，机器人状态以边缘上报为准。在线、离线、故障等状态变化同时触发统计快照刷新。 |
| `panorama.device.location.changed` | `robot.state` 携带 `location/localization/status.localization` 时派生；联调设备 `test111`、`SN005`、`SN006` 在无真实定位时使用专用演示坐标 | 按“浏览器会话 + `robotId`”独立限频。首条立即推送；同一设备 1 秒内的多条位置只保留最新一条，每秒最多推送一次；`localized=false` 立即推送。没有新定位时不重复发送旧坐标。 |
| `panorama.task.changed` | 上游任务变更事件，或管理端 STOMP 任务通知转换的 `management.task.invalidated` | 具备完整任务计划 ID 的原始变更立即转换。失效通知以 300ms 去抖，按当前 WebSocket 会话身份重查管理端权威快照，逐项比较后只推送发生变化的任务；任务删除或失权时推送 `data.changeType=REMOVE`。`taskId` 缺失的旧版事件不直接下发。 |
| `panorama.alarm.changed` | 上游完整告警事件，或管理端 STOMP `alarm.changed.v1` 转换的 `management.alarm.invalidated` | 完整事件立即转换；失效通知以 300ms 去抖，按当前 WebSocket 会话身份重查管理端告警快照，只推送变化项；告警删除或失权时推送 `data.changeType=REMOVE`。没有真实上游事件时不生成模拟告警。 |
| `panorama.stats.changed` | 设备业务变更、设备在线/离线/故障状态切换、任务或告警变更 | 短时间内的多次触发合并 500ms 后按事件类型只重算受影响统计块（设备/任务/告警），推送仍为完整合并快照，只在快照与上次不同时推送。各统计块带 3 秒 TTL 缓存（按用户隔离），多会话与多事件在窗口内共享一次管理端查询。普通电量、速度、位置心跳不触发统计刷新。 |

Control 对 `totalMileage/currentMileage` 计算出的有效里程增量累计达到配置阈值时，
广播 `robot.mileage.changed`。BFF 收到后沿用上述统计刷新机制，输出更新后的
`panorama.stats.changed`；原始里程事件仍会透传。

BFF 仍会原样转发上游消息，上表只描述追加生成的 `panorama.*` 事件。浏览器会话关闭后，其待发位置事件和统计/任务刷新状态一并清理。

设备状态事件示例：

```json
{
  "event": "panorama.device.status.changed",
  "timestamp": "2026-06-12 11:31:10",
  "data": {
    "robotId": "test111",
    "status": "online",
    "battery": 96,
    "controlMode": "手动模式",
    "speed": 0.6
  }
}
```

设备位置事件示例：

```json
{
  "event": "panorama.device.location.changed",
  "timestamp": "2026-06-12 11:31:11",
  "data": {
    "robotId": "test111",
    "location": {
      "lng": 113.923556,
      "lat": 22.512385,
      "altitude": null,
      "x": 118.4,
      "y": 42.8,
      "z": 0.0,
      "address": "A区主干道",
      "updatedAt": "2026-06-12 11:31:11"
    }
  }
}
```

任务变化事件示例：

```json
{
  "event": "panorama.task.changed",
  "timestamp": "2026-06-12 11:31:15",
  "data": {
    "taskId": 1,
    "workflowInstanceId": 1001,
    "robotId": "PATROL-001",
    "status": "running",
    "statusName": "执行中",
    "currentLocation": "x:9.2,y:7.8",
    "location": {"x": 9.2, "y": 7.8, "z": 0, "yaw": 88}
  }
}
```

当前任务与告警桥接方案：本项目 Control 作为 STOMP 客户端连接同事的
`eiop-control-service:/ws/control`，固定订阅 `/topic/platform/realtime-events`。
收到 `task.changed.v1` 且 `scopes` 包含 `PLAN` 或 `EXECUTION` 时，向本地
`/ws/control` 推送 `management.task.invalidated`；收到 `alarm.changed.v1` 时推送
`management.alarm.invalidated`。BFF 收到失效通知后防抖查询管理端权威快照并比较，
将变化项转换为现有 `panorama.task.changed` 或 `panorama.alarm.changed` 结构。STOMP
仅负责通知“数据已变化”，任务和告警业务状态始终以管理端 HTTP 查询结果为准。

`taskId` 始终表示任务计划 ID，`workflowInstanceId` 表示本次执行实例 ID。
兼容旧版 Control 时，若 BFF 收到 `taskId` 为空的 `panorama.task.changed`，
不向前端转发该残缺事件，而是重查管理端任务计划与实例，通过
`activeWorkflowInstanceId/lastWorkflowInstanceId` 完成关联后再推送完整事件。

告警变化事件示例：

```json
{
  "event": "panorama.alarm.changed",
  "timestamp": "2026-06-12 11:31:18",
  "data": {
    "alarmId": "alarm-001",
    "summary": {
      "totalToday": 50,
      "handled": 18,
      "unhandled": 0,
      "handleRate": 100,
      "handleRateText": "100%"
    },
    "alarm": {
      "alarmId": "alarm-001",
      "title": "发生火灾",
      "categoryName": "任务告警",
      "level": "HIGH",
      "levelName": "高风险",
      "eventTime": "2023-08-01 10:00:00",
      "location": {
        "lng": 106.03655278081857,
        "lat": 30.7478613352993,
        "altitude": null,
        "x": 118.4,
        "y": 42.8,
        "z": 0.0,
        "address": "A区主干道",
        "updatedAt": "2026-06-12 11:30:58"
      },
      "robotId": "test111",
      "deviceName": "R1轮式机器人",
      "taskId": "task-002",
      "taskName": "A区-仓库复核",
      "status": "unhandled",
      "snapshotUrl": {
        "visible": "/api/media/files/file_04a5e988115c40a68f1b697622375979/content",
        "thermal": "/api/media/files/file_3c13535973f94599b34b77e2fd5bf647/content",
        "front": "/api/media/files/file_1e82f88847c545579fd1850ce753b8c0/content"
      }
    }
  }
}
```

任务或告警从当前用户权威快照中消失时，BFF 推送移除事件；`REMOVE` 事件不再携带
已经失效的完整业务对象：

```json
{
  "event": "panorama.task.changed",
  "timestamp": "2026-06-12 11:32:00",
  "data": {"taskId": 1, "changeType": "REMOVE"}
}
```

```json
{
  "event": "panorama.alarm.changed",
  "timestamp": "2026-06-12 11:32:01",
  "data": {
    "alarmId": "alarm-001",
    "changeType": "REMOVE",
    "summary": {"totalToday": 49, "handled": 18, "unhandled": 31}
  }
}
```

统计变化事件示例：

```json
{
  "event": "panorama.stats.changed",
  "timestamp": "2026-06-12 11:31:20",
  "data": {
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
    "patrolOverview": {
      "durationToday": 32.6,
      "durationUnit": "小时",
      "mileageToday": 262.6,
      "mileageUnit": "KM"
    },
    "taskOverview": {
      "totalToday": 50,
      "completedRate": 100,
      "completedRateText": "100%",
      "running": 48,
      "pending": 2
    },
    "alarmStats": {
      "high": 5,
      "medium": 5,
      "low": 5
    },
    "alarmSummary": {
      "totalToday": 50,
      "handled": 18,
      "unhandled": 0,
      "handleRate": 100,
      "handleRateText": "100%"
    }
  }
}
```

位置类事件已在 BFF 按设备实施 1 Hz 上限和最新值合并；该限频不会制造新位置，也不会在无新数据时重复推送旧坐标。

## 7. 测试与验收

构建、接口检查和代理排障见 [Bigscreen BFF README](../../../bigscreen-bff/README.md)，测试范围和退出条件见[大屏 BFF 测试方案](../../04-测试与验收/测试方案/大屏BFF测试方案.md)。

## 8. 后续演进

第一阶段：

- BFF 独立启动。
- `/api/control/robots` 在 BFF 对外移除，前端改从 `/api/bigscreen/panorama/overview.devices` 获取机器人列表。
- 其他 `/api/control/**` 可按迁移节奏继续短期兼容。
- `/api/bigscreen/panorama/**` 返回真实查询聚合模型；未查询到的字段保留 `null` 或空数组。
- 全景地图前端改调用 `/api/bigscreen/panorama/overview`。

第二阶段：

- BFF 接中心端已有机器人接口，填充设备数量、设备类型、地图点位。
- 任务、告警如中心端暂无接口，返回空集合或 `null` 字段。
- 前端全景地图 tab 将静态数据替换为 `/api/bigscreen/panorama/overview`。

第三阶段：

- BFF 不再本地模拟推送全量 `panorama.*` 动态事件。
- `/ws/control` 保留到 Control Service WebSocket 的桥接能力；Control Service WebSocket 暂不可用时，不推送本地假数据。
- BFF 已将中心端 `robot.state` 转换为 `panorama.device.status.changed` 并追加转发给前端；当 `robot.state` 携带定位或任务字段时，同步追加 `panorama.device.location.changed`、`panorama.task.changed`。
- 当前控制服务并行订阅 `eiop/v1/edge/{serialNumber}/status`，转换并广播 `robot.state`。BFF 将其中的健康、速度、电量、控制模式和 SLAM `x/y/z/yaw/mapId` 等字段继续转换为全景事件，无需前端直接订阅 MQTT。
- 联调期仅针对 `robotId=test111` 保留定位兜底：当中心端 `robot.state` 未携带定位数据时，BFF 按三组 XYZ 坐标循环追加 `panorama.device.location.changed`。
- BFF 在设备状态发生切换以及收到设备、任务、告警变化事件后，延迟 500ms 合并刷新统计；按事件类型只重算受影响统计块（设备/任务/告警），各块带 3 秒 TTL 缓存（按用户隔离），多会话共享；统计数据与 `/api/bigscreen/panorama/overview` 使用相同管理端数据源和计算口径。新旧统计快照一致时不推送，普通电量、速度、位置心跳不触发统计刷新。
- `panorama.stats.changed` 推送完整的 `deviceStats`、`deviceTypeStats`、`patrolOverview`、`taskOverview`、`alarmStats`、`alarmSummary`，前端仅更新事件实际携带的统计块。
- 如果中心端推送 `task.*`、`alarm.*` 原始事件，BFF 会转换为 `panorama.task.changed`、`panorama.alarm.changed`；没有真实原始事件源时不生成本地假数据。
- 后续前端继续从只打印事件演进为消费更多 `panorama.*`，形成 REST 快照 + WebSocket 增量。

第三阶段后半段：

- 中心端任务、告警、位置服务接入。
- BFF WebSocket 继续补齐任务、告警、位置等中心端事件到 `panorama.*` 页面事件的转换。
- 前端从 `/ws/control` 收口到 `/ws/bigscreen`。

第四阶段：

- 前端路径全部收口到 `/api/bigscreen/**`。
- BFF 内部 adapter 分别对接 `center-manage`、`center-control`、`center-media`、`center-task`、`center-alarm`。
