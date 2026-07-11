# 大屏 BFF 字段管理端供数清单

更新时间：2026-07-09

## 1. 说明

本文只列出大屏 BFF 当前字段中需要管理端提供数据来源的字段。字段名均为 BFF 对前端返回或接收的字段名，不列管理端接口路径，不约束管理端接口如何设计。

未出现在本文中的 BFF 字段，默认由 BFF 生成、BFF 计算、前端传入、本地存储或控制端实时数据提供。

提供标记：

| 标记 | 说明 |
|---|---|
| 需要 | 该 BFF 字段需要管理端提供业务数据来源 |
| 条件需要 | 页面要展示该数据时，需要管理端提供；否则 BFF 可返回 `null` 或 `[]` |
| 支持 | 管理端需要支持该动作或状态变更，不一定是查询字段 |

## 2. `/api/bigscreen/panorama/overview`

### 2.1 顶层字段

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `devices` | array<object> | 需要 | 全部机器人/设备列表，设备基础信息来自管理端 |
| `patrolOverview.durationToday` | number/null | 需要 | 今日巡逻时长，BFF 可基于管理端任务实例耗时计算 |
| `patrolOverview.mileageToday` | number/null | 条件需要 | 今日巡逻里程；当前无真实来源时返回 `null` |
| `tasks` | array<object> | 需要 | 任务列表，来自管理端任务计划、任务实例、路线点位等数据 |
| `map` | array<object> | 需要 | 地图列表，当前 BFF 透传管理端地图记录 |
| `alarms` | object | 需要 | 告警聚合对象，告警明细来自管理端 |

### 2.2 `devices[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `devices[].robotId` | string/number | 需要 | 机器人唯一展示 ID |
| `devices[].clientId` | string/null | 条件需要 | 机器人客户端 ID |
| `devices[].name` | string/null | 需要 | 设备名称 |
| `devices[].type` | string/null | 需要 | 设备类型展示名，BFF 可根据管理端设备类型编码转换 |
| `devices[].typeCode` | string/null | 需要 | 设备类型编码 |
| `devices[].vendor` | string/null | 条件需要 | 设备厂商 |
| `devices[].model` | string/null | 条件需要 | 设备型号 |
| `devices[].cameras` | array<object> | 条件需要 | 相机配置；需要真实视频流配置时由管理端或后续权威数据源提供 |
| `devices[].mountedDevices` | array<object> | 条件需要 | 上装设备/组件集合 |
| `devices[].mountedDeviceCount` | number/null | 条件需要 | 上装设备数量，BFF 可根据管理端组件集合计算 |
| `devices[].task` | array<object> | 条件需要 | 当前任务详情；任务 ID 可来自控制端，任务名称/状态/时间可由管理端补充 |

### 2.3 `devices[].mountedDevices[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `devices[].mountedDevices[].deviceId` | string/number | 需要 | 上装设备/组件 ID |
| `devices[].mountedDevices[].name` | string/null | 需要 | 上装设备/组件名称 |
| `devices[].mountedDevices[].type` | string/null | 条件需要 | 上装设备/组件类型或能力类型 |
| `devices[].mountedDevices[].status` | string/null | 条件需要 | 上装设备独立状态；如管理端不提供，BFF 当前只能复用机器人状态 |

### 2.4 `devices[].cameras[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `devices[].cameras[].cameraId` | string | 条件需要 | 相机 ID |
| `devices[].cameras[].deviceId` | string/number | 条件需要 | 相机所属设备或组件 ID |
| `devices[].cameras[].groupType` | string | 条件需要 | 相机分组，如本体相机、云台相机 |
| `devices[].cameras[].name` | string | 条件需要 | 相机名称 |
| `devices[].cameras[].quality` | string | 条件需要 | 默认播放清晰度 |
| `devices[].cameras[].streamCode` | string/null | 条件需要 | 视频流标识；需要精准播放视频流时提供 |

### 2.5 `devices[].task[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `devices[].task[].name` | string/null | 条件需要 | 当前任务名称 |
| `devices[].task[].status` | string/null | 条件需要 | 当前任务状态 |
| `devices[].task[].timeRange` | string/null | 条件需要 | 当前任务时间段，BFF 可根据管理端任务开始/结束时间计算 |

