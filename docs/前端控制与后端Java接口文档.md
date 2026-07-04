# Frontend、Control 与 Backend Java 接口文档（当前代码版）

本文按当前仓库 Java 代码反查整理，生成时间：2026-07-04。

## 1. 范围说明

`frontend` 和 `robot-ui` 是 Vue 前端工程，本身不包含 Java Controller。本文中的“Frontend 接口”指前端当前访问的 Java BFF/API：

- `bigscreen-bff`：大屏前端 BFF，包含 `/api/bigscreen/**` 聚合接口和代理接口。
- `control-service`：控制侧服务，包含 `/api/control/**` 设备控制、实时视频、文件接口，以及机器人在线状态/设备状态内存注册表。
- `backend`：媒体后端服务，包含 `/internal/media/video-sessions/**`、`/internal/media/files/**`、`/api/media/files/**`、`/api/media/tts/**`。

本文按当前 Controller 代码整理接口；设计文档中存在但当前 Java Controller 未暴露的路径不作为可调用接口列入正文。

当前前端存在少量遗留/调试调用，联调时需注意：

| 前端遗留路径 | 当前状态 |
|---|---|
| `/api/control/snapshots/**` | 当前 `control-service` 无 Snapshot Controller |
| `/api/control/recordings/**` | 已迁移到 `/api/control/files` 与 `/api/control/video-sessions/{sessionId}/recordings/**` |
| `/internal/media/video-sessions/{sessionId}/_mock/track-published/{trackSid}` | `backend` 已实现的调试接口，不属于 BFF/Control 对外接口 |

## 2. 通用约定

### 2.1 鉴权与用户上下文 Header

当前开发阶段由前端请求头模拟登录态；缺省值在 `CurrentUserResolver` 中定义。

| Header | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `X-User-Id` | string | 否 | 当前用户 ID；缺省 `dev-user` |
| `X-Org-Id` | string | 否 | 当前组织 ID；缺省取配置 |
| `X-Roles` | string | 否 | 逗号分隔角色；control 缺省 `MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR` |
| `X-Client-Id` | string | 否 | 浏览器标签页/终端实例 ID；缺省 `web` |

请求示例：

```http
X-User-Id: u1001
X-Org-Id: org001
X-Roles: MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR
X-Client-Id: web-1783120000000-abcd
Content-Type: application/json
```

### 2.2 通用枚举

| 枚举 | 可选值 | 说明 |
|---|---|---|
| `VideoChannel` | `visible`、`thermal`、`fusion` | 可见光、热成像、融合通道 |
| `VideoQuality` | `sub`、`main`、`auto` | 子码流、主码流、自动 |
| `VideoSessionStatus` | `INIT`、`REQUESTING_CLIENT`、`ROOM_READY`、`STREAMING`、`INTERRUPTED`、`IDLE_WAIT`、`STOPPING`、`CLOSED`、`TIMEOUT`、`FAILED` | 实时视频会话状态 |
| `IntercomStatus` | `IDLE`、`STARTING`、`ACTIVE`、`INTERRUPTED`、`STOPPING`、`FAILED` | 对讲状态 |
| `FileType` | `VIDEO`、`IMAGE`、`LOG`、`CONFIG`、`MAP`、`DOCUMENT`、`OTHER` | 文件类型 |
| `FileStatus` | `UPLOADING`、`PROCESSING`、`READY`、`FAILED`、`DELETED` | 文件状态 |
| `controlMode` | `MANUAL`、`ASSISTED`、`NAVIGATION` | 设备控制模式 |
| `disposalStatus` | `IMMEDIATE_DISPOSAL`、`FALSE_ALARM` | 告警处置状态；其他值会返回 `BAD_REQUEST` |

### 2.3 通用错误响应

本仓库 `backend` 的 media-service 有全局错误响应，格式如下。`control-service` 当前独立模块没有本地 `RestControllerAdvice`；独立部署时下游错误响应可能退回 Spring Boot 默认格式，建议后续补齐同款异常处理。`bigscreen-bff` 的告警处置参数错误返回 `success=false`。

```json
{
  "timestamp": "2026-07-04T10:30:00+08:00",
  "code": "VALIDATION_ERROR",
  "message": "请求参数校验失败"
}
```

### 2.4 通用响应模型

#### VideoSessionResponse

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | 视频会话 ID |
| `robotId` | string | 机器人 ID |
| `deviceId` | string | 摄像头或设备 ID |
| `channel` | enum | `VideoChannel` |
| `quality` | enum | `VideoQuality` |
| `status` | enum | `VideoSessionStatus` |
| `roomName` | string | LiveKit Room 名称 |
| `livekitUrl` | string | LiveKit 连接地址 |
| `viewerToken` | string/null | 观看 Token；部分接口返回为空 |
| `trackSid` | string/null | LiveKit Track SID |
| `trackName` | string/null | Track 名称 |
| `viewerCount` | number | 当前观看者数量 |
| `intercomStatus` | enum | `IntercomStatus` |
| `intercomAudioOnly` | boolean | 是否仅对讲音频会话 |
| `intercomOperatorId` | string/null | 当前对讲操作员 |
| `robotAudioTrackSid` | string/null | 机器人音频 Track SID |
| `robotAudioTrackName` | string/null | 机器人音频 Track 名称 |
| `lastErrorCode` | string/null | 最近错误码 |
| `lastErrorMessage` | string/null | 最近错误描述 |
| `createdAt` | datetime | 创建时间 |
| `updatedAt` | datetime | 更新时间 |

示例：

```json
{
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "channel": "visible",
  "quality": "sub",
  "status": "STREAMING",
  "roomName": "media.test001.camera01.visible",
  "livekitUrl": "ws://127.0.0.1:7880",
  "viewerToken": "eyJhbGciOi...",
  "trackSid": "TR_VC_001",
  "trackName": "video.visible.sub",
  "viewerCount": 1,
  "intercomStatus": "IDLE",
  "intercomAudioOnly": false,
  "intercomOperatorId": null,
  "robotAudioTrackSid": null,
  "robotAudioTrackName": null,
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "createdAt": "2026-07-04T10:00:00+08:00",
  "updatedAt": "2026-07-04T10:01:00+08:00"
}
```

#### IntercomResponse

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | 绑定的视频会话 ID |
| `robotId` | string | 机器人 ID |
| `deviceId` | string | 摄像头或设备 ID |
| `roomName` | string | LiveKit Room |
| `videoStatus` | enum | `VideoSessionStatus` |
| `intercomStatus` | enum | `IntercomStatus` |
| `intercomAudioOnly` | boolean | 是否音频-only |
| `livekitUrl` | string | LiveKit 地址 |
| `operatorToken` | string | 操作员对讲 Token |
| `expiresAt` | datetime | Token 过期时间 |

示例：

```json
{
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "roomName": "media.test001.camera01.visible",
  "videoStatus": "STREAMING",
  "intercomStatus": "ACTIVE",
  "intercomAudioOnly": false,
  "livekitUrl": "ws://127.0.0.1:7880",
  "operatorToken": "eyJhbGciOi...",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

#### FileListItemResponse

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` | string | 文件 ID |
| `robotId` | string/null | 机器人 ID |
| `deviceId` | string/null | 设备 ID |
| `taskExecutionId` | string/null | 任务执行 ID |
| `fileType` | string | 文件类型 |
| `fileName` | string | 文件名 |
| `contentType` | string | MIME 类型 |
| `fileSize` | number | 文件大小，字节 |
| `durationSeconds` | number/null | 视频时长 |
| `startedAt` | datetime/null | 录像/任务开始时间 |
| `endedAt` | datetime/null | 录像/任务结束时间 |
| `width` | number/null | 图片/视频宽度 |
| `height` | number/null | 图片/视频高度 |
| `status` | string | 文件状态 |
| `videoStatus` | string/null | 视频处理状态 |
| `errorCode` | string/null | 错误码 |
| `uploadedAt` | datetime/null | 上传完成时间 |
| `createdAt` | datetime | 创建时间 |
| `metadata` | string/null | 业务扩展元数据，通常为 JSON 字符串 |

示例：

```json
{
  "fileId": "file_001",
  "robotId": "test001",
  "deviceId": "camera01",
  "taskExecutionId": null,
  "fileType": "IMAGE",
  "fileName": "snapshot.jpg",
  "contentType": "image/jpeg",
  "fileSize": 245760,
  "durationSeconds": null,
  "startedAt": null,
  "endedAt": null,
  "width": 1920,
  "height": 1080,
  "status": "READY",
  "videoStatus": null,
  "errorCode": null,
  "uploadedAt": "2026-07-04T10:02:00+08:00",
  "createdAt": "2026-07-04T10:02:00+08:00",
  "metadata": "{\"source\":\"snapshot\"}"
}
```

## 3. Frontend / Bigscreen BFF Java 接口

### 3.1 接口清单

| 方法 | 路径 | Controller | 说明 |
|---|---|---|---|
| GET | `/api/bigscreen/panorama/overview` | `PanoramaController` | 全景地图总览 |
| GET | `/api/bigscreen/panorama/devices/{deviceId}` | `PanoramaController` | 设备详情 |
| GET | `/api/bigscreen/panorama/tasks` | `PanoramaController` | 任务列表 |
| GET | `/api/bigscreen/panorama/alarms` | `PanoramaController` | 告警聚合 |
| POST | `/api/bigscreen/panorama/alarms/{alarmId}/disposal` | `PanoramaController` | 告警处置 |
| GET | `/api/bigscreen/statistics/overview` | `StatisticsController` | 数据统计总览 |
| POST | `/api/bigscreen/statistics/reports/export` | `StatisticsController` | 导出统计 PDF |
| GET | `/api/bigscreen/statistics/reports` | `StatisticsController` | 历史报告列表 |
| GET | `/api/bigscreen/statistics/reports/{id}/download` | `StatisticsController` | 下载历史报告 |
| DELETE | `/api/bigscreen/statistics/reports/{id}` | `StatisticsController` | 删除历史报告 |
| 任意 | `/api/control/robots` | `BigscreenProxyController` | 在 BFF 中显式移除，返回 410 |
| 任意 | `/api/control/**`、`/api/bigscreen/**`、`/api/media/**`、`/api/manage/**`、`/api/v1/management/**`、`/internal/media/**` | `BigscreenProxyController` | 透明代理到配置的后端服务 |

### 3.2 GET `/api/bigscreen/panorama/overview`

用途：返回大屏全景地图首屏所需的设备、任务、告警、统计和地图配置。

请求参数：无。

请求示例：

```http
GET /api/bigscreen/panorama/overview
```

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `serverTime` | string | 服务端时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `deviceStats` | object | 设备总数、在线、故障、离线统计 |
| `deviceTypeStats` | array | 按设备类型统计 |
| `patrolOverview` | object | 今日巡逻时长、里程 |
| `taskOverview` | object | 今日任务总数、完成率、运行/待执行数量 |
| `deviceGroups` | object | 在线/离线分组；当前作为 overview 内联字段返回 |
| `devices` | array | 设备摘要列表 |
| `tasks` | array | 任务列表 |
| `alarms` | object | 告警聚合，结构同 `/alarms` |
| `map` | object | 地图中心点、缩放和图层配置 |

响应示例：

```json
{
  "serverTime": "2026-07-04 10:00:00",
  "deviceStats": {"total": 12, "online": 10, "fault": 1, "offline": 1},
  "deviceTypeStats": [
    {"type": "ROBOT_DOG", "name": "机器狗", "count": 4, "fault": 1, "offline": 0}
  ],
  "patrolOverview": {"durationToday": 32.6, "durationUnit": "小时", "mileageToday": 262.6, "mileageUnit": "KM"},
  "taskOverview": {"totalToday": 50, "completedRate": 100, "completedRateText": "100%", "running": 48, "pending": 2},
  "devices": [
    {
      "robotId": "test001",
      "clientId": "robot-media-client",
      "name": "R1轮式机器人",
      "type": "轮式机器人",
      "typeCode": "WHEELED_ROBOT",
      "vendor": "SONGLING",
      "model": "SCOUT",
      "status": "offline",
      "battery": 100,
      "fault": true,
      "alarmLevel": "HIGH",
      "controlMode": "AUTO",
      "mountedDeviceCount": 3,
      "location": {"lng": 106.03824884204943, "lat": 30.746587087515316, "address": "A区仓库"},
      "mapDisplay": {"icon": "wheeled_robot", "label": "R1轮式机器人", "badgeText": "告警中", "badgeStatus": "alarm"},
      "task": {"taskId": "task-002", "name": "A区-仓库复核", "status": "paused", "timeRange": "10:00-11:30"}
    }
  ],
  "map": {
    "center": {"lng": 106.03655278081857, "lat": 30.7478613352993},
    "zoom": 17,
    "defaultLayer": "dark-vector",
    "updatedAt": "2026-07-04 10:00:00"
  }
}
```

