# 具身智能装备统一控制一期方案

## 1. 一期目标

一期目标是完成远程控制最小闭环，支撑以下设备组合：

- 松灵四轮机器人：本体、双光云台、捕网器、警示灯、车灯光、语音对讲。
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
| 发射器 | 支持真实安全开关、二次确认、6 发通道发射、审计 |
| 捕网枪 | 前端本地安全开关解锁、二次确认、1 发发射、审计 |
| 急停 | 独立 topic，最高优先级 |
| WebSocket 高频控制 | 本体和云台连续控制走 WebSocket |
| MQTT 控制链路 | 后端按设备类型组装参数并通过 MQTT 下发给 Go 客户端 |
| 客户端状态上报 | Go 客户端通过 `robot/{robotId}/media/client/status` 统一上报在线状态、控制模式和设备状态 |

### 2.2 暂不实现

| 能力 | 放到二期原因 |
|---|---|
| 机械臂复杂控制 | 需要软限位、碰撞、轨迹和姿态策略 |
| 激光雷达点云流和模式控制 | 一期暂不接入雷达控制代码，后续如需接入放到二期 |
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
| 客户端状态 | 统一从 `robot/{robotId}/media/client/status` 获取，不单独设计控制确认、执行状态或错误 topic |

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
      "actions": ["ptz.move", "ptz.auto_rotate", "ptz.home", "camera.zoom"],
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
  "actions": ["ptz.move", "ptz.auto_rotate"],
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
  "actions": ["ptz.move", "ptz.auto_rotate"],
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
    "deviceId": "net-gun-001",
    "deviceType": "NET_GUN"
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
    "deviceId": "net-gun-001"
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
    "deviceId": "net-gun-001",
    "deviceType": "NET_GUN"
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
  "status": "PUBLISHED",
  "robotId": "robot-songling-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-gun-001"
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
- 客户端状态变化推送。

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
      "angularZ": -0.2
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
| `robot/{robotId}/control/body/command` | 后端 -> Go | 机器人本体控制，前后、转向、左移右移 |
| `robot/{robotId}/control/ptz/command` | 后端 -> Go | 双光云台控制，8 方向、自动旋转、变焦等 |
| `robot/{robotId}/control/audio/command` | 后端 -> Go | 客户端音量控制，滑块调节、音量加减、静音 |
| `robot/{robotId}/control/launcher/command` | 后端 -> Go | 发射器控制，6 发类设备 |
| `robot/{robotId}/control/net-gun/command` | 后端 -> Go | 捕网枪控制，1 发类设备 |
| `robot/{robotId}/control/warning-light/command` | 后端 -> Go | 左右警示灯开关控制 |
| `robot/{robotId}/control/vehicle-light/command` | 后端 -> Go | 前后车灯控制，常开、常关、呼吸灯、自定义亮度 |
| `robot/{robotId}/control/safety/estop` | 后端 -> Go | 急停 |
| `robot/{robotId}/media/client/status` | Go -> 后端 | 统一客户端状态上报，包含在线状态、控制模式、任务状态和设备状态 |

说明：

- Go 模拟客户端订阅 `robot/{robotId}/control/#`，按 topic 和 payload 中的 `target.deviceId/action` 打印或分发。
- 控制 topic 只保留设备大类，不再携带 `{deviceId}`；具体设备由 payload 中的 `target.deviceId` 标识。
- 前端实时状态由后端消费 `media/client/status` 后通过 WebSocket 推送 `robot.state`。
- `controlMode`、`stateSeq`、`devices` 随 `media/client/status` 上报。
- 后端发布控制命令成功后即可向前端返回 `PUBLISHED`；一期不等待客户端接收确认。

后端 topic 构造规则：

| `deviceType` / action | 下发 topic |
|---|---|
| `WHEELED_BASE`、`QUADRUPED_BASE`、`BIPED_BASE` | `robot/{robotId}/control/body/command` |
| `DUAL_LIGHT_PTZ` | `robot/{robotId}/control/ptz/command` |
| `CLIENT_AUDIO`、`INTERCOM`、`VOLUME_CONTROL` 或 `volume.*` | `robot/{robotId}/control/audio/command` |
| `LAUNCHER` | `robot/{robotId}/control/launcher/command` |
| `NET_GUN`、`NET_LAUNCHER` | `robot/{robotId}/control/net-gun/command` |
| `WARNING_LIGHT` 或 `light.warning.*` | `robot/{robotId}/control/warning-light/command` |
| `VEHICLE_LIGHT`、`SEARCHLIGHT` 或 `light.vehicle.*` | `robot/{robotId}/control/vehicle-light/command` |

