# 大屏 BFF 字段来源映射清单

更新时间：2026-07-08

## 1. 说明

本文按当前 `bigscreen-bff` 代码梳理 BFF 响应字段来源。

来源类型：

| 类型 | 含义 |
|---|---|
| 管理端 | 来自 `management-api.md` 中 `/api/v1/management/**` 接口字段 |
| 控制端 | 来自 `/api/v1/control/**` 实时状态接口字段 |
| BFF 计算 | BFF 根据已查询数据统计、分组、格式化或转换 |
| BFF 生成 | BFF 自行生成页面辅助字段 |
| 本地存储 | BFF 本地文件或内存记录 |
| 未对接 | 当前没有真实来源，保持 `null` 或 `[]` |

## 2. 当前已调用的管理端接口

| 用途 | 管理端接口 |
|---|---|
| 设备列表 | `GET /api/v1/management/devices?pageNum=1&pageSize=100` |
| 设备详情/组件 | `GET /api/v1/management/devices/{id}` |
| 任务计划 | `GET /api/v1/management/task-workflow-plans?pageNum=1&pageSize=20&enabled=true` |
| 任务实例列表 | `GET /api/v1/management/task-workflow-instances?pageNum=1&pageSize=100&includeRunning=true` |
| 任务实例详情 | `GET /api/v1/management/task-workflow-instances/{id}` |
| 任务执行回放 | `GET /api/v1/management/task-workflow-instances/{id}/replay` |
| 设备子任务 | `GET /api/v1/management/device-task-instances?workflowInstanceId={id}` |
| 任务定义 | `GET /api/v1/management/task-workflow-definitions/{workflowDefinitionId}` |
| 地图列表 | `GET /api/v1/management/maps?pageNum=1&pageSize=500&enabled=true` |
| 地图点位 | `GET /api/v1/management/maps/{mapId}/points` |
| 路径点位 | `GET /api/v1/management/paths/{pathId}/points` |
| 告警列表 | `GET /api/v1/management/alarms?pageNum=1&pageSize=20` |
| 告警处置 | `PATCH /api/v1/management/alarms/{alarmId}/handled` |

## 3. 全景地图聚合接口

接口：

```text
GET /api/bigscreen/panorama/overview
```

### 3.1 顶层字段

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `serverTime` | BFF 当前服务时间 | BFF 生成 | `OffsetDateTime.now(+08:00)` |
| `devices` | 全部机器人/设备展示列表 | 管理端 + 控制端 + BFF 组装 | 见 3.2 |
| `deviceStats.total` | 设备总数 | BFF 计算 | `devices.size()` |
| `deviceStats.online` | 在线设备数 | BFF 计算 | 统计 `devices[].status == online` |
| `deviceStats.fault` | 故障设备数 | BFF 计算 | 统计 `devices[].fault == true` |
| `deviceStats.offline` | 离线设备数 | BFF 计算 | 统计 `devices[].status == offline` |
| `deviceTypeStats[]` | 按设备类型统计 | BFF 计算 | 按 `devices[].typeCode` 分组，计算 `count/fault/offline` |
| `patrolOverview.durationToday` | 今日巡逻时长，单位小时 | BFF 计算 | 今日任务实例 `durationSeconds`；没有时用 `startedAt/completedAt` 计算 |
| `patrolOverview.durationUnit` | 巡逻时长单位 | BFF 生成 | `durationToday` 有值时为 `小时` |
| `patrolOverview.mileageToday` | 今日巡逻里程 | 未对接 | 当前管理端无直接里程字段，返回 `null` |
| `patrolOverview.mileageUnit` | 巡逻里程单位 | 未对接 | 当前返回 `null` |
| `tasks` | 任务列表 | 管理端 + BFF 组装 | 见 3.6 |
| `taskOverview.totalToday` | 今日任务数/当前任务列表总数 | BFF 计算 | `tasks.size()` |
| `taskOverview.completedRate` | 完成率数字 | BFF 计算 | `completed / total * 100` |
| `taskOverview.completedRateText` | 完成率文案 | BFF 计算 | `completedRate + "%"` |
| `taskOverview.running` | 执行中任务数 | BFF 计算 | 统计 `tasks[].status == running` |
| `taskOverview.pending` | 待执行任务数 | BFF 计算 | 统计 `tasks[].status == pending` |
| `map` | 地图列表 | 管理端 | 直接返回 `/api/v1/management/maps` 的 `data.records` |
| `alarms` | 告警聚合对象 | 管理端 + BFF 组装 | 见 3.8 |

