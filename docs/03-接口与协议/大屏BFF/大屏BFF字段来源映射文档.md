# 大屏 BFF 字段来源映射文档

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
| `deviceStats.fault` | 故障设备数 | BFF 计算 | 统计 `devices[].fault == true`；没有明确故障上报时为 `0` |
| `deviceStats.offline` | 离线设备数 | BFF 计算 | 统计 `devices[].status == offline` |
| `deviceTypeStats[]` | 按机器人整机类型统计 | BFF 计算 | 按 `devices[].typeCode` 分组，`type/name` 与 `devices[].typeCode/type` 保持一致，并计算 `count/fault/offline`；整机类型为空的设备不参与分类统计，固定摄像头按 `FIXED_CAMERA/固定摄像头` 统计 |
| `patrolOverview.durationToday` | 今日巡逻时长，单位小时 | BFF 计算 | 今日任务实例 `durationSeconds`；没有时用 `startedAt/completedAt` 计算 |
| `patrolOverview.durationUnit` | 巡逻时长单位 | BFF 生成 | `durationToday` 有值时为 `小时` |
| `patrolOverview.mileageToday` | 今日巡逻里程 | 控制端 | Control 持久化今日有效里程增量，BFF 由米换算为 KM |
| `patrolOverview.mileageUnit` | 巡逻里程单位 | BFF | 有里程采集基线时返回 `KM`，否则返回 `null` |
| `tasks` | 任务列表 | 管理端 + BFF 组装 | 见 3.6 |
| `taskOverview.totalToday` | 今日任务数/当前任务列表总数 | BFF 计算 | `tasks.size()` |
| `taskOverview.completedRateText` | 完成率文案 | BFF 计算 | `completedRate + "%"` |
| `taskOverview.running` | 执行中任务数 | BFF 计算 | 统计 `tasks[].status == running` |
| `taskOverview.pending` | 待执行任务数 | BFF 计算 | 统计 `tasks[].status == pending` |
| `map` | 地图列表 | 管理端 + BFF 组装 | 返回 `/api/v1/management/maps` 的 `data.records`，并为每张地图补充点位集合 |
| `alarms` | 告警聚合对象 | 管理端 + BFF 组装 | 见 3.8 |

### 3.2 `devices[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `robotId` | 机器人唯一展示 ID | 管理端 | `DeviceResponse.serialNumber` |
| `name` | 设备名称 | 管理端 | `DeviceResponse.deviceName`，兼容 `name` |
| `type` | 机器人整机类型名称 | 管理端字典 + BFF 关联 | 按 `typeCode` 匹配 `/api/v1/management/selection-options/dictionaries/device_type` 的 `value/label`；未匹配时返回编码本身；固定摄像头由 BFF 固定返回“固定摄像头” |
| `typeCode` | 机器人整机类型编码 | 管理端 | `DeviceResponse.deviceType`，编码范围以管理端 `device_type` 字典为准；固定摄像头由 BFF 固定返回 `FIXED_CAMERA` |
| `model` | 型号 | 管理端 | `DeviceResponse.model` |
| `status` | 在线状态 | 控制端 | `DeviceRealtimeStatus.onlineStatus` 转为 `online/offline/fault` |
| `battery` | 电量百分比 | 控制端 | `status.energy.batteryPercent` |
| `cameras` | 相机展示集合 | 控制端 + BFF 兜底拼装 | 见 3.4；优先与 `robot.state.cameras` 使用同一份控制端实时相机清单 |
| `stateSeq` | 实时状态序号 | 控制端 | `DeviceRealtimeStatus.stateSeq`；无则 `null` |
| `fault` | 是否故障 | 控制端 + BFF 计算 | `status.basic.healthStatus` 明确为 `ERROR`、`FAULT`、`异常`或`故障`时为 `true`，`NORMAL` 为 `false`，缺失或未知值为 `null` |
| `alarmLevel` | 设备告警等级 | 控制端 + BFF 转换 | `status.basic.alarmStatus` 转 `HIGH/MEDIUM/LOW`；正常为空 |
| `controlMode` | 控制模式 | 控制端 | `status.control.controlMode` |
| `mountedDeviceCount` | 上装设备数量 | BFF 计算 | `DeviceDetailResponse.components.size()`；未返回组件时为 `null` |
| `speed` | 当前速度 | 控制端 | `status.motion.speed` |
| `location` | 设备定位信息 | 控制端 | 见 3.5 |
| `task` | 当前任务数组 | 控制端 + 管理端 + BFF 组装 | 见 3.7 |

