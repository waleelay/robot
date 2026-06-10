# 具身智能装备统一控制协议设计方案

## 0. 文档拆分说明

本总方案用于描述完整设计背景和协议边界。开发落地时优先阅读分期文档：

| 文档 | 用途 |
|---|---|
| [具身智能装备统一控制一期方案](embodied-equipment-control-phase-1.md) | 一期实现范围，覆盖设备注册绑定、控制权、本体/云台遥控、捕网器、急停、WebSocket、MQTT |
| [具身智能装备统一控制二期方案](embodied-equipment-control-phase-2.md) | 二期扩展范围，覆盖机械臂、激光雷达、任务接管、driver 插件化、协议版本和审计回放 |

一期先形成稳定闭环，二期在不推翻一期协议外壳的前提下扩展复杂设备和规模化治理。

## 1. 文档范围

本文档面向具身智能指挥调度平台/具身智能装备集成平台，设计多机器人、多上装、多终端、多厂商同时接入时的统一控制接口、MQTT topic 分区、协议结构、控制权、安全策略和机器人侧适配边界。

本方案覆盖：

- 不同类型机器人本体：双足机器人、四足机器狗、轮式机器狗、轮式 AGV、履带机器人等。
- 不同类型上装设备：双光云台、捕网器、机械臂、激光雷达、喊话器、照明灯、气体传感器等。
- 不同控制终端：Web 指挥台、大屏调度端、移动端、第三方业务系统、任务调度服务。
- 不同厂商设备：松灵机器人、云深处四足机器狗、后续其他厂商机器人与上装。

本文档不设计具体厂商 SDK、串口/CAN/ROS2/私有 HTTP 的底层报文格式。底层协议转换由机器人侧接入客户端或边缘网关的 driver 层完成。

## 2. 设计目标

### 2.1 核心目标

1. 前端、后端、机器人侧统一使用一套控制协议外壳，避免每接入一种设备就新增一套接口。
2. 支持一台机器人携带多个异构上装设备，且同一页面或多个终端可分别控制不同设备。
3. 支持多台机器人并发控制，topic、控制权、状态回传、审计日志互不串扰。
4. 后端保留平台级治理能力，包括鉴权、控制权、能力校验、限速、安全确认、审计和状态聚合。
5. 机器人侧保留厂商协议适配能力，平台不直接耦合厂商 SDK。
6. 协议可版本化演进，兼容已接入设备和未来新设备。

### 2.2 非目标

1. 后端不直接构造厂商二进制协议、CAN 帧、串口十六进制报文、ROS2 原始消息。
2. 前端不按厂商型号硬编码控制参数。
3. MQTT topic 不按页面布局设计，而按机器人、设备和能力域设计。
4. 高风险动作不复用普通按钮指令，必须有独立安全策略。

## 3. 总体架构

```text
Web/移动端/调度系统
  -> Control API / WebSocket
  -> Control Server
       - 用户鉴权
       - 机器人/设备能力查询
       - 控制权租约
       - 指令归一化
       - 参数裁剪
       - 安全策略
       - MQTT 下发
       - 状态聚合与事件推送
  -> EMQX
  -> Robot Edge Client / Gateway
       - MQTT 订阅
       - 指令幂等与乱序丢弃
       - deadman 失联保护
       - driver 路由
       - 厂商 SDK/ROS2/CAN/串口/HTTP 适配
  -> 机器人本体 / 上装设备
```

现有实时视频链路已经使用 `Control Server -> MQTT -> Go 云接入客户端` 的模式。远程控制模块建议作为与 `media` 并列的 `control/teleop` 能力扩展，而不是混入实时视频 session。

推荐业务域划分：

| 业务域 | 职责 |
|---|---|
| `robot` | 机器人注册、在线心跳、设备目录、能力集、厂商和型号 |
| `media` | 实时视频、对讲、抓拍、录像 |
| `teleop` | 本体遥控、上装控制、控制权、安全指令、控制状态 |
| `task` | 自主任务、巡检任务、返航、导航、任务调度 |
| `asset` | 设备档案、型号模板、控制 profile、厂商 driver 元数据 |

## 4. 统一抽象模型

### 4.1 Robot

机器人是控制资源的根节点。

机器人设备必须先在平台注册为 `Robot` 实例，再进入调度和控制流程。注册信息可以来自平台管理后台、资产系统同步、厂商交付导入或机器人侧首次接入后的待确认记录，但正式参与控制前必须完成平台侧确认、组织归属、厂商型号、控制 profile 和设备绑定关系配置。

```json
{
  "robotId": "robot-deep-001",
  "name": "云深处四足机器狗",
  "robotType": "QUADRUPED_DOG",
  "vendor": "DEEPNROBOTICS",
  "model": "X30",
  "orgId": "org001",
  "status": "online",
  "capabilities": ["TELEOP_DRIVE", "HOLONOMIC_DRIVE", "ESTOP", "RETURN_HOME"],
  "controlProfileId": "profile-quadruped-x30-safe"
}
```

`robotType` 用于展示、分类、默认能力模板和 UI 布局，但不能作为唯一控制依据。真正决定能否执行动作的是 `devices[].actions`、`capabilities` 和 `controlProfile`。

### 4.2 Device

Device 表示机器人本体能力或上装设备。本体也作为一个特殊设备建模，通常使用 `deviceId=base`。

上装设备同样必须提前注册，并通过绑定关系挂到某个 `robotId` 下。前端页面展示的本体控制区、右侧上装控制区、传感器配置区和对讲区，都应来自后端设备目录查询结果，而不是前端按机器人型号硬编码。机器人侧心跳可以刷新设备在线状态、运行状态和实时能力，但不应随意创建未审核的可控设备。

```json
{
  "deviceId": "ptz-dual-001",
  "robotId": "robot-deep-001",
  "bindingId": "bind-robot-deep-001-ptz-dual-001",
  "scope": "PAYLOAD",
  "deviceType": "DUAL_LIGHT_PTZ",
  "vendor": "CUSTOM",
  "model": "DL-PTZ-01",
  "registered": true,
  "enabled": true,
  "status": "online",
  "actions": ["ptz.move", "ptz.stop", "ptz.home", "camera.zoom"],
  "riskLevel": "LOW",
  "controlProfile": {
    "maxPanSpeed": 1.0,
    "maxTiltSpeed": 1.0
  }
}
```

设备范围建议枚举：

| `scope` | 说明 |
|---|---|
| `BODY` | 机器人本体，如底盘、腿部、姿态、回充 |
| `PAYLOAD` | 上装执行设备，如云台、机械臂、捕网器、灯光 |
| `SENSOR` | 传感器，如激光雷达、气体检测、声学阵列 |
| `AUDIO` | 对讲、喊话、拾音设备 |
| `SAFETY` | 急停、安全锁、保护区域 |
| `MISSION` | 自主任务、导航任务、巡检任务 |

### 4.3 Capability 与 Action

`capability` 是粗粒度能力，用于前端显示模块和权限策略。`action` 是可执行动作，用于统一指令路由。

示例能力：

| 能力 | 说明 |
|---|---|
| `TELEOP_DRIVE` | 本体速度遥控 |
| `HOLONOMIC_DRIVE` | 支持横移/全向移动 |
| `GAIT_SWITCH` | 支持步态切换 |
| `POSE_CONTROL` | 支持姿态/身高控制 |
| `PTZ_CONTROL` | 云台控制 |
| `CAMERA_ZOOM` | 相机变焦 |
| `NET_GUN` | 捕网枪 |
| `ARM_CONTROL` | 机械臂 |
| `LIDAR_CONTROL` | 激光雷达控制或模式切换 |
| `ESTOP` | 急停 |
| `RETURN_HOME` | 一键返航 |
| `DOCKING` | 回充/退出充电桩 |

示例动作：

| action | 目标设备 | 说明 |
|---|---|---|
| `drive.velocity` | `BODY/base` | 本体速度控制 |
| `drive.stop` | `BODY/base` | 本体停止 |
| `gait.set` | `BODY/base` | 设置四足步态 |
| `pose.set` | `BODY/base` | 姿态、身高、俯仰等 |
| `navigation.return_home` | `BODY/base` | 一键返航 |
| `docking.leave` | `BODY/base` | 退出充电桩 |
| `ptz.move` | `PAYLOAD/ptz` | 云台速度控制 |
| `ptz.home` | `PAYLOAD/ptz` | 云台归中 |
| `camera.zoom` | `PAYLOAD/camera` | 相机变焦 |
| `payload.safety_switch` | `PAYLOAD/launcher` | 发射器真实安全开关 |
| `payload.fire` | `PAYLOAD/net` | 捕网器发射 |
| `arm.joint_velocity` | `PAYLOAD/arm` | 机械臂关节速度 |
| `arm.pose` | `PAYLOAD/arm` | 机械臂末端位姿 |
| `lidar.mode.set` | `SENSOR/lidar` | 激光雷达模式 |
| `safety.estop` | `SAFETY` | 急停 |

### 4.4 Robot 与 Device 的边界

`Robot` 与 `Device` 不建议在领域模型和控制协议中合并。

推荐理解：

```text
Robot = 主装备、载体、调度对象、MQTT 路由根节点
Device = Robot 上的可控/可感知能力单元、动作执行目标
```

例如：

```text
松灵四轮机器人 = Robot
云深处四足机器狗 = Robot
宇树机器狗 = Robot

base 本体 = Device
双光云台 = Device
捕网器 = Device
激光雷达 = Device
机械臂 = Device
语音对讲 = Device
探照灯 = Device
```

职责边界：