### 2.6 `tasks[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `tasks[].taskId` | string/number | 需要 | 任务 ID |
| `tasks[].name` | string/null | 需要 | 任务名称 |
| `tasks[].status` | string/null | 需要 | 任务状态 |
| `tasks[].startTime` | string/null | 条件需要 | 任务开始时间 |
| `tasks[].endTime` | string/null | 条件需要 | 任务结束时间 |
| `tasks[].currentLocation` | string/null | 条件需要 | 当前任务位置 |
| `tasks[].equipmentList` | array<object> | 条件需要 | 执行装备列表 |
| `tasks[].mapId` | string/number/null | 条件需要 | 任务绑定地图 ID |
| `tasks[].mapPoints` | array<object> | 条件需要 | 任务地图点位集合 |
| `tasks[].pathPoints` | array<object> | 条件需要 | 任务路径点位集合 |

### 2.7 `tasks[].equipmentList[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `tasks[].equipmentList[].robotId` | string/number | 条件需要 | 执行装备/机器人 ID |
| `tasks[].equipmentList[].name` | string/null | 条件需要 | 执行装备名称 |
| `tasks[].equipmentList[].type` | string/null | 条件需要 | 执行装备类型 |
| `tasks[].equipmentList[].status` | string/null | 条件需要 | 执行装备任务状态 |

### 2.8 `tasks[].mapPoints[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `tasks[].mapPoints[].id` | string/number | 条件需要 | 地图点位 ID |
| `tasks[].mapPoints[].mapId` | string/number | 条件需要 | 所属地图 ID |
| `tasks[].mapPoints[].pointCode` | string/null | 条件需要 | 点位编码 |
| `tasks[].mapPoints[].pointName` | string/null | 条件需要 | 点位名称 |
| `tasks[].mapPoints[].pointType` | string/null | 条件需要 | 点位类型 |
| `tasks[].mapPoints[].coordinateX` | number/null | 条件需要 | 点位坐标 X |
| `tasks[].mapPoints[].coordinateY` | number/null | 条件需要 | 点位坐标 Y |
| `tasks[].mapPoints[].coordinateZ` | number/null | 条件需要 | 点位坐标 Z |

### 2.9 `tasks[].pathPoints[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `tasks[].pathPoints[]` | array<object> | 条件需要 | 任务路径点位明细；BFF 最终需要返回真实地图点位对象 |
| `tasks[].pathPoints[].id` | string/number | 条件需要 | 路径点对应的地图点位 ID |
| `tasks[].pathPoints[].mapId` | string/number | 条件需要 | 路径点所属地图 ID |
| `tasks[].pathPoints[].pointCode` | string/null | 条件需要 | 路径点编码 |
| `tasks[].pathPoints[].pointName` | string/null | 条件需要 | 路径点名称 |
| `tasks[].pathPoints[].pointType` | string/null | 条件需要 | 路径点类型 |
| `tasks[].pathPoints[].coordinateX` | number/null | 条件需要 | 路径点坐标 X |
| `tasks[].pathPoints[].coordinateY` | number/null | 条件需要 | 路径点坐标 Y |
| `tasks[].pathPoints[].coordinateZ` | number/null | 条件需要 | 路径点坐标 Z |

### 2.10 `map[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `map[].id` | string/number | 需要 | 地图 ID |
| `map[].mapCode` | string/null | 条件需要 | 地图编码 |
| `map[].mapName` | string/null | 需要 | 地图名称 |
| `map[].mapType` | string/null | 条件需要 | 地图类型 |
| `map[].regionId` | string/number/null | 条件需要 | 所属区域 ID |
| `map[].fileId` | string/null | 条件需要 | 地图文件 ID |
| `map[].fileUri` | string/null | 条件需要 | 地图文件 URI |
| `map[].fileName` | string/null | 条件需要 | 地图文件名 |
| `map[].fileChecksum` | string/null | 条件需要 | 文件校验值 |
| `map[].previewFileId` | string/null | 条件需要 | 预览图文件 ID |
| `map[].previewImageUri` | string/null | 条件需要 | 预览图 URI |
| `map[].previewWidth` | number/null | 条件需要 | 预览图宽度 |
| `map[].previewHeight` | number/null | 条件需要 | 预览图高度 |
| `map[].resolution` | number/null | 条件需要 | 地图分辨率 |
| `map[].originX` | number/null | 条件需要 | 地图原点 X |
| `map[].originY` | number/null | 条件需要 | 地图原点 Y |
| `map[].originYaw` | number/null | 条件需要 | 地图原点朝向 |
| `map[].previewGeneratedAt` | string/null | 条件需要 | 预览图生成时间 |
| `map[].enabled` | boolean/null | 需要 | 地图是否启用 |
| `map[].remark` | string/null | 条件需要 | 备注 |

