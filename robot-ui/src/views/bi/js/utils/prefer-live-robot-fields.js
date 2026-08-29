/** overview/快照与实时装备合并：仅用 live 覆盖运行态字段，不整体替换快照 */

function statusTime(value) {
  if (!value) return null
  const parsed = Date.parse(String(value).trim().replace(' ', 'T'))
  return Number.isNaN(parsed) ? null : parsed
}

// Date.parse 只保留毫秒，额外比较小数部分，避免同一毫秒内的两次状态更新被误判为相同版本。
function isNewerVersion(current, incoming) {
  const before = statusTime(current)
  const after = statusTime(incoming)
  if (after === null) return before === null
  if (before === null) return true
  if (after !== before) return after > before
  const fraction = value => ((String(value).match(/\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/) || [])[1] || '').padEnd(9, '0')
  return fraction(incoming) > fraction(current)
}

export function normalizeRobotControlMode(value) {
  if (value === '导航模式') return value
  return value === '手动模式' || value === '常规模式' ? '手动模式' : null
}

const runtimeFields = ['battery', 'speed', 'controlMode', 'controlModeName', 'runtimeUpdatedAt']

export function mergeRobotBaseInfo(previous = {}, incoming = {}, fromRealtime = false) {
  const merged = { ...previous, ...incoming }
  if (fromRealtime) {
    // 实时事件不是设备档案，不能覆盖名称、类型、型号和已取得的组件数量。
    ;['name', 'type', 'typeCode', 'model', 'vendor', 'mountedDeviceCount', 'mountedDevices'].forEach(key => {
      if (Object.prototype.hasOwnProperty.call(incoming, key)) {
        if (previous[key] !== undefined) merged[key] = previous[key]
        else if (incoming[key] == null || incoming[key] === '-') delete merged[key]
      }
    })
  }
  if (!isNewerVersion(previous.runtimeUpdatedAt, incoming.runtimeUpdatedAt)) {
    runtimeFields.forEach(key => { merged[key] = previous[key] })
    if (previous.stateSeq !== undefined || incoming.stateSeq !== undefined) merged.stateSeq = previous.stateSeq
  } else {
    runtimeFields.forEach(key => {
      if (incoming[key] === undefined) merged[key] = previous[key]
    })
  }
  if (incoming.status && !shouldApplyLiveRobotStatus(previous, incoming)) {
    merged.status = previous.status
    merged.statusChangedAt = previous.statusChangedAt
    merged.fault = previous.fault
  }
  return merged
}

export function formatRobotSpeed(robot) {
  const value = robot && robot.speed
  if (robot?.status === 'offline' || value == null || value === '' || !Number.isFinite(Number(value))) return '-'
  return `${Number(value).toFixed(2)}m/s`
}

export function shouldApplyLiveRobotStatus(snapshot, live) {
  if (!live || !live.status) return false
  return isNewerVersion(snapshot && snapshot.statusChangedAt, live.statusChangedAt)
}

export function overlayLiveRobotRuntimeFields(snapshot, live) {
  if (!snapshot) return live || null
  if (!live) return snapshot
  const merged = mergeRobotBaseInfo(snapshot, live, true)
  const result = { ...snapshot }
  ;[...runtimeFields, 'status', 'statusChangedAt', 'stateSeq', 'fault'].forEach(key => { result[key] = merged[key] })
  return Object.assign(result, {
    cameras: (live.cameras && live.cameras.length) ? live.cameras : snapshot.cameras,
    devices: live.devices || snapshot.devices
  })
}