`media/client/status` 示例：

```jsonc
{
  "robotId": "robot-songling-001",              // 机器人 ID
  "clientId": "robot-client-songling-001",      // Go 客户端实例 ID
  "clientVersion": "sim-1.0.0",                 // 客户端版本
  "name": "松灵四轮机器人",                       // 机器人名称
  "type": "轮式机器人",                          // 机器人类型展示值
  "status": "online",                           // 媒体模块已有在线状态字段：online/offline
  "onlineStatus": "online",                     // 装备控制使用的在线状态：online/offline
  "battery": 86,                                // 电量百分比
  "controlMode": "MANUAL",                      // 控制模式：MANUAL/ASSISTED/NAVIGATION
  "stateSeq": 1024,                             // 客户端状态递增序号，前端接管时携带 observedStateSeq
  "missionStatus": "IDLE",                      // 任务状态
  "navigationStatus": "IDLE",                   // 导航状态
  "controlOwner": null,                         // 当前控制占用者；无占用为 null
  "estopActive": false,                         // 急停是否生效
  "cameras": [
    {
      "cameraId": "camera01",                   // 摄像头 ID
      "deviceId": "ptz-dual-001",               // 关联设备 ID
      "name": "前向双光云台",                     // 摄像头名称
      "groupType": "dual_gimbal",               // 分组类型
      "channel": "visible",                     // 默认通道
      "quality": "hd",                          // 默认清晰度
      "status": "online"                        // 摄像头在线状态
    }
  ],
  "devices": [
    {
      "deviceId": "base",                       // 设备 ID
      "scope": "BODY",                          // BODY/PAYLOAD/SENSOR
      "deviceType": "WHEELED_BASE",             // 设备类型
      "onlineStatus": "online",                 // 设备在线状态
      "healthStatus": "normal",                 // normal/warning/error
      "controlStatus": "idle",                  // idle/busy/locked/disabled/fault
      "supportedActions": ["drive.velocity"]    // 当前客户端支持的动作
    }
  ],
  "timestamp": "2026-06-03T10:30:00+08:00"      // 客户端上报时间
}
```

## 7. 一期 MQTT 命令格式

一期 MQTT 下发给 Go 客户端的是“执行指令”，不是完整业务上下文。接管权、操作者、确认令牌、控制模式、风险策略等字段由后端在下发前完成校验，并记录在后端日志或审计数据中，不作为客户端执行的必需字段。

一期建议使用精简执行 payload。MQTT topic 已经表达了机器人和设备域，payload 中只保留客户端执行需要的字段。