### 2.11 `alarms.*.items[]`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `alarms.*.items[].alarmId` | string/number | 需要 | 告警 ID |
| `alarms.*.items[].title` | string/null | 需要 | 告警标题 |
| `alarms.*.items[].categoryName` | string/null | 需要 | 告警类型展示名，BFF 可根据管理端告警类型编码转换 |
| `alarms.*.items[].level` | string/null | 需要 | 告警等级编码 |
| `alarms.*.items[].eventTime` | string/null | 需要 | 告警发生时间 |
| `alarms.*.items[].location` | object/null | 条件需要 | 告警详细位置对象 |
| `alarms.*.items[].robotId` | string/number/null | 条件需要 | 告警关联机器人 ID |
| `alarms.*.items[].deviceName` | string/null | 条件需要 | 告警关联设备名称 |
| `alarms.*.items[].taskId` | string/number/null | 条件需要 | 告警所属任务 ID |
| `alarms.*.items[].taskName` | string/null | 条件需要 | 告警所属任务名称 |
| `alarms.*.items[].status` | string/null | 需要 | 告警处置状态 |
| `alarms.*.items[].snapshotUrl` | object/null | 条件需要 | 告警截图对象 |

### 2.12 `alarms.*.items[].location`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `alarms.*.items[].location.lng` | number/null | 条件需要 | 经度 |
| `alarms.*.items[].location.lat` | number/null | 条件需要 | 纬度 |
| `alarms.*.items[].location.altitude` | number/null | 条件需要 | 海拔 |
| `alarms.*.items[].location.x` | number/null | 条件需要 | 局部坐标 X |
| `alarms.*.items[].location.y` | number/null | 条件需要 | 局部坐标 Y |
| `alarms.*.items[].location.z` | number/null | 条件需要 | 局部坐标 Z |
| `alarms.*.items[].location.address` | string/null | 条件需要 | 位置描述 |
| `alarms.*.items[].location.updatedAt` | string/null | 条件需要 | 位置更新时间 |

### 2.13 `alarms.*.items[].snapshotUrl`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `alarms.*.items[].snapshotUrl.visible` | string/null | 条件需要 | 可见光/默认截图地址 |
| `alarms.*.items[].snapshotUrl.thermal` | string/null | 条件需要 | 热成像截图地址 |
| `alarms.*.items[].snapshotUrl.front` | string/null | 条件需要 | 前置/其他截图地址 |

## 3. `/api/bigscreen/panorama/devices/{deviceId}`

设备详情接口复用 `overview.devices[]` 的数据结构。管理端需要提供的字段同本文 `2.2` 至 `2.5`。

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `currentTask` | array<object> | 条件需要 | 当前任务数组，字段同 `devices[].task[]` |

## 4. `/api/bigscreen/panorama/tasks`

任务列表接口复用 `overview.tasks[]` 的数据结构。管理端需要提供的字段同本文 `2.6` 至 `2.9`。

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `items` | array<object> | 需要 | 任务列表，字段同 `overview.tasks[]` |

## 5. `/api/bigscreen/panorama/alarms`

告警列表接口复用 `overview.alarms` 的数据结构。管理端需要提供的字段同本文 `2.11` 至 `2.13`。

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `alarms` | object | 需要 | 告警聚合对象，告警明细字段同 `overview.alarms` |

## 6. `/api/bigscreen/panorama/alarms/{alarmId}/disposal`

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `alarmId` | string/number | 支持 | 告警处置目标 ID，管理端需要支持按该 ID 修改告警处置状态 |
| `disposalStatus` | string | 支持 | 前端传入处置动作，目前取值为 `IMMEDIATE_DISPOSAL`、`FALSE_ALARM`，管理端需要支持对应处置结果 |

