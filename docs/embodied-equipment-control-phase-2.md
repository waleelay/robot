# 具身智能装备统一控制二期方案

## 1. 二期目标

二期目标是在一期统一控制骨架之上，扩展到更复杂的具身智能装备集成能力：

- 机械臂控制。
- 激光雷达模式控制和数据链路协同。
- 任务调度与人工接管。
- 多厂商 driver 插件化。
- 协议版本协商和灰度升级。
- 更完整的审计、回放、指标和运维治理。

二期不改变一期核心协议外壳，继续使用：

```text
robotId + target.deviceId + target.deviceType + action + params + seq + commandId + issuedAt
```

二期可以在后端审计、链路追踪、回放系统中增强 `policy`、`traceId`、操作者上下文等治理信息，但默认不把这些字段作为 Go 客户端执行指令的必需参数。

## 2. 二期范围

### 2.1 新增设备类型

| 设备 | 二期能力 |
|---|---|
| 机械臂 | 关节速度、末端位姿、预设动作、软限位、急停联动 |
| 激光雷达 | 模式切换、建图/定位配置、点云数据链路协同 |
| 探照灯 | 开关、亮度、模式 |
| 喊话器 | 文本喊话、音频播放、音量控制 |
| 多传感器 | 气体、声学、环境传感器配置和状态 |

### 2.2 新增平台能力

| 能力 | 二期说明 |
|---|---|
| 任务模式 | 区分人工遥控、自动任务、辅助控制、维护模式 |
| 人工接管 | 自动任务中支持暂停、取消、局部接管 |
| driver 插件化 | 不同厂商和设备类型通过 driver 适配 |
| 协议版本协商 | 后端根据客户端版本和 supportedActions 下发兼容命令 |
| 状态聚合 | 高频状态内存聚合，低频状态入库，事件即时推送 |
| 审计回放 | 命令、视频、任务、状态关联回放 |

## 3. 二期模型扩展

### 3.1 型号模板与实例覆写

二期引入型号模板，避免每台设备重复配置。

```text
model_template
  + equipment_device 实例配置
  + robot_device_binding 绑定覆写
  + runtime_status 运行状态
  = effective_control_profile
```

新增建议表：

| 表 | 说明 |
|---|---|
| `equipment_model_template` | 厂商型号默认能力和参数 |
| `driver_adapter` | driver 类型、版本、支持协议 |
| `action_schema` | action 参数 schema |
| `action_conflict_matrix` | action 之间的冲突关系 |
| `confirm_token` | 高风险动作确认 token |
| `mission_control_state` | 任务与人工控制状态 |

### 3.2 Action Schema

每个 action 都应有 schema，用于前端渲染、后端校验和 Go driver 适配。

示例：

```json
{
  "action": "arm.pose",
  "targetDeviceType": "ROBOT_ARM",
  "paramsSchema": {
    "frame": {"type": "string", "required": true},
    "position.x": {"type": "number", "required": true, "min": -1.0, "max": 1.0},
    "position.y": {"type": "number", "required": true, "min": -1.0, "max": 1.0},
    "position.z": {"type": "number", "required": true, "min": 0.0, "max": 1.5},
    "speed": {"type": "number", "required": false, "min": 0.0, "max": 0.5}
  },
  "riskLevel": "HIGH",
  "requiresConfirm": true
}
```

## 4. 二期机械臂控制

### 4.1 `arm.joint_velocity`

用途：机械臂关节空间速度控制。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `joints` | array | 是 | 关节速度列表 |
| `joints[].name` | string | 是 | 关节名 |
| `joints[].velocity` | number | 是 | 关节速度 |
| `durationMs` | number | 否 | 执行时长 |
| `controlMode` | string | 是 | `tap` / `hold_update` / `stream` |

示例：

```json
{
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "arm-001",
    "deviceType": "ROBOT_ARM"
  },
  "action": "arm.joint_velocity",
  "params": {
    "joints": [
      {"name": "joint1", "velocity": 0.1},
      {"name": "joint2", "velocity": -0.1}
    ],
    "durationMs": 100,
    "controlMode": "tap"
  }
}
```

### 4.2 `arm.pose`

用途：机械臂末端位姿控制。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `frame` | string | 是 | 坐标系，如 `base_link` |
| `position.x` | number | 是 | X 坐标 |
| `position.y` | number | 是 | Y 坐标 |
| `position.z` | number | 是 | Z 坐标 |
| `orientation.roll` | number | 是 | 横滚 |
| `orientation.pitch` | number | 是 | 俯仰 |
| `orientation.yaw` | number | 是 | 偏航 |
| `speed` | number | 否 | 执行速度 |
| `confirmToken` | string | 高风险时必填 | 二次确认 |

安全要求：

- 默认与本体运动冲突。
- 默认要求设备级或整机强独占。
- Go driver 必须做软限位、碰撞区域和驱动状态校验。
- 机械臂运动中触发急停时，必须停止机械臂和本体。