| 模型 | 不负责 | 负责 |
|---|---|---|
| `Robot` | 不直接承载具体 action | 组织归属、机器人类型、厂商型号、在线状态、任务调度、MQTT 路由 |
| `Device` | 不作为独立调度主对象 | 设备类型、安装绑定、可控 action、控制 profile、设备状态、风险等级 |

控制寻址必须保留两级：

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

其中：

- `robotId` 决定命令发给哪台机器人和哪个 Go 客户端。
- `target.deviceId` 决定这台机器人上的哪个设备执行动作。

可以合并的边界：

| 层次 | 是否可以合并 | 建议 |
|---|---:|---|
| 前端展示模型 | 可以 | 以 `RobotControlProfile.devices` 嵌套展示，前端看起来是一台机器人带多个控制面板 |
| REST 响应 DTO | 可以 | `GET /control-profile` 返回 robot 基本信息和 devices 数组 |
| 数据库领域模型 | 不建议 | 保留 `robot`、`equipment_device`、`robot_device_binding` |
| 控制协议 | 不建议 | 保留 `robotId + target.deviceId + action` |
| MQTT topic | 不建议 | topic 按 `robotId` 分区，payload 内按 `target.deviceId` 路由 |

如果把 Device 完全合并进 Robot，会带来以下问题：

1. 一台机器人挂多个同类型上装时无法稳定区分，例如两个云台或多个传感器。
2. 上装拆卸、换装、借调到另一台机器人时缺少资产生命周期。
3. 设备级控制权、设备状态、设备故障、设备审计会和整机混在一起。
4. 捕网器、机械臂、云台、本体运动的冲突矩阵难以表达。
5. Go 客户端无法稳定按 `deviceId` 路由到不同 driver。

因此第一版可以在接口响应上把 Device 嵌套到 Robot 中，降低前端理解成本；但后端领域模型、控制协议和 MQTT payload 必须保留 Device 抽象。

## 5. 统一指令协议

### 5.1 前端意图请求

前端发给后端的是控制意图。它只描述用户操作，不携带厂商协议细节。

```json
{
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_20260602_0001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ"
  },
  "action": "ptz.move",
  "params": {
    "panSpeed": -0.4,
    "tiltSpeed": 0.0,
    "durationMs": 100
  },
  "client": {
    "terminalId": "web-client-001",
    "source": "ptz_pad",
    "seq": 2101,
    "timestamp": "2026-06-02T10:30:03+08:00"
  }
}
```

统一字段说明：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `robotId` | 是 | 机器人唯一 ID |
| `controlSessionId` | 高频控制是 | 控制权租约 ID |
| `target.scope` | 是 | 控制目标范围 |
| `target.deviceId` | 是 | 设备 ID，本体固定为 `base` |
| `target.deviceType` | 建议 | 设备类型，便于前端和后端校验 |
| `action` | 是 | 平台统一动作名 |
| `params` | 是 | 动作参数 |
| `client.terminalId` | 是 | 终端实例 ID |
| `client.source` | 建议 | 控件来源，如 joystick/button/ptz_pad |
| `client.seq` | 高频控制是 | 终端内递增序号 |
| `client.timestamp` | 是 | 终端发送时间 |

### 5.2 后端标准命令

后端校验并补齐上下文后，下发标准命令。

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_20260602_000001",
  "traceId": "tr_20260602_abc001",
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_20260602_0001",
  "operator": {
    "userId": "u1001",
    "orgId": "org001",
    "terminalId": "web-client-001"
  },
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ",
    "vendor": "CUSTOM",
    "model": "DL-PTZ-01"
  },
  "action": "ptz.move",
  "params": {
    "panSpeed": -0.4,
    "tiltSpeed": 0.0,
    "durationMs": 100
  },
  "policy": {
    "qosClass": "INTERACTIVE",
    "requiresExclusiveControl": true,
    "requiresConfirm": false,
    "deadmanTimeoutMs": 500,
    "expireAt": "2026-06-02T10:30:04+08:00",
    "riskLevel": "LOW"
  },
  "seq": 2101,
  "issuedAt": "2026-06-02T10:30:03+08:00"
}
```

后端标准命令比前端意图多出：

- `protocol/version`：协议版本。
- `commandId/traceId`：幂等、审计、问题追踪。
- `operator`：操作者和终端上下文。
- `target.vendor/model`：由设备目录补齐。
- `policy`：控制策略、安全策略、过期时间。
- `issuedAt`：后端下发时间。

### 5.3 命令类型分类

| `messageType` | 说明 |
|---|---|
| `command` | 普通控制命令 |
| `heartbeat` | 控制心跳 |
| `ack` | 机器人侧接收确认 |
| `status` | 机器人或设备状态 |
| `event` | 业务事件 |
| `error` | 错误消息 |

## 6. 前端接口设计

### 6.1 设备目录接口

```text
GET /api/control/robots/{robotId}/control-profile
```

返回该机器人已注册、已绑定、当前启用的本体和上装可控能力，用于动态渲染页面。前端设备列表、控制按钮、摇杆、滑块、开关、二次确认弹窗都应基于该接口返回的 `devices` 和 `actions` 构造。

如果某台机器人绑定了双光云台、捕网器、激光雷达和语音对讲，则页面只展示这些设备对应的控制能力；如果另一台机器人绑定了双光云台、机械臂和语音对讲，则页面自动切换为该组合。前端不需要知道“云深处一定有机械臂”或“松灵一定有激光雷达”，只需要消费后端返回的设备目录。

```json
{
  "robotId": "robot-deep-001",
  "robotType": "QUADRUPED_DOG",
  "vendor": "DEEPNROBOTICS",
  "model": "X30",
  "devices": [
    {
      "deviceId": "base",
      "bindingId": "bind-robot-deep-001-base",
      "scope": "BODY",
      "deviceType": "QUADRUPED_BASE",
      "displayName": "机器人本体",
      "enabled": true,
      "actions": ["drive.velocity", "drive.stop", "gait.set", "navigation.return_home", "docking.leave"],
      "controlProfile": {
        "driveMode": "QUADRUPED",
        "supportsStrafe": true,
        "maxLinearX": 0.8,
        "maxLinearY": 0.4,
        "maxAngularZ": 0.6,
        "deadmanTimeoutMs": 500,
        "defaultGait": "TROT"
      }
    },
    {
      "deviceId": "ptz-dual-001",
      "bindingId": "bind-robot-deep-001-ptz-dual-001",
      "scope": "PAYLOAD",
      "deviceType": "DUAL_LIGHT_PTZ",
      "displayName": "双光云台",
      "enabled": true,
      "actions": ["ptz.move", "ptz.stop", "ptz.home", "camera.zoom"],
      "controlProfile": {
        "maxPanSpeed": 1.0,
        "maxTiltSpeed": 1.0,
        "zoomLevels": [1, 2, 4, 8]
      }
    },
    {
      "deviceId": "net-gun-001",
      "bindingId": "bind-robot-deep-001-net-gun-001",
      "scope": "PAYLOAD",
      "deviceType": "NET_GUN",
      "displayName": "捕网枪",
      "enabled": true,
      "actions": ["payload.fire"],
      "riskLevel": "HIGH",
      "controlProfile": {
        "requiresConfirm": true,
        "cooldownMs": 3000
      }
    }
  ]
}
```

前端原则：

1. 根据 `devices[].actions` 渲染控制面板。
2. 根据 `controlProfile` 设置速度上限、按钮状态、滑块范围和确认流程。
3. 不根据厂商型号拼接特殊请求体。
4. 每个终端生成稳定 `terminalId`，每个控制会话维护递增 `seq`。
5. 设备未注册、未绑定、未启用、离线或不具备对应 action 时，不展示或禁用对应控制入口。

### 6.2 控制权接口

```text
POST /api/control/robots/{robotId}/control-sessions/acquire
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/release
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/heartbeat
GET  /api/control/robots/{robotId}/control-sessions/active
```

申请控制权：

```json
{
  "scope": "ROBOT",
  "deviceIds": ["base", "ptz-dual-001"],
  "mode": "EXCLUSIVE",
  "reason": "manual_teleop"
}
```

控制权范围建议：

| 范围 | 说明 |
|---|---|
| `ROBOT` | 锁定整台机器人，适用于本体移动和高风险上装 |
| `DEVICE` | 只锁定指定设备，适用于云台、照明、普通传感器 |
| `ACTION` | 只锁定某类动作，适用于低风险共享控制 |

推荐第一版策略：

- 本体移动、急停、捕网器、机械臂运动：`ROBOT` 或强排他。
- 云台、变焦、灯光：可按 `DEVICE` 排他。
- 激光雷达模式切换：按 `DEVICE` 排他。
- 状态查询、视频观看：不需要控制权。

### 6.3 高频控制通道

Web 端可使用业务 WebSocket 发送高频控制意图：

```text
/ws/control
```

WebSocket 入站消息：

```json
{
  "type": "control.command",
  "payload": {
    "robotId": "robot-deep-001",
    "controlSessionId": "tc_20260602_0001",
    "target": {
      "scope": "BODY",
      "deviceId": "base",
      "deviceType": "QUADRUPED_BASE"
    },
    "action": "drive.velocity",
    "params": {
      "linearX": 0.3,
      "linearY": 0.1,
      "angularZ": -0.2,
      "gait": "TROT"
    },
    "client": {
      "terminalId": "web-client-001",
      "source": "body_joystick",
      "seq": 1024,
      "timestamp": "2026-06-02T10:30:00+08:00"
    }
  }
}
```

低频或高风险动作也可走 HTTP：

```text
POST /api/control/robots/{robotId}/commands
```

### 6.4 点触、长按与发送频率

机器人本体运动和双光云台运动必须同时支持点触和长按，两者在协议语义上应明确区分。

| 控制方式 | 适用场景 | 前端行为 | 后端行为 | 机器人侧行为 |
|---|---|---|---|---|
| 点触 `tap` | 微调一步、云台点动、本体低速短移 | 发送一次带 `durationMs` 的短动作 | 校验后下发一次命令 | 执行指定时长，到期自动停止 |
| 长按 `hold_start/hold_update/hold_end` | 摇杆、本体连续移动、云台连续转动 | 按固定频率持续发送最新速度，松手发送 stop | 限流、补策略、下发最新值 | 只执行最新 seq，超时未更新则停止 |
| 拖拽/摇杆 `stream` | 虚拟摇杆、方向盘、滑块 | 持续发送速度向量 | 可合并或丢弃过期命令 | 按 deadman 保护执行 |

推荐频率：

| 控制对象 | 前端发送频率 | 后端下发频率 | deadman 超时 | 说明 |
|---|---:|---:|---:|---|
| 本体摇杆 `drive.velocity` | 10-20 Hz | 10-20 Hz | 300-500 ms | 只执行最新速度，必须有 `drive.stop` |
| 本体点动 `drive.velocity` + `durationMs` | 单次 | 单次 | 300-500 ms | 适合方向按钮点一下走一小段 |
| 云台长按 `ptz.move` | 10-15 Hz | 10-15 Hz | 500-800 ms | 松手必须发 `ptz.stop` |
| 云台点动 `ptz.move` + `durationMs` | 单次 | 单次 | 500-800 ms | 适合上/下/左/右微调 |
| 变焦长按 `camera.zoom` | 5-10 Hz | 5-10 Hz | 800-1000 ms | 松手发 `camera.zoom_stop` 或 `camera.zoom` speed=0 |

前端长按事件建议：

```json
{
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_20260602_0001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ"
  },
  "action": "ptz.move",
  "params": {
    "panSpeed": -0.4,
    "tiltSpeed": 0.0,
    "controlMode": "hold_update"
  },
  "client": {
    "terminalId": "web-client-001",
    "source": "ptz_left_button",
    "seq": 2108,
    "timestamp": "2026-06-02T10:30:03+08:00"
  }
}
```

松手停止事件：

```json
{
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_20260602_0001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ"
  },
  "action": "ptz.stop",
  "params": {
    "controlMode": "hold_end"
  },
  "client": {
    "terminalId": "web-client-001",
    "source": "ptz_left_button",
    "seq": 2109,
    "timestamp": "2026-06-02T10:30:04+08:00"
  }
}
```

点触事件：

```json
{
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_20260602_0001",
  "target": {
    "scope": "BODY",
    "deviceId": "base",
    "deviceType": "QUADRUPED_BASE"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.15,
    "linearY": 0.0,
    "angularZ": 0.0,
    "durationMs": 150,
    "controlMode": "tap"
  },
  "client": {
    "terminalId": "web-client-001",
    "source": "body_forward_button",
    "seq": 1101,
    "timestamp": "2026-06-02T10:30:03+08:00"
  }
}
```

后端频率控制规则：

1. 同一 `robotId + target.deviceId + action + terminalId` 维度维护最新 `seq`。
2. 对高频动作允许合并，优先下发最新命令，丢弃过期命令。
3. 对 `hold_update` 做频率上限，例如本体不超过 20 Hz，云台不超过 15 Hz。
4. `hold_end`、`drive.stop`、`ptz.stop` 不能被普通限流丢弃。
5. 如果前端异常断开，后端控制会话超时后应下发 stop；机器人侧 deadman 也必须本地兜底。

### 6.5 前后端 REST 接口明细

#### 6.5.1 通用请求头

当前调试阶段可使用 Mock 用户头，生产阶段由登录态、网关 Token 或 SSO 解析为同样的用户上下文。

| Header | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `X-User-Id` | string | 是 | 当前操作用户 ID |
| `X-Org-Id` | string | 是 | 当前组织 ID，用于设备隔离 |
| `X-Roles` | string | 是 | 逗号分隔角色，如 `EQUIPMENT_VIEWER,EQUIPMENT_OPERATOR` |
| `X-Client-Id` | string | 是 | 当前浏览器标签页或终端实例 ID |
| `Content-Type` | string | POST 是 | 固定为 `application/json` |

#### 6.5.2 查询机器人列表

```text
GET /api/control/robots
```

用途：查询当前用户可见的机器人摘要列表，用于左侧/地图/调度列表展示。

响应示例：

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
    "battery": 86,
    "lastHeartbeatAt": "2026-06-03T10:30:00+08:00"
  }
]
```