`clientId/vendor/lastHeartbeatAt/mountedDevices/mapDisplay` 不再放入聚合接口的
`devices[]`。这些字段仍可由设备详情等独立接口按原有契约返回。

### 3.3 设备详情中的 `mountedDevices[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `deviceId` | 上装设备/组件 ID | 管理端 | `DeviceComponentResponse.code/deviceId/id` |
| `name` | 上装设备名称 | 管理端 | `DeviceComponentResponse.name`，兼容 `componentName` |
| `type` | 上装能力/类型 | 管理端 | 取 `DeviceComponentCapabilityResponse.code/capabilityCode` 的第一个值 |
| `status` | 上装设备状态 | BFF 派生 | 当前复用机器人 `status`，未对接上装设备独立状态 |

### 3.4 `devices[].cameras[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `cameraId` | 相机 ID | 控制端优先 | `/api/control/robots/registry` 对应机器人的 `cameras[].cameraId`；无实时清单时根据管理端组件兜底拼装 |
| `deviceId` | 关联设备/组件 ID | 控制端优先 | `/api/control/robots/registry` 对应机器人的 `cameras[].deviceId`；无实时清单时使用管理端组件 ID |
| `groupType` | 相机分组 | 控制端优先 | `/api/control/robots/registry` 对应机器人的 `cameras[].groupType`；无实时清单时由组件类型推断 |
| `name` | 相机名称 | 控制端优先 | `/api/control/robots/registry` 对应机器人的 `cameras[].name`；无实时清单时由组件类型生成 |
| `quality` | 默认清晰度 | 控制端优先 | `/api/control/robots/registry` 对应机器人的 `cameras[].quality`；缺失时默认为 `sub` |

说明：Control 服务的机器人注册表与 `robot.state` 使用同一份媒体客户端相机状态，
因此同一机器人在 `robot.state.cameras` 和聚合接口 `devices[].cameras` 中保持一致。
只有控制端尚未取得相机清单时，BFF 才根据管理端组件信息兜底拼装；该集合描述
相机选择信息，不直接包含视频流地址。

### 3.5 `devices[].location`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `lng` | 经度 | 控制端 | `status.localization.lng/longitude` |
| `lat` | 纬度 | 控制端 | `status.localization.lat/latitude` |
| `mapId` | 设备所属管理端地图业务主键，字符串；无关联任务时为 `null` | 管理端 + BFF 关联 | 按 `devices[].robotId = tasks[].equipmentList[].robotId` 匹配，取第一条非空 `tasks[].mapId`；不使用控制端 SLAM 图 ID |
| `x` | 地图/局部坐标 X | 控制端 | `status.localization.coordinateX` |
| `y` | 地图/局部坐标 Y | 控制端 | `status.localization.coordinateY` |
| `z` | 地图/局部坐标 Z | 控制端 | `status.localization.coordinateZ` |
| `address` | 位置文字 | 控制端 | `status.localization.address` |

