# 具身智能装备统一控制一期方案

## 1. 一期目标

一期目标是完成远程控制最小闭环，支撑以下设备组合：

- 松灵四轮机器人：本体、双光云台、捕网器、激光雷达、语音对讲。
- 云深处四足机器狗：本体、双光云台、捕网器、语音对讲。
- 宇树机器狗：本体、双光云台、探照灯。

一期只做平台统一控制骨架和轻量人工接管仲裁，不做复杂机械臂轨迹规划、激光雷达点云流、完整任务调度系统和多厂商插件市场。

## 2. 一期范围

### 2.1 必须实现

| 能力 | 一期要求 |
|---|---|
| 设备注册绑定 | 机器人和上装设备提前注册，前端按绑定设备展示 |
| Effective Profile | 后端返回机器人当前有效控制能力 |
| 控制权 | 支持整机控制权和设备控制权 |
| 控制模式 | 前端实时接收客户端上报的 `MANUAL`、`ASSISTED`、`NAVIGATION` |
| 人工接管 | `NAVIGATION` 或 `ASSISTED` 下，本体手动控制前先走接管接口 |
| 本体遥控 | 前端按 10Hz 发送运动控制帧；点触发少量帧，长按持续发帧 |
| 双光云台 | 前端按 10Hz 发送云台控制帧；点触发少量帧，长按持续发帧 |
| 捕网器 | 支持安全开关、二次确认、发射、冷却、审计 |
| 急停 | 独立 topic，最高优先级 |
| WebSocket 高频控制 | 本体和云台连续控制走 WebSocket |
| MQTT 控制链路 | 后端按设备类型组装参数并通过 MQTT 下发给 Go 客户端 |
| ACK/status/error | Go 客户端回 ACK、状态和错误；客户端如何调用 ROS2 由客户端同事抽象 |

### 2.2 暂不实现

| 能力 | 放到二期原因 |
|---|---|
| 机械臂复杂控制 | 需要软限位、碰撞、轨迹和姿态策略 |
| 激光雷达点云流 | 控制协议只负责配置，数据流需另建链路 |
| 完整任务调度系统 | 一期只做接管仲裁，不实现任务编排、路线规划和恢复策略 |
| 多厂商 driver 插件化 | 一期可先内置 driver |
| 协议灰度和版本协商 | 一期固定 `embodied-control 1.0` |

## 3. 一期抽象模型

### 3.1 Robot 与 Device 不合并

一期前端展示可以把设备嵌套在机器人下面，但后端领域模型和控制协议不合并。

```text
Robot = 主装备、载体、调度对象、MQTT 路由根节点
Device = Robot 上的可控/可感知能力单元、动作执行目标
```

控制寻址固定为：

```text
robotId + target.deviceId + action
```

示例：

```json
{
  "robotId": "robot-songling-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ"
  },
  "action": "ptz.move"
}
```

### 3.2 一期测试数据约定

一期不要求新增数据库表。`4.1 查询机器人列表` 和 `4.2 查询控制能力` 由后端先返回固定测试数据，用于前端联调和控制链路打通。

一期实现建议：

| 数据 | 一期实现 |
|---|---|
| 机器人列表 | 后端硬编码或配置文件返回固定测试数据 |
| 机器人绑定设备 | 后端硬编码或配置文件返回固定 `devices` 数组 |
| 控制 profile | 后端硬编码或配置文件返回固定参数范围 |
| 客户端状态 | 后端缓存 Go 客户端最新上报状态，并通过 WebSocket 推给前端 |
| 控制权 session | 可先用内存 Map 管理 |
| 命令状态 | 可先用内存 Map 和日志记录 |
| ACK/status/error | 通过 WebSocket 实时推送，必要时只保存最新状态 |

后续二期或正式资产管理阶段再落库实现 `robot`、`equipment_device`、`robot_device_binding`、`control_profile` 等表。

## 4. 一期前后端接口

### 4.1 查询机器人列表

```text
GET /api/control/robots
```

响应：

```json
[
  {
    "robotId": "robot-songling-001",
    "name": "松灵四轮机器人",
    "robotType": "WHEELED_AGV",
    "vendor": "SONGLING",
    "model": "SCOUT",
    "onlineStatus": "online",
    "controlStatus": "idle",
    "controlMode": "MANUAL",
    "stateSeq": 1288,
    "battery": 86,
    "lastHeartbeatAt": "2026-06-03T10:30:00+08:00"
  }
]
```

### 4.2 查询控制能力

```text
GET /api/control/robots/{robotId}/control-profile
```

响应：

```json
{
  "robotId": "robot-songling-001",
  "robotType": "WHEELED_AGV",
  "vendor": "SONGLING",
  "model": "SCOUT",
  "onlineStatus": "online",
  "controlMode": "MANUAL",
  "stateSeq": 1288,
  "devices": [
    {
      "deviceId": "base",
      "bindingId": "bind-robot-songling-001-base",
      "scope": "BODY",
      "deviceType": "WHEELED_BASE",
      "displayName": "机器人本体",
      "onlineStatus": "online",
      "controlStatus": "idle",
      "enabled": true,
      "actions": ["drive.velocity", "navigation.return_home", "docking.leave"],
      "controlProfile": {
        "driveMode": "DIFFERENTIAL",
        "supportsStrafe": false,
        "maxLinearX": 1.0,
        "maxLinearY": 0.0,
        "maxAngularZ": 0.8,
        "controlFrameRateHz": 10
      }
    },
    {
      "deviceId": "ptz-dual-001",
      "bindingId": "bind-robot-songling-001-ptz-dual-001",
      "scope": "PAYLOAD",
      "deviceType": "DUAL_LIGHT_PTZ",
      "displayName": "双光云台",
      "onlineStatus": "online",
      "controlStatus": "idle",
      "enabled": true,
      "actions": ["ptz.move", "ptz.home", "camera.zoom"],
      "controlProfile": {
        "maxPanSpeed": 1.0,
        "maxTiltSpeed": 1.0,
        "controlFrameRateHz": 10
      }
    }
  ]
}
```