响应字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `robotId` | string | 是 | 机器人唯一 ID |
| `name` | string | 是 | 展示名称 |
| `robotType` | string | 是 | 机器人类型，如 `WHEELED_AGV`、`QUADRUPED_DOG` |
| `vendor` | string | 是 | 厂商，如 `SONGLING`、`DEEPNROBOTICS`、`UNITREE` |
| `model` | string | 是 | 型号 |
| `onlineStatus` | string | 是 | `online` / `offline` |
| `controlStatus` | string | 是 | `idle` / `locked` / `busy` / `disabled` / `fault` |
| `battery` | number | 否 | 电量百分比 |
| `lastHeartbeatAt` | datetime | 否 | 最近心跳时间 |

#### 6.5.3 查询控制能力 Profile

```text
GET /api/control/robots/{robotId}/control-profile
```

用途：查询机器人本体和已绑定上装的有效控制能力。前端必须根据该接口返回值渲染控制区。

路径参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `robotId` | string | 是 | 机器人唯一 ID |

响应示例：

```json
{
  "robotId": "robot-deep-001",
  "robotType": "QUADRUPED_DOG",
  "vendor": "DEEPNROBOTICS",
  "model": "X30",
  "onlineStatus": "online",
  "devices": [
    {
      "deviceId": "base",
      "bindingId": "bind-robot-deep-001-base",
      "scope": "BODY",
      "deviceType": "QUADRUPED_BASE",
      "displayName": "机器人本体",
      "onlineStatus": "online",
      "controlStatus": "idle",
      "enabled": true,
      "actions": ["drive.velocity", "drive.stop", "navigation.return_home"],
      "controlProfile": {
        "driveMode": "QUADRUPED",
        "supportsStrafe": true,
        "maxLinearX": 0.8,
        "maxLinearY": 0.4,
        "maxAngularZ": 0.6,
        "deadmanTimeoutMs": 500
      }
    }
  ]
}
```

响应字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `robotId` | string | 是 | 机器人唯一 ID |
| `robotType` | string | 是 | 机器人类型 |
| `vendor` | string | 是 | 机器人厂商 |
| `model` | string | 是 | 机器人型号 |
| `onlineStatus` | string | 是 | 机器人在线状态 |
| `devices` | array | 是 | 当前已绑定且可展示的设备列表 |
| `devices[].deviceId` | string | 是 | 设备 ID，本体固定为 `base` |
| `devices[].bindingId` | string | 是 | 机器人与设备绑定关系 ID |
| `devices[].scope` | string | 是 | `BODY` / `PAYLOAD` / `SENSOR` / `AUDIO` / `SAFETY` |
| `devices[].deviceType` | string | 是 | 设备类型，如 `DUAL_LIGHT_PTZ` |
| `devices[].displayName` | string | 是 | 前端展示名称 |
| `devices[].onlineStatus` | string | 是 | `online` / `offline` |
| `devices[].controlStatus` | string | 是 | `idle` / `locked` / `busy` / `disabled` / `fault` |
| `devices[].enabled` | boolean | 是 | 平台是否启用该设备 |
| `devices[].actions` | array[string] | 是 | 支持动作列表 |
| `devices[].controlProfile` | object | 是 | 参数上限、控制模式、安全策略等有效配置 |

#### 6.5.4 申请控制权

```text
POST /api/control/robots/{robotId}/control-sessions/acquire
```

请求示例：

