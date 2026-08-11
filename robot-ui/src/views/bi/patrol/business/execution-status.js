/**
 * 巡检执行状态文案与样式
 * PREPARING / RUNNING / PAUSING / PAUSED / RESUMING / TERMINATING /
 * CONTROL_FAILED / COMPLETED / FAILED / TERMINATED
 */
export const EXECUTION_STATUS_LABEL = {
  WAITING: '待执行',
  IDLE: '待执行',
  PENDING: '待执行',
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
  IDLE: 'info',
  PENDING: 'info',
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

const STATUS_ALIAS = {
  PENDING: 'WAITING',
  IDLE: 'WAITING',
  WAITING: 'WAITING',
  EXECUTING: 'RUNNING'
}

export function normalizeExecutionStatus(value) {
  if (value == null || value === '') return ''
  const raw = String(value).trim()
  if (!raw) return ''
  const upper = raw.replace(/([a-z])([A-Z])/g, '$1_$2').replace(/[-\s]+/g, '_').toUpperCase()
  return STATUS_ALIAS[upper] || upper
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