### 3.2 `devices[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `robotId` | 机器人唯一展示 ID | 管理端 | `DeviceResponse.serialNumber` |
| `clientId` | MQTT/客户端 ID | 管理端 | `DeviceResponse.authMqttClientId`，兼容 `clientId` |
| `name` | 设备名称 | 管理端 | `DeviceResponse.deviceName`，兼容 `name` |
| `type` | 设备类型中文名 | BFF 计算 | 由 `deviceType/typeCode/type` 转换，如 `ROBOT_DOG -> 机器狗` |
| `typeCode` | 设备类型编码 | 管理端 | `DeviceResponse.deviceType`，兼容 `typeCode/type` |
| `vendor` | 厂商 | 管理端 | `DeviceResponse.manufacturer`，兼容 `vendor` |
| `model` | 型号 | 管理端 | `DeviceResponse.model` |
| `status` | 在线状态 | 控制端 | `DeviceRealtimeStatus.onlineStatus` 转为 `online/offline/fault` |
| `battery` | 电量百分比 | 控制端 | `status.energy.batteryPercent` |
| `lastHeartbeatAt` | 最近心跳/上报时间 | 控制端 + BFF 格式化 | `lastSeenAt/receivedAt/reportedAt` |
| `cameras` | 相机展示集合 | BFF 拼装 | 见 3.4；当前未对接权威媒体/相机接口 |
| `mountedDevices` | 上装设备/组件集合 | 管理端 + BFF 组装 | 见 3.3 |
| `stateSeq` | 实时状态序号 | 控制端 | `DeviceRealtimeStatus.stateSeq`；无则 `null` |
| `fault` | 是否故障 | 控制端 + BFF 计算 | `status.basic.healthStatus != NORMAL` |
| `alarmLevel` | 设备告警等级 | 控制端 + BFF 转换 | `status.basic.alarmStatus` 转 `HIGH/MEDIUM/LOW`；正常为空 |
| `controlMode` | 控制模式 | 控制端 | `status.control.controlMode` |
| `mountedDeviceCount` | 上装设备数量 | BFF 计算 | `DeviceDetailResponse.components.size()`；未返回组件时为 `null` |
| `speed` | 当前速度 | 控制端 | `status.motion.speed` |
| `location` | 设备定位信息 | 控制端 | 见 3.5 |
| `mapDisplay.icon` | 地图图标 | 未对接 | 当前固定 `null` |
| `mapDisplay.label` | 地图展示名称 | BFF 生成 | 使用 `devices[].name` |
| `mapDisplay.badgeText` | 地图状态文案 | BFF 计算 | 有告警为 `告警中`；否则由状态转中文 |
| `mapDisplay.badgeStatus` | 地图状态编码 | BFF 计算 | 有告警为 `alarm`；否则使用 `status` |
| `task` | 当前任务数组 | 控制端 + 管理端 + BFF 组装 | 见 3.7 |

### 3.3 `devices[].mountedDevices[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `deviceId` | 上装设备/组件 ID | 管理端 | `DeviceComponentResponse.code/deviceId/id` |
| `name` | 上装设备名称 | 管理端 | `DeviceComponentResponse.name`，兼容 `componentName` |
| `type` | 上装能力/类型 | 管理端 | 取 `DeviceComponentCapabilityResponse.code/capabilityCode` 的第一个值 |
| `status` | 上装设备状态 | BFF 派生 | 当前复用机器人 `status`，未对接上装设备独立状态 |