### 3.3 GET `/api/bigscreen/panorama/devices/{deviceId}`

用途：查询单台设备详情。

Payload 字段：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `deviceId` | path | string | 是 | 设备/机器人 ID |

请求示例：

```http
GET /api/bigscreen/panorama/devices/test001
```

响应参数：在设备摘要基础上补充 `mountedDevices`、`currentTask`、`actions`。

响应示例：

```json
{
  "robotId": "test001",
  "clientId": "robot-media-client",
  "name": "R1轮式机器人",
  "type": "轮式机器人",
  "typeCode": "WHEELED_ROBOT",
  "status": "offline",
  "battery": 100,
  "alarmStatus": "HIGH",
  "alarmText": "存在未处理告警",
  "controlMode": "AUTO",
  "mountedDeviceCount": 3,
  "mountedDevices": [
    {"deviceId": "camera01", "name": "前向双光云台", "type": "DUAL_GIMBAL", "status": "online"},
    {"deviceId": "audio-control-001", "name": "客户端音频", "type": "CLIENT_AUDIO", "status": "online"},
    {"deviceId": "ptz-001", "name": "云台控制", "type": "PTZ", "status": "online"}
  ],
  "currentTask": {"taskId": "task-002", "name": "A区-仓库复核", "status": "paused", "timeRange": "10:00-11:30"},
  "actions": {
    "remoteControl": false,
    "slamMap": false,
    "returnHome": false,
    "returnChargingPile": false,
    "showPath": true,
    "showArea": true
  }
}
```

### 3.4 GET `/api/bigscreen/panorama/tasks`

用途：查询大屏任务列表。

请求示例：

```http
GET /api/bigscreen/panorama/tasks
```

响应示例：

```json
{
  "serverTime": "2026-07-04 10:00:00",
  "total": 1,
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
      "equipmentList": [
        {"robotId": "test001", "name": "R1轮式机器人", "type": "WHEELED_ROBOT", "status": "online"}
      ]
    }
  ]
}
```

### 3.5 GET `/api/bigscreen/panorama/alarms`

用途：查询告警统计和按风险等级分组的告警列表。

请求示例：

```http
GET /api/bigscreen/panorama/alarms
```

响应示例：

```json
{
  "serverTime": "2026-07-04 10:00:00",
  "alarms": {
    "total": 15,
    "summary": {"totalToday": 15, "handled": 15, "unhandled": 0, "handleRate": 100, "handleRateText": "100%"},
    "high": {
      "items": [
        {
          "alarmId": "alarm-001",
          "title": "发生火灾",
          "categoryName": "业务告警",
          "level": "HIGH",
          "levelName": "高风险",
          "eventTime": "2023-08-01 10:00:00",
          "location": {"address": "A区仓库"},
          "robotId": "test001",
          "deviceName": "R1轮式机器人",
          "taskId": "task-002",
          "taskName": "A区-仓库复核",
          "status": "unhandled"
        }
      ]
    },
    "medium": {"items": []},
    "low": {"items": []}
  }
}
```

### 3.6 POST `/api/bigscreen/panorama/alarms/{alarmId}/disposal`

用途：处置告警；BFF 会优先尝试调用管理中心，失败时返回 mock 更新结果。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `alarmId` | path | string | 是 | 告警 ID |
| `disposalStatus` | body | string | 是 | 处置状态，仅支持 `IMMEDIATE_DISPOSAL`、`FALSE_ALARM` |

请求示例：

```http
POST /api/bigscreen/panorama/alarms/alarm-001/disposal
Content-Type: application/json

{"disposalStatus": "IMMEDIATE_DISPOSAL"}
```

响应示例：

```json
{
  "success": true,
  "serverTime": "2026-07-04 10:00:00",
  "alarmId": "alarm-001",
  "disposalStatus": "IMMEDIATE_DISPOSAL",
  "disposalStatusName": "立即处置",
  "status": "handled",
  "message": "告警处置状态已更新"
}
```

### 3.7 GET `/api/bigscreen/statistics/overview`

用途：大屏统计总览。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `range` | query | string | 否 | `all`、`today`、`week`、`month`、`custom`；默认 `month` |
| `startTime` | query | string | 否 | 自定义开始时间，`yyyy-MM-dd HH:mm:ss` |
| `endTime` | query | string | 否 | 自定义结束时间 |
| `deviceType` | query | string | 否 | `all`、`UAV`、`ROBOT_DOG`、`UGV`、`HUMANOID_ROBOT`；默认 `all` |
| `areaId` | query | string | 否 | 区域 ID |

请求示例：

```http
GET /api/bigscreen/statistics/overview?range=month&deviceType=ROBOT_DOG
```

响应示例：

```json
{
  "serverTime": "2026-07-04 10:00:00",
  "range": {"type": "month", "startTime": "2026-07-01 00:00:00", "endTime": "2026-07-04 23:59:59"},
  "filters": {"deviceType": "ROBOT_DOG", "areaId": null},
  "kpis": {
    "taskTotal": {"value": 418, "compareRate": -5},
    "patrolMileage": {"value": 517.4, "compareRate": 12},
    "aiAlarmTotal": {"value": 415, "compareRate": 8},
    "autoHandleSuccessRate": {"value": 98, "compareRate": 2}
  },
  "equipmentRuntime": {
    "onlineRate": 98,
    "taskCompletionRate": 100,
    "unit": "小时",
    "items": [
      {"deviceType": "ROBOT_DOG", "deviceTypeName": "机器狗", "runningHours": 128, "faultHours": 20, "offlineHours": 26}
    ]
  },
  "aiAlarmAnalysis": {
    "alarmTypeRanking": [{"type": "FIGHT", "name": "打架斗殴", "count": 116, "percent": 28.0}],
    "handleMethodRanking": [{"method": "VOICE_BROADCAST", "name": "语音播报", "count": 139}]
  },
  "alarmAreaRanking": [{"areaId": "area-001", "areaName": "2监区8号楼", "count": 75, "percent": 100}],
  "alarmTrend": {"unit": "次", "points": [{"date": "2026-07-04", "label": "7.4", "count": 44}]},
  "taskCompletion": {
    "items": [{"status": "COMPLETED", "name": "已完成", "count": 284, "percent": 98}],
    "insight": "本月对比上月任务处置时长缩短10%，系统响应速度提升"
  }
}
```

### 3.8 POST `/api/bigscreen/statistics/reports/export`

用途：按筛选条件生成并下载统计 PDF，同时写入历史报告索引。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `modules` | array[string] | 否 | 模块列表；为空时默认全部模块 |
| `timeRange.type` | string | 否 | `all`、`today`、`week`、`month`、`custom` |
| `timeRange.startTime` | string/null | 否 | 自定义开始时间 |
| `timeRange.endTime` | string/null | 否 | 自定义结束时间 |
| `deviceType` | string | 否 | 设备类型，默认 `all` |
| `format` | string | 否 | 当前仅实际返回 PDF |

请求示例：

```http
POST /api/bigscreen/statistics/reports/export
Content-Type: application/json
Accept: application/pdf

{
  "modules": ["equipmentRuntime", "aiAlarmAnalysis", "alarmAreaRanking", "alarmTrend", "taskCompletion"],
  "timeRange": {"type": "month", "startTime": null, "endTime": null},
  "deviceType": "all",
  "format": "PDF"
}
```

响应示例：

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename*=UTF-8''巡逻巡查数据统计报告-本月-全部-20260704100000.pdf