### 4.3 申请控制权

```text
POST /api/control/robots/{robotId}/control-sessions/acquire
```

请求：

```json
{
  "scope": "DEVICE",
  "deviceIds": ["ptz-dual-001"],
  "actions": ["ptz.move"],
  "mode": "EXCLUSIVE",
  "reason": "manual_teleop",
  "ttlSeconds": 30
}
```

响应：

```json
{
  "controlSessionId": "tc_20260603_0001",
  "robotId": "robot-songling-001",
  "ownerUserId": "u1001",
  "ownerClientId": "web-client-001",
  "scope": "DEVICE",
  "deviceIds": ["ptz-dual-001"],
  "actions": ["ptz.move"],
  "status": "ACTIVE",
  "leaseExpireAt": "2026-06-03T10:30:30+08:00"
}
```

### 4.4 控制权续租与释放

```text
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/heartbeat
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/release
GET  /api/control/robots/{robotId}/control-sessions/active
```

释放请求：

```json
{
  "reason": "user_release"
}
```

### 4.5 人工接管

```text
POST /api/control/robots/{robotId}/control-sessions/takeover
```

使用场景：

- 前端实时状态显示 `controlMode=NAVIGATION`，用户要接管本体手动控制。
- 前端实时状态显示 `controlMode=ASSISTED`，用户要切到纯手动控制。
- 前端要开始发送本体 `drive.velocity` 控制帧，但当前模式不是 `MANUAL`。

前端请求：