## 5. 二期激光雷达控制

### 5.1 `lidar.mode.set`

用途：切换激光雷达工作模式。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `mode` | string | 是 | `IDLE` / `LOCALIZATION` / `MAPPING` / `INSPECTION` |
| `scanRateHz` | number | 否 | 扫描频率 |
| `publishPointCloud` | boolean | 否 | 是否发布点云 |
| `frameId` | string | 否 | 坐标系 ID |

示例：

```json
{
  "target": {
    "scope": "SENSOR",
    "deviceId": "lidar-001",
    "deviceType": "LIDAR"
  },
  "action": "lidar.mode.set",
  "params": {
    "mode": "MAPPING",
    "scanRateHz": 10,
    "publishPointCloud": true,
    "frameId": "lidar_link"
  }
}
```

### 5.2 点云数据链路

控制协议只负责启停和配置，点云数据不通过普通 MQTT 控制 topic 承载。

可选链路：

| 链路 | 适用 |
|---|---|
| WebSocket 二进制 | 低频、小规模调试 |
| WebRTC DataChannel | 实时点云预览 |
| ROS bridge | 机器人内网或边缘节点 |
| 对象存储 | 离线点云文件 |
| 专用流服务 | 高并发调度平台 |

## 6. 二期任务模式与人工接管

### 6.1 控制模式

| 模式 | 说明 |
|---|---|
| `MANUAL_TELEOP` | 人工遥控 |
| `AUTO_MISSION` | 自动任务 |
| `ASSISTED_CONTROL` | 辅助控制 |
| `REMOTE_SUPERVISION` | 远程监督 |
| `MAINTENANCE` | 运维调试 |

### 6.2 接管策略

| 当前状态 | 人工请求 | 策略 |
|---|---|---|
| 自动巡检中 | 控本体 | 暂停任务并申请整机控制权 |
| 自动巡检中 | 控云台 | 可设备级接管，任务继续 |
| 自动返航中 | 控本体 | 高权限才允许打断 |
| 急停中 | 任意普通控制 | 禁止 |
| 维护模式 | 普通用户控制 | 禁止 |

新增接口建议：

```text
POST /api/control/robots/{robotId}/missions/{missionId}/pause-for-teleop
POST /api/control/robots/{robotId}/missions/{missionId}/resume
POST /api/control/robots/{robotId}/control-sessions/takeover
```

## 7. 二期协议版本与灰度

二期需要支持客户端能力协商。

Go 客户端心跳上报：

```json
{
  "robotId": "robot-deep-001",
  "clientId": "robot-client-deep-001",
  "clientVersion": "2.0.0",
  "protocolVersions": ["1.0", "1.1", "2.0"],
  "supportedActions": [
    "drive.velocity",
    "ptz.move",
    "arm.pose",
    "lidar.mode.set"
  ]
}
```

后端下发前判断：

- 客户端是否支持协议版本。
- 客户端是否支持 action。
- 当前设备绑定关系是否允许该 action。
- 当前 action schema 是否兼容客户端版本。

## 8. 二期 driver 插件化

Go 客户端 driver 建议接口：

```go
type Driver interface {
    Supports(target Target, action string) bool
    Execute(ctx context.Context, command Command) error
    Stop(ctx context.Context, target Target) error
    Status(ctx context.Context, target Target) (DeviceState, error)
}
```

二期 driver 目录：

```text
client/internal/control/drivers/
├── songling/
├── deeprobotics/
├── unitree/
├── ptz/
├── netlauncher/
├── arm/
├── lidar/
└── ros2/
```

## 9. 二期审计与回放

二期需要把控制事件与视频、任务、状态关联起来。

审计字段：

| 字段 | 说明 |
|---|---|
| `commandId` | 命令 ID |
| `traceId` | 链路 ID |
| `missionId` | 任务 ID |
| `videoSessionId` | 视频会话 ID |
| `robotId` | 机器人 |
| `deviceId` | 设备 |
| `action` | 动作 |
| `paramsSnapshot` | 参数快照 |
| `operatorUserId` | 操作者 |
| `terminalId` | 终端 |
| `statusTimeline` | 生命周期状态 |

回放目标：

- 能看出某个时刻谁控制了哪台机器人。
- 能关联当时的视频画面。
- 能看到命令发布、设备状态变化、完成或失败。
- 能看到高风险动作确认记录。

## 10. 二期验收标准

1. 机械臂动作通过 action schema 校验，危险动作必须二次确认。
2. 激光雷达支持模式切换，点云数据不挤占控制 MQTT topic。
3. 自动任务中可按策略进行人工接管。
4. 不同版本 Go 客户端能通过 supportedActions 控制前端展示。
5. 新厂商设备通过 driver 接入，不改前后端主协议。
6. 控制事件可按机器人、任务、视频会话进行追踪和复盘。
