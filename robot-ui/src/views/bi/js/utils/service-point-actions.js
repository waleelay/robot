const MAX_RUNTIME_AGE_MS = 30 * 1000

function invalidLocation(location) {
  return !location || location.localized !== true || !String(location.mapId || '').trim()
    || !Number.isFinite(Number(location.x)) || !Number.isFinite(Number(location.y))
}

export function commonActionUnavailableReason(robot, now = Date.now()) {
  if (robot?.status !== 'online') return '设备不在线'
  const updatedAt = Date.parse(robot?.runtimeUpdatedAt || '')
  if (!Number.isFinite(updatedAt) || now - updatedAt < 0 || now - updatedAt > MAX_RUNTIME_AGE_MS) return '设备状态已过期'
  if (robot?.taskStatus !== 'IDLE') return robot?.taskStatus == null ? '任务状态未知' : '设备正在执行任务'
  if (typeof robot?.charging !== 'boolean') return '充电状态未知'
  return ''
}

export function navigationUnavailableReason(robot, intent, pending = false, now = Date.now()) {
  if (pending) return '正在提交设备操作'
  const commonReason = commonActionUnavailableReason(robot, now)
  if (commonReason) return commonReason
  if (intent === 'CHARGE' && robot.charging) return '设备正在充电'
  if (invalidLocation(robot.edgeLocation)) return '设备未完成有效定位'
  return ''
}

export function leaveChargerUnavailableReason(robot, supportsLeave, hasOperator, pending = false, now = Date.now()) {
  if (pending) return '正在提交设备操作'
  if (!hasOperator) return '当前用户没有装备操作权限'
  if (!supportsLeave) return '设备未登记退出充电桩能力'
  const commonReason = commonActionUnavailableReason(robot, now)
  if (commonReason) return commonReason
  if (!robot.charging) return '设备当前未充电'
  return ''
}