```json
{
  "scope": "DEVICE",
  "deviceIds": ["ptz-dual-001"],
  "actions": ["ptz.move", "ptz.stop"],
  "mode": "EXCLUSIVE",
  "reason": "manual_teleop",
  "ttlSeconds": 30
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `scope` | string | 是 | `ROBOT` / `DEVICE` / `ACTION` |
| `deviceIds` | array[string] | 是 | 控制目标设备，整机控制至少包含 `base` |
| `actions` | array[string] | 否 | 希望控制的动作；为空表示按 scope 锁定 |
| `mode` | string | 是 | 第一版固定 `EXCLUSIVE` |
| `reason` | string | 否 | 申请原因，如 `manual_teleop` |
| `ttlSeconds` | number | 否 | 租约秒数，默认 30 |

成功响应：

```json
{
  "controlSessionId": "tc_20260603_0001",
  "robotId": "robot-deep-001",
  "ownerUserId": "u1001",
  "ownerClientId": "web-client-001",
  "scope": "DEVICE",
  "deviceIds": ["ptz-dual-001"],
  "actions": ["ptz.move", "ptz.stop"],
  "mode": "EXCLUSIVE",
  "status": "ACTIVE",
  "leaseExpireAt": "2026-06-03T10:30:30+08:00"
}
```

冲突响应：

```json
{
  "code": "CONTROL_LOCKED",
  "message": "target device is controlled by another terminal",
  "holder": {
    "userId": "u2001",
    "clientId": "web-client-009",
    "controlSessionId": "tc_20260603_0009",
    "scope": "DEVICE",
    "deviceIds": ["ptz-dual-001"],
    "leaseExpireAt": "2026-06-03T10:30:20+08:00"
  }
}
```

#### 6.5.5 续租控制权

```text
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/heartbeat
```

请求示例：

```json
{
  "ttlSeconds": 30,
  "clientSeq": 1024,
  "timestamp": "2026-06-03T10:30:10+08:00"
}
```

响应示例：

```json
{
  "controlSessionId": "tc_20260603_0001",
  "status": "ACTIVE",
  "leaseExpireAt": "2026-06-03T10:30:40+08:00"
}
```

#### 6.5.6 释放控制权

```text
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/release
```

请求示例：

```json
{
  "reason": "user_release",
  "stopActiveMotion": true
}
```

响应示例：

```json
{
  "controlSessionId": "tc_20260603_0001",
  "status": "RELEASED",
  "releasedAt": "2026-06-03T10:30:20+08:00"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `reason` | string | 否 | 释放原因 |
| `stopActiveMotion` | boolean | 否 | 是否同时下发停止命令，默认 true |

#### 6.5.7 查询当前控制权

```text
GET /api/control/robots/{robotId}/control-sessions/active
```

响应示例：

```json
[
  {
    "controlSessionId": "tc_20260603_0001",
    "ownerUserId": "u1001",
    "ownerClientId": "web-client-001",
    "scope": "DEVICE",
    "deviceIds": ["ptz-dual-001"],
    "actions": ["ptz.move"],
    "status": "ACTIVE",
    "leaseExpireAt": "2026-06-03T10:30:40+08:00"
  }
]
```

#### 6.5.8 申请高风险动作确认 Token

```text
POST /api/control/robots/{robotId}/commands/confirm-token
```

请求示例：

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

响应示例：

```json
{
  "confirmToken": "confirm_20260603_abc001",
  "expiresAt": "2026-06-03T10:31:00+08:00",
  "robotId": "robot-deep-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-gun-001"
  },
  "action": "payload.fire"
}
```

#### 6.5.9 发送低频控制命令

```text
POST /api/control/robots/{robotId}/commands
```

用途：发送低频动作或高风险动作。本体摇杆、云台长按等高频动作优先走 WebSocket。

请求示例：

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
    "source": "net_gun_fire",
    "seq": 3101,
    "timestamp": "2026-06-03T10:30:30+08:00"
  }
}
```

成功响应：

```json
{
  "commandId": "cmd_20260603_0001",
  "traceId": "tr_20260603_0001",
  "status": "PUBLISHED",
  "robotId": "robot-deep-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-gun-001"
  },
  "action": "payload.fire",
  "issuedAt": "2026-06-03T10:30:30+08:00"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `controlSessionId` | string | 需要控制权时必填 | 控制权租约 ID |
| `target.scope` | string | 是 | 控制目标范围 |
| `target.deviceId` | string | 是 | 控制目标设备 |
| `target.deviceType` | string | 建议 | 设备类型，后端以绑定关系为准 |
| `action` | string | 是 | 动作名 |
| `params` | object | 是 | 动作参数 |
| `client.terminalId` | string | 是 | 终端实例 ID |
| `client.source` | string | 否 | 控件来源 |
| `client.seq` | number | 是 | 终端内递增序号 |
| `client.timestamp` | datetime | 是 | 终端发送时间 |

#### 6.5.10 通用错误响应

```json
{
  "code": "VALIDATION_PARAM_OUT_OF_RANGE",
  "message": "linearX exceeds maxLinearX",
  "traceId": "tr_20260603_0001",
  "details": {
    "field": "params.linearX",
    "max": 0.8,
    "actual": 1.2
  }
}
```

常用错误码：

| 错误码 | HTTP 状态 | 说明 |
|---|---:|---|
| `AUTH_REQUIRED` | 401 | 未提供用户身份 |
| `AUTH_FORBIDDEN` | 403 | 无操作权限 |
| `ROBOT_NOT_FOUND` | 404 | 机器人不存在或不可见 |
| `DEVICE_NOT_BOUND` | 400 | 设备未绑定到该机器人 |
| `DEVICE_OFFLINE` | 409 | 设备离线 |
| `CONTROL_LOCKED` | 409 | 控制权冲突 |
| `CONTROL_SESSION_EXPIRED` | 409 | 控制会话已过期 |
| `UNSUPPORTED_ACTION` | 400 | 设备不支持该动作 |
| `VALIDATION_PARAM_MISSING` | 400 | 缺少必填参数 |
| `VALIDATION_PARAM_OUT_OF_RANGE` | 400 | 参数超出 profile 范围 |
| `SAFETY_CONFIRM_REQUIRED` | 400 | 高风险动作缺少确认 |
| `SAFETY_CONFIRM_EXPIRED` | 400 | 确认 token 过期或已使用 |
| `SAFETY_SWITCH_OFF` | 409 | 安全开关未打开 |
| `COMMAND_PUBLISH_FAILED` | 500 | MQTT 下发失败 |

### 6.6 前后端 WebSocket 消息明细

连接地址：

```text
/ws/control
```

#### 6.6.1 前端发送控制命令