### 3.4 `devices[].cameras[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `cameraId` | 相机 ID | BFF 拼装 | 双光云台组件拼 `{componentId}camera01/02`；本体相机用 `robotId` |
| `deviceId` | 关联设备/组件 ID | BFF 拼装 | 双光云台用组件 ID；本体相机用 `robotId` |
| `groupType` | 相机分组 | BFF 拼装 | 双光云台为 `dual_gimbal`，本体相机为 `body` |
| `name` | 相机名称 | BFF 拼装 | `云台-可见光`、`云台-热成像`、`本体相机` |
| `quality` | 默认清晰度 | BFF 生成 | 固定 `sub` |

说明：`cameras[]` 当前只根据管理端组件名称包含“双光云台”进行推断，未对接媒体服务或管理端的权威相机/视频流字段。

### 3.5 `devices[].location`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `lng` | 经度 | 控制端 | `status.localization.lng/longitude` |
| `lat` | 纬度 | 控制端 | `status.localization.lat/latitude` |
| `altitude` | 高度 | 控制端 | `status.localization.altitude` |
| `x` | 地图/局部坐标 X | 控制端 | `status.localization.coordinateX` |
| `y` | 地图/局部坐标 Y | 控制端 | `status.localization.coordinateY` |
| `z` | 地图/局部坐标 Z | 控制端 | `status.localization.coordinateZ` |
| `address` | 位置文字 | 控制端 | `status.localization.address` |
| `updatedAt` | 定位更新时间 | 控制端 + BFF 格式化 | `reportedAt/receivedAt/lastSeenAt` |

### 3.6 `tasks[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `taskId` | 任务计划 ID | 管理端 | `TaskWorkflowPlanResponse.id`，兼容 `taskId` |
| `name` | 任务名称 | 管理端 | `TaskWorkflowPlanResponse.planName/workflowName/name` |
| `status` | 任务状态编码 | 管理端 + BFF 转换 | 优先 `activeWorkflowInstanceStatus`，再取任务实例 `status`、计划 `executionStatus/lastResultStatus/status` |
| `statusName` | 任务状态中文名 | BFF 转换 | 由 `status` 转中文 |
| `startTime` | 任务开始时间 | 管理端 + BFF 格式化 | 优先任务实例 `startedAt`，其次计划 `startedAt/lastStartedAt/startTime` |
| `endTime` | 任务结束时间 | 管理端 + BFF 格式化 | 优先任务实例 `completedAt`，其次计划 `completedAt/lastCompletedAt/endTime` |
| `timeRange` | 页面展示时间段 | BFF 计算 | 由 `startTime/endTime` 截取 `HH:mm-HH:mm`；时间不完整为 `null` |
| `currentLocation` | 当前任务位置 | 管理端 + BFF 组装 | 优先计划 `currentLocation`；没有时取回放 `trackGroups[].samples` 最后一个 `pointName` |
| `equipmentList` | 执行装备列表 | 管理端 + BFF 组装 | 见 3.6.1 |
| `mapId` | 地图 ID | 管理端 | `TaskWorkflowDefinitionResponse.mapId` |
| `mapPoints` | 地图点位集合 | 管理端 | `/api/v1/management/maps/{mapId}/points` 返回的点位数组 |
| `pathPoints` | 路径点位集合 | 管理端 + BFF 过滤 | 用 `TaskWorkflowDefinitionResponse.pathId` 查 `/paths/{pathId}/points`，再按 `mapPointId` 从 `mapPoints` 过滤 |