```json
{
  "fromMode": "NAVIGATION",
  "toMode": "MANUAL",
  "scope": "ROBOT",
  "deviceIds": ["base"],
  "actions": ["drive.velocity"],
  "observedStateSeq": 1288,
  "reason": "manual_takeover"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `fromMode` | string | 是 | 前端看到的当前模式：`MANUAL` / `ASSISTED` / `NAVIGATION` |
| `toMode` | string | 是 | 目标模式，一期接管固定 `MANUAL` |
| `scope` | string | 是 | 接管范围，本体接管用 `ROBOT` |
| `deviceIds` | array[string] | 是 | 接管设备，本体固定包含 `base` |
| `actions` | array[string] | 是 | 接管后允许的动作，如 `drive.velocity` |
| `observedStateSeq` | number | 是 | 前端最近一次收到的客户端状态序号 |
| `reason` | string | 否 | 接管原因 |

成功响应：

```json
{
  "robotId": "robot-songling-001",
  "controlSessionId": "tc_20260603_0008",
  "previousMode": "NAVIGATION",
  "controlMode": "MANUAL",
  "missionStatus": "PAUSED",
  "scope": "ROBOT",
  "deviceIds": ["base"],
  "actions": ["drive.velocity"],
  "status": "ACTIVE",
  "leaseExpireAt": "2026-06-03T10:31:00+08:00"
}
```

失败响应示例：

```json
{
  "code": "ROBOT_STATE_CHANGED",
  "message": "robot state changed, refresh status before takeover",
  "latestStateSeq": 1290,
  "latestControlMode": "MANUAL"
}
```

后端处理规则：

1. 使用后端缓存的最新客户端状态作为仲裁依据，不能只相信前端传来的 `fromMode`。
2. `observedStateSeq` 小于后端最新 `stateSeq` 时，返回 `ROBOT_STATE_CHANGED`。
3. `NAVIGATION -> MANUAL` 时，后端先下发暂停导航或模式切换 MQTT，再创建控制权。
4. `ASSISTED -> MANUAL` 时，后端下发模式切换 MQTT，再创建控制权。
5. 如果当前已是 `MANUAL` 且无人控制，可直接创建控制权。
6. 如果当前已是 `MANUAL` 且其他终端持有本体控制权，返回 `CONTROL_LOCKED`。
7. 云台控制默认不要求打断 `NAVIGATION`，可走普通设备级控制权。
8. 急停不走接管流程，仍然最高优先级。

### 4.6 高风险确认 Token

```text
POST /api/control/robots/{robotId}/commands/confirm-token
```

请求：

```json
{
  "controlSessionId": "tc_20260603_0001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-launcher-001",
    "deviceType": "NET_LAUNCHER"
  },
  "action": "payload.fire",
  "reason": "manual_confirm"
}
```

响应：

```json
{
  "confirmToken": "confirm_20260603_abc001",
  "expiresAt": "2026-06-03T10:31:00+08:00",
  "robotId": "robot-songling-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-launcher-001"
  },
  "action": "payload.fire"
}
```

### 4.7 低频命令

```text
POST /api/control/robots/{robotId}/commands
```

适用：捕网器发射、返航、退出充电桩、云台归中等低频动作。

请求：

```json
{
  "controlSessionId": "tc_20260603_0001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-launcher-001",
    "deviceType": "NET_LAUNCHER"
  },
  "action": "payload.fire",
  "params": {
    "channel": 1,
    "confirmToken": "confirm_20260603_abc001"
  },
  "client": {
    "terminalId": "web-client-001",
    "source": "fire_button",
    "seq": 3101,
    "timestamp": "2026-06-03T10:30:30+08:00"
  }
}
```

响应：

```json
{
  "commandId": "cmd_20260603_0001",
  "traceId": "tr_20260603_0001",
  "status": "PUBLISHED",
  "robotId": "robot-songling-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-launcher-001"
  },
  "action": "payload.fire",
  "issuedAt": "2026-06-03T10:30:30+08:00"
}
```

## 5. 一期 WebSocket 协议

连接地址：

```text
/ws/control
```

用途：

- 本体摇杆。
- 本体方向键长按。
- 双光云台方向键长按。
- 变焦长按。
- 控制 ACK/status/error 推送。

前端发送：

```json
{
  "type": "control.command",
  "requestId": "req_20260603_0001",
  "payload": {
    "robotId": "robot-songling-001",
    "controlSessionId": "tc_20260603_0001",
    "target": {
      "scope": "BODY",
      "deviceId": "base",
      "deviceType": "WHEELED_BASE"
    },
    "action": "drive.velocity",
    "params": {
      "linearX": 0.3,
      "linearY": 0,
      "angularZ": -0.2,
      "frameRateHz": 10
    },
    "client": {
      "terminalId": "web-client-001",
      "source": "body_joystick",
      "seq": 1024,
      "timestamp": "2026-06-03T10:30:00+08:00"
    }
  }
}
```

后端即时响应：

```json
{
  "type": "control.command.accepted",
  "requestId": "req_20260603_0001",
  "payload": {
    "commandId": "cmd_20260603_0001",
    "traceId": "tr_20260603_0001",
    "status": "PUBLISHED",
    "seq": 1024
  }
}
```

客户端状态推送：

```json
{
  "type": "robot.state",
  "payload": {
    "robotId": "robot-songling-001",
    "controlMode": "NAVIGATION",
    "stateSeq": 1288,
    "currentTaskId": "task_20260603_001",
    "missionStatus": "RUNNING",
    "navigationStatus": "RUNNING",
    "controlOwner": null,
    "estopActive": false,
    "onlineStatus": "online",
    "timestamp": "2026-06-03T10:30:00+08:00"
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `controlMode` | string | 是 | `MANUAL` / `ASSISTED` / `NAVIGATION` |
| `stateSeq` | number | 是 | 客户端状态递增序号，用于接管防旧状态 |
| `currentTaskId` | string | 否 | 当前任务 ID |
| `missionStatus` | string | 否 | `RUNNING` / `PAUSED` / `IDLE` |
| `navigationStatus` | string | 否 | 导航状态 |
| `controlOwner` | object | 否 | 当前控制者；为空表示无人持有 |
| `estopActive` | boolean | 是 | 急停是否生效 |

前端使用规则：

- `controlMode=NAVIGATION` 且要控制本体时，先调用 `4.5 人工接管`。
- `controlMode=ASSISTED` 且要切到纯手动本体控制时，先调用 `4.5 人工接管`。
- `controlMode=MANUAL` 且无人持有控制权时，可直接申请普通控制权。
- `controlMode=NAVIGATION` 时，云台控制默认可申请设备级控制权，不打断导航。
- 前端发起接管时必须携带最近一次收到的 `stateSeq` 作为 `observedStateSeq`。

## 6. 一期 MQTT Topic

| Topic | 方向 | 一期用途 |
|---|---|---|
| `robot/{robotId}/control/command` | 后端 -> Go | 普通控制命令 |
| `robot/{robotId}/control/heartbeat` | 后端 -> Go | 控制会话保活 |
| `robot/{robotId}/control/estop` | 后端 -> Go | 急停 |
| `robot/{robotId}/control/ack` | Go -> 后端 | 命令接收确认 |
| `robot/{robotId}/control/status` | Go -> 后端 | 设备状态 |
| `robot/{robotId}/control/error` | Go -> 后端 | 执行错误 |
| `robot/{robotId}/registry/status` | Go -> 后端 | 客户端与设备状态心跳 |

## 7. 一期 MQTT 命令格式

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_20260603_0001",
  "traceId": "tr_20260603_0001",
  "robotId": "robot-songling-001",
  "controlSessionId": "tc_20260603_0001",
  "controlMode": "MANUAL",
  "operator": {
    "userId": "u1001",
    "orgId": "org001",
    "terminalId": "web-client-001"
  },
  "target": {
    "scope": "BODY",
    "deviceId": "base",
    "deviceType": "WHEELED_BASE",
    "vendor": "SONGLING",
    "model": "SCOUT"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.3,
    "linearY": 0,
    "angularZ": -0.2,
    "frameRateHz": 10
  },
  "policy": {
    "qosClass": "INTERACTIVE",
    "requiresExclusiveControl": true,
    "requiresConfirm": false,
    "controlFrameRateHz": 10,
    "expireAt": "2026-06-03T10:30:01+08:00",
    "riskLevel": "MEDIUM"
  },
  "seq": 1024,
  "issuedAt": "2026-06-03T10:30:00+08:00"
}
```

## 8. 一期 Action 参数

### 8.1 连续控制类

连续控制类不区分点触、长按或结束状态。前端统一按 10Hz 发送控制帧；点触就是发送少量帧，长按就是持续发送帧。后端不解释点触或长按语义，只校验、限幅并按设备类型组装 MQTT 参数。

#### `drive.velocity`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `linearX` | number | 是 | 前后速度，正数向前 |
| `linearY` | number | 否 | 横移速度；不支持横移的设备由后端裁剪为 0 |
| `angularZ` | number | 是 | 转向角速度 |

#### `ptz.move`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `panSpeed` | number | 是 | 云台水平速度，负数向左，正数向右 |
| `tiltSpeed` | number | 是 | 云台垂直速度 |

#### `camera.zoom`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `zoomSpeed` | number | 是 | 变焦速度，正数放大，负数缩小 |

### 8.2 离散动作类

#### `payload.fire`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `channel` | number | 是 | 发射通道 |
| `confirmToken` | string | 是 | 二次确认 token |

#### `payload.safety_switch`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `enabled` | boolean | 是 | true 打开安全开关，false 关闭 |

#### `light.set`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `enabled` | boolean | 是 | true 打开，false 关闭 |
| `brightness` | number | 否 | 亮度 0-100 |
| `mode` | string | 否 | `STEADY` 常亮，`FLASH` 闪烁 |

#### `lidar.mode.set`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `mode` | string | 是 | `IDLE` / `LOCALIZATION` / `MAPPING` |
| `scanRateHz` | number | 否 | 扫描频率 |
| `publishPointCloud` | boolean | 否 | 一期默认 false，不通过控制 MQTT 传点云 |

## 9. 前端到 MQTT 参数构造

本章说明前端如何构造请求参数，后端如何根据设备类型解析并组装 MQTT 下发参数。客户端收到后如何抽象 ROS2 调用不在本文档范围内，由客户端同事负责。

示例使用 JSONC 写注释，实际 HTTP、WebSocket、MQTT 传输时必须去掉注释，使用标准 JSON。

### 9.1 通用构造规则

前端只传“当前控制帧”或“一次性动作意图”，不传厂商底层协议字段。

连续控制类：

```text
前端点触：按 10Hz 发送少量控制帧。
前端长按：按 10Hz 持续发送控制帧。
前端结束操作：不再发送控制帧。
后端：每收到一帧就校验、限幅、组装 MQTT 并下发。
客户端：消费后端参数并调用 ROS2 或设备 driver。
```

后端负责补齐：

| 后端补齐字段 | 来源 | 说明 |
|---|---|---|
| `protocol` | 固定值 | 固定 `embodied-control` |
| `version` | 固定值 | 一期固定 `1.0` |
| `messageType` | 固定值 | 普通命令固定 `command` |
| `commandId` | 后端生成 | 命令 ID |
| `traceId` | 后端生成 | 链路追踪 ID |
| `operator` | 请求头/登录态 | `userId`、`orgId`、`terminalId` |
| `target.vendor` | 固定测试 profile | 设备或机器人厂商 |
| `target.model` | 固定测试 profile | 设备或机器人型号 |
| `policy` | action + profile + 安全策略 | 风险等级、控制类型、过期时间等 |
| `issuedAt` | 后端时间 | 后端下发时间 |

前端必须传：

| 前端字段 | 说明 |
|---|---|
| `robotId` | 控制哪台机器人 |
| `controlSessionId` | 已申请到的控制权 ID；不需要控制权的动作可为空 |
| `target.scope` | 控制目标范围，如 `BODY`、`PAYLOAD`、`SENSOR` |
| `target.deviceId` | 控制哪一个设备 |
| `target.deviceType` | 设备类型，用于后端选择参数构造策略 |
| `controlMode` | 控制模式：`MANUAL` / `ASSISTED` / `NAVIGATION`；本体手动控制通常为 `MANUAL` |
| `action` | 平台动作名 |
| `params` | 动作参数 |
| `client.terminalId` | 当前终端 ID |
| `client.source` | 控件来源 |
| `client.seq` | 当前终端递增序号 |
| `client.timestamp` | 前端发送时间 |

后端处理流程：

```text
1. 解析 HTTP/WebSocket 请求体
2. 解析 CurrentUser：userId、orgId、roles、clientId
3. 校验 robotId 是否存在于固定测试数据
4. 校验 target.deviceId 是否属于该 robotId
5. 根据 target.deviceType 选择参数构造策略
6. 校验 action 是否在 devices[].actions 中
7. 校验 controlMode 与后端缓存的最新客户端状态是否允许执行
8. 校验 controlSessionId 是否有效，或判断该 action 是否无需控制权
9. 按 action 校验 params
10. 按 controlProfile 裁剪速度、角速度、亮度等参数
11. 按 action 和 deviceType 生成 policy
12. 生成 commandId、traceId、issuedAt、expireAt
13. 组装 MQTT payload
14. 发布到 robot/{robotId}/control/command 或 estop topic
15. 通过 WebSocket 给前端回 accepted/rejected
```

后端设备类型构造策略：

| `deviceType` | 典型 action | 后端构造重点 |
|---|---|---|
| `WHEELED_BASE` | `drive.velocity` | 不支持横移时 `linearY=0`，按轮式底盘限速 |
| `QUADRUPED_BASE` | `drive.velocity` | 允许横移时保留 `linearY`，按四足限速 |
| `DUAL_LIGHT_PTZ` | `ptz.move`、`camera.zoom` | 裁剪云台速度、变焦速度 |
| `NET_LAUNCHER` | `payload.safety_switch`、`payload.fire` | 校验 confirmToken、安全开关、冷却 |
| `SEARCHLIGHT` | `light.set` | 裁剪亮度，校验模式 |
| `LIDAR` | `lidar.mode.set` | 校验雷达模式和频率 |

后端组装伪代码：

```text
intent = parse(frontendPayload)
profile = fixedProfile.find(robotId, intent.target.deviceId)
builder = commandBuilderFactory.get(intent.target.deviceType)
params = builder.validateAndBuildParams(intent.action, intent.params, profile.controlProfile)
policy = builder.buildPolicy(intent.action, profile)

mqttPayload = {
  protocol: "embodied-control",
  version: "1.0",
  messageType: "command",
  commandId: newCommandId(),
  traceId: newTraceId(),
  robotId: intent.robotId,
  controlSessionId: intent.controlSessionId,
  controlMode: intent.controlMode,
  operator: currentUser,
  target: enrichTarget(intent.target, profile),
  action: intent.action,
  params: params,
  policy: policy,
  seq: intent.client.seq,
  issuedAt: now()
}
```

### 9.2 本体移动：`drive.velocity`

前端发送频率：

| 场景 | 前端行为 |
|---|---|
| 点触 | 按 10Hz 发送少量 `drive.velocity` 控制帧 |
| 长按 | 按住期间持续按 10Hz 发送 `drive.velocity` 控制帧 |
| 摇杆 | 有摇杆输入时按 10Hz 发送当前摇杆值 |

前端 WebSocket 参数：

```jsonc
{
  "type": "control.command",                    // WebSocket 消息类型
  "requestId": "req_20260603_1001",             // 前端生成，请求响应匹配用
  "payload": {
    "robotId": "robot-songling-001",            // 要控制的机器人 ID
    "controlSessionId": "tc_20260603_0001",     // 控制权会话 ID
    "controlMode": "MANUAL",                    // 本体手动控制固定 MANUAL
    "target": {
      "scope": "BODY",                          // 本体
      "deviceId": "base",                       // 本体设备固定 base
      "deviceType": "WHEELED_BASE"              // 后端按该类型构造底盘参数
    },
    "action": "drive.velocity",                 // 本体速度控制帧
    "params": {
      "linearX": 0.3,                            // 前后速度
      "linearY": 0.0,                            // 横移速度；轮式底盘不支持时为 0
      "angularZ": -0.2                           // 转向角速度
    },
    "client": {
      "terminalId": "web-client-001",            // 当前终端 ID
      "source": "body_joystick",                 // 控件来源
      "seq": 1024,                               // 前端递增序号
      "timestamp": "2026-06-03T10:30:00+08:00"   // 前端发送时间
    }
  }
}
```

后端组装 MQTT：

```jsonc
{
  "protocol": "embodied-control",                // 平台统一控制协议
  "version": "1.0",                              // 一期协议版本
  "messageType": "command",                      // MQTT 消息类型
  "commandId": "cmd_20260603_1001",              // 后端生成
  "traceId": "tr_20260603_1001",                 // 后端生成
  "robotId": "robot-songling-001",               // MQTT topic 按 robotId 分区
  "controlSessionId": "tc_20260603_0001",        // 控制权 ID
  "controlMode": "MANUAL",                       // 后端校验通过后透传给客户端
  "operator": {
    "userId": "u1001",                           // 操作者
    "orgId": "org001",                           // 组织
    "terminalId": "web-client-001"               // 终端
  },
  "target": {
    "scope": "BODY",                             // 本体
    "deviceId": "base",                          // 本体设备
    "deviceType": "WHEELED_BASE",                // 设备类型
    "vendor": "SONGLING",                        // 后端根据固定 profile 补齐
    "model": "SCOUT"                             // 后端根据固定 profile 补齐
  },
  "action": "drive.velocity",                    // 客户端消费该 action 并调用 ROS2/driver
  "params": {
    "linearX": 0.3,                               // 后端限幅后的前后速度
    "linearY": 0.0,                               // 后端按 WHEELED_BASE 裁剪为 0
    "angularZ": -0.2                              // 后端限幅后的转向角速度
  },
  "policy": {
    "controlType": "FRAME",                      // 控制帧，一条消息对应一次控制输入
    "frameRateHz": 10,                           // 前端预期发送频率
    "requiresExclusiveControl": true,            // 本体控制要求独占
    "requiresConfirm": false,                    // 不要求二次确认
    "riskLevel": "MEDIUM",                       // 中风险
    "expireAt": "2026-06-03T10:30:01+08:00"      // 后端给客户端的过期时间
  },
  "seq": 1024,                                   // 前端序号
  "issuedAt": "2026-06-03T10:30:00+08:00"        // 后端下发时间
}
```

### 9.3 双光云台：`ptz.move`、`camera.zoom`

前端发送频率：

| 场景 | 前端行为 |
|---|---|
| 方向键点触 | 按 10Hz 发送少量 `ptz.move` 控制帧 |
| 方向键长按 | 按住期间持续按 10Hz 发送 `ptz.move` 控制帧 |
| 变焦长按 | 按住期间持续按 10Hz 发送 `camera.zoom` 控制帧 |

前端 WebSocket 参数：

```jsonc
{
  "type": "control.command",                    // WebSocket 控制命令
  "requestId": "req_20260603_2101",             // 前端请求 ID
  "payload": {
    "robotId": "robot-songling-001",            // 云台挂载的机器人
    "controlSessionId": "tc_20260603_0002",     // 云台设备控制权
    "controlMode": "NAVIGATION",                // 导航中控制云台时可保持 NAVIGATION；纯手动可填 MANUAL
    "target": {
      "scope": "PAYLOAD",                       // 上装设备
      "deviceId": "ptz-dual-001",               // 双光云台设备 ID
      "deviceType": "DUAL_LIGHT_PTZ"            // 后端按该类型构造云台参数
    },
    "action": "ptz.move",                       // 云台控制帧
    "params": {
      "panSpeed": -0.4,                          // 水平速度
      "tiltSpeed": 0.0                           // 垂直速度
    },
    "client": {
      "terminalId": "web-client-001",            // 当前终端
      "source": "ptz_left_button",               // 控件来源
      "seq": 2101,                               // 递增序号
      "timestamp": "2026-06-03T10:30:03+08:00"   // 前端发送时间
    }
  }
}
```

后端组装 MQTT：

```jsonc
{
  "protocol": "embodied-control",                // 统一协议
  "version": "1.0",                              // 协议版本
  "messageType": "command",                      // 控制命令
  "commandId": "cmd_20260603_2101",              // 后端生成
  "traceId": "tr_20260603_2101",                 // 后端生成
  "robotId": "robot-songling-001",               // 机器人 ID
  "controlSessionId": "tc_20260603_0002",        // 控制权 ID
  "controlMode": "NAVIGATION",                   // 云台控制不一定打断导航
  "operator": {
    "userId": "u1001",                           // 操作者
    "orgId": "org001",                           // 组织
    "terminalId": "web-client-001"               // 终端
  },
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "ptz-dual-001",                  // 云台设备 ID
    "deviceType": "DUAL_LIGHT_PTZ",              // 设备类型
    "vendor": "CUSTOM",                          // 固定 profile 补齐
    "model": "DL-PTZ-01"                         // 固定 profile 补齐
  },
  "action": "ptz.move",                          // 客户端消费该 action 并调用云台 ROS2/driver
  "params": {
    "panSpeed": -0.4,                             // 后端限幅后的水平速度
    "tiltSpeed": 0.0                              // 后端限幅后的垂直速度
  },
  "policy": {
    "controlType": "FRAME",                      // 控制帧
    "frameRateHz": 10,                           // 前端预期发送频率
    "requiresExclusiveControl": true,            // 云台设备级独占
    "requiresConfirm": false,                    // 不要求二次确认
    "riskLevel": "LOW",                          // 低风险
    "expireAt": "2026-06-03T10:30:04+08:00"      // 过期时间
  },
  "seq": 2101,                                   // 前端序号
  "issuedAt": "2026-06-03T10:30:03+08:00"        // 后端时间
}
```

变焦前端参数：

```jsonc
{
  "action": "camera.zoom",                       // 相机变焦控制帧
  "params": {
    "zoomSpeed": 0.5                             // 正数放大，负数缩小
  }
}
```

### 9.4 捕网器：`payload.safety_switch`、`payload.fire`

使用场景：

- 打开/关闭捕网器安全开关。
- 二次确认后发射捕网器。

捕网器动作建议走 REST `/commands`，不走高频 WebSocket。

#### 9.4.1 安全开关

前端 REST 请求：

```jsonc
{
  "controlSessionId": "tc_20260603_0003",        // 捕网器设备级或整机控制权
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "net-launcher-001",              // 捕网器设备 ID
    "deviceType": "NET_LAUNCHER"                 // 捕网器类型
  },
  "action": "payload.safety_switch",             // 设置安全开关
  "params": {
    "enabled": true                              // true 打开，false 关闭
  },
  "client": {
    "terminalId": "web-client-001",              // 终端 ID
    "source": "net_safety_toggle",               // 安全开关控件
    "seq": 3001,                                 // 序号
    "timestamp": "2026-06-03T10:30:20+08:00"     // 前端时间
  }
}
```

后端 MQTT：

```jsonc
{
  "protocol": "embodied-control",                // 统一协议
  "version": "1.0",                              // 协议版本
  "messageType": "command",                      // 控制命令
  "commandId": "cmd_20260603_3001",              // 命令 ID
  "traceId": "tr_20260603_3001",                 // 追踪 ID
  "robotId": "robot-songling-001",               // 机器人 ID
  "controlSessionId": "tc_20260603_0003",        // 控制权
  "operator": {
    "userId": "u1001",                           // 操作者
    "orgId": "org001",                           // 组织
    "terminalId": "web-client-001"               // 终端
  },
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "net-launcher-001",              // 捕网器 ID
    "deviceType": "NET_LAUNCHER",                // 捕网器类型
    "vendor": "CUSTOM",                          // 固定 profile
    "model": "NL-01"                             // 固定 profile
  },
  "action": "payload.safety_switch",             // 安全开关
  "params": {
    "enabled": true                              // 打开安全开关
  },
  "policy": {
    "qosClass": "RELIABLE_ACTION",               // 可靠动作
    "requiresExclusiveControl": true,            // 需要独占
    "requiresConfirm": false,                    // 打开安全开关本身不要求 confirmToken
    "expireAt": "2026-06-03T10:30:25+08:00",     // 过期时间
    "riskLevel": "HIGH",                         // 高风险设备
    "auditLevel": "HIGH"                         // 高级别审计
  },
  "seq": 3001,                                   // 序号
  "issuedAt": "2026-06-03T10:30:20+08:00"        // 后端时间
}
```

#### 9.4.2 发射

发射前先请求 confirmToken：

```jsonc
{
  "controlSessionId": "tc_20260603_0003",        // 控制权 ID
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "net-launcher-001",              // 捕网器
    "deviceType": "NET_LAUNCHER"                 // 捕网器类型
  },
  "action": "payload.fire",                      // 准备发射
  "reason": "manual_confirm"                     // 人工二次确认
}
```

拿到 `confirmToken` 后发送发射命令：

```jsonc
{
  "controlSessionId": "tc_20260603_0003",        // 控制权 ID
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "net-launcher-001",              // 捕网器 ID
    "deviceType": "NET_LAUNCHER"                 // 捕网器类型
  },
  "action": "payload.fire",                      // 捕网器发射
  "params": {
    "channel": 1,                                // 发射通道，一期默认 1
    "confirmToken": "confirm_20260603_abc001"    // 二次确认 token
  },
  "client": {
    "terminalId": "web-client-001",              // 终端
    "source": "net_fire_button",                 // 发射按钮
    "seq": 3101,                                 // 序号
    "timestamp": "2026-06-03T10:30:30+08:00"     // 前端时间
  }
}
```

后端 MQTT：

```jsonc
{
  "protocol": "embodied-control",                // 统一协议
  "version": "1.0",                              // 协议版本
  "messageType": "command",                      // 控制命令
  "commandId": "cmd_20260603_3101",              // 命令 ID
  "traceId": "tr_20260603_3101",                 // 追踪 ID
  "robotId": "robot-songling-001",               // 机器人 ID
  "controlSessionId": "tc_20260603_0003",        // 控制权
  "operator": {
    "userId": "u1001",                           // 操作者
    "orgId": "org001",                           // 组织
    "terminalId": "web-client-001"               // 终端
  },
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "net-launcher-001",              // 捕网器
    "deviceType": "NET_LAUNCHER",                // 捕网器类型
    "vendor": "CUSTOM",                          // 固定 profile
    "model": "NL-01"                             // 固定 profile
  },
  "action": "payload.fire",                      // 发射动作
  "params": {
    "channel": 1,                                // 发射通道
    "confirmToken": "confirm_20260603_abc001"    // 透传给 Go，Go 可按需二次校验
  },
  "policy": {
    "qosClass": "RELIABLE_ACTION",               // 可靠动作
    "requiresExclusiveControl": true,            // 强独占
    "requiresConfirm": true,                     // 要求 confirmToken
    "requiresSafetySwitch": true,                // 要求安全开关打开
    "cooldownMs": 3000,                          // 发射冷却
    "expireAt": "2026-06-03T10:30:35+08:00",     // 命令过期
    "riskLevel": "HIGH",                         // 高风险
    "auditLevel": "HIGH"                         // 高审计级别
  },
  "seq": 3101,                                   // 序号
  "issuedAt": "2026-06-03T10:30:30+08:00"        // 后端时间
}
```

### 9.5 探照灯：`light.set`

使用场景：

- 宇树机器狗绑定探照灯时，打开/关闭探照灯。
- 调整亮度或模式。

一期如果接入探照灯，建议作为低频 REST 命令。

前端 REST 请求：

```jsonc
{
  "controlSessionId": "tc_20260603_0004",        // 探照灯设备级控制权
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "searchlight-001",               // 探照灯设备 ID
    "deviceType": "SEARCHLIGHT"                  // 探照灯类型
  },
  "action": "light.set",                         // 设置探照灯
  "params": {
    "enabled": true,                             // true 打开，false 关闭
    "brightness": 80,                            // 亮度 0-100
    "mode": "STEADY"                             // STEADY 常亮，FLASH 闪烁
  },
  "client": {
    "terminalId": "web-client-001",              // 终端
    "source": "searchlight_toggle",              // 控件来源
    "seq": 4001,                                 // 序号
    "timestamp": "2026-06-03T10:30:40+08:00"     // 前端时间
  }
}
```

后端 MQTT：

```jsonc
{
  "protocol": "embodied-control",                // 统一协议
  "version": "1.0",                              // 协议版本
  "messageType": "command",                      // 控制命令
  "commandId": "cmd_20260603_4001",              // 命令 ID
  "traceId": "tr_20260603_4001",                 // 追踪 ID
  "robotId": "robot-unitree-001",                // 宇树机器狗 ID
  "controlSessionId": "tc_20260603_0004",        // 控制权
  "operator": {
    "userId": "u1001",                           // 操作者
    "orgId": "org001",                           // 组织
    "terminalId": "web-client-001"               // 终端
  },
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "searchlight-001",               // 探照灯
    "deviceType": "SEARCHLIGHT",                 // 探照灯类型
    "vendor": "CUSTOM",                          // 固定 profile
    "model": "SL-01"                             // 固定 profile
  },
  "action": "light.set",                         // 设置探照灯
  "params": {
    "enabled": true,                             // 打开
    "brightness": 80,                            // 亮度
    "mode": "STEADY"                             // 常亮
  },
  "policy": {
    "qosClass": "RELIABLE_ACTION",               // 可靠动作
    "requiresExclusiveControl": true,            // 设备级独占
    "requiresConfirm": false,                    // 不要求二次确认
    "expireAt": "2026-06-03T10:30:45+08:00",     // 过期
    "riskLevel": "LOW"                           // 低风险
  },
  "seq": 4001,                                   // 序号
  "issuedAt": "2026-06-03T10:30:40+08:00"        // 后端时间
}
```

### 9.6 激光雷达：`lidar.mode.set`

使用场景：

- 松灵机器人绑定激光雷达时，切换定位/建图/空闲模式。
- 一期只做模式控制，不承载点云数据流。

前端 REST 请求：

```jsonc
{
  "controlSessionId": "tc_20260603_0005",        // 激光雷达设备级控制权
  "target": {
    "scope": "SENSOR",                           // 传感器
    "deviceId": "lidar-001",                     // 激光雷达设备 ID
    "deviceType": "LIDAR"                        // 激光雷达类型
  },
  "action": "lidar.mode.set",                    // 设置雷达模式
  "params": {
    "mode": "MAPPING",                           // IDLE 空闲，LOCALIZATION 定位，MAPPING 建图
    "scanRateHz": 10,                            // 扫描频率
    "publishPointCloud": false                   // 一期默认 false，不走控制 MQTT 传点云
  },
  "client": {
    "terminalId": "web-client-001",              // 终端
    "source": "lidar_mode_select",               // 模式选择控件
    "seq": 5001,                                 // 序号
    "timestamp": "2026-06-03T10:30:50+08:00"     // 前端时间
  }
}
```

后端 MQTT：

```jsonc
{
  "protocol": "embodied-control",                // 统一协议
  "version": "1.0",                              // 协议版本
  "messageType": "command",                      // 控制命令
  "commandId": "cmd_20260603_5001",              // 命令 ID
  "traceId": "tr_20260603_5001",                 // 追踪 ID
  "robotId": "robot-songling-001",               // 松灵机器人
  "controlSessionId": "tc_20260603_0005",        // 控制权
  "operator": {
    "userId": "u1001",                           // 操作者
    "orgId": "org001",                           // 组织
    "terminalId": "web-client-001"               // 终端
  },
  "target": {
    "scope": "SENSOR",                           // 传感器
    "deviceId": "lidar-001",                     // 雷达设备
    "deviceType": "LIDAR",                       // 雷达类型
    "vendor": "CUSTOM",                          // 固定 profile
    "model": "LIDAR-01"                          // 固定 profile
  },
  "action": "lidar.mode.set",                    // 模式切换
  "params": {
    "mode": "MAPPING",                           // 建图模式
    "scanRateHz": 10,                            // 频率
    "publishPointCloud": false                   // 一期不通过控制 MQTT 发点云
  },
  "policy": {
    "qosClass": "RELIABLE_ACTION",               // 可靠动作
    "requiresExclusiveControl": true,            // 设备级独占
    "requiresConfirm": false,                    // 一期不要求二次确认
    "expireAt": "2026-06-03T10:30:55+08:00",     // 过期
    "riskLevel": "LOW"                           // 低风险
  },
  "seq": 5001,                                   // 序号
  "issuedAt": "2026-06-03T10:30:50+08:00"        // 后端时间
}
```

### 9.7 语音对讲：沿用媒体对讲链路

一期语音对讲建议沿用当前实时视频模块的对讲设计，不纳入 `embodied-control` 普通设备控制 MQTT。

原因：

- 对讲使用 LiveKit 音频链路，不是普通设备动作。
- 当前项目已有视频会话绑定对讲的接口和 MQTT topic。
- 前端点击对讲按钮时，应走媒体模块的 intercom start/stop，而不是 `/commands`。

一期调用方式：

```text
POST /api/control/robots/{robotId}/cameras/{deviceId}/video/intercom/start
POST /api/control/video-sessions/{sessionId}/intercom/stop
```

如果二期需要把语音对讲也纳入统一设备目录，可增加：

```text
target.scope = AUDIO
target.deviceId = intercom-001
action = audio.intercom.start / audio.intercom.stop
```

但一期不建议迁移，避免和现有 LiveKit 对讲链路重复。

## 10. 控制模式与接管策略

### 10.1 控制模式

| 模式 | 说明 | 一期处理 |
|---|---|---|
| `MANUAL` | 手动模式，用户直接控制本体或设备 | 本体控制帧必须处于该模式 |
| `ASSISTED` | 辅助模式，机器人或算法参与辅助控制 | 本体手动控制前需要接管到 `MANUAL` |
| `NAVIGATION` | 导航模式，机器人执行导航或任务 | 本体手动控制前需要接管到 `MANUAL`；云台可默认不打断导航 |

### 10.2 接管策略

| 最新客户端状态 | 前端动作 | 后端策略 |
|---|---|---|
| `MANUAL` 且无人控制 | 申请本体控制 | 允许创建控制权 |
| `MANUAL` 且他人控制本体 | 申请本体控制 | 拒绝，返回 `CONTROL_LOCKED` |
| `ASSISTED` | 手动控制本体 | 先调用 takeover，切到 `MANUAL` 后返回控制权 |
| `NAVIGATION` | 手动控制本体 | 先调用 takeover，暂停导航后返回控制权 |
| `NAVIGATION` | 控制云台 | 默认允许申请设备级控制权，不打断导航 |
| `NAVIGATION` | 捕网器发射 | 默认禁止，除非任务策略明确允许 |
| 任意模式 | 急停 | 允许，最高优先级 |
| 急停生效中 | 普通控制 | 禁止 |

### 10.3 接管时序

```text
Go 客户端上报 robot.state
  -> 后端缓存最新 stateSeq/controlMode
  -> WebSocket 推给前端
  -> 前端展示“接管”按钮
  -> 用户点击接管
  -> 前端调用 /control-sessions/takeover，携带 observedStateSeq
  -> 后端比对 observedStateSeq 与最新 stateSeq
  -> 后端按策略暂停导航或切换模式
  -> 后端创建 controlSessionId
  -> 前端开始按 10Hz 发送 drive.velocity 控制帧