```json
{
  "type": "control.command",
  "requestId": "req_20260603_0001",
  "payload": {
    "robotId": "robot-deep-001",
    "controlSessionId": "tc_20260603_0001",
    "target": {
      "scope": "BODY",
      "deviceId": "base",
      "deviceType": "QUADRUPED_BASE"
    },
    "action": "drive.velocity",
    "params": {
      "linearX": 0.3,
      "linearY": 0.1,
      "angularZ": -0.2,
      "controlMode": "hold_update"
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

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `type` | string | 是 | 固定 `control.command` |
| `requestId` | string | 是 | 前端请求 ID，用于匹配即时响应 |
| `payload` | object | 是 | 与 REST `POST /commands` 请求体一致 |

#### 6.6.2 后端即时响应

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

如果校验失败：

```json
{
  "type": "control.command.rejected",
  "requestId": "req_20260603_0001",
  "payload": {
    "code": "CONTROL_SESSION_EXPIRED",
    "message": "control session expired",
    "traceId": "tr_20260603_0001"
  }
}
```

#### 6.6.3 后端推送机器人状态

一期不单独推送控制确认、控制状态或控制错误事件。后端消费 `robot/{robotId}/media/client/status` 后，统一向前端推送 `robot.state`。

```json
{
  "type": "robot.state",
  "payload": {
    "robotId": "robot-deep-001",
    "onlineStatus": "online",
    "controlMode": "MANUAL",
    "stateSeq": 1024,
    "missionStatus": "IDLE",
    "navigationStatus": "IDLE",
    "estopActive": false,
    "devices": [
      {
        "deviceId": "base",
        "deviceType": "QUADRUPED_BASE",
        "onlineStatus": "online",
        "healthStatus": "normal",
        "controlStatus": "idle"
      }
    ],
    "timestamp": "2026-06-03T10:30:00+08:00"
  }
}
```

### 6.7 第一版 Action 参数明细

#### 6.7.1 `drive.velocity`

用途：机器人本体速度控制，支持点触和长按。

适用设备：`scope=BODY`、`deviceId=base`。

参数：

| 字段 | 类型 | 必填 | 范围/枚举 | 说明 |
|---|---|---:|---|---|
| `linearX` | number | 是 | `-maxLinearX` 到 `maxLinearX` | 前后速度，正数向前 |
| `linearY` | number | 否 | `-maxLinearY` 到 `maxLinearY` | 左右横移；不支持横移时后端裁剪为 0 |
| `angularZ` | number | 是 | `-maxAngularZ` 到 `maxAngularZ` | 转向角速度，正负方向由平台约定 |
| `controlMode` | string | 是 | `tap` / `hold_update` / `stream` | 控制模式 |
| `durationMs` | number | `tap` 必填 | 50-1000 | 点触执行时长 |
| `speedLevel` | string | 否 | `LOW` / `NORMAL` / `HIGH` | 速度档位 |
| `gait` | string | 四足可选 | 由 profile 定义 | 四足步态，如 `WALK`、`TROT` |
| `bodyHeight` | number | 否 | 由 profile 定义 | 四足身高偏移 |

校验规则：

- `controlMode=tap` 时必须有 `durationMs`。
- `controlMode=hold_update` 或 `stream` 时不应依赖 `durationMs`，必须依赖 stop 或 deadman 停止。
- 不支持横移的机器人，`linearY` 必须为 0 或由后端裁剪为 0。

#### 6.7.2 `drive.stop`

用途：停止机器人本体运动。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `controlMode` | string | 否 | 通常为 `hold_end` |
| `reason` | string | 否 | 停止原因，如 `user_release`、`deadman` |

处理要求：

- `drive.stop` 不能被后端限流丢弃。
- Go 客户端必须幂等处理，多次 stop 不应产生副作用。

#### 6.7.3 `ptz.move`

用途：双光云台方向控制，支持点动和长按。

适用设备：`scope=PAYLOAD`、`deviceType=DUAL_LIGHT_PTZ`。

参数：

| 字段 | 类型 | 必填 | 范围/枚举 | 说明 |
|---|---|---:|---|---|
| `panSpeed` | number | 是 | `-maxPanSpeed` 到 `maxPanSpeed` | 水平方向速度，正负方向由平台约定 |
| `tiltSpeed` | number | 是 | `-maxTiltSpeed` 到 `maxTiltSpeed` | 垂直方向速度 |
| `controlMode` | string | 是 | `tap` / `hold_update` / `stream` | 控制模式 |
| `durationMs` | number | `tap` 必填 | 50-1000 | 点动时长 |

校验规则：

- `panSpeed` 和 `tiltSpeed` 不能同时长期为 0；停止应使用 `ptz.stop`。
- 长按期间前端按 10-15 Hz 发送 `hold_update`。

#### 6.7.4 `ptz.stop`

用途：停止云台运动。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `controlMode` | string | 否 | 通常为 `hold_end` |
| `reason` | string | 否 | 停止原因 |

处理要求：

- `ptz.stop` 不能被后端限流丢弃。
- Go 客户端必须停止对应 `deviceId` 的云台运动。

#### 6.7.5 `ptz.home`

用途：云台归中。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `speed` | number | 否 | 归中速度，默认由 profile 决定 |

#### 6.7.6 `camera.zoom`

用途：相机变焦。

参数：

| 字段 | 类型 | 必填 | 范围/枚举 | 说明 |
|---|---|---:|---|---|
| `direction` | string | 是 | `IN` / `OUT` | 放大或缩小 |
| `speed` | number | 否 | 0-1 | 变焦速度 |
| `controlMode` | string | 是 | `tap` / `hold_update` / `hold_end` | 控制模式 |
| `durationMs` | number | `tap` 必填 | 50-1000 | 点触变焦时长 |

#### 6.7.7 `payload.safety_switch`

用途：设置发射器等具备真实安全锁的上装安全开关。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `enabled` | boolean | 是 | true 表示打开安全开关 |

#### 6.7.8 `payload.fire`

用途：发射器或捕网枪发射。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `channel` | number | 是 | 发射通道，第一版默认 1 |
| `confirmToken` | string | 是 | 高风险动作确认 token |

校验规则：

- 必须持有满足策略的控制权。
- 必须有未过期且未使用的 `confirmToken`。
- 发射器必须校验真实安全开关状态和冷却时间。
- 捕网枪一期页面安全开关只作为前端本地按钮解锁，不作为后端下发动作。
- Go 客户端仍应按真实设备能力做本地安全、冷却和设备状态校验。

#### 6.7.9 `navigation.return_home`

用途：机器人一键返航。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `mode` | string | 否 | `AUTO` / `MANUAL_CONFIRM`，默认 `AUTO` |
| `confirmToken` | string | 高风险策略要求时必填 | 二次确认 token |

#### 6.7.10 `docking.leave`

用途：退出充电桩。

参数：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `confirmToken` | string | 策略要求时必填 | 二次确认 token |
| `safeDistanceM` | number | 否 | 退出后安全距离，默认由 profile 决定 |

## 7. MQTT Topic 设计

### 7.1 设计原则

1. 顶层必须按 `robotId` 分区，确保一台机器人只消费自己的命令。
2. 统一控制命令 topic，靠 payload 中的 `target` 和 `action` 路由到设备 driver。
3. 高风险安全指令保留独立 topic，避免被普通命令队列阻塞。
4. 第一版不单独设计控制接收确认、执行状态、错误或注册状态 topic，客户端状态统一通过 `media/client/status` 上报。
5. 不按前端页面区域设计 topic，例如不要出现 `right-panel/ptz` 或 `bottom-control`。

### 7.2 推荐 Topic

第一版推荐：

| Topic | 方向 | 说明 |
|---|---|---|
| `robot/{robotId}/control/body/command` | 后端 -> 机器人 | 机器人本体控制，前后、转向、左移右移 |
| `robot/{robotId}/control/ptz/command` | 后端 -> 机器人 | 双光云台控制 |
| `robot/{robotId}/control/audio/command` | 后端 -> 机器人 | 客户端音量控制 |
| `robot/{robotId}/control/launcher/command` | 后端 -> 机器人 | 发射器控制 |
| `robot/{robotId}/control/net-gun/command` | 后端 -> 机器人 | 捕网枪控制 |
| `robot/{robotId}/control/warning-light/command` | 后端 -> 机器人 | 警示灯控制 |
| `robot/{robotId}/control/vehicle-light/command` | 后端 -> 机器人 | 车灯光控制 |
| `robot/{robotId}/control/lidar/command` | 后端 -> 机器人 | 雷达控制 |
| `robot/{robotId}/control/safety/estop` | 后端 -> 机器人 | 急停，高优先级 |
| `robot/{robotId}/media/client/status` | 机器人 -> 后端 | 统一客户端状态上报，包含在线状态、控制模式、任务状态和设备状态 |

第一版不再拆分控制保活、接收确认、执行状态、错误或注册状态 topic。

Go 客户端可订阅 `robot/{robotId}/control/#`，topic 只区分设备大类，具体设备按 payload 中的 `target.deviceId/action` 分发。

### 7.3 QoS 建议

| 消息类型 | QoS | 说明 |
|---|---:|---|
| 高频速度指令 `drive.velocity` | 0 或 1 | 只执行最新值，客户端按 `seq` 丢弃旧包 |
| 停止 `drive.stop` | 1 | 至少送达一次，客户端幂等处理 |
| 急停 `safety.estop` | 1 或 2 | 高优先级，独立 topic，客户端幂等 |
| 云台连续移动 | 0 或 1 | 只执行最新值，松手必须发 `ptz.stop` |
| 捕网器发射 | 1 | 必须幂等，必须确认 token 和冷却时间 |
| 状态上报 | 1 | 至少送达一次，后端幂等入库 |
| 在线心跳 | 1 | 用于设备在线判定和能力刷新 |

运动安全不能只依赖 MQTT QoS。机器人侧必须实现本地 deadman 超时保护。

### 7.4 后端与 Go 客户端 MQTT 消息明细

#### 7.4.1 设备域控制命令

方向：后端 -> Go 客户端。

用途：下发普通控制命令，包括本体运动、云台、音量、发射器、捕网枪、警示灯、车灯、雷达等。不同设备域使用不同 topic，但 payload 结构保持一致。

示例 topic：

```text
robot/{robotId}/control/body/command
robot/{robotId}/control/ptz/command
robot/{robotId}/control/audio/command
robot/{robotId}/control/launcher/command
robot/{robotId}/control/net-gun/command
robot/{robotId}/control/warning-light/command
robot/{robotId}/control/vehicle-light/command
robot/{robotId}/control/lidar/command
```

第一版 MQTT 下发的是客户端执行指令。控制权、操作者、确认令牌、控制模式、风险策略等平台治理信息由后端完成校验和审计，不作为客户端执行协议字段。

Payload 示例：

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_20260603_0001",
  "robotId": "robot-deep-001",
  "seq": 1024,
  "target": {
    "deviceId": "base",
    "deviceType": "QUADRUPED_BASE"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.3,
    "linearY": 0.1,
    "angularZ": -0.2
  },
  "issuedAt": "2026-06-03T10:30:00+08:00"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `protocol` | string | 是 | 固定 `embodied-control` |
| `version` | string | 是 | 协议版本，第一版 `1.0` |
| `messageType` | string | 是 | 固定 `command` |
| `commandId` | string | 是 | 命令 ID，用于 ACK、日志和排查问题 |
| `robotId` | string | 是 | 机器人 ID，必须与客户端自身 `ROBOT_ID` 一致 |
| `target.deviceId` | string | 是 | 目标设备 |
| `target.deviceType` | string | 是 | 设备类型 |
| `action` | string | 是 | 平台动作名 |
| `params` | object | 是 | 后端校验、限幅后的动作参数 |
| `seq` | number | 建议 | 前端递增序号，客户端可用于日志和观察丢帧/乱序 |
| `issuedAt` | datetime | 是 | 后端下发时间 |

Go 客户端处理要求：

1. `robotId` 不匹配时丢弃并记录日志。
2. `version` 不支持时回 `UNSUPPORTED_PROTOCOL_VERSION`。
3. `commandId` 可用于 ACK、日志和幂等观察。
4. 按 `target.deviceType + action` 分发给对应设备处理逻辑。
5. 找不到 driver 或 action 不支持时回 `UNSUPPORTED`。
6. `operator`、`controlSessionId`、`policy`、`confirmToken` 等字段不应作为客户端执行依赖。

#### 7.4.2 急停命令 `robot/{robotId}/control/safety/estop`

方向：后端 -> Go 客户端。

用途：最高优先级急停。Go 客户端收到后应优先停止本体和高风险运动设备。