#### 3.6.1 `tasks[].equipmentList[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `robotId` | 执行装备/机器人 ID | 管理端 | 优先 `DeviceTaskInstanceResponse.serialNumber/deviceId/id`，其次 `deviceSummaries[].serialNumber/deviceId/id`，最后 `roleBindings[].deviceIds[]` |
| `name` | 装备名称 | 管理端 | `DeviceTaskInstanceResponse.deviceName`，其次 `deviceSummaries[].deviceName/name`；`roleBindings` 兜底时为 `null` |
| `type` | 装备类型中文名 | 管理端 + BFF 转换 | `deviceType/type` 转中文；字段缺失时为 `null` |
| `status` | 装备任务状态 | 管理端 + BFF 转换 | `DeviceTaskInstanceResponse.status` 或 `deviceSummaries[].status` 转换；缺失时为 `null` |

### 3.7 `devices[].task[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `taskId` | 当前任务实例 ID | 控制端 | `status.task.taskInstanceId` |
| `name` | 当前任务名称 | 控制端 + 管理端 | 优先 `status.task.taskName/workflowName`；没有时按 `taskInstanceId` 查任务实例 `workflowName/planName/name` |
| `status` | 当前任务状态 | 控制端 + 管理端 + BFF 转换 | 优先 `status.task.taskStatus/status`；没有时用任务实例 `status` |
| `timeRange` | 当前任务时间段 | 管理端 + BFF 计算 | 任务实例 `startedAt/completedAt` 计算；缺失为 `null` |

### 3.8 `alarms`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `total` | 告警总数 | BFF 计算 | 告警列表 `items.size()` |
| `summary.totalToday` | 今日/当前告警数 | BFF 计算 | 当前实现等于告警列表总数 |
| `summary.handled` | 已处理告警数 | BFF 计算 | 统计状态为 `handled/false_alarm/acknowledged` 的告警 |
| `summary.unhandled` | 未处理告警数 | BFF 计算 | `total - handled` |
| `summary.handleRate` | 处置率数字 | BFF 计算 | `handled / total * 100` |
| `summary.handleRateText` | 处置率文案 | BFF 计算 | `handleRate + "%"` |
| `high.items` | 高风险告警集合 | BFF 分组 | `items[].level == HIGH` |
| `medium.items` | 中风险告警集合 | BFF 分组 | `items[].level == MEDIUM` |
| `low.items` | 低风险告警集合 | BFF 分组 | `items[].level == LOW` |

### 3.9 `alarms.*.items[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `alarmId` | 告警 ID | 管理端 | `AlarmRecordResponse.id`，兼容 `alarmId/alarmCode` |
| `title` | 告警标题 | 管理端 | `AlarmRecordResponse.title`，兼容 `alarmName` |
| `categoryName` | 告警类型中文名 | 管理端 + BFF 转换 | `AlarmRecordResponse.alarmType`，兼容 `category` |
| `level` | 告警等级编码 | 管理端 + BFF 转换 | `AlarmRecordResponse.severity`，兼容 `level` |
| `levelName` | 告警等级中文名 | BFF 转换 | 由 `level` 转 `高风险/中风险/低风险` |
| `eventTime` | 告警发生时间 | 管理端 + BFF 格式化 | `AlarmRecordResponse.occurredAt`，兼容 `eventTime/createdAt` |
| `location` | 告警位置对象 | 管理端可选 + BFF 解析 | 优先 `source.location`；没有时尝试解析 `rawPayload.location`；仍无则 `null` |
| `location.lng` | 经度 | 管理端可选 | `location.lng/longitude` |
| `location.lat` | 纬度 | 管理端可选 | `location.lat/latitude` |
| `location.altitude` | 高度 | 管理端可选 | `location.altitude` |
| `location.x` | 坐标 X | 管理端可选 | `location.x/coordinateX` |
| `location.y` | 坐标 Y | 管理端可选 | `location.y/coordinateY` |
| `location.z` | 坐标 Z | 管理端可选 | `location.z/coordinateZ` |
| `location.address` | 位置文字 | 管理端可选 | `location.address` |
| `location.updatedAt` | 位置更新时间 | 管理端可选 + BFF 格式化 | `location.updatedAt/reportedAt/receivedAt` |
| `robotId` | 告警关联机器人 ID | 管理端 | `AlarmRecordResponse.serialNumber`，兼容 `robotId/deviceCode` |
| `deviceName` | 告警关联设备名称 | 管理端 | `AlarmRecordResponse.deviceName` |
| `taskId` | 告警关联任务实例 ID | 管理端 | `AlarmRecordResponse.taskInstanceId`，兼容 `taskId` |
| `taskName` | 告警所属任务名称 | 管理端 | 优先告警 `taskName/workflowName`；没有时按 `taskInstanceId` 查任务实例 `workflowName/planName/name` |
| `status` | 告警状态编码 | 管理端 + BFF 转换 | `AlarmRecordResponse.status` 标准化 |
| `snapshotUrl.visible` | 可见光/默认告警截图 | 管理端 + BFF 组装 | 优先 `snapshotUrl.visible` 或 `rawPayload.snapshotUrl.visible`；没有时用 `imageUrl`；再没有用 `/api/media/files/{imageFileId}/content` |
| `snapshotUrl.thermal` | 热成像截图 | 管理端可选 | `snapshotUrl.thermal` 或 `rawPayload.snapshotUrl.thermal`；没有为 `null` |
| `snapshotUrl.front` | 前置/其他截图 | 管理端可选 | `snapshotUrl.front` 或 `rawPayload.snapshotUrl.front`；没有为 `null` |

