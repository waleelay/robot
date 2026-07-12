import request from '@/utils/request'

// 每个浏览器标签页拥有独立 clientId，但同一标签页刷新后保持不变。
// 这对控制权续用很重要：刷新页面不能被后端误判成另一个终端。
const clientIdKey = 'robot-media-client-id'
const existingClientId = window.sessionStorage.getItem(clientIdKey)
const clientId = existingClientId || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
if (!existingClientId) {
  window.sessionStorage.setItem(clientIdKey, clientId)
}
export const mediaClientId = clientId

const headers = {
  'X-User-Id': 'u1001',
  'X-Org-Id': 'org001',
  'X-Roles': 'MEDIA_VIEWER,MEDIA_OPERATOR',
  'X-Client-Id': clientId
}

const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin

// 创建视频会话
export function createVideoSession(data) {
  const payload = {
    quality: data.quality,
    reuse: data.reuse,
    clientRequestId: data.clientRequestId
  }
  return request({
    url: `/api/control/robots/${data.robotId}/cameras/${data.deviceId}/video/start`,
    method: 'post',
    data: payload
  })
}

// 获取机器人列表
export function getRobots() {
  return request({
    url: '/api/control/robots',
    method: 'get'
  })
}

// 获取活跃视频会话
export function getActiveVideoSessions() {
  return request({
    url: '/api/control/video-sessions/active',
    method: 'get'
  })
}

// 获取观看令牌
export function getViewerToken(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/token`,
    method: 'post'
  })
}

// 停止视频会话
export function stopVideoSession(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/stop`,
    method: 'post'
  })
}

// 视频会话心跳
export function heartbeatVideoSession(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/heartbeat`,
    method: 'post'
  })
}

// 重启视频会话
export function restartVideoSession(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/restart`,
    method: 'post'
  })
}

// 切换频道
export function switchChannel(sessionId, data) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/switch-channel`,
    method: 'post',
    data
  })
}

// 创建快照
export function uploadFile(data) {
  return request({
    url: '/api/control/files',
    method: 'post',
    data,
    timeout: 30000
  })
}

export function getFiles(params = {}) {
  return request({
    url: '/api/control/files',
    method: 'get',
    params
  })
}

export function fileDownloadUrl(fileId) {
  return request({
    url: `/api/control/files/${fileId}/download-url`,
    method: 'post'
  })
}
export function snapshotImageUrl(fileId) {
  const base = process.env.VUE_APP_API_BASE || ''
  return request({
    url: `${base}/api/control/files/${fileId}/content`,
    method: 'get'
  })
}

// 获取会话快照列表
export function getSessionSnapshots(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/snapshots`,
    method: 'get'
  })
}

// 模拟轨道发布（内部接口）
export function mockTrackPublished(sessionId, trackSid) {
  return request({
    url: `/internal/media/video-sessions/${sessionId}/_mock/track-published/${trackSid}`,
    method: 'post'
  })
}
export function stopIntercom(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/intercom/stop`,
    method: 'post'
  })
}
export function startCameraIntercom(data) {
  return request({
    url: `/api/control/robots/${data.robotId}/cameras/${data.deviceId}/video/intercom/start`,
    method: 'post',
    data: {
      quality: data.quality,
      reuse: true
    }
  })
}

export function startSessionIntercom(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/intercom/start`,
    method: 'post'
  })
}
export function heartbeatIntercom(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/intercom/heartbeat`,
    method: 'post'
  })
}

export function getFilePlayUrl(fileId) {
  return request({
    url: `${preUrl}/api/control/files/${fileId}/play-url`,
    method: 'post',
    headers
  })
}

// ==============================================================远程控制=================================================================
// 本体控制
// 双光云台
export function getControlProfile(robotId) {
  return request({
    url: `/api/control/robots/${robotId}/control-profile`,
    method: 'get'
  })
}

export function acquireControl(robotId, data) {
  return request({
    url: `/api/control/robots/${robotId}/control-sessions/acquire`,
    method: 'post',
    data
  })
}

export function takeoverControl(robotId, data) {
  console.log(robotId, data);
  
  return request({
    url: `/api/control/robots/${robotId}/control-sessions/takeover`,
    method: 'post',
    data
  })
}

export function releaseControl(robotId, controlSessionId, data) {
  return request({
    url: `/api/control/robots/${robotId}/control-sessions/${controlSessionId}/release`,
    method: 'post',
    data: data || {}
  })
}

export function createConfirmToken(robotId, data) {
  return request({
    url: `/api/control/robots/${robotId}/commands/confirm-token`,
    method: 'post',
    data
  })
}

export function sendEquipmentCommand(robotId, data) {
  return request({
    url: `/api/control/robots/${robotId}/commands`,
    method: 'post',
    data
  })
}

export function startLiveRecording(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/recordings/start`,
    method: 'post',
    headers
  })
}

export function stopLiveRecording(sessionId, fileId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/recordings/${fileId}/stop`,
    method: 'post',
    headers
  })
}
export function getActiveLiveRecording(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/recordings/active`,
    method: 'get',
    headers
  })
}

// 控制模式 导航模式 NAVIGATION 手动模式 MANUAL
export function setControlMode(data) {
  return request({
    url: `/api/control/robots/${data.robotId}/control-mode`,
    method: 'post',
    data: {
      controlMode: data.controlMode
    }
  })
}
// 告警处置 立即处置：IMMEDIATE_DISPOSAL 误报：FALSE_ALARM
export function executeAlarm(data) {
  return request({
    url: `/api/bigscreen/panorama/alarms/${data.alarmId}/disposal`,
    method: 'post',
    data: {
      disposalStatus: data.disposalStatus
    }
  })
}