```json
{
  "robotId": "robot-songling-001",
  "seq": 1024,
  "target": {
    "deviceId": "base",
    "deviceType": "WHEELED_BASE"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.3,
    "linearY": 0,
    "angularZ": -0.2
  },
  "issuedAt": "2026-06-03T10:30:00+08:00"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `robotId` | string | 是 | 控制哪台机器人，也与 MQTT topic 中的 `{robotId}` 一致 |
| `seq` | number | 建议 | 前端递增序号，客户端可用于日志和观察丢帧/乱序 |
| `target.deviceId` | string | 是 | 控制哪个设备，例如 `base`、`ptz-dual-001`、`net-gun-001` |
| `target.deviceType` | string | 是 | 设备类型，客户端按类型分发处理 |
| `action` | string | 是 | 平台动作名，例如 `drive.velocity`、`ptz.move`、`payload.fire` |
| `params` | object | 是 | 后端校验、限幅后的动作参数 |
| `issuedAt` | datetime | 是 | 后端下发时间 |

以下字段不进入一期 MQTT 执行指令：

| 字段 | 不下发原因 |
|---|---|
| `protocol` | topic 已按控制域隔离，一期客户端只消费控制 topic，可不再用 payload 字段识别协议 |
| `version` | 一期协议固定，暂不做版本协商；后续多版本兼容时再恢复或放到 topic/header |
| `messageType` | 当前 topic 只承载命令，不混传事件或 ack |
| `commandId` | 无客户端 ack 和去重要求时可不下发；后端可在日志、接口响应中内部保留 |
| `operator` | 操作者信息用于后端鉴权、审计，不参与客户端执行 |
| `controlSessionId` | 控制权由后端校验，客户端不再二次判断 |
| `controlMode` | 控制模式由后端结合客户端状态判断，客户端只执行已放行的指令 |
| `traceId` | 链路追踪保留在后端日志即可，一期客户端不依赖 |
| `target.vendor` / `target.model` | 设备归属由客户端配置或后端 profile 管理，执行指令只要求设备 ID 和类型 |
| `policy` | 风险等级、二次确认、独占控制、过期策略由后端处理 |
| `params.frameRateHz` | 10Hz 是前端发送频率，不是动作参数；速度由 `params` 中的速度值决定 |

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

#### `ptz.auto_rotate`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `enabled` | boolean | 是 | true 开启云台自动旋转，false 停止自动旋转 |
| `panSpeed` | number | 否 | 自动旋转水平速度，默认 0.3，由后端按云台能力限幅 |

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

一期约束：`payload.safety_switch` 只用于发射器 `LAUNCHER` 的真实设备安全开关；捕网枪 `NET_GUN` 页面上的安全开关只作为前端本地解锁按钮，不调用后端接口，不下发 MQTT。

#### `volume.set`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `volume` | number | 是 | 绝对音量，范围 0-100；前端滑块拖动结束后下发 |
| `muted` | boolean | 否 | 是否静音；滑块调节时通常传 false |

#### `volume.up` / `volume.down`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `step` | number | 是 | 单次按钮调节步长，一期默认 5 |
| `volume` | number | 建议 | 前端计算后的目标音量，范围 0-100 |
| `muted` | boolean | 否 | 按钮调节音量时通常传 false |

#### `volume.mute`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `muted` | boolean | 是 | true 静音，false 取消静音 |
| `volume` | number | 建议 | 当前音量值，用于取消静音后恢复显示 |

#### `light.set`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `enabled` | boolean | 是 | true 打开，false 关闭 |
| `brightness` | number | 否 | 亮度 0-100 |
| `mode` | string | 否 | `STEADY` 常亮，`FLASH` 闪烁 |

#### `light.vehicle.set`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `front` | object | 是 | 前灯完整状态 |
| `front.mode` | string | 是 | `OFF` 常关、`ON` 常开、`BREATH` 呼吸灯、`CUSTOM` 自定义亮度 |
| `front.modeCode` | number | 是 | 前灯底层模式码：0 常关、1 常开、2 呼吸灯、3 自定义亮度 |
| `front.customValue` | number | 是 | 前灯自定义亮度，范围 0-100；仅 `mode=CUSTOM` 时有效，其余模式传 0 |
| `rear` | object | 是 | 后灯完整状态 |
| `rear.mode` | string | 是 | `OFF` 常关、`ON` 常开、`BREATH` 呼吸灯、`CUSTOM` 自定义亮度 |
| `rear.modeCode` | number | 是 | 后灯底层模式码：0 常关、1 常开、2 呼吸灯、3 自定义亮度 |
| `rear.customValue` | number | 是 | 后灯自定义亮度，范围 0-100；仅 `mode=CUSTOM` 时有效，其余模式传 0 |

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
| `seq` | 前端 `client.seq` | 终端递增序号 |
| `issuedAt` | 后端时间 | 后端下发时间 |

说明：`protocol`、`version`、`messageType`、`commandId` 不进入一期 MQTT 执行 payload；其中 `commandId` 如需排查问题，可由后端保留在接口响应和日志中。

后端内部处理但不下发 MQTT：

| 内部字段 | 用途 |
|---|---|
| `controlSessionId` | 校验控制权、接管关系 |
| `controlMode` | 校验当前模式下是否允许该动作 |
| `operator` | 鉴权、审计、日志 |
| `confirmToken` | 捕网器等高风险动作的二次确认校验 |
| `policy` | 后端内部风险策略、确认策略、冷却策略 |
| `traceId` | 后端链路日志追踪 |

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
11. 对高风险 action 校验 confirmToken、安全开关、冷却时间等后端策略
12. 生成 commandId、issuedAt
13. 组装简化 MQTT 执行 payload
14. 按设备类型发布到对应设备域 topic，急停发布到 safety/estop topic
15. 通过 WebSocket 给前端回 accepted/rejected
```

后端设备类型构造策略：

