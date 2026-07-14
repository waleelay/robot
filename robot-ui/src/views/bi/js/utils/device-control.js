const deviceTypes = {
  'WHEELED_BASE': {
    // 不支持横移时 'linearY=0'，按轮式底盘限速
    actions: ['drive.velocity']
  },
  'QUADRUPED_BASE': {
    // 允许横移时保留 'linearY'，按四足限速
    actions: ['drive.velocity']
  },
  DUAL_LIGHT_PTZ: {
    // 裁剪云台速度、变焦速度
    actions: ['ptz.move', 'camera.zoom']
  },
  'NET_LAUNCHER': {
    // 校验 confirmToken、安全开关、冷却
    actions: ['payload.safety_switch', 'payload.fire']
  },
  SEARCHLIGHT: {
    // 裁剪亮度，校验模式
    actions: ['light.set']
  },
  LIDAR: {
    // 校验雷达模式和频率
    actions: ['lidar.mode.set']
  }
}

// 本体控制
export function createRobotBodyParams(initData = {}) {
  return createControlParams({
    type: 'control.command',
    requestId: initData.requestId,
    payload: {
      robotId: initData.robotId,
      controlSessionId: initData.controlSessionId,
      controlMode: "MANUAL",                    // 本体手动控制固定
      target: {
        scope: 'BODY',
        deviceId: 'base',
        deviceType: initData.deviceType
      },
      action: 'drive.velocity',
      params: {
        linearX: initData.linearX || 0.0,                            // 前后速度
        linearY: initData.linearY || 0.0,                            // 横移速度；轮式底盘不支持时为 0
        angularZ: initData.angularZ || 0.0                           // 转向角速度
      },
      client: {
        terminalId: initData.terminalId,
        source: 'body_joystick',
        seq: initData.seq,
        timestamp: initData.timestamp
      }
    },
  })
}
// 双光云台
export function createDualPtzParams(initData = {}) {
  return {
    type: 'control.command',
    requestId: initData.requestId,
    payload: {
      robotId: initData.robotId,
      controlSessionId: initData.controlSessionId,
      controlMode: initData.controlMode, // 导航中控制云台时可保持 NAVIGATION；纯手动可填 MANUAL
      target: {
        scope: 'PAYLOAD',
        deviceId: '',
        deviceType: 'DUAL_LIGHT_PTZ',
      },
      action: initData.action, // ptz.move 云台移动、camera.zoom 焦距
      params: {
        panSpeed: initData.panSpeed || 0.0, // 水平速度
        tiltSpeed: initData.tiltSpeed || 0.0, // 垂直速度
      },
      client: {
        terminalId: initData.terminalId,
        source: 'ptz_left_button',
        seq: initData.seq,
        timestamp: initData.timestamp
      }
    }
  }
}
// 捕网器http请求
// 捕网器安全开关
export function createBwqSwitchParams(initData = {}) {
  return {
    controlSessionId: initData.controlSessionId,        // 捕网器设备级或整机控制权
    target: {
      scope: 'PAYLOAD',
      deviceId: initData.deviceId,              // 捕网器设备 ID
      deviceType: 'NET_LAUNCHER',                 // 捕网器类型
    },
    action: 'payload.safety_switch',             // 设置安全开关
    params: {
      enabled: initData.enabled || false                              // true 打开，false 关闭
    },
    client: {
      terminalId: initData.terminalId || '',              // 终端 ID
      source: 'net_safety_toggle',               // 安全开关控件
      seq: initData.seq,                                 // 序号
      timestamp: initData.timestamp     // 前端时间
    }
  }
}
// 发射前先请求 confirmToken
export function createBwqBeforeExecuteParams (initData = {}) {
  return {
    controlSessionId: initData.controlSessionId,        // 控制权 ID
    target: {
      scope: 'PAYLOAD',                          // 上装
      deviceId: initData.deviceId,              // 捕网器
      deviceType: 'NET_LAUNCHER'                 // 捕网器类型
    },
    action: 'payload.fire',                      // 准备发射
    reason: 'manual_confirm'                    // 人工二次确认
  }
}
// 捕网器发射
export function createBwqExecuteParams (initData = {}) {
  return {
    controlSessionId: initData.controlSessionId,        // 控制权 ID
    target: {
      scope: 'PAYLOAD',                          // 上装
      deviceId: initData.deviceId,              // 捕网器
      deviceType: 'NET_LAUNCHER'                 // 捕网器类型
    },
    action: 'payload.fire',
    params: {
      channel: initData.channel || 1,                                // 发射通道，一期默认 1
      confirmToken: initData.confirmToken    // 二次确认 token
    },
    client: {
      terminalId: initData.terminalId,              // 终端
      source: 'net_fire_button',                 // 发射按钮
      seq: initData.seq,                                 // 序号
      timestamp: initData.timestamp     // 前端时间
    }
  }
}

// 设备控制参数工厂函数
function createControlParams(initData = {}) {
  return {
    type: initData.type || 'control.command',                    // WebSocket 消息类型
    requestId: initData.requestId || 'req_20260603_1001',             // 前端生成，请求响应匹配用
    payload: {
      robotId: initData.robotId,            // 要控制的机器人 ID
      controlSessionId: initData.controlSessionId,     // 控制权会话 ID
      controlMode: initData.controlMode || 'MANUAL',                    // 本体手动控制固定 MANUAL
      target: {
        scope: initData.scope || 'BODY',                          // BODY 本体 PAYLOAD 上装
        deviceId: initData.deviceId || 'base',                       // 本体设备固定 base
        deviceType: initData.deviceType || 'WHEELED_BASE'              // 后端按该类型构造底盘参数
      },
      action: initData.action || 'drive.velocity',                 // 本体速度控制帧
      params: initData.params || {},
      client: {
        terminalId: initData.terminalId || 'web-client-001',            // 当前终端 ID
        source: initData.source || 'body_joystick',                 // 控件来源
        seq: initData.seq || 1024,                               // 前端递增序号
        timestamp: initData.timestamp || '2026-06-03T10:30:00+08:00'   // 前端发送时间
      }
    }
  }
}