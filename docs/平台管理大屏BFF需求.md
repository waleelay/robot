# 大屏 BFF 对接平台管理服务需求

请平台管理服务提供以下基础业务字段和计算依据字段。时间格式统一：`yyyy-MM-dd HH:mm:ss`。

说明：统计、分组、展示文案等字段不要求管理端直接按大屏结构返回，只需提供可计算这些字段的原始数据。

接口数据范围：

| 字段 | 中文说明 |
|---|---|
| `devices` | 设备列表 |
| `tasks` | 任务列表 |
| `alarms` | 告警数据 |

## 1. 设备数据

设备列表字段：

```json
{
  "robotId": "",
  "clientId": "",
  "name": "",
  "type": "",
  "typeCode": "",
  "vendor": "",
  "model": "",
  "status": "online",
  "battery": 100,
  "lastHeartbeatAt": "yyyy-MM-dd HH:mm:ss",
  "stateSeq": 1,
  "fault": false,
  "alarmLevel": null,
  "controlMode": "MANUAL",
  "speed": 0.0,
  "location": {
    "lng": 0.0,
    "lat": 0.0,
    "altitude": null,
    "x": 0.0,
    "y": 0.0,
    "z": 0.0,
    "address": "",
    "updatedAt": "yyyy-MM-dd HH:mm:ss"
  },
  "cameras": [],
  "devices": [],
  "task": null
}
```

字段说明：

| 字段 | 中文说明 |
|---|---|
| `robotId` | 机器人唯一标识 |
| `clientId` | 机器人客户端标识 |
| `name` | 机器人名称 |
| `type` | 机器人类型中文名 |
| `typeCode` | 机器人类型编码 |
| `vendor` | 厂商 |
| `model` | 型号 |
| `status` | 在线状态，`online/offline/fault` |
| `battery` | 电量百分比 |
| `lastHeartbeatAt` | 最后心跳时间 |
| `stateSeq` | 状态序号 |
| `fault` | 是否故障 |
| `alarmLevel` | 当前告警等级，`HIGH/MEDIUM/LOW/null` |
| `controlMode` | 控制模式 |
| `speed` | 当前速度 |
| `location.lng` | 经度 |
| `location.lat` | 纬度 |
| `location.altitude` | 海拔 |
| `location.x` | 局部坐标 X |
| `location.y` | 局部坐标 Y |
| `location.z` | 局部坐标 Z |
| `location.address` | 位置描述 |
| `location.updatedAt` | 位置更新时间 |
| `cameras` | 摄像头列表 |
| `devices` | 上装设备列表 |
| `task` | 设备列表当前任务摘要 |

摄像头字段：

| 字段 | 中文说明 |
|---|---|
| `cameras.cameraId` | 摄像头唯一标识 |
| `cameras.deviceId` | 摄像头设备标识 |
| `cameras.groupType` | 摄像头分组类型 |
| `cameras.name` | 摄像头名称 |
| `cameras.quality` | 默认清晰度 |

上装设备字段：

| 字段 | 中文说明 |
|---|---|
| `devices.deviceId` | 上装设备标识 |
| `devices.name` | 上装设备名称 |
| `devices.type` | 上装设备类型 |
| `devices.status` | 上装设备状态 |

当前任务摘要字段：

| 字段 | 中文说明 |
|---|---|
| `task.taskId/currentTask.taskId` | 当前任务 ID |
| `task.name/currentTask.name` | 当前任务名称 |
| `task.status/currentTask.status` | 当前任务状态 |
| `task.timeRange/currentTask.timeRange` | 当前任务时间段 |

地图配置字段：

| 字段 | 中文说明 |
|---|---|
| `map.center.lng` | 地图中心点经度 |
| `map.center.lat` | 地图中心点纬度 |
| `map.zoom` | 地图默认缩放级别 |
| `map.defaultLayer` | 地图默认图层 |
| `map.updatedAt` | 地图配置更新时间 |

## 2. 巡逻与任务数据

巡逻数据字段：

| 字段 | 中文说明 |
|---|---|
| `patrolDuration` | 巡逻时长 |
| `patrolDurationUnit` | 巡逻时长单位 |
| `patrolMileage` | 巡逻里程 |
| `patrolMileageUnit` | 巡逻里程单位 |
| `patrolDate` | 巡逻统计日期 |

任务统计依据字段：

