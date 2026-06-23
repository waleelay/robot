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
export function createSnapshot(sessionId, data) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/snapshots`,
    method: 'post',
    data
  })
}

export function createSnapshotFile(sessionId, data) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/snapshots/file`,
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 30000
  })
}
export function snapshotImageUrl(snapshotId) {
  const base = process.env.VUE_APP_API_BASE || ''
  return request({
    url: `${base}/api/control/snapshots/${snapshotId}/image`,
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

// 获取会话事件
export function getSessionEvents(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/events`,
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

export function getRecordings(params) {
  return request({
    url: '/api/control/recordings',
    method: 'get',
    params
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
    method: 'post'
  })
}

export function stopLiveRecording(sessionId, recordingId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/recordings/${recordingId}/stop`,
    method: 'post'
  })
}
export function getActiveLiveRecording(sessionId) {
  return request({
    url: `/api/control/video-sessions/${sessionId}/recordings/active`,
    method: 'get'
  })
}
