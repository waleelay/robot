import { isDeviceAssociatedTaskStatus, taskExecutionStatus } from '../../../patrol/business/execution-status.js'

export function isActiveTemporaryNavigationTask(task) {
  return task?.planType === 'TEMPORARY'
    && isDeviceAssociatedTaskStatus(taskExecutionStatus(task))
}

export function buildTemporaryNavigationTask(response, { robotId, targetPoint, mapId }) {
  const result = response?.data
  if (!result?.workflowPlanId || !result?.workflowInstanceId || !result?.deviceTaskInstanceId) {
    throw new Error('临时导航响应缺少任务实例标识')
  }
  const instanceStatus = String(result.executionStatus || 'PREPARING').toUpperCase()
  return {
    taskId: result.workflowPlanId,
    workflowInstanceId: result.workflowInstanceId,
    deviceTaskInstanceId: result.deviceTaskInstanceId,
    planType: 'TEMPORARY',
    runtimeOnly: true,
    name: result.workflowName || '临时导航',
    executionStatus: instanceStatus === 'PAUSED' ? 'PAUSED' : 'RUNNING',
    activeWorkflowInstanceId: result.workflowInstanceId,
    activeWorkflowInstanceStatus: instanceStatus,
    startTime: result.startedAt,
    timestamp: result.startedAt ? Date.parse(result.startedAt) : Date.now(),
    mapId,
    targetPoint,
    equipmentList: [{ robotId, name: result.deviceName }]
  }
}