<PDF binary>
```

### 3.9 GET `/api/bigscreen/statistics/reports`

用途：查询历史报告列表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `page` | query | int | 否 | 页码，默认 `1`，最小按 `1` 处理 |
| `size` | query | int | 否 | 每页大小，默认 `10`，最小按 `1` 处理 |

请求示例：

```http
GET /api/bigscreen/statistics/reports?page=1&size=10
```

响应示例：

```json
{
  "data": [
    {
      "id": "1",
      "reportName": "巡逻巡查数据统计报告-本月-全部",
      "downloadTime": "2026-07-04 10:00:00",
      "format": "PDF",
      "status": "COMPLETED",
      "filename": "巡逻巡查数据统计报告-本月-全部-20260704100000.pdf",
      "filePath": "1.pdf",
      "statusName": "已完成"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

### 3.10 GET `/api/bigscreen/statistics/reports/{id}/download`

用途：下载历史报告 PDF。

请求示例：

```http
GET /api/bigscreen/statistics/reports/1/download
```

响应示例：

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename*=UTF-8''巡逻巡查数据统计报告-本月-全部-20260704100000.pdf

<PDF binary>
```

若报告不存在，返回 `404 Not Found`。

### 3.11 DELETE `/api/bigscreen/statistics/reports/{id}`

用途：删除历史报告记录和本地 PDF 文件。

请求示例：

```http
DELETE /api/bigscreen/statistics/reports/1
```

响应示例：

```http
HTTP/1.1 204 No Content
```

若报告不存在，返回 `404 Not Found`。

### 3.12 BigscreenProxyController

#### `/api/control/robots` 移除提示

请求示例：

```http
GET /api/control/robots
```

响应示例：

```json
{
  "code": "API_REMOVED",
  "message": "Use /api/bigscreen/panorama/overview instead of /api/control/robots."
}
```

HTTP 状态码：`410 Gone`。

#### 透明代理

匹配路径：

```text
/api/control/**
/api/bigscreen/**
/api/media/**
/api/manage/**
/api/v1/management/**
/internal/media/**
```

请求方法：保持原请求方法。请求头和响应头会过滤 hop-by-hop headers；multipart 请求会重新组装后转发。

路由规则：

| 原始路径 | 目标服务 |
|---|---|
| `/api/manage/**`、`/api/v1/management/**` | `manageBaseUrl` |
| `/api/v1/control/**` | `v1ControlBaseUrl` |
| `/api/media/**`、`/internal/media/**` | `mediaBaseUrl` |
| 其他 | `controlBaseUrl` |
| `/api/bigscreen/**` 代理路径 | 转成目标 `/api/control/**` |

示例：

```http
GET /api/media/files?fileType=IMAGE&page=0&size=20
```

响应：透明返回下游服务状态码、响应头和字节内容。

## 4. Control Service Java 接口

### 4.1 接口清单

#### 机器人与设备控制

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/control/robots/{robotId}/control-profile` | 获取机器人控制画像 |
| POST | `/api/control/robots/{robotId}/control-sessions/acquire` | 获取控制会话 |
| POST | `/api/control/robots/{robotId}/control-sessions/takeover` | 接管控制 |
| POST | `/api/control/robots/{robotId}/control-mode` | 设置控制模式 |
| POST | `/api/control/robots/{robotId}/control-sessions/{controlSessionId}/release` | 释放控制会话 |
| POST | `/api/control/robots/{robotId}/commands/confirm-token` | 生成高风险动作确认 token |
| POST | `/api/control/robots/{robotId}/commands` | 下发设备控制命令 |
| POST | `/api/control/robots/{robotId}/cameras/{deviceId}/video/start` | 从摄像头入口开始观看 |
| POST | `/api/control/robots/{robotId}/cameras/{deviceId}/video/intercom/start` | 从摄像头入口开始对讲 |

#### 视频会话

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/control/video-sessions` | 查询当前用户最近会话 |
| GET | `/api/control/video-sessions/active` | 查询活跃会话 |
| GET | `/api/control/video-sessions/{sessionId}` | 查询单会话 |
| GET | `/api/control/video-sessions/{sessionId}/events` | 查询会话事件 |
| GET | `/api/control/video-sessions/{sessionId}/tracks` | 查询会话 Track |
| POST | `/api/control/video-sessions/{sessionId}/token` | 签发观看 Token |
| POST | `/api/control/video-sessions/{sessionId}/intercom/start` | 在已有会话中开启对讲 |
| POST | `/api/control/video-sessions/{sessionId}/intercom/token` | 重新签发对讲 Token |
| POST | `/api/control/video-sessions/{sessionId}/intercom/heartbeat` | 对讲心跳 |
| POST | `/api/control/video-sessions/{sessionId}/intercom/stop` | 停止对讲 |
| POST | `/api/control/video-sessions/{sessionId}/heartbeat` | 观看心跳 |
| POST | `/api/control/video-sessions/{sessionId}/stop` | 停止当前观看 |
| POST | `/api/control/video-sessions/{sessionId}/restart` | 重启推流 |
| POST | `/api/control/video-sessions/{sessionId}/switch-channel` | 切换通道/码流 |
| POST | `/api/control/video-sessions/{sessionId}/recordings/start` | 开始实时录像 |
| POST | `/api/control/video-sessions/{sessionId}/recordings/{fileId}/stop` | 停止实时录像 |
| GET | `/api/control/video-sessions/{sessionId}/recordings/active` | 查询当前活跃录像 |

#### 文件

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/control/files` | multipart 简单上传 |
| GET | `/api/control/files` | 文件分页查询 |
| GET | `/api/control/files/{fileId}` | 文件详情 |
| POST | `/api/control/files/{fileId}/download-url` | 获取下载 URL |
| GET | `/api/control/files/{fileId}/content` | 获取文件正文 |
| POST | `/api/control/files/{fileId}/play-url` | 获取播放 URL |
| GET | `/api/control/files/{fileId}/hls/{objectName}` | 获取 HLS 切片/索引 |

### 4.2 Control 接口与 MQTT Topic 总览

下表只列出当前 Java 请求链路中会直接发布 MQTT，或由 MQTT 订阅回调触发调用的接口。未列出的 Control 查询、文件、录像、Token、心跳类接口不直接发布或消费 MQTT。

| 接口 | MQTT 方向 | Topic | 触发条件 / 说明 |
|---|---|---|---|
| `POST /api/control/robots/{robotId}/control-mode` | Control -> Go 客户端 | `robot/{robotId}/control/body/command` | 发布 `control.mode.set`；当前目标设备为 `base`，设备域映射为 `body` |
| `POST /api/control/robots/{robotId}/commands` | Control -> Go 客户端 | `robot/{robotId}/control/{domain}/command` | 按 `target.deviceType` 或 `action` 映射 `{domain}`，见 4.9 |
| `POST /api/control/robots/{robotId}/cameras/{deviceId}/video/start` | Control -> Go 客户端 | `robot/{robotId}/media/video/start` | 仅新建会话、audio-only 会话补视频 Track、或需要重新请求客户端推流时发布 |
| `POST /api/control/robots/{robotId}/cameras/{deviceId}/video/intercom/start` | Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/start` | 创建/复用对讲会话后发布 |
| `POST /api/control/video-sessions/{sessionId}/restart` | Control -> Go 客户端 | `robot/{robotId}/media/video/start` | 重新生成 start command 后发布 |
| `POST /api/control/video-sessions/{sessionId}/switch-channel` | Control -> Go 客户端 | `robot/{robotId}/media/video/switch-channel` | 更新通道/码流后，用当前 start command 作为 payload 发布 |
| `POST /api/control/video-sessions/{sessionId}/intercom/start` | Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/start` | 在已有视频会话中开启对讲后发布 |
| `POST /api/control/video-sessions/{sessionId}/intercom/stop` | Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/stop` | 停止对讲后发布 |
| `POST /internal/media/video-sessions/status` | Go 客户端 -> Control -> Media | `robot/+/media/video/status` | `RobotMediaStatusSubscriber` 消费后转调 media-service |
| `POST /internal/media/video-sessions/intercom/status` | Go 客户端 -> Control -> Media | `robot/+/media/video/intercom/status` | `RobotMediaStatusSubscriber` 消费后转调 media-service |
| Control 本地 `RobotRegistryService.update(...)` | Go 客户端 -> Control | `robot/+/media/client/status` | 客户端上线、下线、心跳和设备状态上报；Control 本地更新机器人状态，上线时再向 Media 查询恢复推流命令 |

调度器也会间接发布 MQTT：

| 调度场景 | 先调用的 Backend 接口 | 可能发布的 Topic |
|---|---|---|
| 中断会话恢复 | `/internal/media/video-sessions/interrupted-restart-candidates` -> `/internal/media/video-sessions/{sessionId}/restart-command` | `robot/{robotId}/media/video/start` |
| 空闲会话释放 | `/internal/media/video-sessions/idle-release-candidates` -> `/internal/media/video-sessions/{sessionId}/release-idle` | `robot/{robotId}/media/video/stop` |
| 对讲心跳超时 | `/internal/media/video-sessions/intercom-timeout-candidates` -> `/internal/media/video-sessions/{sessionId}/intercom/expire` | `robot/{robotId}/media/video/intercom/stop` |

`EquipmentControlCommandPublisher` 中存在 `robot/{robotId}/control/safety/estop` 发布方法，但当前 Java Controller 尚未暴露直接调用它的接口。

### 4.3 GET `/api/control/robots/{robotId}/control-profile`

用途：获取机器人本体、上装设备、可执行动作和控制参数。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `robotId` | path | string | 是 | 当前固定支持 `test001`、`test002`、`robot-unitree-001` |

请求示例：

```http
GET /api/control/robots/test001/control-profile
```

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `robotId` | string | 机器人 ID |
| `type` | string | 机器人类型 |
| `vendor` | string | 厂商 |
| `model` | string | 型号 |
| `onlineStatus` | string | 在线状态 |
| `controlMode` | string | 控制模式 |
| `stateSeq` | number | 状态序号 |
| `devices` | array | 设备能力数组 |

`devices[]` 主要字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `deviceId` | string | 设备 ID |
| `scope` | string | `BODY`、`PAYLOAD`、`AUDIO` 等 |
| `deviceType` | string | 如 `WHEELED_BASE`、`DUAL_LIGHT_PTZ`、`CLIENT_AUDIO` |
| `displayName` | string | 展示名称 |
| `actions` | array[string] | 可执行动作 |
| `controlProfile` | object | 动作参数边界 |

响应示例：

```json
{
  "robotId": "test001",
  "type": "轮式机器人",
  "vendor": "SONGLING",
  "model": "SCOUT",
  "onlineStatus": "offline",
  "controlMode": "MANUAL",
  "stateSeq": 1,
  "devices": [
    {
      "deviceId": "base",
      "scope": "BODY",
      "deviceType": "WHEELED_BASE",
      "displayName": "机器人本体",
      "vendor": "SONGLING",
      "model": "SCOUT",
      "onlineStatus": "offline",
      "controlStatus": "idle",
      "enabled": true,
      "actions": ["drive.velocity", "navigation.return_home", "docking.leave"],
      "controlProfile": {"maxLinearX": 1.0, "maxLinearY": 0.4, "maxAngularZ": 0.8, "controlFrameRateHz": 10}
    }
  ]
}
```

### 4.4 POST `/api/control/robots/{robotId}/control-sessions/acquire`

用途：申请设备级或整机控制锁。若同一 `X-Client-Id` 已持有控制锁，会续约 `leaseExpireAt`。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `scope` | string | 否 | `DEVICE` 或 `ROBOT`，默认 `DEVICE` |
| `deviceIds` | array[string] | 否 | 需要控制的设备 ID；为空视为与任意已有会话冲突 |
| `actions` | array[string] | 否 | 计划执行的动作 |

请求示例：

```http
POST /api/control/robots/test001/control-sessions/acquire
Content-Type: application/json

{
  "scope": "DEVICE",
  "deviceIds": ["base"],
  "actions": ["drive.velocity"]
}
```

响应示例（成功）：

```json
{
  "controlSessionId": "tc_8e3f1c",
  "robotId": "test001",
  "ownerUserId": "u1001",
  "ownerClientId": "web-1783120000000-abcd",
  "scope": "DEVICE",
  "deviceIds": ["base"],
  "actions": ["drive.velocity"],
  "mode": "EXCLUSIVE",
  "status": "ACTIVE",
  "leaseExpireAt": "2026-07-04T10:00:30+08:00"
}
```

响应示例（被其他终端占用）：

```json
{
  "code": "CONTROL_LOCKED",
  "message": "target is controlled by another terminal",
  "holder": {
    "controlSessionId": "tc_existing",
    "robotId": "test001",
    "ownerUserId": "u1002",
    "ownerClientId": "web-other",
    "status": "ACTIVE"
  }
}
```

### 4.5 POST `/api/control/robots/{robotId}/control-sessions/takeover`

用途：强制接管控制，将机器人状态切到 `MANUAL`，任务状态切到 `PAUSED`。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `observedStateSeq` | number | 否 | 前端观察到的状态序号；小于后端最新值时返回 `ROBOT_STATE_CHANGED` |
| `scope` | string | 否 | 默认 `ROBOT` |
| `deviceIds` | array[string] | 否 | 为空时默认 `["base"]` |
| `actions` | array[string] | 否 | 动作列表 |
| `fromMode` | string | 否 | 接管前模式，默认 `NAVIGATION` |

请求示例：

```json
{
  "observedStateSeq": 1,
  "scope": "ROBOT",
  "deviceIds": ["base"],
  "actions": ["drive.velocity"],
  "fromMode": "NAVIGATION"
}
```

响应示例：

```json
{
  "controlSessionId": "tc_91ad22",
  "robotId": "test001",
  "ownerUserId": "u1001",
  "ownerClientId": "web-1783120000000-abcd",
  "scope": "ROBOT",
  "deviceIds": ["base"],
  "actions": ["drive.velocity"],
  "mode": "EXCLUSIVE",
  "status": "ACTIVE",
  "leaseExpireAt": "2026-07-04T10:00:30+08:00",
  "previousMode": "NAVIGATION",
  "controlMode": "MANUAL",
  "missionStatus": "PAUSED"
}
```

状态已变化时：

```json
{
  "code": "ROBOT_STATE_CHANGED",
  "message": "robot state changed, refresh status before takeover",
  "latestStateSeq": 3,
  "latestControlMode": "MANUAL"
}
```

### 4.6 POST `/api/control/robots/{robotId}/control-mode`

用途：设置机器人控制模式，并通过 MQTT 发布 `control.mode.set`。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/control/body/command` | 服务端组装 `action=control.mode.set`、`target.deviceId=base`、`params.controlMode`；QoS 1 |

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `controlMode` | string | 是 | `MANUAL`、`ASSISTED`、`NAVIGATION` |

请求示例：

```json
{"controlMode": "NAVIGATION"}
```

响应示例：

```json
{
  "status": "PUBLISHED",
  "robotId": "test001",
  "controlMode": "NAVIGATION",
  "stateSeq": 2,
  "issuedAt": "2026-07-04T10:00:00+08:00"
}
```

### 4.7 POST `/api/control/robots/{robotId}/control-sessions/{controlSessionId}/release`

用途：释放控制会话。

请求参数：

| 参数/字段 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `robotId` | path | string | 是 | 机器人 ID |
| `controlSessionId` | path | string | 是 | 控制会话 ID |
| `reason` | body | string | 否 | 释放原因，默认 `user_release` |

请求示例：

```json
{"reason": "user_release"}
```

响应示例：

```json
{
  "controlSessionId": "tc_91ad22",
  "status": "RELEASED",
  "releasedAt": "2026-07-04T10:01:00+08:00"
}
```

### 4.8 POST `/api/control/robots/{robotId}/commands/confirm-token`

用途：为高风险动作生成短期确认 token。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `target.scope` | string | 否 | 目标 scope |
| `target.deviceId` | string | 否 | 目标设备 ID |
| `action` | string | 是 | 动作名，如 `payload.fire` |

请求示例：

```json
{
  "target": {"scope": "PAYLOAD", "deviceId": "launcher-001"},
  "action": "payload.fire"
}
```

响应示例：

```json
{
  "confirmToken": "confirm_b8c4a1",
  "expiresAt": "2026-07-04T10:00:30+08:00",
  "robotId": "test001",
  "target": {"scope": "PAYLOAD", "deviceId": "launcher-001"},
  "action": "payload.fire"
}
```

### 4.9 POST `/api/control/robots/{robotId}/commands`

用途：下发设备控制命令。服务端会按设备能力对部分动作参数做裁剪/归一化，然后发布 MQTT。

关联 MQTT：

| 方向 | Topic | 匹配规则 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/control/body/command` | `target.deviceType` 为 `WHEELED_BASE`、`QUADRUPED_BASE`、`BIPED_BASE` |
| Control -> Go 客户端 | `robot/{robotId}/control/ptz/command` | `target.deviceType=DUAL_LIGHT_PTZ` |
| Control -> Go 客户端 | `robot/{robotId}/control/audio/command` | `target.deviceType` 为 `CLIENT_AUDIO`、`INTERCOM`、`VOLUME_CONTROL`，或 `action` 以 `volume.` 开头 |
| Control -> Go 客户端 | `robot/{robotId}/control/launcher/command` | `target.deviceType=LAUNCHER`，或 `action` 以 `payload.fire` 开头 |
| Control -> Go 客户端 | `robot/{robotId}/control/net-gun/command` | `target.deviceType` 为 `NET_GUN`、`NET_LAUNCHER` |
| Control -> Go 客户端 | `robot/{robotId}/control/warning-light/command` | `target.deviceType=WARNING_LIGHT`，或 `action` 以 `light.warning.` 开头 |
| Control -> Go 客户端 | `robot/{robotId}/control/vehicle-light/command` | `target.deviceType` 为 `VEHICLE_LIGHT`、`SEARCHLIGHT`，或 `action` 以 `light.vehicle.` 开头 |
| Control -> Go 客户端 | `robot/{robotId}/control/payload/command` | 其他未匹配设备类型或动作的兜底 topic |

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `target.deviceId` | string | 是 | 目标设备 ID |
| `target.deviceType` | string | 是 | 目标设备类型 |
| `target.scope` | string | 否 | 目标范围 |
| `action` | string | 是 | 动作名 |
| `params` | object | 否 | 动作参数 |
| `client.seq` | number | 否 | 前端命令序号 |

当前有特殊归一化逻辑的动作：

| action | 参数说明 |
|---|---|
| `drive.velocity` | `linearX`、`linearY`、`angularZ` 按底盘能力裁剪；轮式底盘 `linearY` 固定为 `0` |
| `ptz.move` | `panSpeed`、`tiltSpeed` 按云台能力裁剪 |
| `camera.zoom` | `zoomSpeed` 裁剪到 `[-1, 1]` |
| `ptz.auto_rotate` | `enabled`、`panSpeed` |
| `control.mode.set` | `controlMode` 归一化 |
| `volume.*` | `volume`、`step`、`muted` |
| `light.set` | `enabled`、`brightness`、`mode` |
| `light.vehicle.set` | `front`、`rear` 车灯模式，组装 ROS topic 消息 |
| `payload.fire` | `channel` |
| `payload.safety_switch` | `enabled` |

请求示例：

```json
{
  "target": {"scope": "BODY", "deviceId": "base", "deviceType": "WHEELED_BASE"},
  "action": "drive.velocity",
  "params": {"linearX": 0.5, "linearY": 0.2, "angularZ": 0.1},
  "client": {"seq": 18}
}
```

响应示例：

```json
{
  "commandId": "cmd_f5b0a1",
  "status": "PUBLISHED",
  "robotId": "test001",
  "target": {"deviceId": "base", "deviceType": "WHEELED_BASE"},
  "action": "drive.velocity",
  "issuedAt": "2026-07-04T10:00:00+08:00"
}
```

### 4.10 POST `/api/control/robots/{robotId}/cameras/{deviceId}/video/start`

用途：从设备/摄像头入口创建或复用实时视频会话，并在需要时下发机器人开始推流命令。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/media/video/start` | 当 media-service 返回 `INIT`，或 audio-only 会话需要补发视频 Track 时，Control 先调用 `/internal/media/video-sessions/{sessionId}/client-start` 生成 `VideoStartCommand`，再发布到该 topic；QoS 1 |

请求参数：

| 参数/字段 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---:|---|---|
| `robotId` | path | string | 是 | - | 机器人 ID |
| `deviceId` | path | string | 是 | - | 摄像头设备 ID |
| `channel` | body | enum | 否 | `visible` | 视频通道 |
| `quality` | body | enum | 否 | `sub` | 清晰度 |
| `reuse` | body | boolean | 否 | `true` | 是否复用可用会话 |
| `clientRequestId` | body | string | 否 | null | 前端请求幂等/追踪 ID |

请求示例：

```json
{
  "channel": "visible",
  "quality": "sub",
  "reuse": true,
  "clientRequestId": "req-001"
}
```

响应参数：`VideoSessionResponse`。

响应示例：见 2.4 `VideoSessionResponse`。

### 4.11 POST `/api/control/robots/{robotId}/cameras/{deviceId}/video/intercom/start`

用途：从摄像头入口创建/复用会话并开始对讲；内部强制 `reuse=true`。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/start` | Control 创建/复用对讲会话后，调用 `/internal/media/video-sessions/{sessionId}/intercom/start-command` 生成 `IntercomStartCommand` 并发布；QoS 1 |

请求参数：同 4.10，`reuse` 会被服务端强制视为 `true`。

请求示例：

```json
{"channel": "visible", "quality": "sub", "reuse": true}
```

响应参数：`IntercomResponse`。

响应示例：见 2.4 `IntercomResponse`。

### 4.12 视频会话查询类接口

#### GET `/api/control/video-sessions`

用途：查询当前用户最近会话。

请求示例：

```http
GET /api/control/video-sessions
```

响应示例：

```json
[
  {
    "sessionId": "vs_123456",
    "robotId": "test001",
    "deviceId": "camera01",
    "channel": "visible",
    "quality": "sub",
    "status": "STREAMING",
    "roomName": "media.test001.camera01.visible",
    "livekitUrl": "ws://127.0.0.1:7880",
    "viewerToken": "eyJhbGciOi...",
    "viewerCount": 1,
    "intercomStatus": "IDLE",
    "intercomAudioOnly": false,
    "createdAt": "2026-07-04T10:00:00+08:00",
    "updatedAt": "2026-07-04T10:01:00+08:00"
  }
]
```

#### GET `/api/control/video-sessions/active`

用途：查询活跃视频墙会话。

请求示例：

```http
GET /api/control/video-sessions/active
```

响应示例：`VideoSessionResponse[]`，结构同上。

#### GET `/api/control/video-sessions/{sessionId}`

用途：查询单个会话。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `sessionId` | path | string | 是 | 视频会话 ID |

请求示例：

```http
GET /api/control/video-sessions/vs_123456
```

响应示例：`VideoSessionResponse`，见 2.4。

#### GET `/api/control/video-sessions/{sessionId}/events`

用途：查询会话事件日志。

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | string | 事件 ID |
| `sessionId` | string | 会话 ID |
| `eventType` | string | 事件类型 |
| `eventPayload` | string | JSON 字符串 payload |
| `traceId` | string/null | 链路 ID |
| `createdAt` | datetime | 创建时间 |

请求示例：

```http
GET /api/control/video-sessions/vs_123456/events
```

响应示例：

```json
[
  {
    "eventId": "evt_001",
    "sessionId": "vs_123456",
    "eventType": "video.client.streaming",
    "eventPayload": "{\"trackSid\":\"TR_VC_001\"}",
    "traceId": null,
    "createdAt": "2026-07-04T10:01:00+08:00"
  }
]
```

#### GET `/api/control/video-sessions/{sessionId}/tracks`

用途：查询会话 Track。

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `trackId` | string | Track 记录 ID |
| `sessionId` | string | 会话 ID |
| `trackSid` | string | LiveKit Track SID |
| `trackName` | string | Track 名称 |
| `participantIdentity` | string | 发布者身份 |
| `kind` | string | `video` 或 `audio` |
| `channel` | enum | 视频通道 |
| `quality` | enum | 清晰度 |
| `publishedAt` | datetime | 发布时间 |
| `unpublishedAt` | datetime/null | 取消发布时间 |

请求示例：

```http
GET /api/control/video-sessions/vs_123456/tracks
```

响应示例：

```json
[
  {
    "trackId": "mt_001",
    "sessionId": "vs_123456",
    "trackSid": "TR_VC_001",
    "trackName": "video.visible.sub",
    "participantIdentity": "robot:test001:camera01",
    "kind": "video",
    "channel": "visible",
    "quality": "sub",
    "publishedAt": "2026-07-04T10:00:30+08:00",
    "unpublishedAt": null
  }
]
```

### 4.13 视频会话操作类接口

#### POST `/api/control/video-sessions/{sessionId}/token`

用途：重新签发观看 Token。

请求示例：

```http
POST /api/control/video-sessions/vs_123456/token
```

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `livekitUrl` | string | LiveKit 地址 |
| `roomName` | string | Room |
| `token` | string | 观看 Token |
| `expiresAt` | datetime | 过期时间 |

响应示例：

```json
{
  "livekitUrl": "ws://127.0.0.1:7880",
  "roomName": "media.test001.camera01.visible",
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

#### POST `/api/control/video-sessions/{sessionId}/heartbeat`

用途：刷新当前 viewer 心跳，维持 viewerCount。

请求示例：

```http
POST /api/control/video-sessions/vs_123456/heartbeat
```

响应示例：`VideoSessionResponse`。

#### POST `/api/control/video-sessions/{sessionId}/stop`

用途：停止当前 viewer；若无人观看，媒体侧可进入空闲释放。

关联 MQTT：本接口本身不直接发布 MQTT；当会话进入 `IDLE_WAIT` 后，由 Control 调度器调用 `/internal/media/video-sessions/{sessionId}/release-idle`，若 media-service 确认释放 Room，则发布 `robot/{robotId}/media/video/stop`。

请求示例：

```http
POST /api/control/video-sessions/vs_123456/stop
```

响应示例：`VideoSessionResponse`。

#### POST `/api/control/video-sessions/{sessionId}/restart`

用途：重新下发机器人推流命令。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/media/video/start` | Control 调用 `/internal/media/video-sessions/{sessionId}/restart-command` 生成 `VideoStartCommand` 后发布；QoS 1 |

请求示例：

```http
POST /api/control/video-sessions/vs_123456/restart
```

响应示例：`VideoSessionResponse`。

#### POST `/api/control/video-sessions/{sessionId}/switch-channel`

用途：切换视频通道或码流，并通知机器人重新推流。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/media/video/switch-channel` | Control 先调用 `/internal/media/video-sessions/{sessionId}/switch-channel` 更新会话，再调用 `/internal/media/video-sessions/{sessionId}/start-command` 取当前 `VideoStartCommand` 并发布；QoS 1 |

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `channel` | enum | 是 | `visible`、`thermal`、`fusion` |
| `quality` | enum | 否 | `sub`、`main`、`auto`；为空时 media-service 决定 |

请求示例：

```json
{"channel": "thermal", "quality": "sub"}
```

响应示例：`VideoSessionResponse`。

### 4.14 对讲接口

#### POST `/api/control/video-sessions/{sessionId}/intercom/start`

用途：在已有视频会话中开启对讲。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/start` | Control 调用 `/internal/media/video-sessions/{sessionId}/intercom/start-command` 生成 `IntercomStartCommand` 后发布；QoS 1 |

请求示例：

```http
POST /api/control/video-sessions/vs_123456/intercom/start
```

响应示例：`IntercomResponse`，见 2.4。

#### POST `/api/control/video-sessions/{sessionId}/intercom/token`

用途：对讲重连或 Token 过期时重新签发操作员 Token。

请求示例：

```http
POST /api/control/video-sessions/vs_123456/intercom/token
```

响应示例：`IntercomResponse`。

#### POST `/api/control/video-sessions/{sessionId}/intercom/heartbeat`

用途：刷新对讲占用心跳。

请求示例：

```http
POST /api/control/video-sessions/vs_123456/intercom/heartbeat
```

响应示例：`IntercomResponse`。

#### POST `/api/control/video-sessions/{sessionId}/intercom/stop`

用途：停止对讲，并通知机器人停止音频桥。

关联 MQTT：

| 方向 | Topic | Payload 来源 / 说明 |
|---|---|---|
| Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/stop` | Payload 包含 `sessionId`、`roomName`、`commandId=cmd_intercom_stop_{sessionId}`；QoS 1 |

请求示例：

```http
POST /api/control/video-sessions/vs_123456/intercom/stop
```

响应示例：`VideoSessionResponse`。

### 4.15 录像接口

#### POST `/api/control/video-sessions/{sessionId}/recordings/start`

用途：在实时视频会话上开始 LiveKit Egress 录像。

请求示例：

```http
POST /api/control/video-sessions/vs_123456/recordings/start
```

响应示例：`FileListItemResponse`，见 2.4。

#### POST `/api/control/video-sessions/{sessionId}/recordings/{fileId}/stop`

用途：停止指定录像文件。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `sessionId` | path | string | 是 | 视频会话 ID |
| `fileId` | path | string | 是 | 录像文件 ID |

请求示例：

```http
POST /api/control/video-sessions/vs_123456/recordings/file_001/stop
```

响应示例：`FileListItemResponse`。

#### GET `/api/control/video-sessions/{sessionId}/recordings/active`

用途：查询当前会话正在录制的文件。

请求示例：

```http
GET /api/control/video-sessions/vs_123456/recordings/active
```

响应示例：`FileListItemResponse`；若无活跃录像，下游行为以 media-service 实现为准。

### 4.16 POST `/api/control/files`

用途：通过 Control API 上传小文件或抓拍图片，转发到 media-service `/internal/media/files`。

请求类型：`multipart/form-data`。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `file` | file | 是 | 文件内容 |
| `fileType` | enum | 是 | `FileType` |
| `robotId` | string | 否 | 机器人 ID |
| `deviceId` | string | 否 | 设备 ID |
| `taskExecutionId` | string | 否 | 任务执行 ID |
| `sourceFileId` | string | 否 | 来源文件 ID |
| `metadata` | string | 否 | 元数据，建议 JSON 字符串 |

请求示例：

```http
POST /api/control/files
Content-Type: multipart/form-data

file=@snapshot.jpg
fileType=IMAGE
robotId=test001
deviceId=camera01
metadata={"source":"snapshot"}
```

响应示例：`FileListItemResponse`，见 2.4。

### 4.17 GET `/api/control/files`

用途：分页查询统一文件表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---:|---|---|
| `robotId` | query | string | 否 | - | 机器人 ID |
| `deviceId` | query | string | 否 | - | 设备 ID |
| `taskExecutionId` | query | string | 否 | - | 任务执行 ID |
| `fileType` | query | enum | 否 | - | 文件类型 |
| `status` | query | enum | 否 | - | 文件状态 |
| `page` | query | int | 否 | `0` | 页码 |
| `size` | query | int | 否 | `20` | 每页数量 |

请求示例：

```http
GET /api/control/files?robotId=test001&fileType=IMAGE&status=READY&page=0&size=20
```

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `items` | array[`FileListItemResponse`] | 文件列表 |
| `page` | int | 当前页 |
| `size` | int | 页大小 |
| `total` | number | 总数 |

响应示例：

```json
{
  "items": [
    {
      "fileId": "file_001",
      "robotId": "test001",
      "deviceId": "camera01",
      "fileType": "IMAGE",
      "fileName": "snapshot.jpg",
      "contentType": "image/jpeg",
      "fileSize": 245760,
      "status": "READY",
      "createdAt": "2026-07-04T10:02:00+08:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

### 4.18 GET `/api/control/files/{fileId}`

用途：查询文件详情。

请求示例：

```http
GET /api/control/files/file_001
```

响应示例：`FileListItemResponse`。

### 4.19 POST `/api/control/files/{fileId}/download-url`

用途：获取带有效期的下载 URL。

请求示例：

```http
POST /api/control/files/file_001/download-url
```

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` | string | 文件 ID |
| `downloadUrl` | string | 下载 URL |
| `expiresAt` | datetime | 过期时间 |

响应示例：

```json
{
  "fileId": "file_001",
  "downloadUrl": "/api/control/files/file_001/content?token=abc",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

### 4.20 GET `/api/control/files/{fileId}/content`

用途：通过 Control API 代理读取文件正文，常用于图片预览或普通下载。

请求示例：

```http
GET /api/control/files/file_001/content
```

响应示例：

```http
HTTP/1.1 200 OK
Content-Type: image/jpeg

<file binary>
```

### 4.21 POST `/api/control/files/{fileId}/play-url`

用途：获取视频播放 URL，通常为 HLS 地址。

请求示例：

```http
POST /api/control/files/file_video_001/play-url
```

响应参数：

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` | string | 文件 ID |
| `format` | string | 播放格式，如 `HLS` |
| `contentType` | string | MIME 类型 |
| `playUrl` | string | 播放地址 |
| `expiresAt` | datetime | 过期时间 |

响应示例：

```json
{
  "fileId": "file_video_001",
  "format": "HLS",
  "contentType": "application/vnd.apple.mpegurl",
  "playUrl": "/api/control/files/file_video_001/hls/index.m3u8?token=play_abc",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

### 4.22 GET `/api/control/files/{fileId}/hls/{objectName}`

用途：读取 HLS 索引或切片。Control 根据 `objectName` 后缀设置 `Content-Type`。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `fileId` | path | string | 是 | 文件 ID |
| `objectName` | path | string | 是 | HLS 对象名，如 `index.m3u8`、`segment_000001.ts` |
| `token` | query | string | 是 | 播放 token |

请求示例：

```http
GET /api/control/files/file_video_001/hls/index.m3u8?token=play_abc
```

响应示例：

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.apple.mpegurl

#EXTM3U
...
```

后缀与响应类型：

| 后缀 | Content-Type |
|---|---|
| `.m3u8` | `application/vnd.apple.mpegurl` |
| `.m4s`、`.mp4` | `video/mp4` |
| `.ts` | `video/mp2t` |
| 其他 | `application/octet-stream` |

## 5. Backend Media Service Java 接口

本章覆盖 `backend/src/main/java` 下的 Java Controller。`backend` 的主要职责是媒体能力，其中：

- `/internal/media/video-sessions/**` 主要供 `control-service`、调度器、MQTT 视频/对讲状态消费链路调用。
- `/internal/media/files/**` 主要供 `control-service` 文件代理调用。
- `/api/media/files/**` 主要供机器人客户端或管理端直接调用，和 `/internal/media/files/**` 共用同一个 `FileController`。
- `/api/media/tts/**` 当前是媒体后端直接暴露的 TTS 调试/集成接口。

Backend MQTT 关联说明：

| Backend 接口 | MQTT 方向 | Topic | 说明 |
|---|---|---|---|
| `POST /internal/media/video-sessions/status` | Go 客户端 -> Control -> Media | `robot/+/media/video/status` | Control 订阅该 topic 后转调本接口写回视频会话状态 |
| `POST /internal/media/video-sessions/intercom/status` | Go 客户端 -> Control -> Media | `robot/+/media/video/intercom/status` | Control 订阅该 topic 后转调本接口写回对讲状态 |
| `POST /internal/media/video-sessions/{sessionId}/client-start` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/start` | 本接口只生成 `VideoStartCommand`，实际发布由 Control 完成 |
| `POST /internal/media/video-sessions/{sessionId}/restart-command` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/start` | 本接口只生成重启用 `VideoStartCommand` |
| `POST /internal/media/video-sessions/{sessionId}/start-command` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/switch-channel` | 切换通道时 Control 用该接口返回的 `VideoStartCommand` 作为 payload 发布 |
| `POST /internal/media/video-sessions/online-restart-commands` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/start` | 客户端上线后返回待恢复命令列表，Control 逐条发布 |
| `POST /internal/media/video-sessions/{sessionId}/release-idle` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/stop` | 仅返回非空 stop payload 时由 Control 发布 |
| `POST /internal/media/video-sessions/{sessionId}/intercom/start-command` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/start` | 本接口只生成 `IntercomStartCommand` |
| `POST /internal/media/video-sessions/{sessionId}/intercom/expire` | Media -> Control -> Go 客户端 | `robot/{robotId}/media/video/intercom/stop` | 仅返回非空 stop payload 时由 Control 发布 |

### 5.1 Backend 接口清单

#### 视频会话：`VideoSessionController`

| 方法 | 路径 | 调用方 | 说明 |
|---|---|---|---|
| POST | `/internal/media/video-sessions` | Control | 创建或复用实时视频会话 |
| POST | `/internal/media/video-sessions/intercom` | Control | 创建/复用承载对讲的会话 |
| GET | `/internal/media/video-sessions` | Control | 查询当前用户最近会话 |
| GET | `/internal/media/video-sessions/active` | Control/前端恢复 | 查询可复用活跃会话 |
| GET | `/internal/media/video-sessions/interrupted-restart-candidates` | 调度器 | 查询需自动重启的中断会话 |
| GET | `/internal/media/video-sessions/idle-release-candidates` | 调度器 | 查询需释放的空闲会话 |
| GET | `/internal/media/video-sessions/intercom-timeout-candidates` | 调度器 | 查询对讲心跳超时会话 |
| GET | `/internal/media/video-sessions/{sessionId}` | Control | 查询单会话并签发 viewer token |
| GET | `/internal/media/video-sessions/{sessionId}/events` | Control | 查询会话事件 |
| GET | `/internal/media/video-sessions/{sessionId}/tracks` | Control | 查询会话 Track |
| POST | `/internal/media/video-sessions/status` | MQTT 消费链路 | 处理机器人视频状态 |
| POST | `/internal/media/video-sessions/intercom/status` | MQTT 消费链路 | 处理机器人对讲状态 |
| POST | `/internal/media/video-sessions/{sessionId}/token` | Control | 签发观看 Token |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/start` | Control | 开启会话对讲 |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/token` | Control | 签发对讲 Token |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/heartbeat` | Control | 对讲心跳 |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/stop` | Control | 停止对讲 |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/expire` | 调度器 | 对讲超时释放 |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/start-command` | Control | 生成机器人对讲启动命令 |
| POST | `/internal/media/video-sessions/{sessionId}/heartbeat` | Control | 观看心跳 |
| POST | `/internal/media/video-sessions/{sessionId}/stop` | Control | 停止当前 viewer |
| POST | `/internal/media/video-sessions/{sessionId}/restart` | Control | 当前用户手动重启 |
| POST | `/internal/media/video-sessions/{sessionId}/restart-command` | Control/调度器 | 生成重启推流命令 |
| POST | `/internal/media/video-sessions/{sessionId}/client-start` | Control | 生成首次推流命令 |
| POST | `/internal/media/video-sessions/{sessionId}/start-command` | Control | 按当前会话生成 start 命令 |
| POST | `/internal/media/video-sessions/online-restart-commands` | Control | 机器人上线后生成恢复命令 |
| POST | `/internal/media/video-sessions/{sessionId}/release-idle` | 调度器 | 释放空闲会话并返回 stop payload |
| POST | `/internal/media/video-sessions/{sessionId}/switch-channel` | Control | 切换通道/码流 |
| POST | `/internal/media/video-sessions/{sessionId}/recordings/start` | Control | 开始实时录像 |
| POST | `/internal/media/video-sessions/{sessionId}/recordings/{fileId}/stop` | Control | 停止实时录像 |
| GET | `/internal/media/video-sessions/{sessionId}/recordings/active` | Control | 查询当前活跃录像 |
| POST | `/internal/media/video-sessions/{sessionId}/_mock/track-published/{trackSid}` | 调试 | 模拟 Track 已发布 |

#### 文件：`FileController`

以下路径同时支持两个 base path：

```text
/internal/media/files
/api/media/files
```

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `{base}` | multipart 简单上传 |
| POST | `{base}/multipart-uploads` | 创建或恢复分片上传 |
| POST | `{base}/multipart-uploads/{uploadId}/part-urls` | 批量获取 part 上传 URL |
| POST | `{base}/multipart-uploads/{uploadId}/complete` | 完成分片上传 |
| GET | `{base}/{fileId}/status` | 查询文件处理状态 |
| GET | `{base}` | 文件分页查询 |
| POST | `{base}/task-execution-binding` | 绑定任务执行 ID |
| GET | `{base}/{fileId}` | 文件详情 |
| POST | `{base}/{fileId}/download-url` | 获取下载 URL |
| GET | `{base}/{fileId}/content` | 获取文件正文 |
| POST | `{base}/{fileId}/play-url` | 获取播放 URL |
| GET | `{base}/{fileId}/hls/{objectName}` | 获取 HLS 索引/切片 |

#### TTS：`RobotTtsController`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/media/tts/generate-file` | 生成 TTS 音频文件并返回文件流 |
| GET | `/api/media/tts/generate-and-play` | 生成 TTS 并发布给前端播放，无响应体 |

### 5.2 Backend 视频通用请求/响应模型

#### CreateVideoSessionRequest

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---:|---|---|
| `robotId` | string | 是 | - | 机器人 ID |
| `deviceId` | string | 是 | - | 摄像头/设备 ID |
| `channel` | enum | 是 | - | `visible`、`thermal`、`fusion` |
| `quality` | enum | 是 | `sub` | `sub`、`main`、`auto` |
| `reuse` | boolean | 否 | `false` | 是否复用已有可用会话 |
| `clientRequestId` | string | 否 | null | 客户端请求 ID |

示例：

```json
{
  "robotId": "test001",
  "deviceId": "camera01",
  "channel": "visible",
  "quality": "sub",
  "reuse": true,
  "clientRequestId": "req-001"
}
```

#### VideoStatusMessage

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `sessionId` | string | 是 | 会话 ID |
| `status` | string | 是 | 机器人侧状态：`room_ready`、`publishing`、`streaming`、`track_published`、`interrupted`、`stopped`、`closed`、`failed`、`error` 等 |
| `trackSid` | string | 否 | LiveKit Track SID |
| `trackName` | string | 否 | LiveKit Track 名称 |
| `errorCode` | string | 否 | 错误码 |
| `message` | string | 否 | 状态说明 |
| `timestamp` | datetime | 否 | 机器人侧时间 |

示例：

```json
{
  "sessionId": "vs_123456",
  "status": "streaming",
  "trackSid": "TR_VC_001",
  "trackName": "video.visible.sub",
  "message": "publisher started",
  "timestamp": "2026-07-04T10:01:00+08:00"
}
```

#### IntercomStatusMessage

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `sessionId` | string | 是 | 会话 ID |
| `status` | string | 是 | `starting`、`active`、`interrupted`、`stopped`、`closed`、`failed`、`error` 等 |
| `robotAudioTrackSid` | string | 否 | 机器人音频 Track SID |
| `robotAudioTrackName` | string | 否 | 机器人音频 Track 名称 |
| `errorCode` | string | 否 | 错误码 |
| `message` | string | 否 | 状态说明 |
| `timestamp` | datetime | 否 | 机器人侧时间 |

示例：

```json
{
  "sessionId": "vs_123456",
  "status": "active",
  "robotAudioTrackSid": "TR_AM_001",
  "robotAudioTrackName": "audio.robot.mic",
  "timestamp": "2026-07-04T10:02:00+08:00"
}
```

#### VideoStartCommand

| 字段 | 类型 | 说明 |
|---|---|---|
| `commandId` | string | 命令 ID |
| `sessionId` | string | 会话 ID |
| `robotId` | string | 机器人 ID |
| `deviceId` | string | 摄像头/设备 ID |
| `channel` | enum | 视频通道 |
| `quality` | enum | 码流质量 |
| `livekitUrl` | string | LiveKit 地址 |
| `roomName` | string | LiveKit Room |
| `publisherToken` | string | 机器人发布端 Token |
| `publishIdentity` | string | 发布者身份 |
| `expiresAt` | datetime | Token 过期时间 |

响应示例：

```json
{
  "commandId": "cmd_001",
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "channel": "visible",
  "quality": "sub",
  "livekitUrl": "ws://127.0.0.1:7880",
  "roomName": "media.test001.camera01.visible",
  "publisherToken": "eyJhbGciOi...",
  "publishIdentity": "robot:test001:camera01",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

#### IntercomStartCommand

| 字段 | 类型 | 说明 |
|---|---|---|
| `commandId` | string | 命令 ID |
| `sessionId` | string | 会话 ID |
| `robotId` | string | 机器人 ID |
| `deviceId` | string | 摄像头/设备 ID |
| `roomName` | string | LiveKit Room |
| `livekitUrl` | string | LiveKit 地址 |
| `robotToken` | string | 机器人对讲 Token |
| `publishAudio` | boolean | 机器人是否发布音频 |
| `subscribeOperatorAudio` | boolean | 是否订阅操作员音频 |
| `publishVideo` | boolean | 对讲启动命令是否同时发布视频，当前为 `false` |
| `expiresAt` | datetime | Token 过期时间 |

响应示例：

```json
{
  "commandId": "cmd_intercom_001",
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "roomName": "media.test001.camera01.visible",
  "livekitUrl": "ws://127.0.0.1:7880",
  "robotToken": "eyJhbGciOi...",
  "publishAudio": true,
  "subscribeOperatorAudio": true,
  "publishVideo": false,
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

### 5.3 POST `/internal/media/video-sessions`

用途：创建或复用实时视频会话。新会话返回 `INIT`，Control 随后调用 `client-start` 获取机器人推流命令。

请求体：`CreateVideoSessionRequest`。

请求示例：

```http
POST /internal/media/video-sessions
Content-Type: application/json

{
  "robotId": "test001",
  "deviceId": "camera01",
  "channel": "visible",
  "quality": "sub",
  "reuse": true,
  "clientRequestId": "req-001"
}
```

响应参数：`VideoSessionResponse`，见 2.4。

响应示例：

```json
{
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "channel": "visible",
  "quality": "sub",
  "status": "INIT",
  "roomName": "media.test001.camera01.visible",
  "livekitUrl": "ws://127.0.0.1:7880",
  "viewerToken": "eyJhbGciOi...",
  "viewerCount": 1,
  "intercomStatus": "IDLE",
  "intercomAudioOnly": false,
  "createdAt": "2026-07-04T10:00:00+08:00",
  "updatedAt": "2026-07-04T10:00:00+08:00"
}
```

### 5.4 POST `/internal/media/video-sessions/intercom`

用途：创建或复用承载对讲的 `VideoSession`，并开启对讲占用。

请求体：`CreateVideoSessionRequest`。

请求示例：

```http
POST /internal/media/video-sessions/intercom
Content-Type: application/json

{
  "robotId": "test001",
  "deviceId": "camera01",
  "channel": "visible",
  "quality": "sub",
  "reuse": true
}
```

响应参数：`IntercomResponse`，见 2.4。

响应示例：

```json
{
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "roomName": "media.test001.camera01.visible",
  "videoStatus": "ROOM_READY",
  "intercomStatus": "STARTING",
  "intercomAudioOnly": true,
  "livekitUrl": "ws://127.0.0.1:7880",
  "operatorToken": "eyJhbGciOi...",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

### 5.5 Backend 视频查询接口

#### GET `/internal/media/video-sessions`

用途：查询当前用户最近创建的 20 条视频会话。

请求示例：

```http
GET /internal/media/video-sessions
```

响应示例：`VideoSessionResponse[]`。

```json
[
  {
    "sessionId": "vs_123456",
    "robotId": "test001",
    "deviceId": "camera01",
    "channel": "visible",
    "quality": "sub",
    "status": "STREAMING",
    "roomName": "media.test001.camera01.visible",
    "livekitUrl": "ws://127.0.0.1:7880",
    "viewerToken": null,
    "viewerCount": 1,
    "intercomStatus": "IDLE",
    "createdAt": "2026-07-04T10:00:00+08:00",
    "updatedAt": "2026-07-04T10:01:00+08:00"
  }
]
```

#### GET `/internal/media/video-sessions/active`

用途：查询可复用状态的活跃会话，最多 16 条。

请求示例：

```http
GET /internal/media/video-sessions/active
```

响应示例：`VideoSessionResponse[]`。

#### GET `/internal/media/video-sessions/{sessionId}`

用途：查询单会话，并为当前用户签发 `viewerToken`。

请求示例：

```http
GET /internal/media/video-sessions/vs_123456
```

响应示例：`VideoSessionResponse`。

#### GET `/internal/media/video-sessions/{sessionId}/events`

用途：查询会话最近事件。

请求示例：

```http
GET /internal/media/video-sessions/vs_123456/events
```

响应示例：

```json
[
  {
    "eventId": "evt_001",
    "sessionId": "vs_123456",
    "eventType": "video.session.streaming",
    "eventPayload": "{\"sessionId\":\"vs_123456\"}",
    "traceId": null,
    "createdAt": "2026-07-04T10:01:00+08:00"
  }
]
```

#### GET `/internal/media/video-sessions/{sessionId}/tracks`

用途：查询会话 Track。

请求示例：

```http
GET /internal/media/video-sessions/vs_123456/tracks
```

响应示例：

```json
[
  {
    "trackId": "mt_001",
    "sessionId": "vs_123456",
    "trackSid": "TR_VC_001",
    "trackName": "video.visible.sub",
    "participantIdentity": "robot:test001:camera01",
    "kind": "video",
    "channel": "visible",
    "quality": "sub",
    "publishedAt": "2026-07-04T10:01:00+08:00",
    "unpublishedAt": null
  }
]
```

### 5.6 Backend 调度候选查询接口

三个接口都通过 `before` 查询时间阈值，返回 `sessionId` 字符串数组。

| 方法 | 路径 | Query 参数 | 响应说明 |
|---|---|---|---|
| GET | `/internal/media/video-sessions/interrupted-restart-candidates` | `before`：datetime，必填 | 状态为 `INTERRUPTED` 且仍有 viewer 的会话 |
| GET | `/internal/media/video-sessions/idle-release-candidates` | `before`：datetime，必填 | 状态为 `IDLE_WAIT`、无 viewer、无对讲占用的会话 |
| GET | `/internal/media/video-sessions/intercom-timeout-candidates` | `before`：datetime，必填 | 对讲状态为 `STARTING`/`ACTIVE` 且心跳早于阈值的会话 |

请求示例：

```http
GET /internal/media/video-sessions/interrupted-restart-candidates?before=2026-07-04T10:00:00%2B08:00
```

响应示例：

```json
["vs_123456", "vs_789012"]
```

### 5.7 Backend 视频状态上报接口

#### POST `/internal/media/video-sessions/status`

用途：MQTT 消费链路把机器人客户端视频状态写回 media-service。

关联 MQTT：

| 方向 | Topic | 消费方 |
|---|---|---|
| Go 客户端 -> Control -> Media | `robot/+/media/video/status` | `RobotMediaStatusSubscriber.statusListener` 反序列化 `VideoStatusMessage` 后调用本接口 |

请求体：`VideoStatusMessage`。

请求示例：

```http
POST /internal/media/video-sessions/status
Content-Type: application/json

{
  "sessionId": "vs_123456",
  "status": "streaming",
  "trackSid": "TR_VC_001",
  "trackName": "video.visible.sub",
  "message": "publisher started"
}
```

响应示例：

```http
HTTP/1.1 200 OK
```

#### POST `/internal/media/video-sessions/intercom/status`

用途：MQTT 消费链路把机器人客户端对讲状态写回 media-service。

关联 MQTT：

| 方向 | Topic | 消费方 |
|---|---|---|
| Go 客户端 -> Control -> Media | `robot/+/media/video/intercom/status` | `RobotMediaStatusSubscriber.intercomStatusListener` 反序列化 `IntercomStatusMessage` 后调用本接口 |

请求体：`IntercomStatusMessage`。

请求示例：

```http
POST /internal/media/video-sessions/intercom/status
Content-Type: application/json

{
  "sessionId": "vs_123456",
  "status": "active",
  "robotAudioTrackSid": "TR_AM_001",
  "robotAudioTrackName": "audio.robot.mic"
}
```

响应示例：

```http
HTTP/1.1 200 OK
```

### 5.8 Backend 视频操作接口

#### POST `/internal/media/video-sessions/{sessionId}/token`

用途：签发 viewer token。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/token
```

响应示例：

```json
{
  "livekitUrl": "ws://127.0.0.1:7880",
  "roomName": "media.test001.camera01.visible",
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

#### POST `/internal/media/video-sessions/{sessionId}/heartbeat`

用途：刷新当前 viewer 心跳。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/heartbeat
```

响应示例：`VideoSessionResponse`。

#### POST `/internal/media/video-sessions/{sessionId}/stop`

用途：停止当前 viewer；无人观看且无对讲时切入 `IDLE_WAIT`。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/stop
```

响应示例：`VideoSessionResponse`。

#### POST `/internal/media/video-sessions/{sessionId}/restart`

用途：当前用户手动重启会话，刷新 viewer 并把会话置为请求客户端推流。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/restart
```

响应示例：`VideoSessionResponse`。

#### POST `/internal/media/video-sessions/{sessionId}/switch-channel`

用途：切换通道/码流，更新 `roomName` 并准备重新推流。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `channel` | enum | 是 | `visible`、`thermal`、`fusion` |
| `quality` | enum | 否 | `sub`、`main`、`auto` |

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/switch-channel
Content-Type: application/json

{"channel": "thermal", "quality": "sub"}
```

响应示例：`VideoSessionResponse`。

### 5.9 Backend 对讲接口

| 方法 | 路径 | 请求参数 | 响应 |
|---|---|---|---|
| POST | `/internal/media/video-sessions/{sessionId}/intercom/start` | Path: `sessionId` | `IntercomResponse` |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/token` | Path: `sessionId` | `IntercomResponse` |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/heartbeat` | Path: `sessionId` | `IntercomResponse` |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/stop` | Path: `sessionId` | `VideoSessionResponse` |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/expire` | Path: `sessionId` | `Map`，需要下发给机器人的 stop payload；无需停止时 `{}` |
| POST | `/internal/media/video-sessions/{sessionId}/intercom/start-command` | Path: `sessionId` | `IntercomStartCommand` |

关联 MQTT：

| Backend 接口 | MQTT Topic | 说明 |
|---|---|---|
| `/internal/media/video-sessions/{sessionId}/intercom/start-command` | `robot/{robotId}/media/video/intercom/start` | 只生成 `IntercomStartCommand`，Control 负责发布 |
| `/internal/media/video-sessions/{sessionId}/intercom/expire` | `robot/{robotId}/media/video/intercom/stop` | 返回非空 stop payload 时，Control 调度器负责发布 |
| `/internal/media/video-sessions/{sessionId}/intercom/start` | 无直接 MQTT | 只更新 media-service 状态并签发 operator token；Control 外层接口会额外调用 `intercom/start-command` 并发布 start |
| `/internal/media/video-sessions/{sessionId}/intercom/stop` | 无直接 MQTT | 只更新 media-service 状态；Control 外层接口会额外发布 intercom stop |

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/intercom/start
```

响应示例：

```json
{
  "sessionId": "vs_123456",
  "robotId": "test001",
  "deviceId": "camera01",
  "roomName": "media.test001.camera01.visible",
  "videoStatus": "STREAMING",
  "intercomStatus": "STARTING",
  "intercomAudioOnly": false,
  "livekitUrl": "ws://127.0.0.1:7880",
  "operatorToken": "eyJhbGciOi...",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

`intercom/expire` 响应示例：

```json
{
  "robotId": "test001",
  "sessionId": "vs_123456",
  "commandId": "cmd_stop_intercom_001",
  "roomName": "media.test001.camera01.visible"
}
```

### 5.10 Backend 推流命令接口

#### POST `/internal/media/video-sessions/{sessionId}/client-start`

用途：为首次观看或补发视频 Track 生成机器人推流命令，并把会话状态切到 `REQUESTING_CLIENT`。

关联 MQTT：本接口不直接发布 MQTT；Control 获取响应中的 `VideoStartCommand` 后发布到 `robot/{robotId}/media/video/start`。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---:|---|---|
| `sessionId` | path | string | 是 | - | 会话 ID |
| `event` | query | string | 否 | `video.client.requested` | 记录的事件名 |

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/client-start?event=video.client.requested
```

响应示例：`VideoStartCommand`，见 5.2。

#### POST `/internal/media/video-sessions/{sessionId}/restart-command`

用途：生成重启推流命令。

关联 MQTT：本接口不直接发布 MQTT；Control 获取响应中的 `VideoStartCommand` 后发布到 `robot/{robotId}/media/video/start`。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/restart-command
```

响应示例：`VideoStartCommand`。

#### POST `/internal/media/video-sessions/{sessionId}/start-command`

用途：按当前会话状态生成 start 命令，不更新会话状态。

关联 MQTT：本接口不直接发布 MQTT；切换通道时 Control 获取响应中的 `VideoStartCommand` 后发布到 `robot/{robotId}/media/video/switch-channel`。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/start-command
```

响应示例：`VideoStartCommand`。

#### POST `/internal/media/video-sessions/online-restart-commands`

用途：机器人客户端上线后，查询并生成需要恢复的推流命令。

关联 MQTT：本接口不直接发布 MQTT；Control 消费 `robot/+/media/client/status` 且判断客户端从离线变在线后调用本接口，并将返回列表逐条发布到 `robot/{robotId}/media/video/start`。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `robotId` | query | string | 是 | 机器人 ID |
| `status` | query | string | 是 | 机器人客户端状态；非 `online` 返回空数组 |

请求示例：

```http
POST /internal/media/video-sessions/online-restart-commands?robotId=test001&status=online
```

响应示例：

```json
[
  {
    "commandId": "cmd_001",
    "sessionId": "vs_123456",
    "robotId": "test001",
    "deviceId": "camera01",
    "channel": "visible",
    "quality": "sub",
    "livekitUrl": "ws://127.0.0.1:7880",
    "roomName": "media.test001.camera01.visible",
    "publisherToken": "eyJhbGciOi...",
    "publishIdentity": "robot:test001:camera01",
    "expiresAt": "2026-07-04T10:30:00+08:00"
  }
]
```

#### POST `/internal/media/video-sessions/{sessionId}/release-idle`

用途：释放已进入空闲等待的会话；若真正关闭 Room，则返回需要下发给机器人的 stop payload。

关联 MQTT：本接口不直接发布 MQTT；Control 调度器收到非空响应后发布到 `robot/{robotId}/media/video/stop`。

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/release-idle
```

响应示例：

```json
{
  "robotId": "test001",
  "sessionId": "vs_123456",
  "commandId": "cmd_stop_001",
  "roomName": "media.test001.camera01.visible"
}
```

不需要释放时：

```json
{}
```

### 5.11 Backend 录像与调试接口

| 方法 | 路径 | 请求参数 | 响应 |
|---|---|---|---|
| POST | `/internal/media/video-sessions/{sessionId}/recordings/start` | Path: `sessionId` | `FileListItemResponse` |
| POST | `/internal/media/video-sessions/{sessionId}/recordings/{fileId}/stop` | Path: `sessionId`、`fileId` | `FileListItemResponse` |
| GET | `/internal/media/video-sessions/{sessionId}/recordings/active` | Path: `sessionId` | `FileListItemResponse` |
| POST | `/internal/media/video-sessions/{sessionId}/_mock/track-published/{trackSid}` | Path: `sessionId`、`trackSid` | `VideoSessionResponse` |

请求示例：

```http
POST /internal/media/video-sessions/vs_123456/recordings/start
```

响应示例：

```json
{
  "fileId": "file_rec_001",
  "robotId": "test001",
  "deviceId": "camera01",
  "fileType": "VIDEO",
  "fileName": "live-recording.mp4",
  "contentType": "video/mp4",
  "fileSize": 0,
  "status": "PROCESSING",
  "videoStatus": "PROCESSING",
  "createdAt": "2026-07-04T10:05:00+08:00",
  "metadata": "{\"source\":\"LIVEKIT_EGRESS\"}"
}
```

调试接口请求示例：

```http
POST /internal/media/video-sessions/vs_123456/_mock/track-published/TR_VC_001
```

响应示例：`VideoSessionResponse`，状态通常变为 `STREAMING`。

### 5.12 Backend 文件接口通用模型

#### CreateMultipartFileUploadRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `robotId` | string | 否 | 机器人 ID；也可通过 `X-Robot-Id` 头传入 |
| `deviceId` | string | 否 | 设备 ID |
| `taskExecutionId` | string | 否 | 任务执行 ID |
| `sourceFileId` | string | 否 | 来源文件 ID |
| `fileType` | enum | 是 | `FileType` |
| `fileName` | string | 是 | 原始文件名 |
| `contentType` | string | 是 | MIME 类型 |
| `fileSize` | number | 是 | 文件大小，必须大于 0 |
| `metadata` | string | 否 | 元数据，建议 JSON 字符串 |

#### FileUploadResponse

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` | string | 文件 ID |
| `uploadId` | string | 上传 ID |
| `uploadMode` | string | `SIMPLE` 或 `MULTIPART` |
| `status` | string | 上传状态：`ACTIVE`、`COMPLETED`、`EXPIRED`、`ABORTED`、`FAILED` |
| `partSize` | number | 每片大小 |
| `partCount` | int | 分片数量 |
| `uploadedParts` | array | 已上传分片，元素含 `partNumber`、`etag`、`size` |
| `partUrls` | array | part 上传 URL，元素含 `partNumber`、`uploadUrl` |
| `expiresAt` | datetime | URL 过期时间 |

#### FilePartUrlsResponse

| 字段 | 类型 | 说明 |
|---|---|---|
| `expiresAt` | datetime | URL 过期时间 |
| `parts[].partNumber` | int | 分片号 |
| `parts[].uploadUrl` | string | 上传 URL |

#### FileStatusResponse

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` | string | 文件 ID |
| `status` | string | 文件状态 |
| `fileSize` | number | 文件大小 |
| `ready` | boolean | 是否可读/可播 |
| `errorCode` | string/null | 错误码 |
| `errorMessage` | string/null | 错误描述 |
| `uploadedAt` | datetime/null | 上传完成时间 |

### 5.13 POST `{base}` 简单上传

用途：小文件 multipart 上传。`{base}` 为 `/api/media/files` 或 `/internal/media/files`。

请求类型：`multipart/form-data`。

请求参数：同 4.16。

请求示例：

```http
POST /api/media/files
X-Robot-Id: test001
Content-Type: multipart/form-data

file=@snapshot.jpg
fileType=IMAGE
robotId=test001
deviceId=camera01
metadata={"source":"robot-upload"}
```

响应示例：`FileListItemResponse`，见 2.4。

### 5.14 POST `{base}/multipart-uploads`

用途：创建或恢复大文件分片上传。

请求头：

| Header | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `X-Robot-Id` | string | 否 | 机器人 ID；可作为请求体 `robotId` 的补充 |

请求体：`CreateMultipartFileUploadRequest`。

请求示例：

```http
POST /api/media/files/multipart-uploads
X-Robot-Id: test001
Content-Type: application/json

{
  "robotId": "test001",
  "deviceId": "camera01",
  "taskExecutionId": "task_exec_001",
  "fileType": "VIDEO",
  "fileName": "patrol.mp4",
  "contentType": "video/mp4",
  "fileSize": 104857600,
  "metadata": "{\"source\":\"robot\"}"
}
```

响应示例：

```json
{
  "fileId": "file_video_001",
  "uploadId": "upl_001",
  "uploadMode": "MULTIPART",
  "status": "ACTIVE",
  "partSize": 8388608,
  "partCount": 13,
  "uploadedParts": [],
  "partUrls": [
    {"partNumber": 1, "uploadUrl": "http://minio.local/upload/part1"}
  ],
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

### 5.15 POST `{base}/multipart-uploads/{uploadId}/part-urls`

用途：为指定分片号批量生成上传 URL。

请求参数：

| 参数/字段 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `X-Robot-Id` | header | string | 否 | 机器人 ID |
| `uploadId` | path | string | 是 | 上传 ID |
| `partNumbers` | body | array[int] | 是 | 分片号列表 |

请求示例：

```http
POST /api/media/files/multipart-uploads/upl_001/part-urls
X-Robot-Id: test001
Content-Type: application/json

{"partNumbers": [1, 2, 3]}
```

响应示例：

```json
{
  "expiresAt": "2026-07-04T10:30:00+08:00",
  "parts": [
    {"partNumber": 1, "uploadUrl": "http://minio.local/upload/part1"},
    {"partNumber": 2, "uploadUrl": "http://minio.local/upload/part2"}
  ]
}
```

### 5.16 POST `{base}/multipart-uploads/{uploadId}/complete`

用途：通知服务端分片上传完成，服务端进入处理/可用状态。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `X-Robot-Id` | header | string | 否 | 机器人 ID |
| `uploadId` | path | string | 是 | 上传 ID |

请求示例：

```http
POST /api/media/files/multipart-uploads/upl_001/complete
X-Robot-Id: test001
```

响应示例：

```json
{
  "fileId": "file_video_001",
  "status": "PROCESSING",
  "fileSize": 104857600,
  "ready": false,
  "errorCode": null,
  "errorMessage": null,
  "uploadedAt": "2026-07-04T10:10:00+08:00"
}
```

### 5.17 GET `{base}/{fileId}/status`

用途：查询文件处理状态。

请求示例：

```http
GET /api/media/files/file_video_001/status
X-Robot-Id: test001
```

响应示例：

```json
{
  "fileId": "file_video_001",
  "status": "READY",
  "fileSize": 104857600,
  "ready": true,
  "errorCode": null,
  "errorMessage": null,
  "uploadedAt": "2026-07-04T10:10:00+08:00"
}
```

### 5.18 Backend 文件查询、播放与下载接口

这些接口和 Control 文件接口的参数/响应结构一致，差别是 base path 为 `/api/media/files` 或 `/internal/media/files`。

| 方法 | 路径 | 请求参数 | 响应 |
|---|---|---|---|
| GET | `{base}` | Query: `robotId`、`deviceId`、`taskExecutionId`、`fileType`、`status`、`page`、`size` | `FileListResponse` |
| GET | `{base}/{fileId}` | Path: `fileId` | `FileListItemResponse` |
| POST | `{base}/{fileId}/download-url` | Path: `fileId` | `FileDownloadUrlResponse` |
| GET | `{base}/{fileId}/content` | Path: `fileId` | 文件二进制 |
| POST | `{base}/{fileId}/play-url` | Path: `fileId` | `FilePlayUrlResponse` |
| GET | `{base}/{fileId}/hls/{objectName}` | Path: `fileId`、`objectName`；Query: `token` | HLS 二进制/文本 |

请求示例：

```http
GET /api/media/files?robotId=test001&fileType=VIDEO&status=READY&page=0&size=20
```

响应示例：

```json
{
  "items": [
    {
      "fileId": "file_video_001",
      "robotId": "test001",
      "deviceId": "camera01",
      "taskExecutionId": "task_exec_001",
      "fileType": "VIDEO",
      "fileName": "patrol.mp4",
      "contentType": "video/mp4",
      "fileSize": 104857600,
      "durationSeconds": 600,
      "status": "READY",
      "videoStatus": "READY",
      "uploadedAt": "2026-07-04T10:10:00+08:00",
      "createdAt": "2026-07-04T10:00:00+08:00",
      "metadata": "{\"source\":\"robot\"}"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

播放 URL 请求示例：

```http
POST /api/media/files/file_video_001/play-url
```

播放 URL 响应示例：

```json
{
  "fileId": "file_video_001",
  "format": "HLS",
  "contentType": "application/vnd.apple.mpegurl",
  "playUrl": "/api/media/files/file_video_001/hls/index.m3u8?token=play_abc",
  "expiresAt": "2026-07-04T10:30:00+08:00"
}
```

### 5.19 POST `{base}/task-execution-binding`

用途：把文件绑定到任务执行记录。Controller 接收通用 `Map<String,Object>`，具体字段由 `FileService.bindTaskExecution` 解释。

常用请求字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `fileId` | string | 是 | 文件 ID |
| `taskExecutionId` | string | 是 | 任务执行 ID |

请求示例：

```http
POST /api/media/files/task-execution-binding
Content-Type: application/json

{
  "fileId": "file_video_001",
  "taskExecutionId": "task_exec_001"
}
```

响应示例：

```http
HTTP/1.1 204 No Content
```

### 5.20 Control Service 机器人状态内部模块

机器人在线状态、摄像头和设备能力已从 `backend` 迁移到 `control-service/src/main/java/com/robot/control/robot`。当前没有 `GET /internal/media/robots` 或 `POST /internal/media/robots/client-status` 这类 media-service 接口；客户端状态由 MQTT 订阅回调直接写入 Control 本地内存注册表。

内部响应模型：

| 字段 | 类型 | 说明 |
|---|---|---|
| `robotId` | string | 机器人 ID |
| `clientId` | string | 客户端 ID |
| `name` | string | 展示名称 |
| `type` | string | 机器人类型 |
| `battery` | number/null | 电量 |
| `status` | string | `online`/`offline` 等 |
| `controlMode` | string | 控制模式 |
| `stateSeq` | number/null | 状态序号 |
| `missionStatus` | string | 任务状态 |
| `navigationStatus` | string | 导航状态 |
| `controlOwner` | object/null | 控制占用者 |
| `estopActive` | boolean/null | 急停状态 |
| `lastHeartbeatAt` | datetime | 最近心跳时间 |
| `cameras` | array | 摄像头列表 |
| `devices` | array | 设备能力列表 |
| `timestamp` | string/null | 机器人状态时间 |

`RobotDeviceResponse` 示例：

```json
[
  {
    "robotId": "test001",
    "clientId": "robot-client-test001",
    "name": "R1轮式机器人",
    "type": "轮式机器人",
    "battery": 86,
    "status": "online",
    "controlMode": "MANUAL",
    "stateSeq": 12,
    "missionStatus": "IDLE",
    "navigationStatus": "IDLE",
    "controlOwner": null,
    "estopActive": false,
    "lastHeartbeatAt": "2026-07-04T10:00:00+08:00",
    "cameras": [
      {"cameraId": "camera01", "deviceId": "camera01", "groupType": "dual_gimbal", "name": "云台-可见光", "quality": "sub"}
    ],
    "devices": [],
    "timestamp": "2026-07-04T10:00:00+08:00"
  }
]
```

#### MQTT `robot/+/media/client/status`

用途：更新 Control Service 本地机器人客户端状态；返回是否从离线变为在线等判断结果仅在进程内使用。

关联 MQTT：

| 方向 | Topic | 消费方 / 后续动作 |
|---|---|---|
| Go 客户端 -> Control | `robot/+/media/client/status` | `RobotMediaStatusSubscriber.clientStatusListener` 反序列化为 `Map` 后调用 `RobotRegistryService.update(...)`；若客户端从离线变在线，会再查询 `/internal/media/video-sessions/online-restart-commands` 并发布 `robot/{robotId}/media/video/start` |

请求参数：

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---:|---|---|
| `robotId` | string | 是 | - | 机器人 ID |
| `clientId` | string | 是 | - | 客户端 ID |
| `status` | string | 是 | - | `online`、`offline` 等 |
| `name` | string | 否 | `robotId` | 展示名称 |
| `type` | string | 否 | `机器人` | 机器人类型 |
| `controlMode` | string | 否 | `MANUAL` | 控制模式 |
| `stateSeq` | number | 否 | null | 状态序号 |
| `missionStatus` | string | 否 | `IDLE` | 任务状态 |
| `navigationStatus` | string | 否 | `IDLE` | 导航状态 |
| `controlOwner` | object | 否 | null | 控制占用者 |
| `estopActive` | boolean | 否 | null | 急停状态 |
| `battery` | number | 否 | null | 电量 |
| `cameras` | array | 否 | `[]` | 摄像头列表 |
| `devices` | array | 否 | `[]` | 设备能力列表 |

Payload 示例：

```json
{
  "robotId": "test001",
  "clientId": "robot-client-test001",
  "status": "online",
  "name": "R1轮式机器人",
  "type": "轮式机器人",
  "battery": 86,
  "controlMode": "MANUAL",
  "stateSeq": 12,
  "missionStatus": "IDLE",
  "navigationStatus": "IDLE",
  "estopActive": false,
  "cameras": [
    {"cameraId": "camera01", "deviceId": "camera01", "groupType": "dual_gimbal", "name": "云台-可见光", "quality": "sub"}
  ],
  "devices": []
}
```

响应示例：

无 HTTP 响应；`becameOnline` 结果仅用于 Control 进程内判断是否触发恢复推流。

### 5.21 Backend TTS 接口

#### GET `/api/media/tts/generate-file`

用途：生成 TTS 音频文件并以文件流返回。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `X-Robot-Id` | header | string | 是 | 机器人 ID |
| `text` | query | string | 是 | 待合成文本 |

请求示例：

```http
GET /api/media/tts/generate-file?text=%E8%AF%B7%E6%B3%A8%E6%84%8F%E5%AE%89%E5%85%A8
X-Robot-Id: test001
```

响应示例：

```http
HTTP/1.1 200 OK
Content-Type: audio/wav
Content-Disposition: attachment; filename="tts-test001.wav"

<audio binary>
```

具体 `Content-Type`、文件名和状态码以 `TtsAudioService.generateAndReturnFile` 生成结果为准。

#### GET `/api/media/tts/generate-and-play`

用途：生成 TTS 并发布给前端/播放链路，无响应体。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---:|---|
| `X-Robot-Id` | header | string | 是 | 机器人 ID |
| `text` | query | string | 是 | 待合成文本 |

请求示例：

```http
GET /api/media/tts/generate-and-play?text=%E8%AF%B7%E6%B3%A8%E6%84%8F%E5%AE%89%E5%85%A8
X-Robot-Id: test001
```

响应示例：

```http
HTTP/1.1 200 OK
```

## 6. 前端实际调用对照

当前 `frontend/src/api/media.js` 已覆盖的有效 Java 接口：

| 前端函数 | Java 接口 |
|---|---|
| `getRobots` | `GET /api/bigscreen/panorama/overview` |
| `getControlProfile` | `GET /api/control/robots/{robotId}/control-profile` |
| `acquireControl` | `POST /api/control/robots/{robotId}/control-sessions/acquire` |
| `takeoverControl` | `POST /api/control/robots/{robotId}/control-sessions/takeover` |
| `setControlMode` | `POST /api/control/robots/{robotId}/control-mode` |
| `releaseControl` | `POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/release` |
| `createConfirmToken` | `POST /api/control/robots/{robotId}/commands/confirm-token` |
| `sendEquipmentCommand` | `POST /api/control/robots/{robotId}/commands` |
| `createVideoSession` | `POST /api/control/robots/{robotId}/cameras/{deviceId}/video/start` |
| `startCameraIntercom` | `POST /api/control/robots/{robotId}/cameras/{deviceId}/video/intercom/start` |
| `getActiveVideoSessions` | `GET /api/control/video-sessions/active` |
| `getViewerToken` | `POST /api/control/video-sessions/{sessionId}/token` |
| `startSessionIntercom` | `POST /api/control/video-sessions/{sessionId}/intercom/start` |
| `heartbeatIntercom` | `POST /api/control/video-sessions/{sessionId}/intercom/heartbeat` |
| `stopIntercom` | `POST /api/control/video-sessions/{sessionId}/intercom/stop` |
| `stopVideoSession` | `POST /api/control/video-sessions/{sessionId}/stop` |
| `heartbeatVideoSession` | `POST /api/control/video-sessions/{sessionId}/heartbeat` |
| `restartVideoSession` | `POST /api/control/video-sessions/{sessionId}/restart` |
| `switchChannel` | `POST /api/control/video-sessions/{sessionId}/switch-channel` |
| `uploadFile` | `POST /api/control/files` |
| `getFiles` | `GET /api/control/files` |
| `fileDownloadUrl` | `POST /api/control/files/{fileId}/download-url` |
| `snapshotImageUrl` | `GET /api/control/files/{fileId}/content` |
| `getSessionEvents` | `GET /api/control/video-sessions/{sessionId}/events` |
| `getFilePlayUrl` | `POST /api/control/files/{fileId}/play-url` |
| `startLiveRecording` | `POST /api/control/video-sessions/{sessionId}/recordings/start` |
| `stopLiveRecording` | `POST /api/control/video-sessions/{sessionId}/recordings/{fileId}/stop` |
| `getActiveLiveRecording` | `GET /api/control/video-sessions/{sessionId}/recordings/active` |

`robot-ui/src/api/media.js` 中仍有 `/api/control/snapshots/**`、`/api/control/recordings/**` 旧调用，当前 Java Controller 未实现，联调时建议迁移到 `/api/control/files` 和视频会话录像接口。
