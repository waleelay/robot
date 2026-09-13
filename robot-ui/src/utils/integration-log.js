const IMPORTANT_WS_EVENTS = new Set([
  'management.task.invalidated',
  'panorama.task.changed',
  'panorama.alarms.changed',
  'panorama.alarm.changed',
  'panorama.workflow-alarms.changed',
  'robot.trajectory.changed'
])

export function integrationLog(stage, fields = {}, level = 'info') {
  if (process.env.VUE_APP_INTEGRATION_LOG_ENABLED === 'false') return
  const logger = console[level] || console.info
  logger('[管理端链路]', { 服务: 'robot-ui', 阶段: stage, ...fields })
}

export function logWebSocketEvent(event) {
  const eventType = event?.event || event?.type || ''
  if (!IMPORTANT_WS_EVENTS.has(eventType)) return
  const data = event?.data || event?.payload || {}
  integrationLog('接收事件', {
    protocol: 'websocket',
    outcome: '已接受',
    eventType,
    eventId: data.eventId,
    correlationId: event?.correlationId || data.correlationId,
    taskId: data.taskId || data.task?.taskId,
    alarmId: data.alarmId || data.alarm?.alarmId,
    robotId: data.robotId,
    workflowInstanceId: data.workflowInstanceId,
    action: data.action,
    itemCount: Array.isArray(data.items) ? data.items.length : undefined,
    pointCount: Array.isArray(data.points) ? data.points.length : undefined
  })
}

export function newRequestId() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