| `deviceType` | 典型 action | 后端构造重点 |
|---|---|---|
| `WHEELED_BASE` | `drive.velocity` | 不支持横移时 `linearY=0`，按轮式底盘限速 |
| `QUADRUPED_BASE` | `drive.velocity` | 允许横移时保留 `linearY`，按四足限速 |
| `DUAL_LIGHT_PTZ` | `ptz.move`、`ptz.auto_rotate`、`camera.zoom` | 裁剪云台速度、自动旋转速度、变焦速度 |
| `LAUNCHER` | `payload.safety_switch`、`payload.fire` | 安全开关真实下发，发射按 1 到 6 通道构造 |
| `NET_GUN` | `payload.fire` | 前端本地安全开关解锁后才能点击；后端校验 confirmToken |
| `SEARCHLIGHT` | `light.set` | 裁剪亮度，校验模式 |
| `WARNING_LIGHT` | `light.warning.set` | 按左右警示灯设备分别构造开关参数 |
| `VEHICLE_LIGHT` | `light.vehicle.set` | 前后灯作为一个整体设备，转换为 `/robot_light_ctl` 的 `RobotLightCmd` |

后端组装伪代码：

```text
intent = parse(frontendPayload)
profile = fixedProfile.find(robotId, intent.target.deviceId)
builder = commandBuilderFactory.get(intent.target.deviceType)
params = builder.validateAndBuildParams(intent.action, intent.params, profile.controlProfile)
checkControlSession(intent.controlSessionId, intent.target, intent.action)
checkControlMode(intent.controlMode, latestRobotState, intent.action)
checkInternalPolicy(intent.action, intent.params, currentUser)

mqttPayload = {
  robotId: intent.robotId,
  seq: intent.client.seq,
  target: {
    deviceId: intent.target.deviceId,
    deviceType: intent.target.deviceType
  },
  action: intent.action,
  params: params,
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
  "robotId": "robot-songling-001",               // MQTT topic 按 robotId 分区
  "seq": 1024,                                   // 前端序号
  "target": {
    "deviceId": "base",                          // 本体设备
    "deviceType": "WHEELED_BASE"                 // 设备类型，客户端据此分发
  },
  "action": "drive.velocity",                    // 客户端消费该 action 并调用 ROS2/driver
  "params": {
    "linearX": 0.3,                               // 后端限幅后的前后速度
    "linearY": 0.0,                               // 后端按 WHEELED_BASE 裁剪为 0
    "angularZ": -0.2                              // 后端限幅后的转向角速度
  },
  "issuedAt": "2026-06-03T10:30:00+08:00"        // 后端下发时间
}
```

### 9.3 双光云台：`ptz.move`、`ptz.auto_rotate`、`camera.zoom`

前端发送频率：

| 场景 | 前端行为 |
|---|---|
| 方向键点触 | 按 10Hz 发送少量 `ptz.move` 控制帧 |
| 方向键长按 | 按住期间持续按 10Hz 发送 `ptz.move` 控制帧 |
| 自动旋转按钮 | 点击一次发送 `ptz.auto_rotate enabled=true`，再次点击发送 `enabled=false` |
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
  "robotId": "robot-songling-001",               // 机器人 ID
  "seq": 2101,                                   // 前端序号
  "target": {
    "deviceId": "ptz-dual-001",                  // 云台设备 ID
    "deviceType": "DUAL_LIGHT_PTZ"               // 设备类型，客户端据此分发
  },
  "action": "ptz.move",                          // 客户端消费该 action 并调用云台 ROS2/driver
  "params": {
    "panSpeed": -0.4,                             // 后端限幅后的水平速度
    "tiltSpeed": 0.0                              // 后端限幅后的垂直速度
  },
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

自动旋转走 REST `/commands`，不是 10Hz 连续帧。页面在“左”和“右”按钮之间显示 `自动旋转` 按钮；开启后按钮高亮并显示 `停止旋转`。

前端 REST 请求：

```jsonc
{
  "controlSessionId": "tc_20260603_0002",        // 云台设备控制权
  "target": {
    "scope": "PAYLOAD",                          // 上装设备
    "deviceId": "ptz-dual-001",                  // 双光云台设备 ID
    "deviceType": "DUAL_LIGHT_PTZ"               // 云台类型
  },
  "action": "ptz.auto_rotate",                   // 自动旋转开关
  "params": {
    "enabled": true,                             // true 开启，false 停止
    "panSpeed": 0.3                              // 自动旋转速度，由后端限幅
  },
  "client": {
    "terminalId": "web-client-001",              // 当前终端
    "source": "ptz_auto_rotate_on",              // 控件来源
    "seq": 2102,                                 // 递增序号
    "timestamp": "2026-06-03T10:30:04+08:00"     // 前端发送时间
  }
}
```