## 7. `/api/bigscreen/statistics/overview`

统计页当前大部分字段未真实对接。如需统计页展示真实数据，以下 BFF 字段需要管理端提供统计数据来源。

| BFF 字段 | 类型 | 管理端提供 | 字段说明 |
|---|---|---|---|
| `kpis.taskTotal.value` | number/null | 条件需要 | 任务执行总数 |
| `kpis.taskTotal.compareRate` | number/null | 条件需要 | 任务总数环比 |
| `kpis.patrolMileage.value` | number/null | 条件需要 | 巡逻总里程 |
| `kpis.patrolMileage.compareRate` | number/null | 条件需要 | 巡逻里程环比 |
| `kpis.aiAlarmTotal.value` | number/null | 条件需要 | AI 告警总数 |
| `kpis.aiAlarmTotal.compareRate` | number/null | 条件需要 | AI 告警环比 |
| `kpis.autoHandleSuccessRate.value` | number/null | 条件需要 | 自动处置成功率 |
| `kpis.autoHandleSuccessRate.compareRate` | number/null | 条件需要 | 自动处置成功率环比 |
| `equipmentRuntime.onlineRate` | number/null | 条件需要 | 装备在线率 |
| `equipmentRuntime.taskCompletionRate` | number/null | 条件需要 | 装备任务完成率 |
| `equipmentRuntime.unit` | string/null | 条件需要 | 装备运行时长单位 |
| `equipmentRuntime.items` | array<object> | 条件需要 | 装备运行时长明细 |
| `equipmentRuntime.items[].deviceType` | string/null | 条件需要 | 设备类型编码 |
| `equipmentRuntime.items[].deviceTypeName` | string/null | 条件需要 | 设备类型名称 |
| `equipmentRuntime.items[].runningHours` | number/null | 条件需要 | 运行时长 |
| `equipmentRuntime.items[].faultHours` | number/null | 条件需要 | 故障时长 |
| `equipmentRuntime.items[].offlineHours` | number/null | 条件需要 | 离线时长 |
| `aiAlarmAnalysis.alarmTypeRanking` | array<object> | 条件需要 | AI 告警类型排行 |
| `aiAlarmAnalysis.alarmTypeRanking[].name` | string/null | 条件需要 | 告警类型名称 |
| `aiAlarmAnalysis.alarmTypeRanking[].count` | number/null | 条件需要 | 告警数量 |
| `aiAlarmAnalysis.alarmTypeRanking[].percent` | number/null | 条件需要 | 占比 |
| `aiAlarmAnalysis.handleMethodRanking` | array<object> | 条件需要 | 告警处理方式排行 |
| `aiAlarmAnalysis.handleMethodRanking[].name` | string/null | 条件需要 | 处理方式名称 |
| `aiAlarmAnalysis.handleMethodRanking[].count` | number/null | 条件需要 | 数量 |
| `alarmAreaRanking` | array<object> | 条件需要 | 告警高发区域排行 |
| `alarmAreaRanking[].areaId` | string/number/null | 条件需要 | 区域 ID |
| `alarmAreaRanking[].areaName` | string/null | 条件需要 | 区域名称 |
| `alarmAreaRanking[].count` | number/null | 条件需要 | 告警数量 |
| `alarmAreaRanking[].percent` | number/null | 条件需要 | 占比 |
| `alarmTrend.unit` | string/null | 条件需要 | 告警趋势统计单位 |
| `alarmTrend.points` | array<object> | 条件需要 | 告警趋势点集合 |
| `alarmTrend.points[].label` | string/null | 条件需要 | 横轴标签 |
| `alarmTrend.points[].count` | number/null | 条件需要 | 告警数量 |
| `taskCompletion.items` | array<object> | 条件需要 | 任务完成情况集合 |
| `taskCompletion.items[].name` | string/null | 条件需要 | 任务状态/分类名称 |
| `taskCompletion.items[].count` | number/null | 条件需要 | 数量 |
| `taskCompletion.items[].percent` | number/null | 条件需要 | 占比 |
| `taskCompletion.insight` | string/null | 条件需要 | 统计结论 |