## 4. 设备详情接口

接口：

```text
GET /api/bigscreen/panorama/devices/{deviceId}
```

设备详情不是独立调用管理端详情再重组，而是从当前 `devices[]` 中按 `robotId` 过滤并补充详情展示字段。

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `robotId/clientId/name/type/typeCode/vendor/model/status/battery/lastHeartbeatAt/cameras/controlMode/speed/location/mountedDeviceCount/mountedDevices` | 设备基础与实时字段 | 同 `devices[]` | 见 3.2 到 3.5 |
| `stateSeq` | 实时状态序号 | 控制端 | 同 `devices[].stateSeq` |
| `alarmStatus` | 告警状态/等级 | BFF 派生 | 使用 `devices[].alarmLevel` |
| `alarmText` | 告警提示文案 | BFF 生成 | 有 `alarmLevel` 时为 `存在未处理告警`，否则 `null` |
| `currentTask` | 当前任务数组 | BFF 复制 | 复制 `devices[].task[]` |
| `actions.remoteControl` | 远程控制按钮是否可用 | BFF 计算 | 设备在线为 `true`，离线为 `false`，未知为 `null` |
| `actions.slamMap` | SLAM 地图按钮是否可用 | BFF 计算 | 同在线状态 |
| `actions.returnHome` | 一键返航按钮是否可用 | BFF 计算 | 同在线状态 |
| `actions.returnChargingPile` | 退出充电桩按钮是否可用 | BFF 计算 | 同在线状态 |
| `actions.showPath` | 显示路径按钮是否可用 | BFF 生成 | 设备状态已知时为 `true`，未知为 `null` |
| `actions.showArea` | 显示区域按钮是否可用 | BFF 生成 | 设备状态已知时为 `true`，未知为 `null` |

## 5. 任务列表接口

接口：

```text
GET /api/bigscreen/panorama/tasks
```

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `serverTime` | BFF 当前服务时间 | BFF 生成 | 当前时间 |
| `total` | 任务数量 | BFF 计算 | `items.size()` |
| `items` | 任务数组 | 管理端 + BFF 组装 | 同 3.6 `tasks[]` |

## 6. 告警列表接口

接口：

```text
GET /api/bigscreen/panorama/alarms
```

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `serverTime` | BFF 当前服务时间 | BFF 生成 | 当前时间 |
| `alarms` | 告警聚合对象 | 管理端 + BFF 组装 | 同 3.8 到 3.9 |