后端组装 MQTT：

```jsonc
{
  "robotId": "robot-songling-001",               // 机器人 ID
  "seq": 2102,                                   // 前端序号
  "target": {
    "deviceId": "ptz-dual-001",                  // 云台设备 ID
    "deviceType": "DUAL_LIGHT_PTZ"               // 设备类型
  },
  "action": "ptz.auto_rotate",                   // 客户端消费该 action 并调用云台自动旋转
  "params": {
    "enabled": true,                             // 开启自动旋转
    "panSpeed": 0.3                              // 后端限幅后的旋转速度
  },
  "issuedAt": "2026-06-03T10:30:04+08:00"        // 后端时间
}
```

### 9.4 发射器与捕网枪：`payload.safety_switch`、`payload.fire`

使用场景：

- 发射器打开/关闭真实安全开关，并通过接口下发给客户端。
- 发射器二次确认后按 1 到 6 通道发射。
- 捕网枪页面保留本地安全开关按钮，只用于解锁前端发射按钮，不调用后端接口。
- 捕网枪二次确认后按 1 发通道发射。

发射器和捕网枪动作建议走 REST `/commands`，不走高频 WebSocket。

#### 9.4.1 发射器安全开关

前端 REST 请求：

```jsonc
{
  "controlSessionId": "tc_20260603_0003",        // 发射器设备级或整机控制权
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "launcher-001",                  // 发射器设备 ID
    "deviceType": "LAUNCHER"                     // 发射器类型
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
  "robotId": "robot-songling-001",               // 机器人 ID
  "seq": 3001,                                   // 序号
  "target": {
    "deviceId": "launcher-001",                  // 发射器 ID
    "deviceType": "LAUNCHER"                     // 发射器类型
  },
  "action": "payload.safety_switch",             // 安全开关
  "params": {
    "enabled": true                              // 打开安全开关
  },
  "issuedAt": "2026-06-03T10:30:20+08:00"        // 后端时间
}
```

#### 9.4.2 捕网枪本地安全开关

捕网枪页面上的安全开关不产生后端请求。前端只维护本地状态：

```js
netGunSafety[deviceId] = true
```

当本地安全开关为 `false` 时，捕网枪发射按钮置灰；切换为 `true` 后才允许用户点击发射并进入二次确认。

#### 9.4.3 捕网枪发射

发射前先请求 confirmToken：

```jsonc
{
  "controlSessionId": "tc_20260603_0003",        // 控制权 ID
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "net-gun-001",                   // 捕网枪
    "deviceType": "NET_GUN"                      // 捕网枪类型
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
    "deviceId": "net-gun-001",                   // 捕网枪 ID
    "deviceType": "NET_GUN"                      // 捕网枪类型
  },
  "action": "payload.fire",                      // 捕网器发射
  "params": {
    "channel": 1,                                // 发射通道，一期默认 1
    "confirmToken": "confirm_20260603_abc001"    // 二次确认 token
  },
  "client": {
    "terminalId": "web-client-001",              // 终端
    "source": "net_gun_fire",                    // 捕网枪发射按钮
    "seq": 3101,                                 // 序号
    "timestamp": "2026-06-03T10:30:30+08:00"     // 前端时间
  }
}
```

后端 MQTT：

```jsonc
{
  "robotId": "robot-songling-001",               // 机器人 ID
  "seq": 3101,                                   // 序号
  "target": {
    "deviceId": "net-gun-001",                   // 捕网枪
    "deviceType": "NET_GUN"                      // 捕网枪类型
  },
  "action": "payload.fire",                      // 发射动作
  "params": {
    "channel": 1                                 // 发射通道；confirmToken 已由后端校验，不下发客户端
  },
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
  "robotId": "robot-unitree-001",                // 宇树机器狗 ID
  "seq": 4001,                                   // 序号
  "target": {
    "deviceId": "searchlight-001",               // 探照灯
    "deviceType": "SEARCHLIGHT"                  // 探照灯类型
  },
  "action": "light.set",                         // 设置探照灯
  "params": {
    "enabled": true,                             // 打开
    "brightness": 80,                            // 亮度
    "mode": "STEADY"                             // 常亮
  },
  "issuedAt": "2026-06-03T10:30:40+08:00"        // 后端时间
}
```

