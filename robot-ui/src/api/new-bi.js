import request from '@/utils/request'
const pre = '/api'

// 巡逻巡查
// 全景地图
export function getPatrolPanoramaOverview() {
  return request({
    url: pre + '/bigscreen/panorama/overview',
    method: 'get'
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

// 任务相关
const taskPre = ''
// 获取任务列表 { pageNum, pageSize, status }
export function getTaskList(params) {
  return request({
    url: taskPre + '/api/v1/management/task-workflow-plans',
    method: 'get',
    params
  })
}
export function getTaskDetail(id) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-plans/${id}`,
    method: 'get'
  })
}
export function createTask(data) {
  return request({
    url: taskPre + '/api/v1/management/task-workflow-plans',
    method: 'post',
    data
  })
}
export function updateTask(id, data) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-plans/${id}`,
    method: 'put',
    data
  })
}
export function updateTaskEnabled(id, enabled) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-plans/${id}/enabled`,
    method: 'patch',
    data: { enabled }
  })
}
export function previewTaskConfiguration(data) {
  return request({
    url: taskPre + '/api/v1/management/task-workflow-plans/configuration-previews',
    method: 'post',
    data
  })
}
export function startTask(id, data) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-plans/${id}/starts`,
    method: 'post',
    data
  })
}

export function deleteTask(id) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-plans/${id}`,
    method: 'delete'
  })
}

export function getTaskWorkflowDefinitions(params) {
  return request({
    url: taskPre + '/api/v1/management/task-workflow-definitions',
    method: 'get',
    params
  })
}

export function getTaskWorkflowVersionDetail(id, versionId) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-definitions/${id}/versions/${versionId}`,
    method: 'get'
  })
}

export function getManagementDevices(params) {
  return request({
    url: taskPre + '/api/v1/management/devices',
    method: 'get',
    params
  })
}

// 执行记录
// detail: (id) => getData(`/api/v1/management/task-workflow-instances/${id}`),
// replay: (id) => getData(`/api/v1/management/task-workflow-instances/${id}/replay`),
// trackSamples: (id, params) => getData(`/api/v1/management/task-workflow-instances/${id}/track-samples`, params),
// humanTasks: (id) => getData(`/api/v1/management/task-workflow-instances/${id}/human-tasks`),
// completeHumanTask: (id, taskId, variables = {}) =>
//   postData(`/api/v1/management/task-workflow-instances/${id}/human-tasks/${taskId}/complete`, { variables }),
// applyComponentSelections: (id, payload) =>
//   postData(`/api/v1/management/task-workflow-instances/${id}/component-selections`, payload)
export function getTaskRecordList(params) {
  return request({
    url: taskPre + '/api/v1/management/task-workflow-instances',
    method: 'get',
    params
  })
}
export function getTaskRecordDetail(id) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-instances/${id}`,
    method: 'get'
  })
}
export function getTaskRecordReplay(id) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-instances/${id}/replay`,
    method: 'get'
  })
}
export function getTrackRecordSamples(id, params) {
  return request({
    url: taskPre + `/api/v1/management/task-workflow-instances/${id}/track-samples`,
    method: 'get',
    params
  })
}

export function previewImageBlob(id, cacheKey) {
  return request({
    url: taskPre + `/api/v1/management/maps/${id}/preview-image`,
    method: 'get',
    params: cacheKey ? { t: cacheKey } : undefined,
    responseType: 'blob'
  })
}