Payload 示例：

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_estop_20260603_0001",
  "robotId": "robot-deep-001",
  "target": {
    "deviceId": "safety",
    "deviceType": "ESTOP"
  },
  "action": "safety.estop",
  "params": {
    "reason": "manual_estop"
  },
  "issuedAt": "2026-06-03T10:30:00+08:00"
}
```

处理要求：

- 不等待普通命令队列。
- 幂等执行，多次收到同一 `commandId` 不重复触发副作用。
- 急停生效状态通过下一次 `media/client/status` 上报体现，例如 `estopActive=true`。

#### 7.4.3 统一客户端状态 `robot/{robotId}/media/client/status`

方向：Go 客户端 -> 后端。

用途：统一上报客户端在线状态、控制模式、任务状态、摄像头状态和装备设备状态。前端实时状态由后端消费该 topic 后通过 WebSocket 推送 `robot.state`。

Payload 示例：

```json
{
  "robotId": "robot-deep-001",
  "clientId": "robot-client-deep-001",
  "clientVersion": "sim-1.0.0",
  "name": "云深处四足机器狗",
  "type": "四足机器人",
  "status": "online",
  "onlineStatus": "online",
  "battery": 78,
  "controlMode": "MANUAL",
  "stateSeq": 1024,
  "missionStatus": "IDLE",
  "navigationStatus": "IDLE",
  "controlOwner": null,
  "estopActive": false,
  "cameras": [
    {
      "cameraId": "camera01",
      "deviceId": "ptz-dual-001",
      "name": "前向双光云台",
      "groupType": "dual_gimbal",
      "channel": "visible",
      "quality": "hd",
      "status": "online"
    }
  ],
  "devices": [
    {
      "deviceId": "base",
      "scope": "BODY",
      "deviceType": "QUADRUPED_BASE",
      "onlineStatus": "online",
      "healthStatus": "normal",
      "controlStatus": "idle",
      "supportedActions": ["drive.velocity"]
    },
    {
      "deviceId": "ptz-dual-001",
      "scope": "PAYLOAD",
      "deviceType": "DUAL_LIGHT_PTZ",
      "onlineStatus": "online",
      "healthStatus": "normal",
      "controlStatus": "idle",
      "supportedActions": ["ptz.move", "camera.zoom"]
    }
  ],
  "timestamp": "2026-06-03T10:30:00+08:00"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `clientId` | string | 是 | Go 客户端实例 ID |
| `clientVersion` | string | 是 | 客户端版本 |
| `status` | string | 是 | 媒体模块已有在线状态字段 |
| `onlineStatus` | string | 是 | 机器人在线状态 |
| `controlMode` | string | 是 | `MANUAL` / `ASSISTED` / `NAVIGATION` |
| `stateSeq` | number | 是 | 客户端状态递增序号，前端接管时携带 |
| `missionStatus` | string | 否 | 任务状态 |
| `navigationStatus` | string | 否 | 导航状态 |
| `controlOwner` | object/null | 否 | 当前控制占用者 |
| `estopActive` | boolean | 是 | 急停是否生效 |
| `battery` | number | 否 | 电量 |
| `cameras` | array | 否 | 媒体摄像头状态，沿用已有媒体状态结构 |
| `devices` | array | 是 | 客户端发现的设备状态 |
| `devices[].deviceId` | string | 是 | 设备 ID，必须能与平台绑定设备匹配 |
| `devices[].onlineStatus` | string | 是 | `online` / `offline` |
| `devices[].healthStatus` | string | 是 | `normal` / `warning` / `error` |
| `devices[].controlStatus` | string | 是 | `idle` / `busy` / `locked` / `disabled` / `fault` |
| `devices[].supportedActions` | array[string] | 否 | 客户端当前支持动作，用于与平台档案比对 |

## 8. 控制权与多终端协同

### 8.1 控制会话模型

```json
{
  "controlSessionId": "tc_20260602_0001",
  "robotId": "robot-deep-001",
  "ownerUserId": "u1001",
  "ownerTerminalId": "web-client-001",
  "scope": "DEVICE",
  "deviceIds": ["ptz-dual-001"],
  "mode": "EXCLUSIVE",
  "status": "ACTIVE",
  "leaseExpireAt": "2026-06-02T10:30:30+08:00",
  "createdAt": "2026-06-02T10:30:00+08:00"
}
```

### 8.2 多终端规则

同一台机器人可能同时被多个终端观看和部分控制。建议规则：

1. 视频观看可多人共享。
2. 本体移动同一时间只允许一个控制者。
3. 高风险上装与本体移动互斥，避免边移动边发射或边移动边大幅机械臂动作。
4. 云台可按设备独占，允许 A 控本体、B 控云台，但平台可配置是否允许。
5. 机械臂默认要求整机或设备强独占。
6. 急停不需要持有控制权，具备权限的终端均可触发。
7. 第三方调度系统和人工遥控冲突时，以安全策略配置优先级。

### 8.3 控制权冲突响应

```json
{
  "code": "CONTROL_LOCKED",
  "message": "robot device is controlled by another terminal",
  "holder": {
    "userId": "u1001",
    "terminalId": "web-client-001",
    "scope": "DEVICE",
    "deviceIds": ["ptz-dual-001"],
    "leaseExpireAt": "2026-06-02T10:30:30+08:00"
  }
}
```

## 9. 参数构造规则

### 9.1 统一构造流程

```text
前端控件事件
  -> 按 target/action 生成控制意图
  -> 后端校验控制权
  -> 查询 robot/device/controlProfile
  -> 校验 action 是否支持
  -> 参数 schema 校验
  -> 参数裁剪和默认值补齐
  -> 安全策略检查
  -> 生成标准命令
  -> MQTT 下发
  -> 客户端通过 media/client/status 上报状态
  -> 后端通过 WebSocket 推送 robot.state 给前端
```

### 9.2 松灵四轮机器人本体

前端意图：

```json
{
  "robotId": "robot-songling-001",
  "controlSessionId": "tc_001",
  "target": {
    "scope": "BODY",
    "deviceId": "base",
    "deviceType": "WHEELED_BASE"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.4,
    "linearY": 0.0,
    "angularZ": -0.2
  },
  "client": {
    "terminalId": "web-client-001",
    "source": "body_joystick",
    "seq": 1001,
    "timestamp": "2026-06-02T10:30:00+08:00"
  }
}
```

后端标准命令：

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_001",
  "robotId": "robot-songling-001",
  "controlSessionId": "tc_001",
  "target": {
    "scope": "BODY",
    "deviceId": "base",
    "deviceType": "WHEELED_BASE",
    "vendor": "SONGLING",
    "model": "SCOUT"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.4,
    "linearY": 0.0,
    "angularZ": -0.2,
    "driveMode": "DIFFERENTIAL"
  },
  "policy": {
    "qosClass": "INTERACTIVE",
    "requiresExclusiveControl": true,
    "deadmanTimeoutMs": 500,
    "riskLevel": "MEDIUM"
  },
  "seq": 1001,
  "issuedAt": "2026-06-02T10:30:00+08:00"
}
```

如果该车型不支持横移，后端应将 `linearY` 裁剪为 `0`，并可在响应或状态事件中返回参数裁剪信息。

### 9.3 云深处四足机器狗本体

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_002",
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_002",
  "target": {
    "scope": "BODY",
    "deviceId": "base",
    "deviceType": "QUADRUPED_BASE",
    "vendor": "DEEPNROBOTICS",
    "model": "X30"
  },
  "action": "drive.velocity",
  "params": {
    "linearX": 0.25,
    "linearY": 0.1,
    "angularZ": 0.15,
    "gait": "TROT",
    "bodyHeight": 0.0,
    "stabilityMode": "SAFE"
  },
  "policy": {
    "qosClass": "INTERACTIVE",
    "requiresExclusiveControl": true,
    "deadmanTimeoutMs": 500,
    "riskLevel": "MEDIUM"
  },
  "seq": 884,
  "issuedAt": "2026-06-02T10:30:00+08:00"
}
```

四足机器狗支持的 `gait`、`bodyHeight`、`stabilityMode` 由 `controlProfile` 决定。前端显示选择项，后端二次校验。

### 9.4 双光云台

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_003",
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_003",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ",
    "vendor": "CUSTOM",
    "model": "DL-PTZ-01"
  },
  "action": "ptz.move",
  "params": {
    "panSpeed": -0.4,
    "tiltSpeed": 0.0,
    "durationMs": 100
  },
  "policy": {
    "qosClass": "INTERACTIVE",
    "requiresExclusiveControl": true,
    "deadmanTimeoutMs": 800,
    "riskLevel": "LOW"
  },
  "seq": 2101,
  "issuedAt": "2026-06-02T10:30:03+08:00"
}
```

### 9.5 捕网器

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_004",
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_004",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "net-gun-001",
    "deviceType": "NET_GUN",
    "vendor": "CUSTOM",
    "model": "NL-01"
  },
  "action": "payload.fire",
  "params": {
    "channel": 1,
    "confirmToken": "confirm_abc123"
  },
  "policy": {
    "qosClass": "RELIABLE_ACTION",
    "requiresExclusiveControl": true,
    "requiresConfirm": true,
    "requiresSafetySwitch": true,
    "cooldownMs": 3000,
    "riskLevel": "HIGH",
    "auditLevel": "HIGH"
  },
  "seq": 3101,
  "issuedAt": "2026-06-02T10:30:06+08:00"
}
```

高风险动作必须满足：

- 用户具备对应权限。
- 控制会话有效。
- 安全开关状态满足要求。
- `confirmToken` 未过期且未使用。
- 设备未处于冷却期。
- 后端和机器人侧均做幂等处理。

### 9.6 机械臂

机械臂建议先支持两类动作：关节空间和末端空间。

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
    "durationMs": 100
  }
}
```

```json
{
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "arm-001",
    "deviceType": "ROBOT_ARM"
  },
  "action": "arm.pose",
  "params": {
    "frame": "base_link",
    "position": {"x": 0.3, "y": 0.0, "z": 0.5},
    "orientation": {"roll": 0.0, "pitch": 0.0, "yaw": 1.57},
    "speed": 0.2
  }
}
```

机械臂默认要求强独占，并建议机器人侧做碰撞区域、软限位和本地急停。

### 9.7 激光雷达

激光雷达一般不是高频人工遥控，而是模式、频率、启停、数据订阅配置。

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
    "publishPointCloud": true
  }
}
```

如果涉及点云数据流，控制协议只负责启停和配置，数据流本身应走独立链路，例如 WebRTC DataChannel、WebSocket、ROS bridge、对象存储或专用流服务。

## 10. 状态、ACK 与错误协议

### 10.1 ACK

