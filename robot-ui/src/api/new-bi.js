import request from '@/utils/request'
const pre = '/api'

// 巡逻巡查
// 全景地图
export function getPatrolPanoramaOverview() {
  return request({
    url: pre + '/bigscreen/panorama/overview',
    method: 'get',
    // 首屏总览必须快速失败并交由页面提示重试，不能沿用全局 5 分钟超时一直遮住地图。
    timeout: 15000
  })
}

export function getActionableWorkflowAlarms() {
  return request({
    url: pre + '/bigscreen/panorama/alarms/actionable-workflow',
    method: 'get'
  })
}

export function handleWorkflowAlarm(alarmId, data) {
  return request({
    url: pre + `/bigscreen/panorama/alarms/${alarmId}/handle-and-continue`,
    method: 'post',
    data
  })
}

// 数据统计
export function getPatrolStatisticsOverview(params) {
  return request({
    url: pre + '/bigscreen/statistics/overview',
    method: 'get',
    params
  })
}

export function exportPatrolStatisticsReport(data) {
  return request({
    url: pre + '/bigscreen/statistics/reports/export',
    method: 'post',
    data,
    responseType: 'blob',
    timeout: 300000
  })
}

export function getHistoryList(params) {
  return request({
    url: pre + '/bigscreen/statistics/reports',
    method: 'get',
    params
  })
}
export function downloadReport(id) {
  return request({
    url: pre + `/bigscreen/statistics/reports/${id}/download`,
    method: 'get',
    responseType: 'blob',
    timeout: 300000
  })
}
export function deleteReport(id) {
  return request({
    url: pre + `/bigscreen/statistics/reports/${id}`,
    method: 'delete'
  })
}

// 任务相关
const taskPre = '/api/bigscreen/business'
// 获取任务列表 { pageNum, pageSize, status }
export function getTaskList(params) {
  return request({
    url: taskPre + '/tasks/plans',
    method: 'get',
    params
  })
}
export function getTaskDetail(id) {
  return request({
    url: taskPre + `/tasks/plans/${id}`,
    method: 'get'
  })
}
export function createTask(data) {
  return request({
    url: taskPre + '/tasks/plans',
    method: 'post',
    data
  })
}
export function updateTask(id, data) {
  return request({
    url: taskPre + `/tasks/plans/${id}`,
    method: 'put',
    data
  })
}
export function updateTaskEnabled(id, enabled) {
  return request({
    url: taskPre + `/tasks/plans/${id}/${enabled ? 'enable' : 'disable'}`,
    method: 'patch'
  })
}
export function previewTaskConfiguration(data) {
  return request({
    url: taskPre + '/tasks/plans/configuration-previews',
    method: 'post',
    data
  })
}
export function startTaskPreview(id, data) {
  return request({
    url: taskPre + `/tasks/plans/${id}/start-previews`,
    method: 'post',
    data
  })
}
export function startTask(id, data) {
  return request({
    url: taskPre + `/tasks/plans/${id}/starts`,
    method: 'post',
    data
  })
}

export function deleteTask(id) {
  return request({
    url: taskPre + `/tasks/plans/${id}`,
    method: 'delete'
  })
}

export function getTaskWorkflowDefinitions(params) {
  return request({
    url: taskPre + '/tasks/workflow-definitions',
    method: 'get',
    params
  })
}

export function getTaskWorkflowVersionDetail(id, versionId) {
  return request({
    url: taskPre + `/tasks/workflow-definitions/${id}/versions/${versionId}`,
    method: 'get'
  })
}

export function getManagementDevices(params) {
  return request({
    url: taskPre + '/devices',
    method: 'get',
    params
  })
}

// 执行记录
// detail: (id) => getData(`/tasks/execution-records/${id}`),
// replay: (id) => getData(`/tasks/execution-records/${id}/replay`),
// trackSamples: (id, params) => getData(`/tasks/execution-records/${id}/track-samples`, params)
export function getTaskRecordList(params) {
  return request({
    url: taskPre + '/tasks/execution-records',
    method: 'get',
    params
  })
}
export function getTaskRecordDetail(id) {
  return request({
    url: taskPre + `/tasks/execution-records/${id}`,
    method: 'get'
  })
}
export function getTaskRecordReplay(id) {
  return request({
    url: taskPre + `/tasks/execution-records/${id}/replay`,
    method: 'get'
  })
}
export function pauseTaskRecord(id, data) {
  return request({
    url: taskPre + `/tasks/execution-records/${id}/pause`,
    method: 'post',
    data: data || {}
  })
}
export function resumeTaskRecord(id, data) {
  return request({
    url: taskPre + `/tasks/execution-records/${id}/resume`,
    method: 'post',
    data: data || {}
  })
}
export function terminateTaskRecord(id, data) {
  return request({
    url: taskPre + `/tasks/execution-records/${id}/terminate`,
    method: 'post',
    data: data || {}
  })
}
export function forceTerminateTaskRecord(id, reason) {
  return request({
    url: taskPre + `/tasks/execution-records/${id}/force-terminate`,
    method: 'post',
    data: { reason }
  })
}

export function previewImageBlob(id, cacheKey) {
  return request({
    url: taskPre + `/maps/${id}/preview-image`,
    method: 'get',
    params: cacheKey ? { t: cacheKey } : undefined,
    responseType: 'blob'
  })
}

// 添加指定点的临时任务
export function addTaskByPoint(data) {
  return request({
    url: `/api/v1/management/external/temporary-navigations`,
    method: 'post',
    data
  })
}