## 7. 告警处置接口

接口：

```text
POST /api/bigscreen/panorama/alarms/{alarmId}/disposal
```

请求：

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `alarmId` | 告警 ID | 前端路径参数 | 转发为管理端路径 `{id}` |
| `disposalStatus` | 处置状态 | 前端请求体 | 仅支持 `IMMEDIATE_DISPOSAL`、`FALSE_ALARM` |

管理端调用：

```text
PATCH /api/v1/management/alarms/{alarmId}/handled
```

请求体：

```json
{
  "handledBy": "bigscreen",
  "handleResult": "IMMEDIATE_DISPOSAL 或 FALSE_ALARM"
}
```

响应：

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `success` | 是否处置成功 | BFF 生成 | 管理端调用无异常为 `true`，异常为 `false` |
| `serverTime` | BFF 当前服务时间 | BFF 生成 | 当前时间 |
| `alarmId` | 告警 ID | 前端路径参数 | 原样返回 |
| `disposalStatus` | 处置状态编码 | BFF 生成 | 原请求状态 |
| `disposalStatusName` | 处置状态中文名 | BFF 转换 | `立即处置/误报` |
| `status` | 告警处置后状态 | BFF 转换 | 成功时 `IMMEDIATE_DISPOSAL -> handled`，`FALSE_ALARM -> false_alarm`；失败为 `null` |
| `message` | 处置结果文案 | BFF 生成 | 成功/失败文案 |

## 8. 地图字段

`overview.map[]` 直接返回管理端 `/api/v1/management/maps` 的 `records`，BFF 当前不改字段名。

| 管理端字段 | 字段说明 |
|---|---|
| `id` | 地图 ID |
| `mapCode` | 地图编码 |
| `mapName` | 地图名称 |
| `mapType` | 地图类型 |
| `regionId` | 区域 ID |
| `fileId` | 地图文件 ID |
| `fileUri` | 地图文件 URI |
| `fileName` | 地图文件名 |
| `fileChecksum` | 文件校验值 |
| `previewFileId` | 预览图文件 ID |
| `previewImageUri` | 预览图 URI |
| `previewWidth` | 预览图宽度 |
| `previewHeight` | 预览图高度 |
| `resolution` | 地图分辨率 |
| `originX` | 地图原点 X |
| `originY` | 地图原点 Y |
| `originYaw` | 地图原点朝向 |
| `previewGeneratedAt` | 预览图生成时间 |
| `enabled` | 是否启用 |
| `remark` | 备注 |

## 9. 统计接口

接口：

```text
GET /api/bigscreen/statistics/overview
```