机器人侧收到命令后应尽快回 ACK。ACK 表示已接收，不一定表示执行完成。

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "ack",
  "commandId": "cmd_003",
  "traceId": "tr_20260602_abc001",
  "robotId": "robot-deep-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001"
  },
  "status": "ACCEPTED",
  "seq": 2101,
  "timestamp": "2026-06-02T10:30:03+08:00"
}
```

ACK 状态：

| 状态 | 说明 |
|---|---|
| `ACCEPTED` | 已接收 |
| `REJECTED` | 已拒绝 |
| `DUPLICATE` | 重复命令 |
| `STALE` | 旧序号或已过期命令 |
| `UNSUPPORTED` | 不支持的 target/action |

### 10.2 状态上报

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "status",
  "robotId": "robot-deep-001",
  "target": {
    "scope": "BODY",
    "deviceId": "base",
    "deviceType": "QUADRUPED_BASE"
  },
  "status": "ACTIVE",
  "state": {
    "linearX": 0.25,
    "linearY": 0.1,
    "angularZ": 0.15,
    "gait": "TROT",
    "battery": 78,
    "latencyMs": 86
  },
  "timestamp": "2026-06-02T10:30:04+08:00"
}
```

### 10.3 错误

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "error",
  "commandId": "cmd_004",
  "robotId": "robot-deep-001",
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "launcher-001"
  },
  "errorCode": "SAFETY_SWITCH_OFF",
  "message": "launcher safety switch is off",
  "retryable": false,
  "timestamp": "2026-06-02T10:30:06+08:00"
}
```

建议错误码分类：

| 前缀 | 说明 |
|---|---|
| `AUTH_` | 权限或控制权错误 |
| `VALIDATION_` | 参数错误 |
| `UNSUPPORTED_` | 能力不支持 |
| `SAFETY_` | 安全策略拒绝 |
| `DEVICE_` | 设备离线、故障、忙碌 |
| `DRIVER_` | 厂商 driver 执行错误 |
| `TIMEOUT_` | 超时 |
| `BROKER_` | MQTT 或链路错误 |

## 11. 安全策略

### 11.1 Deadman 失联停车

本体移动、云台连续移动、机械臂连续运动都必须有 deadman 保护。

要求：

1. 前端高频发送速度指令或心跳。
2. 后端控制会话有租约过期时间。
3. 机器人侧收到命令后启动本地超时计时。
4. 超过 `deadmanTimeoutMs` 未收到新指令时，机器人侧主动停止对应设备。
5. MQTT 断连时，机器人侧立即停止本体和高风险运动设备。
6. 松手停止不能只依赖前端 `stop` 消息；即使 `hold_end` 丢失，机器人侧也必须依靠 deadman 自动停止。

### 11.2 急停

急停设计原则：

- 使用独立 topic：`robot/{robotId}/control/safety/estop`。
- 不要求持有普通控制权，但要求用户具备急停权限。
- 机器人侧收到后必须优先处理，并尽可能绕过普通 driver 队列。
- 急停解除必须是独立动作，例如 `safety.estop_release`，并可要求更高权限。

### 11.3 高风险动作

高风险动作包括捕网器发射、机械臂大幅运动、危险载荷启用等。

必须支持：

- 二次确认。
- 安全开关状态校验。
- 冷却时间。
- 控制权强独占。
- 审计日志。
- 后端和机器人侧双重幂等。

## 12. 机器人侧接入客户端设计

机器人侧 Go client 或边缘网关建议新增 `control` 模块：

```text
client/internal/control/
├── mqtt.go          订阅 control topic，发布 media/client/status
├── dispatcher.go    按 target/action 路由
├── session.go       控制会话、seq、deadman 管理
├── driver.go        driver 接口
├── drivers/
│   ├── songling/
│   ├── deep/        
│   ├── ros2/
│   ├── ptz/
│   ├── arm/
│   └── lidar/
└── model.go         控制协议结构
```

driver 接口示意：

```go
type Driver interface {
    Supports(target Target, action string) bool
    Execute(ctx context.Context, command Command) error
    Stop(ctx context.Context, target Target) error
    Status(ctx context.Context, target Target) (DeviceState, error)
}
```

机器人侧职责：

1. 订阅 `robot/{robotId}/control/#`。
2. 校验 `robotId`、协议版本和过期时间。
3. 按 `commandId` 幂等去重。
4. 按 `seq` 丢弃乱序旧包。
5. 按 `target.deviceId + action` 路由 driver。
6. 对连续运动动作做 deadman 超时停止。
7. MQTT 断连时停止本体和高风险设备。
8. 通过 `robot/{robotId}/media/client/status` 周期性上报在线状态、控制模式和设备状态。

## 13. 后端模块落地建议

建议在后端新增 `teleop` 或 `equipment` 业务域：

```text
backend/src/main/java/com/robot/mediaserver/teleop/
├── api/
│   ├── ControlProfileController.java
│   ├── ControlSessionController.java
│   └── ControlCommandController.java
├── dto/
├── messaging/
│   └── EquipmentCommandPublisher.java
├── model/
│   ├── ControlSession.java
│   ├── EquipmentCommandLog.java
│   └── RobotDeviceCapability.java
├── repository/
└── service/
    ├── ControlProfileService.java
    ├── ControlSessionService.java
    ├── ControlCommandService.java
    ├── CommandPolicyService.java
    └── CommandSchemaService.java
```

后端核心服务：

| 服务 | 职责 |
|---|---|
| `ControlProfileService` | 查询机器人和设备能力，返回前端控制 profile |
| `ControlSessionService` | 控制权申请、续租、释放、冲突判断 |
| `ControlCommandService` | 统一命令入口，构造标准命令并下发 |
| `CommandPolicyService` | 风险等级、二次确认、限速、冷却、互斥策略 |
| `CommandSchemaService` | action 参数 schema 校验和默认值补齐 |
| `EquipmentCommandPublisher` | MQTT 下发 |
| `RobotMediaStatusSubscriber` | 订阅 `media/client/status`，刷新机器人状态并推送 WebSocket |

### 13.1 数据表建议

| 表 | 说明 |
|---|---|
| `robot` | 已注册机器人实例，包含组织、厂商、型号、类型、启用状态 |
| `equipment_device` | 已注册设备实例，包含本体设备和上装设备 |
| `robot_device_binding` | 机器人与上装设备绑定关系，包含安装位置、启用状态、绑定时间 |
| `robot_device_capability` | 绑定设备的能力和支持动作，可由型号模板继承后覆写 |
| `control_profile` | 型号或设备级控制参数 |
| `control_session` | 控制权租约 |
| `equipment_command_log` | 指令审计日志 |
| `equipment_status_snapshot` | 最新设备状态快照 |
| `equipment_event_log` | 设备控制事件流水 |

其中 `equipment_device` 表示平台资产意义上的设备，`robot_device_binding` 表示某个设备当前挂载在哪台机器人上。同一类双光云台可以注册多台设备实例，也可以在运维流程中从一台机器人解绑后再绑定到另一台机器人。控制指令始终通过 `robotId + target.deviceId` 寻址，后端根据绑定关系校验该设备是否属于当前机器人。

## 14. 协议版本与兼容

协议字段中必须包含：

```json
{
  "protocol": "embodied-control",
  "version": "1.0"
}
```

兼容策略：

1. `1.x` 内只允许新增可选字段，不删除字段，不改变字段语义。
2. 新 action 必须先在设备能力目录中声明，前端看到能力后才显示控件。
3. 机器人侧不识别的 action 返回 `UNSUPPORTED`，不能静默忽略。
4. 后端保留设备 `clientProtocolVersions`，下发前选择兼容版本。
5. 对接第三方系统时，优先转换成平台统一协议，再进入控制链路。

## 15. 第一阶段五项定版设计

第一阶段先不追求覆盖所有设备细节，而是定清楚平台统一控制的五个地基。后续接入松灵、云深处、宇树或新增机械臂、激光雷达、探照灯、喊话器，本质上都应复用这五个机制。

### 15.1 设备注册绑定与 Effective Profile

定版目标：

- 机器人和上装设备必须先在平台注册。
- 上装设备通过绑定关系挂载到某台机器人。
- 前端只展示后端返回的有效能力，不按厂商型号硬编码。
- 后端控制命令必须校验 `robotId + target.deviceId` 的绑定关系。

第一版数据模型：

| 模型 | 第一版职责 |
|---|---|
| `robot` | 机器人实例，包含组织、厂商、型号、类型、启用状态 |
| `equipment_device` | 设备资产实例，包含本体设备和上装设备 |
| `robot_device_binding` | 机器人与设备绑定关系，包含安装位置、启用状态 |
| `control_profile` | 型号级或实例级控制参数 |
| `robot_device_capability` | 当前绑定设备的可控 action 和参数范围 |

Effective Profile 合成规则：

```text
厂商型号默认模板
  + 设备实例配置
  + 机器人绑定覆写
  + 平台安全策略
  + 当前在线/健康/锁定状态
  = 前端可见 effective_control_profile
```

前端只消费：

```text
GET /api/control/robots/{robotId}/control-profile
```

第一版验收口径：

- 松灵机器人绑定双光云台、捕网器、激光雷达、语音对讲时，前端只展示这些能力。
- 云深处四足机器狗绑定双光云台、捕网器、机械臂、语音对讲时，前端自动展示这套组合。
- 宇树机器狗绑定双光云台、探照灯时，前端不展示捕网器、机械臂、激光雷达控制。

### 15.2 控制权与冲突矩阵

定版目标：

- 控制权不只按机器人整机判断，也支持设备级控制。
- 本体、高风险上装、自动任务之间必须有明确冲突规则。
- 急停不受普通控制权限制，但受权限限制。