### 3.6 `tasks[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `taskId` | 任务计划 ID | 管理端 | `TaskWorkflowPlanResponse.id`，兼容 `taskId` |
| `workflowInstanceId` | 当前任务实例 ID，用于暂停、恢复、终止任务实例 | 管理端 | `TaskWorkflowPlanResponse.activeWorkflowInstanceId/lastWorkflowInstanceId/workflowInstanceId` |
| `name` | 任务名称 | 管理端 | `TaskWorkflowPlanResponse.planName/workflowName/name` |
| `executionMode` | 执行模式 | 管理端 | 任务计划接口 `executionMode`，例如 `MANUAL`、`SCHEDULE`；缺失时为 `null` |
| `expectedDurationSeconds` | 预计执行时长，单位秒 | 管理端 | 任务计划接口 `expectedDurationSeconds`；缺失时为 `null` |
| `status` | 任务状态编码 | 管理端任务计划 + BFF 转换 | 仅取任务计划 `executionStatus`：`WAITING/RUNNING/PAUSED` 分别返回小写 `waiting/running/paused` |
| `statusName` | 任务状态中文名 | BFF 转换 | 由 `status` 转中文 |
| `startTime` | 任务开始时间 | 管理端 + BFF 格式化 | 优先任务实例 `startedAt`，其次计划 `startedAt/lastStartedAt/startTime` |
| `endTime` | 任务结束时间 | 管理端 + BFF 格式化 | 优先任务实例 `completedAt`，其次计划 `completedAt/lastCompletedAt/endTime` |
| `timeRange` | 页面展示时间段 | BFF 计算 | 由 `startTime/endTime` 截取 `HH:mm-HH:mm`；时间不完整为 `null` |
| `currentLocation` | 当前任务位置 | 管理端 + BFF 组装 | 优先计划 `currentLocation`；没有时取回放 `trackGroups[].samples` 最后一个 `pointName` |
| `equipmentList` | 执行装备列表 | 管理端 + BFF 组装 | 见 3.6.1 |
| `mapId` | 地图 ID | 管理端 | `TaskWorkflowDefinitionResponse.mapId` |
| `pathPoints` | 路径点位集合 | 管理端 + BFF 过滤 | 用 `TaskWorkflowDefinitionResponse.pathId` 查 `/paths/{pathId}/points`，再按 `mapPointId` 从地图点位中过滤；每个点仅返回 `id/pointCode/pointName/pointType/coordinateX/coordinateY` |

#### 3.6.1 `tasks[].equipmentList[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `robotId` | 执行装备/机器人 ID | 管理端 | 优先 `DeviceTaskInstanceResponse.serialNumber/deviceId/id`，其次 `deviceSummaries[].serialNumber/deviceId/id`，最后 `roleBindings[].deviceIds[]` |
| `name` | 装备名称 | 管理端 | `DeviceTaskInstanceResponse.deviceName`，其次 `deviceSummaries[].deviceName/name`；`roleBindings` 兜底时为 `null` |
| `type` | 装备类型中文名 | 管理端 + BFF 转换 | `deviceType/type` 转中文；字段缺失时为 `null` |
| `status` | 装备在线状态 | 管理端 + BFF 关联 | 按 `robotId` 关联 `devices[].status`，仅返回 `online`、`offline`、`fault`；设备未匹配或状态未知时为 `null` |

### 3.7 `devices[].task[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `taskId` | 任务计划 ID | 管理端 | 按 `tasks[].equipmentList[].robotId` 关联活务后，复制 `tasks[].taskId` |
| `workflowInstanceId` | 当前执行实例 ID | 控制端 + 管理端 | 实时状态 `status.task.taskInstanceId`，并与 `tasks[].workflowInstanceId` 关联；每次执行都可能变化 |
| `name` | 当前任务名称 | 控制端 + 管理端 | 优先 `status.task.taskName/workflowName`；缺失时用已查询的 `tasks[].name` 补齐 |
| `status` | 当前任务状态 | 控制端 + 管理端 + BFF 转换 | 优先 `status.task.taskStatus/status`；缺失时用已查询的 `tasks[].status` 补齐 |
| `timeRange` | 当前任务时间段 | 管理端 + BFF 计算 | 任务实例 `startedAt/completedAt` 计算；缺失时用已查询的 `tasks[].timeRange` 补齐 |

BFF 只会将 `running/pausing/paused/resuming/terminating` 状态的任务关联到设备，不会把待执行或已结束任务填入“当前任务”。关联复用总览已查询的 `tasks[]`，不会为每台设备新增管理端请求。

### 3.8 `map[]`

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `points` | 地图点位列表 | 管理端 | `/api/v1/management/maps/{mapId}/points` 返回值 |
| `deviceIds` | 当前地图的设备 ID 列表 | BFF 过滤 | 按 `devices[].location.mapId` 与地图 `id/mapId` 匹配，只返回 `robotId`；完整对象使用顶层 `devices[]` |
| `fixedCamares` | 当前地图的固定摄像头列表 | 管理端 | `/api/v1/management/fixed-cameras?pageNum=1&pageSize=100&mapId={mapId}` 的 `data.records` |