### 9.6 车灯光：`light.vehicle.set`

使用场景：

- 松灵四轮机器人绑定车灯光时，控制前灯和后灯。
- 前灯、后灯在页面上各显示一组单选按钮：`常关 | 常开 | 呼吸 | 自定义`。
- 每组只能选中一个模式；只有选择 `自定义` 时才显示亮度滑块。
- 底层车灯接口一次接收前后灯完整状态，所以前端每次都提交 `front + rear`。

前端 REST 请求：

```jsonc
{
  "controlSessionId": "tc_20260603_0005",        // 车灯设备级控制权
  "target": {
    "scope": "PAYLOAD",                          // 上装
    "deviceId": "vehicle-light",                 // 车灯作为一个整体设备
    "deviceType": "VEHICLE_LIGHT"                // 车灯类型
  },
  "action": "light.vehicle.set",                 // 设置前后车灯
  "params": {
    "front": {
      "mode": "CUSTOM",                          // OFF 常关，ON 常开，BREATH 呼吸灯，CUSTOM 自定义亮度
      "modeCode": 3,                             // 0 常关，1 常开，2 呼吸灯，3 自定义亮度
      "customValue": 70                          // 仅 CUSTOM 时有效，范围 0-100
    },
    "rear": {
      "mode": "BREATH",                          // 后灯当前模式
      "modeCode": 2,                             // 后灯底层模式码
      "customValue": 0                           // 非 CUSTOM 模式固定传 0
    }
  },
  "client": {
    "terminalId": "web-client-001",              // 终端
    "source": "vehicle_light_front_custom",      // 控件来源
    "seq": 5001,                                 // 序号
    "timestamp": "2026-06-03T10:30:50+08:00"     // 前端时间
  }
}
```

后端 MQTT：

```jsonc
{
  "robotId": "robot-songling-001",               // 松灵机器人
  "seq": 5001,                                   // 序号
  "target": {
    "deviceId": "vehicle-light",                 // 车灯整体设备
    "deviceType": "VEHICLE_LIGHT"                // 车灯类型
  },
  "action": "light.vehicle.set",                 // 车灯设置
  "params": {
    "op": "publish",                             // Go 客户端底层发布动作
    "topic": "/robot_light_ctl",                 // 机器人侧灯光控制 topic
    "type": "robot_status_core/RobotLightCmd",   // 机器人侧消息类型
    "msg": {
      "front_mode": 3,                           // 前灯自定义亮度
      "front_custom_value": 70,                  // 前灯亮度
      "rear_mode": 2,                            // 后灯呼吸灯
      "rear_custom_value": 0                     // 后灯非 CUSTOM 固定 0
    }
  },
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
- 发射器 `payload.fire` 必须有控制权、confirmToken、真实安全开关和冷却校验。
- 捕网枪 `payload.fire` 必须有控制权和 confirmToken；安全开关只在前端本地解锁按钮，不作为后端校验项。
- 急停使用 `robot/{robotId}/control/safety/estop`，不进入普通命令队列。
- 客户端如何消费控制帧并调用 ROS2，由客户端同事单独抽象实现。

## 12. 一期验收标准

1. 前端能根据 `control-profile` 动态展示不同机器人绑定的上装控制面板。
2. 两个终端不能同时控制同一机器人本体。
3. 一个终端控制本体时，另一个终端可按配置控制云台。
4. 本体和云台点触时，前端能按 10Hz 发送少量控制帧，后端逐帧组装 MQTT。
5. 本体和云台长按时，前端能按 10Hz 持续发送控制帧，后端逐帧组装 MQTT。
6. 发射器没有 confirmToken 或真实安全开关时不能发射；捕网枪没有 confirmToken 或前端本地安全开关未打开时不能点击发射。
7. `NAVIGATION` 或 `ASSISTED` 下手动控制本体时，前端必须先接管成功再发送控制帧。
8. 前端接管请求携带旧 `observedStateSeq` 时，后端返回 `ROBOT_STATE_CHANGED`。
9. 后端能记录命令发布，并通过 `media/client/status` 刷新机器人在线状态、控制模式和设备状态。
