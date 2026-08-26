# 大屏 BFF 未接入数据字段清单

更新时间：2026-08-12

## 1. 口径

本文只梳理 BFF 当前接口中“不是从中心端接口直接查询得到”的字段。

字段分三类：

| 类型 | 含义 | 是否需要中心端补接口 |
|---|---|---|
| BFF 生成 | 时间、筛选条件回显、本地报告记录等页面辅助字段 | 不需要 |
| BFF 计算 | 根据已查询到的数据统计、分组、转换出的字段 | 通常不需要 |
| 缺少真实来源 | 中心端当前没有提供，BFF 只能返回 `null`、`[]`，或本地拼出来 | 需要补齐或确认可为空 |

当前 `PanoramaMockService`、`StatisticsMockService` 已移除，REST 聚合不再使用整套固定场景数据兜底。但 `PanoramaWebSocketEventAdapter` 对无定位的 `test111`、`SN005`、`SN006` 仍会派生硬编码演示位置事件；这些事件不是权威定位，生产不得作为真实数据。

## 2. 全景地图接口

涉及接口：

```text
GET  /api/bigscreen/panorama/overview
GET  /api/bigscreen/panorama/devices/{deviceId}
GET  /api/bigscreen/panorama/tasks
GET  /api/bigscreen/panorama/alarms
POST /api/bigscreen/panorama/alarms/{alarmId}/disposal
```

### 2.1 已真实查询的数据来源

| 数据 | 当前来源 |
|---|---|
| 设备档案 | `/api/v1/management/devices`、`/api/v1/management/devices/{id}` |
| 设备实时状态 | `/api/v1/control/device-realtime-statuses` |
| 任务计划 | `/api/v1/management/task-workflow-plans` |
| 任务实例 | `/api/v1/management/task-workflow-instances`、`/api/v1/management/task-workflow-instances/{id}` |
| 设备子任务 | `/api/v1/management/device-task-instances?workflowInstanceId=...` |
| 任务执行回放 | `/api/v1/management/task-workflow-instances/{id}/replay` |
| 任务路线定义 | `/api/v1/management/task-workflow-definitions/{workflowDefinitionId}` |
| 地图列表 | `/api/v1/management/maps` |
| 地图点位 | `/api/v1/management/maps/{mapId}/points` |
| 路径点位引用 | `/api/v1/management/paths/{pathId}/points` |
| 告警列表 | `/api/v1/management/alarms` |
| 告警处置 | `/api/v1/management/alarms/{alarmId}/handled` |
| 固定摄像头 | `/api/v1/management/fixed-cameras` |

### 2.2 顶层字段

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `serverTime` | BFF 生成 | 当前 BFF 时间，不查询中心端 |
| `deviceStats` | BFF 计算 | 由 `devices[]` 的总数、状态、故障标识计算 |
| `gpsDevices` | BFF 筛选 | 从 `devices[]` 中筛选经纬度完整的设备，不新增下游数据源 |
| `map[].deviceIds` | BFF 匹配 | 按 `devices[].location.mapId` 将 `robotId` 归入对应地图，不重复返回完整设备对象，不新增下游数据源 |
| `deviceTypeStats` | BFF 计算 | 由 `devices[]` 按 `typeCode` 分组计算 |
| `taskOverview` | BFF 计算 | 由 `tasks[]` 的状态统计计算 |
| `alarms.total` | BFF 计算 | 由告警列表数量计算 |
| `alarms.summary.*` | BFF 计算 | 由告警列表状态计算 |
| `alarms.high/medium/low` | BFF 分组 | 由告警等级分组得到 |
| `patrolOverview.durationToday` | BFF 计算 | 由今日任务实例 `durationSeconds` 或 `startedAt/completedAt` 计算小时数 |
| `patrolOverview.durationUnit` | BFF 生成 | `durationToday` 有值时返回“小时” |
| `patrolOverview.mileageUnit` | BFF 生成 | 查询范围存在有效里程样本时返回 `KM` |