当前统计接口没有对接管理端统计数据。

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `serverTime` | BFF 当前服务时间 | BFF 生成 | 当前时间 |
| `range.type` | 统计范围类型 | BFF 生成/回显 | 查询参数 `range`，默认 `month` |
| `range.startTime` | 统计开始时间 | BFF 生成/回显 | `range=custom` 时用查询参数；其他范围 BFF 自动计算 |
| `range.endTime` | 统计结束时间 | BFF 生成/回显 | 同上 |
| `filters.deviceType` | 设备类型筛选 | BFF 回显 | 查询参数 `deviceType`，默认 `all` |
| `filters.areaId` | 区域筛选 | BFF 回显 | 查询参数 `areaId` |
| `kpis.taskTotal.value` | 任务执行总数 | 未对接 | 当前 `null` |
| `kpis.taskTotal.compareRate` | 任务总数环比 | 未对接 | 当前 `null` |
| `kpis.patrolMileage.value` | 巡逻总里程 | 未对接 | 当前 `null` |
| `kpis.patrolMileage.compareRate` | 巡逻里程环比 | 未对接 | 当前 `null` |
| `kpis.aiAlarmTotal.value` | AI 告警总数 | 未对接 | 当前 `null` |
| `kpis.aiAlarmTotal.compareRate` | AI 告警环比 | 未对接 | 当前 `null` |
| `kpis.autoHandleSuccessRate.value` | 自动处置成功率 | 未对接 | 当前 `null` |
| `kpis.autoHandleSuccessRate.compareRate` | 自动处置成功率环比 | 未对接 | 当前 `null` |
| `equipmentRuntime.onlineRate` | 装备在线率 | 未对接 | 当前 `null` |
| `equipmentRuntime.taskCompletionRate` | 装备任务完成率 | 未对接 | 当前 `null` |
| `equipmentRuntime.unit` | 装备运行统计单位 | 未对接 | 当前 `null` |
| `equipmentRuntime.items` | 装备运行时长明细 | 未对接 | 当前 `[]` |
| `aiAlarmAnalysis.alarmTypeRanking` | AI 告警类型排行 | 未对接 | 当前 `[]` |
| `aiAlarmAnalysis.handleMethodRanking` | 告警处理方式排行 | 未对接 | 当前 `[]` |
| `alarmAreaRanking` | 告警高发区域排行 | 未对接 | 当前 `[]` |
| `alarmTrend.unit` | 告警趋势单位 | 未对接 | 当前 `null` |
| `alarmTrend.points` | 告警趋势点 | 未对接 | 当前 `[]` |
| `taskCompletion.items` | 任务完成情况明细 | 未对接 | 当前 `[]` |
| `taskCompletion.insight` | 任务完成统计结论 | 未对接 | 当前 `null` |

## 10. 统计报告接口

接口：

```text
POST   /api/bigscreen/statistics/reports/export
GET    /api/bigscreen/statistics/reports
GET    /api/bigscreen/statistics/reports/{id}/download
DELETE /api/bigscreen/statistics/reports/{id}
```

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `reports[].id` | 报告 ID | 本地存储 | BFF 自增 ID |
| `reports[].reportName` | 报告名称 | BFF 生成 | 根据时间范围和设备类型生成 |
| `reports[].filename` | 文件名 | BFF 生成 | 报告名称 + 时间戳 |
| `reports[].downloadTime` | 生成/下载时间 | BFF 生成 | 当前时间 |
| `reports[].format` | 报告格式 | BFF 生成 | 固定 `PDF` |
| `reports[].status` | 报告状态 | BFF 生成 | 固定 `COMPLETED` |
| `reports[].filePath` | 本地文件路径 | 本地存储 | `data/statistics-reports/{id}.pdf` |
| `reports[].statusName` | 报告状态中文名 | BFF 生成 | `已完成` |

说明：统计报告当前由 BFF 本地生成和保存，不对接管理端报告接口。

## 11. 当前未对接字段汇总

| 字段/模块 | 当前状态 | 建议来源 |
|---|---|---|
| `patrolOverview.mileageToday/mileageUnit` | `null` | 管理端任务实例统计或轨迹里程统计接口 |
| `devices[].cameras[]` 权威相机/视频流字段 | BFF 本地推断 | 管理端设备组件扩展相机配置，或媒体服务相机/流接口 |
| `devices[].mountedDevices[].status` 独立上装状态 | 复用机器人状态 | 管理端组件状态或控制端组件状态 |
| `devices[].mapDisplay.icon` | `null` | 设备类型图标配置或前端本地配置 |
| `alarms.*.items[].location` | 仅在告警对象或 `rawPayload` 提供时有值 | 管理端告警记录标准化结构化位置字段 |
| `alarms.*.items[].snapshotUrl.thermal/front` | 仅在告警对象或 `rawPayload` 提供时有值 | 管理端告警多路截图字段 |
| `/api/bigscreen/statistics/overview` 业务统计字段 | 大部分 `null/[]` | 管理端统计聚合接口，或 BFF 基于任务/告警/设备历史数据聚合 |
| 统计报告 | BFF 本地生成 | 如需统一管理，后续对接管理端报告中心 |