第一版控制权范围：

| 范围 | 用途 |
|---|---|
| `ROBOT` | 本体运动、整机接管、高风险联动 |
| `DEVICE` | 云台、探照灯、激光雷达、语音对讲等单设备 |
| `ACTION` | 低风险动作，后续扩展 |

第一版冲突矩阵：

| 已持有/执行中 | 新请求 | 处理 |
|---|---|---|
| `drive.velocity` | `ptz.move` | 默认允许，可配置禁止 |
| `drive.velocity` | `payload.fire` | 禁止 |
| `drive.velocity` | `arm.pose` / `arm.joint_velocity` | 默认禁止 |
| `ptz.move` | `camera.zoom` 同设备 | 允许同一控制者，其他终端禁止 |
| `payload.fire` | 任意本体运动 | 禁止 |
| `arm.pose` | 本体运动 | 默认禁止 |
| `AUTO_MISSION` | 人工本体控制 | 先暂停或取消任务，再接管 |
| 任意状态 | `safety.estop` | 允许，最高优先级 |
| `safety.estop` 生效中 | 普通控制 | 禁止 |

第一版接口：

```text
POST /api/control/robots/{robotId}/control-sessions/acquire
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/release
POST /api/control/robots/{robotId}/control-sessions/{controlSessionId}/heartbeat
GET  /api/control/robots/{robotId}/control-sessions/active
```

第一版验收口径：

- 两个终端不能同时控制同一台机器人的本体运动。
- 一个终端控制本体时，另一个终端是否能控云台由冲突矩阵配置决定。
- 捕网器、机械臂危险动作默认与本体运动互斥。
- 急停按钮在有权限时始终可用。

### 15.3 点触、长按、频率与 Deadman

定版目标：

- 本体运动和双光云台运动必须同时支持点触和长按。
- 高频控制只执行最新值，不排队执行旧指令。
- 松手 stop 丢失时，机器人侧必须自动停止。

第一版控制模式：

| 模式 | 语义 |
|---|---|
| `tap` | 点触短动作，必须带 `durationMs` |
| `hold_start` | 长按开始，可选，用于前端状态表达 |
| `hold_update` | 长按期间持续发送最新速度 |
| `hold_end` | 松手停止，必须优先处理 |
| `stream` | 摇杆/拖拽连续流，按 `seq` 更新最新值 |

第一版频率：

| 控制对象 | 发送频率 | Deadman |
|---|---:|---:|
| 本体摇杆 | 10-20 Hz | 300-500 ms |
| 本体点动 | 单次 | 300-500 ms |
| 云台长按 | 10-15 Hz | 500-800 ms |
| 云台点动 | 单次 | 500-800 ms |
| 变焦长按 | 5-10 Hz | 800-1000 ms |

后端规则：

- 按 `robotId + target.deviceId + action + terminalId` 维护最新 `seq`。
- 高频 `hold_update` 可以合并，只下发最新值。
- `hold_end`、`drive.stop`、`ptz.stop` 不能被限流丢弃。
- 前端断开时，控制会话超时后后端应主动下发 stop。

机器人侧规则：

- 丢弃旧 `seq`。
- 丢弃过期 `expireAt` 命令。
- 连续动作超过 `deadmanTimeoutMs` 未收到新指令时自动停止。
- MQTT 断连时停止本体和高风险运动设备。

第一版验收口径：

- 本体按钮点一下会短促移动，到时自动停。
- 本体摇杆长按连续移动，松手立即停。
- 云台方向键支持点动和长按连续转动。
- 拔网线或关闭页面时，机器人侧在 deadman 时间内自动停止。

### 15.4 统一命令协议与状态生命周期

定版目标：

- 所有控制命令使用同一协议外壳。
- ACK 与执行完成分开。
- 高频指令轻量处理，高风险动作完整审计。

第一版命令外壳：

```json
{
  "protocol": "embodied-control",
  "version": "1.0",
  "messageType": "command",
  "commandId": "cmd_001",
  "traceId": "tr_001",
  "robotId": "robot-deep-001",
  "controlSessionId": "tc_001",
  "operator": {
    "userId": "u1001",
    "orgId": "org001",
    "terminalId": "web-client-001"
  },
  "target": {
    "scope": "PAYLOAD",
    "deviceId": "ptz-dual-001",
    "deviceType": "DUAL_LIGHT_PTZ"
  },
  "action": "ptz.move",
  "params": {},
  "policy": {},
  "seq": 1001,
  "issuedAt": "2026-06-02T10:30:00+08:00"
}
```

第一版状态生命周期：

```text
CREATED
  -> VALIDATED
  -> PUBLISHED
  -> ACKED
  -> EXECUTING
  -> COMPLETED / FAILED / EXPIRED / CANCELED
```

处理差异：

| 指令类型 | 生命周期要求 |
|---|---|
| 高频本体/云台速度 | 可只记录摘要和最新状态，不逐条完整入库 |
| stop / estop | 必须记录发布和执行结果 |
| 捕网器、机械臂危险动作 | 必须完整记录每个生命周期状态 |
| 模式切换、返航、退出充电桩 | 建议完整记录 |

第一版 MQTT topic：

```text
robot/{robotId}/control/{device-domain}/command
robot/{robotId}/control/{device-domain}/{deviceId}/command
robot/{robotId}/control/safety/estop
robot/{robotId}/media/client/status
```

第一版验收口径：

- 后端能通过 `commandId` 查到命令是否已发布。
- 后端能通过 `media/client/status` 刷新在线状态、控制模式和设备状态。
- 本体和云台高频动作不会造成日志爆炸。

### 15.5 高风险动作安全策略

定版目标：

- 捕网器、机械臂危险动作、急停解除不能按普通按钮处理。
- 后端和机器人侧都必须做安全校验。
- 所有高风险动作必须可审计、可追踪、可复盘。

第一版高风险动作：

| 动作 | 风险点 | 策略 |
|---|---|---|
| `payload.fire` | 发射器、捕网枪发射 | 强独占、二次确认、冷却、审计；发射器额外要求真实安全开关 |
| `arm.pose` 大幅运动 | 碰撞、夹伤、倾覆 | 强独占、软限位、本地 driver 校验、审计 |
| `safety.estop_release` | 恢复运动能力 | 高权限、二次确认、状态检查 |
| `docking.leave` | 退出充电桩 | 控制权、周边安全状态、执行结果 |

Confirm Token 流程：

```text
1. 前端请求高风险动作确认 token
2. 后端校验用户、权限、控制权、目标设备和 action
3. 后端生成一次性 confirmToken，绑定 userId/terminalId/robotId/deviceId/action
4. 前端弹二次确认
5. 用户确认后下发命令并携带 confirmToken
6. 后端校验 token 未过期、未使用、上下文匹配
7. 后端下发标准命令
8. 机器人侧再次校验真实安全开关、冷却、本地状态
```

第一版接口：

```text
POST /api/control/robots/{robotId}/commands/confirm-token
POST /api/control/robots/{robotId}/commands
```

第一版验收口径：

- 发射器未打开真实安全开关时无法发射；捕网枪前端本地安全开关未打开时无法点击发射。
- confirmToken 过期、重复使用、目标设备不匹配时拒绝执行。
- 高风险动作记录操作者、终端、目标设备、参数、确认记录、执行结果。
- 急停解除必须独立确认，不能通过普通恢复按钮绕过。

## 16. 推荐落地阶段

### 阶段一：统一控制骨架

- 建立设备目录和 control profile。
- 建立控制权 session。
- 支持统一命令外壳。
- 支持本体 `drive.velocity`、`drive.stop`、云台 `ptz.move`、`ptz.stop`。
- MQTT topic 按设备域拆分，Go 客户端订阅 `robot/{robotId}/control/#`，状态统一上报 `robot/{robotId}/media/client/status`。
- 前端按 `devices[].actions` 渲染本体和上装控制。
- 支持点触、长按、频率限制和机器人侧 deadman。
- 支持统一客户端状态上报和高风险动作审计模型。

### 阶段二：高风险上装与安全策略

- 接入捕网器。
- 增加 `confirmToken`、安全开关、冷却时间、审计日志。
- 增加急停独立 topic。
- 增加控制权冲突和接管流程。

### 阶段三：机械臂、激光雷达和多终端协同

- 接入机械臂关节/末端控制。
- 接入激光雷达模式控制。
- 支持设备级控制权，让不同终端控制不同上装。
- 增加任务调度系统与人工遥控的优先级策略。

### 阶段四：多厂商规模化接入

- 建立厂商型号模板库。
- driver 插件化。
- 支持协议版本协商。
- 支持设备能力热更新。
- 增加控制链路延迟、成功率、错误码统计。

## 17. 结论

本平台应以 `robot + device + capability + action + controlProfile` 作为统一抽象，而不是以页面布局或厂商型号作为接口边界。

推荐统一控制协议核心结构：

```text
robotId
target.deviceId
target.deviceType
action
params
seq
commandId
issuedAt
```

推荐 MQTT topic 第一版保持简洁：

```text
robot/{robotId}/control/{device-domain}/command
robot/{robotId}/control/{device-domain}/{deviceId}/command
robot/{robotId}/control/safety/estop
robot/{robotId}/media/client/status
```

这样设计后，同一套前端和后端接口可以同时覆盖松灵四轮机器人、云深处四足机器狗、双光云台、捕网器、机械臂、激光雷达以及未来更多厂商设备。平台负责统一治理，机器人侧负责厂商协议适配，二者边界清晰，后续扩展成本最低。