### 2.3 `devices[]`

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `type` | BFF 转换 | 由 `typeCode` 转中文名称 |
| `status` | BFF 转换 | 由控制端 `onlineStatus` 标准化为 `online/offline/fault` |
| `lastHeartbeatAt` | BFF 格式化 | 由实时状态时间字段格式化 |
| `mountedDevices[].type` | BFF 提取 | 从组件 `capabilities` 取第一个能力编码 |
| `mountedDevices[].status` | BFF 派生 | 当前直接使用机器人在线状态，不是上装设备独立状态 |
| `mountedDeviceCount` | BFF 计算 | 由组件数量计算；组件未返回时为 `null` |
| `cameras[]` | 控制端实时清单优先 | 优先取 `/api/control/robots/registry` 中与 `robot.state.cameras` 同源的相机清单；控制端无清单时才根据管理端组件兜底拼装 |
| `cameras[].quality` | 控制端优先、BFF 兜底 | 优先取实时相机清单中的清晰度，字段缺失时默认为 `sub` |
| `stateSeq` | 可能缺少来源 | 只有控制端实时状态返回 `stateSeq` 时才有值，否则为 `null` |
| `fault` | BFF 计算 | 由 `status.basic.healthStatus` 判断 |
| `alarmLevel` | BFF 转换 | 由 `status.basic.alarmStatus` 转换 |
| `location.lng` | 可能缺少来源 | 只有控制端定位返回经度时才有值 |
| `location.lat` | 可能缺少来源 | 只有控制端定位返回纬度时才有值 |
| `location.altitude` | 可能缺少来源 | 只有控制端定位返回高度时才有值 |
| `location.address` | 可能缺少来源 | 只有控制端定位返回地址时才有值 |
| `location.updatedAt` | BFF 格式化 | 由实时状态上报时间格式化 |
| `mapDisplay.icon` | 缺少真实来源 | 当前返回 `null` |
| `mapDisplay.label` | BFF 派生 | 使用设备名称 |
| `mapDisplay.badgeText` | BFF 派生 | 根据状态/告警展示文案 |
| `mapDisplay.badgeStatus` | BFF 派生 | 根据状态/告警展示编码 |
| `task[].name` | 间接查询 | 优先取控制端实时任务名称；没有时按任务实例 ID 查询管理端任务实例补齐 |
| `task[].timeRange` | BFF 计算 | 按任务实例 `startedAt/completedAt` 计算；时间不完整时为 `null` |

固定摄像头也会合并进 `devices[]`。其配置启停、配置完整性、Gateway 心跳和 RTSP 健康
已经分离；只有全部满足时 `status=online`，健康缺失或过期归为 `offline`。`playable` 是
`enabled && configReady` 的兼容字段，不表示在线。BFF 不输出码流 URL。

### 2.4 `/devices/{deviceId}` 设备详情

设备详情来自 `overview.devices[]` 的单个设备对象，没有额外查询新的详情来源。

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `alarmStatus` | BFF 派生 | 来自 `devices[].alarmLevel` |
| `alarmText` | BFF 派生 | 有告警等级时固定为“存在未处理告警” |
| `currentTask` | BFF 复制 | 复制 `devices[].task[]` |
| `actions.*` | BFF 派生 | 根据设备在线状态生成按钮可用性；设备状态未知时返回 `null` |

### 2.5 `tasks[]`

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `status` | BFF 转换 | 优先由任务实例状态/计划 active 状态标准化，缺少时使用任务计划状态 |
| `statusName` | BFF 转换 | 由状态编码转中文 |
| `startTime` | BFF 格式化 | 优先由任务实例 `startedAt` 格式化，缺少时使用任务计划时间 |
| `endTime` | BFF 格式化 | 优先由任务实例 `completedAt` 格式化，缺少时使用任务计划时间 |
| `timeRange` | BFF 计算 | 由 `startTime/endTime` 计算；时间不完整时为 `null` |
| `currentLocation` | 间接查询 | 优先取任务计划 `currentLocation`；没有时取任务回放最新轨迹点 `pointName` |
| `equipmentList[]` | 间接查询 | 优先取设备子任务，其次取任务实例/回放 `deviceSummaries`，最后保留计划 `roleBindings` 空字段结构 |
| `equipmentList[].name` | 间接查询 | 优先来自设备子任务或 `deviceSummaries`；`roleBindings` 兜底时为 `null` |
| `equipmentList[].type` | 可能缺少来源 | 只有设备子任务或 `deviceSummaries` 返回设备类型时才有值 |
| `equipmentList[].status` | BFF 关联 | 按 `robotId` 关联 `devices[].status`，表示设备在线状态；未匹配时为 `null` |
| `mapId` | 间接查询 | 由工作流定义接口返回，不是任务计划直接返回 |
| `mapPoints` | 间接查询 | 由 `mapId` 查询地图点位 |
| `pathPoints` | BFF 过滤 | 先查路径点位引用，再按 `mapPointId` 从 `mapPoints` 中过滤 |

### 2.6 告警数据

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `categoryName` | BFF 转换 | 由告警类型编码转中文 |
| `level` | BFF 转换 | 由严重级别标准化为 `HIGH/MEDIUM/LOW` |
| `levelName` | BFF 转换 | 由等级编码转中文 |
| `eventTime` | BFF 格式化 | 由告警时间格式化 |
| `status` | BFF 转换 | 由告警状态标准化 |
| `location` | 可能缺少来源 | 优先取告警接口结构化 `location`；没有时尝试从 `rawPayload.location` 读取；仍没有则为 `null` |
| `snapshotUrl` | 间接查询 | 优先取告警 `snapshotUrl` 或 `rawPayload.snapshotUrl`；没有时将管理端 `imageUrl/imageFileId` 映射到 `visible` |
| `taskName` | 间接查询 | 优先取告警字段；没有时按 `taskInstanceId` 查询任务实例的 `workflowName` |

### 2.7 告警处置返回

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `serverTime` | BFF 生成 | 当前 BFF 时间 |
| `disposalStatusName` | BFF 转换 | 由请求参数 `IMMEDIATE_DISPOSAL/FALSE_ALARM` 转中文 |
| `status` | BFF 转换 | 管理端处置成功后映射为 `handled/false_alarm`；失败时为 `null` |
| `message` | BFF 生成 | 根据调用成功与否返回文案 |