地图对象保留 `id/mapName/fileId/previewWidth/previewHeight/resolution/originX/originY/originYaw/previewGeneratedAt/points/deviceIds/fixedCamares`。
`mapCode/mapType/regionId/fileName/previewImageUrl/enabled/remark` 不再放入聚合接口。
`points[]` 仅返回 `id/pointCode/pointName/pointType/coordinateX/coordinateY`。

### 3.9 `alarms`

告警采用双路查询：`summary` 统计今日告警（`occurredFrom/occurredTo` 传当日 0 点至当前时刻，不按状态过滤）；分组列表查询未处理告警（`status=NEW`，不限制时间窗口）。

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `total` | 未处理告警总数 | BFF 计算 | 未处理告警列表 `items.size()` |
| `summary.totalToday` | 今日告警数 | 管理端 + BFF 计算 | 今日窗口（当日 0 点起）内告警总数，依赖管理端 `occurredFrom/occurredTo` 过滤 |
| `summary.handled` | 今日已处理告警数 | BFF 计算 | 今日窗口内统计状态为 `handled/false_alarm` 的告警 |
| `summary.unhandled` | 今日未处理告警数 | BFF 计算 | 今日窗口内 `totalToday - handled` |
| `summary.handleRateText` | 处置率文案 | BFF 计算 | `handleRate + "%"` |
| `high.items` | 高风险未处理告警集合 | 管理端 + BFF 分组 | 管理端 `status=NEW` 过滤后，`items[].level == HIGH` |
| `medium.items` | 中风险未处理告警集合 | 管理端 + BFF 分组 | 管理端 `status=NEW` 过滤后，`items[].level == MEDIUM` |
| `low.items` | 低风险未处理告警集合 | 管理端 + BFF 分组 | 管理端 `status=NEW` 过滤后，`items[].level == LOW` |

### 3.10 `alarms.*.items[]`

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
| `location.x` | 坐标 X | 管理端可选 | `location.x/coordinateX` |
| `location.y` | 坐标 Y | 管理端可选 | `location.y/coordinateY` |
| `location.z` | 坐标 Z | 管理端可选 | `location.z/coordinateZ` |
| `location.address` | 位置文字 | 管理端可选 | `location.address` |
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
| `robotId/clientId/name/type/typeCode/vendor/model/status/battery/lastHeartbeatAt/cameras/controlMode/speed/location/mountedDeviceCount/mountedDevices` | 设备基础与实时字段 | 管理端 + 控制端 + BFF 组装 | 设备详情继续返回完整字段；其中 `clientId/vendor/lastHeartbeatAt/mountedDevices` 不受聚合接口精简影响 |
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
| `items` | 任务数组 | 管理端 + BFF 组装 | 字段来源同 3.6；独立任务接口仍额外返回完整 `mapPoints[]` |

## 6. 告警列表接口

接口：

```text
GET /api/bigscreen/panorama/alarms
```

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `serverTime` | BFF 当前服务时间 | BFF 生成 | 当前时间 |
| `alarms` | 告警聚合对象 | 管理端 + BFF 组装 | 字段来源同 3.8 到 3.9；独立告警接口仍返回 `summary.handleRate` 以及位置的 `altitude/updatedAt` |

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

`overview.map[]` 从管理端地图记录中投影前端需要的字段，并按每条记录的
`id/mapId` 查询地图点位、匹配设备，补充 `points/deviceIds/fixedCamares`。

| BFF 字段 | 字段说明 |
|---|---|
| `id` | 地图 ID |
| `mapName` | 地图名称 |
| `fileId` | 地图文件 ID |
| `previewWidth` | 预览图宽度 |
| `previewHeight` | 预览图高度 |
| `resolution` | 地图分辨率 |
| `originX` | 地图原点 X |
| `originY` | 地图原点 Y |
| `originYaw` | 地图原点朝向 |
| `previewGeneratedAt` | 预览图生成时间 |
| `points` | 地图点位数组，仅含 `id/pointCode/pointName/pointType/coordinateX/coordinateY` |
| `deviceIds` | 当前地图设备 ID 数组；按 `devices[].location.mapId` 匹配 |
| `fixedCamares` | 当前地图固定摄像头原始记录数组 |

## 9. 统计接口

接口：

```text
GET /api/bigscreen/statistics/overview
```

当前统计接口由 BFF 基于管理端设备、实时状态、任务实例和告警明细聚合计算。

