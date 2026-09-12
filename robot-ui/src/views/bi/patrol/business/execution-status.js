/**
 * 巡检执行状态文案与样式
 * PREPARING / RUNNING / PAUSING / PAUSED / RESUMING / TERMINATING /
 * CONTROL_FAILED / COMPLETED / FAILED / TERMINATED
 */
export const EXECUTION_STATUS_LABEL = {
  WAITING: '待执行',
  PREPARING: '准备中',
  RUNNING: '执行中',
  PAUSING: '暂停中',
  PAUSED: '已暂停',
  RESUMING: '恢复中',
  TERMINATING: '终止中',
  CONTROL_FAILED: '控制失败',
  COMPLETED: '已完成',
  FAILED: '失败',
  TERMINATED: '已终止',
  CANCELED: '已终止'
}

export const EXECUTION_STATUS_TYPE = {
  WAITING: 'info',
  PREPARING: 'info',
  RUNNING: 'orange',
  PAUSING: 'orange',
  PAUSED: 'info',
  RESUMING: 'orange',
  TERMINATING: 'orange',
  CONTROL_FAILED: 'red',
  COMPLETED: 'green',
  FAILED: 'red',
  TERMINATED: 'info',
  CANCELED: 'info'
}

export function normalizeExecutionStatus(value) {
  if (value == null || value === '') return ''
  const raw = String(value).trim()
  if (!raw) return ''
  const upper = raw.replace(/([a-z])([A-Z])/g, '$1_$2').replace(/[-\s]+/g, '_').toUpperCase()
  return upper
}

/** 任务计划状态只使用管理端 executionStatus，不与工作流实例 status 混用。 */
export function taskExecutionStatus(task) {
  const status = task?.executionStatus
  return status === 'WAITING' || status === 'RUNNING' || status === 'PAUSED' ? status : undefined
}

export function executionStatusLabel(value, fallback = '-') {
  const key = normalizeExecutionStatus(value)
  if (!key) return fallback
  return EXECUTION_STATUS_LABEL[key] || value || fallback
}

export function executionStatusType(value, fallback = 'info') {
  const key = normalizeExecutionStatus(value)
  if (!key) return fallback
  return EXECUTION_STATUS_TYPE[key] || fallback
}

export function isRunningTaskStatus(value) {
  return value === 'RUNNING'
}

export function isPausedTaskStatus(value) {
  return value === 'PAUSED'
}

/** 任务计划是否正在占用装备。executionStatus 仅有 WAITING/RUNNING/PAUSED。 */
export function isActiveTaskStatus(value) {
  return isDeviceAssociatedTaskStatus(value)
}

/** 可挂到装备 task 上的活跃状态，与 BFF devices.task 口径一致 */
export function isDeviceAssociatedTaskStatus(value) {
  return isRunningTaskStatus(value) || isPausedTaskStatus(value)
}

export function taskStatusColorClass(value) {
  if (value === 'RUNNING') return 'green'
  if (value === 'WAITING') return 'orange'
  if (value === 'PAUSED') return 'gray'
  return ''
}