## 3. 数据统计接口

涉及接口：

```text
GET    /api/bigscreen/statistics/overview
POST   /api/bigscreen/statistics/reports/export
GET    /api/bigscreen/statistics/reports
GET    /api/bigscreen/statistics/reports/{id}/download
DELETE /api/bigscreen/statistics/reports/{id}
```

### 3.1 `/statistics/overview`

当前统计总览已基于管理端设备、实时状态、任务实例、告警明细和 Control 里程汇总完成首批真实统计。下表仅保留尚未接入的字段；已接入的里程指标不再列为缺口。

| 字段 | 当前处理 | 说明 |
|---|---|---|
| `serverTime` | BFF 生成 | 当前 BFF 时间 |
| `range` | BFF 生成/回显 | 根据查询参数生成统计时间范围 |
| `filters` | BFF 回显 | 回显 `deviceType/areaId` |
| `kpis.taskTotal.compareRate` | 缺少真实来源 | 当前返回 `null` |
| `kpis.aiAlarmTotal.compareRate` | 缺少真实来源 | 当前返回 `null` |
| `kpis.autoHandleSuccessRate.value` | 缺少真实来源 | 当前返回 `null` |
| `kpis.autoHandleSuccessRate.compareRate` | 缺少真实来源 | 当前返回 `null` |
| `equipmentRuntime.items[].faultHours` | 缺少设备故障历史时长 | 当前返回 `null` |
| `equipmentRuntime.items[].offlineHours` | 缺少设备离线历史时长 | 当前返回 `null` |
| `alarmAreaRanking[].areaId` | 告警明细缺少稳定区域 ID | 当前返回 `null` |

### 3.2 报告接口

| 接口 | 当前处理 | 说明 |
|---|---|---|
| `POST /statistics/reports/export` | BFF 本地生成 | 使用当前统计总览数据生成 PDF；不是中心端报告文件 |
| `GET /statistics/reports` | BFF 本地查询 | 查询本地 `data/statistics-reports/index.json` |
| `GET /statistics/reports/{id}/download` | BFF 本地读取 | 读取本地生成的 PDF |
| `DELETE /statistics/reports/{id}` | BFF 本地删除 | 删除本地报告记录和文件 |

## 4. 代理接口

以下接口是透明代理或路径映射，BFF 不组装响应字段，因此不单独统计“未真实查询字段”：

```text
/api/bigscreen/business/**
/api/control/**
/api/media/**
/api/manage/**
/api/v1/management/**
```

`/internal/**` 已从 BFF 对外代理范围移除，只允许内部服务通过内网地址直接调用。

`/api/v1/control/**` 不是 BFF 对外透明代理。BFF 仅在全景聚合内部通过 `CENTER_V1_CONTROL_BASE_URL` 调用旧版控制服务的 `/api/v1/control/device-realtime-statuses`。

特殊说明：

| 接口 | 当前处理 |
|---|---|
| `/api/control/robots` | 已移除，返回 `410 Gone`，提示改用 `/api/bigscreen/panorama/overview` |
| `/api/bigscreen/business/**` | 映射到管理端任务、设备、地图等接口 |

## 5. WebSocket

当前 BFF 注册：

```text
/ws/control
/ws/media
/ws/bigscreen
```

实际行为是桥接到配置的 Control `center.websocket-control-url`。当前没有定时推送整套本地 panorama mock 的调度器；如果上游 WebSocket 不可用，BFF 不会主动补齐业务快照。上游发来 `robot.state` 但不带定位时，只有上述三个演示 robotId 会触发本地位置事件。

需要中心端提供或确认的实时数据：

| 事件/数据 | 当前状态 |
|---|---|
| 设备状态变化 | 依赖中心端 WebSocket 推送 |
| 设备位置变化 | 依赖 Control WebSocket 中的 `robot.state` 定位；三个特定 ID 存在演示兜底 |
| 任务变化 | 依赖中心端 WebSocket 推送 |
| 告警变化 | 依赖中心端 WebSocket 推送 |
| 统计类实时变化 | 设备/任务/告警变化可触发局部统计刷新；更细粒度历史指标仍未接入 |

## 6. 建议优先补齐

优先级从高到低：

| 优先级 | 需要补齐的数据 | 原因 |
|---|---|---|
| P0 | 设备经纬度、地址、上报时间 | 全景地图设备点位展示依赖 |
| P0 | 告警结构化位置、多路截图 `snapshotUrl.thermal/front` | 告警列表和弹窗展示依赖 |
| P1 | 统计总览未接入字段的权威来源 | 已接入字段可用；环比、故障/离线时长和自动处置指标仍为空 |
| P1 | 相机与可播放视频源映射 | 相机清单已与 `robot.state` 统一，但当前仍不直接提供流地址，播放能力依赖后续视频会话链路 |
| P2 | 上装设备独立状态 | 当前上装设备状态直接复用机器人状态 |
| P2 | 设备详情按钮权限/能力 | 当前 `actions.*` 由在线状态简单派生 |