```

注意：前端实时状态只用于展示和发起接管，最终是否允许接管由后端根据最新客户端状态、用户权限、控制权和策略决定。

## 11. 一期频率和安全

| 控制对象 | 前端发送频率 | 后端处理 |
|---|---:|---|
| 本体点触 | 10Hz，发送少量帧 | 每帧校验、限幅、按本体设备类型组装 MQTT |
| 本体长按/摇杆 | 10Hz，按住期间持续发送 | 每帧校验、限幅、按本体设备类型组装 MQTT |
| 云台点触 | 10Hz，发送少量帧 | 每帧校验、限幅、按云台设备类型组装 MQTT |
| 云台长按 | 10Hz，按住期间持续发送 | 每帧校验、限幅、按云台设备类型组装 MQTT |
| 变焦长按 | 10Hz，按住期间持续发送 | 每帧校验、限幅、按云台/相机设备类型组装 MQTT |

安全规则：

- 连续控制类不要求前端发送结束帧。
- 后端不区分点触、长按、松手，只处理收到的每一帧控制数据。
- 本体手动控制必须处于 `MANUAL`，`NAVIGATION` 或 `ASSISTED` 下需先接管。
- `payload.fire` 必须有控制权、confirmToken、安全开关和冷却校验。
- 急停使用 `robot/{robotId}/control/estop`，不进入普通命令队列。
- 客户端如何消费控制帧并调用 ROS2，由客户端同事单独抽象实现。

## 12. 一期验收标准

1. 前端能根据 `control-profile` 动态展示不同机器人绑定的上装控制面板。
2. 两个终端不能同时控制同一机器人本体。
3. 一个终端控制本体时，另一个终端可按配置控制云台。
4. 本体和云台点触时，前端能按 10Hz 发送少量控制帧，后端逐帧组装 MQTT。
5. 本体和云台长按时，前端能按 10Hz 持续发送控制帧，后端逐帧组装 MQTT。
6. 捕网器没有 confirmToken 或安全开关时不能发射。
7. `NAVIGATION` 或 `ASSISTED` 下手动控制本体时，前端必须先接管成功再发送控制帧。
8. 前端接管请求携带旧 `observedStateSeq` 时，后端返回 `ROBOT_STATE_CHANGED`。
9. 后端能记录命令发布、ACK、状态和错误。