| 字段 | 中文说明 |
|---|---|
| `taskId` | 任务唯一标识 |
| `status` | 任务状态编码 |
| `startTime` | 任务开始时间 |
| `endTime` | 任务结束时间 |

任务列表字段：

| 字段 | 中文说明 |
|---|---|
| `taskId` | 任务唯一标识 |
| `name` | 任务名称 |
| `status` | 任务状态编码 |
| `statusName` | 任务状态名称 |
| `startTime` | 开始时间 |
| `endTime` | 结束时间 |
| `timeRange` | 页面展示时间段 |
| `currentLocation` | 当前任务位置 |
| `equipmentList.robotId` | 执行装备机器人 ID |
| `equipmentList.name` | 执行装备名称 |
| `equipmentList.type` | 执行装备类型 |
| `equipmentList.status` | 执行装备状态 |

## 3. 告警数据

告警统计依据字段：

| 字段 | 中文说明 |
|---|---|
| `alarmId` | 告警唯一标识 |
| `level` | 风险等级编码，`HIGH/MEDIUM/LOW` |
| `status` | 告警处理状态 |
| `eventTime` | 告警发生时间 |

告警列表字段：

| 字段 | 中文说明 |
|---|---|
| `alarmId` | 告警唯一标识 |
| `title` | 告警标题 |
| `category` | 告警分类编码 |
| `categoryName` | 告警分类名称 |
| `level` | 风险等级编码，`HIGH/MEDIUM/LOW` |
| `levelName` | 风险等级名称 |
| `eventTime` | 告警发生时间 |
| `location.lng` | 告警经度 |
| `location.lat` | 告警纬度 |
| `location.altitude` | 告警海拔 |
| `location.x` | 告警局部坐标 X |
| `location.y` | 告警局部坐标 Y |
| `location.z` | 告警局部坐标 Z |
| `location.address` | 告警位置描述 |
| `location.updatedAt` | 位置更新时间 |
| `robotId` | 关联机器人 ID |
| `deviceName` | 关联设备名称 |
| `taskId` | 所属任务 ID |
| `taskName` | 所属任务名称 |
| `status` | 告警处理状态 |
| `snapshotUrl.visible` | 可见光截图地址 |
| `snapshotUrl.thermal` | 热成像截图地址 |
| `snapshotUrl.front` | 前向截图地址 |

告警处置字段：

| 字段 | 中文说明 |
|---|---|
| `alarmId` | 告警唯一标识 |
| `disposalStatus` | 处置状态，`IMMEDIATE_DISPOSAL/FALSE_ALARM` |
| `disposalStatusName` | 处置状态名称 |
| `status` | 处置后的告警状态 |
| `success` | 是否处置成功 |
| `message` | 处置结果提示 |

处置状态：`IMMEDIATE_DISPOSAL=立即处置`，`FALSE_ALARM=误报`。

## 4. 计算字段与依据

以下字段为大屏接口输出字段，管理端无需直接提供，只需提供右侧依据字段。

| 大屏输出字段 | 计算依据字段 |
|---|---|
| `serverTime` | BFF 当前时间 |
| `deviceStats.total/online/fault/offline` | `devices.status`、`devices.fault` |
| `deviceTypeStats.*` | `devices.typeCode`、`devices.type`、`devices.status`、`devices.fault` |
| `mountedDeviceCount` | `devices.devices` 上装设备列表长度 |
| `mountedDevices` | `devices.devices` |
| `alarmStatus` | `devices.alarmLevel` |
| `alarmText` | `devices.alarmLevel` |
| `mapDisplay.*` | 设备基础字段、`devices.status`、`devices.alarmLevel` |
| `currentTask` | `devices.task` |
| `actions.*` | `devices.status` |
| `patrolOverview.*` | `patrolDuration`、`patrolDurationUnit`、`patrolMileage`、`patrolMileageUnit`、`patrolDate` |
| `taskOverview.totalToday/running/pending` | `tasks.status`、`tasks.startTime`、`tasks.endTime` |
| `taskOverview.completedRate/completedRateText` | `tasks.status` 统计出的任务总数、已完成任务数 |
| `tasks.total` | `tasks` 列表长度 |
| `alarms.total` | `alarms` 列表数量 |
| `alarms.summary.*` | `alarms.status`、`alarms.eventTime` |
| `alarms.high/medium/low.items` | `alarms.level` |