| BFF 字段 | 字段说明 | 来源类型 | 对接字段/处理逻辑 |
|---|---|---|---|
| `serverTime` | BFF 当前服务时间 | BFF 生成 | 当前时间 |
| `range.type` | 统计范围类型 | BFF 生成/回显 | 查询参数 `range`，默认 `month` |
| `range.startTime` | 统计开始时间 | BFF 生成/回显 | `range=custom` 时用查询参数；其他范围 BFF 自动计算 |
| `range.endTime` | 统计结束时间 | BFF 生成/回显 | 同上 |
| `filters.deviceType` | 设备类型筛选 | BFF 回显 | 查询参数 `deviceType`，默认 `all` |
| `filters.areaId` | 区域筛选 | BFF 回显 | 查询参数 `areaId` |
| `kpis.taskTotal.value` | 任务执行总数 | BFF 计算 | 统计时间内任务实例数 |
| `kpis.taskTotal.compareRate` | 任务总数环比 | 未对接 | 当前 `null` |
| `kpis.patrolMileage.value` | 巡逻总里程 | 控制端 | 所选时间范围及设备的持久化里程增量，单位 KM |
| `kpis.patrolMileage.compareRate` | 巡逻里程环比 | BFF | 与紧邻的等长上一周期比较；无有效基数时返回 `null` |
| `kpis.aiAlarmTotal.value` | AI 告警总数 | BFF 计算 | 统计时间内管理端告警明细数 |
| `kpis.aiAlarmTotal.compareRate` | AI 告警环比 | 未对接 | 当前 `null` |
| `kpis.autoHandleSuccessRate.value` | 自动处置成功率 | 未对接 | 当前 `null` |
| `kpis.autoHandleSuccessRate.compareRate` | 自动处置成功率环比 | 未对接 | 当前 `null` |
| `equipmentRuntime.onlineRate` | 装备在线率 | BFF 计算 | 当前在线设备数 / 管理端设备数 |
| `equipmentRuntime.taskCompletionRate` | 装备任务完成率 | BFF 计算 | 已完成任务数 / 任务总数 |
| `equipmentRuntime.unit` | 装备运行统计单位 | BFF 生成 | 有任务时长时为“小时” |
| `equipmentRuntime.items` | 装备运行时长明细 | BFF 计算 | 按设备类型累计任务实例时长；故障/离线时长仍为 `null` |
| `aiAlarmAnalysis.alarmTypeRanking` | AI 告警类型排行 | BFF 计算 | 按 `alarmType` 分组 |
| `aiAlarmAnalysis.handleMethodRanking` | 告警处理方式排行 | BFF 计算 | 按 `handleResult` 分组 |
| `alarmAreaRanking` | 告警高发区域排行 | BFF 计算 | 按告警位置名称分组，`areaId` 仍为 `null` |
| `alarmTrend.unit` | 告警趋势单位 | BFF 生成 | 当前为“次” |
| `alarmTrend.points` | 告警趋势点 | BFF 计算 | 按时间范围分时/日/月聚合 |
| `taskCompletion.items` | 任务完成情况明细 | BFF 计算 | 按已完成/执行中/待执行/异常中断归类 |
| `taskCompletion.insight` | 任务完成统计结论 | BFF 生成 | 任务总数、完成数和完成率文案 |

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
| `devices[].cameras[]` 权威相机/视频流字段 | BFF 本地推断 | 管理端设备组件扩展相机配置，或媒体服务相机/流接口 |
| `devices[].mountedDevices[].status` 独立上装状态 | 复用机器人状态 | 管理端组件状态或控制端组件状态 |
| `devices[].mapDisplay.icon` | `null` | 设备类型图标配置或前端本地配置 |
| `alarms.*.items[].location` | 仅在告警对象或 `rawPayload` 提供时有值 | 管理端告警记录标准化结构化位置字段 |
| `alarms.*.items[].snapshotUrl.thermal/front` | 仅在告警对象或 `rawPayload` 提供时有值 | 管理端告警多路截图字段 |
| `/api/bigscreen/statistics/overview` 业务统计字段 | 大部分 `null/[]` | 管理端统计聚合接口，或 BFF 基于任务/告警/设备历史数据聚合 |
| 统计报告 | BFF 本地生成 | 如需统一管理，后续对接管理端报告中心 |
