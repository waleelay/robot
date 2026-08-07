import request from '@/utils/request'
import { mediaClientId } from '@/utils/media-client-id'
import { withApiPrefix, withBigscreenApiPrefix } from '@/utils/api-url'

// 每个浏览器标签页拥有独立 clientId，但同一标签页刷新后保持不变。
// 这对控制权续用很重要：刷新页面不能被后端误判成另一个终端。
export { mediaClientId }

const headers = {
  'X-Client-Id': mediaClientId
}

const sessionHeaders = {
  'X-Client-Id': mediaClientId
}

// 创建视频会话
export function createVideoSession(data) {
  const payload = {
    quality: data.quality,
    reuse: data.reuse,
    clientRequestId: data.clientRequestId
  }
  return request({
    url: `/api/bigscreen/control/robots/${data.robotId}/cameras/${data.deviceId}/video/start`,
    method: 'post',
    data: payload
  })
}

// 获取活跃视频会话
export function getActiveVideoSessions() {
  return request({
    url: '/api/bigscreen/control/video-sessions/active',
    method: 'get'
  })
}

// 获取观看令牌
export function getViewerToken(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/token`,
    method: 'post'
  })
}

// 停止视频会话
export function stopVideoSession(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/stop`,
    method: 'post'
  })
}

// 视频会话心跳
export function heartbeatVideoSession(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/heartbeat`,
    method: 'post'
  })
}

// 重启视频会话
export function restartVideoSession(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/restart`,
    method: 'post'
  })
}

// 切换频道
export function switchChannel(sessionId, data) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/switch-channel`,
    method: 'post',
    data
  })
}

// 创建快照
export function uploadFile(data, timeout = 30000) {
  return request({
    url: '/api/bigscreen/control/files',
    method: 'post',
    data,
    timeout
  })
}

export function transferMultiFunctionAudio(robotId, deviceId, fileId) {
  return request({
    url: `/api/bigscreen/control/robots/${encodeURIComponent(robotId)}/devices/${encodeURIComponent(deviceId)}/audio-file-transfers`,
    method: 'post',
    data: { fileId }
  })
}

export function getFiles(params = {}) {
  return request({
    url: '/api/bigscreen/control/files',
    method: 'get',
    params
  })
}

export function fileDownloadUrl(fileId) {
  return request({
    url: `/api/bigscreen/control/files/${fileId}/download-url`,
    method: 'post'
  })
}

export function getFileContent(fileId) {
  return request({
    url: `/api/bigscreen/control/files/${encodeURIComponent(fileId)}/content`,
    method: 'get',
    responseType: 'blob',
    skipErrorMessage: true
  })
}

export async function createFileObjectUrl(fileId) {
  const data = await getFileContent(fileId)
  const blob = data instanceof Blob ? data : new Blob([data])
  return URL.createObjectURL(blob)
}

export function revokeFileObjectUrl(url) {
  if (typeof url === 'string' && url.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}

export function deleteFile(fileId) {
  return request({
    url: `/api/bigscreen/control/files/${fileId}`,
    method: 'delete'
  })
}

export function deleteFiles(fileIds) {
  return request({
    url: '/api/bigscreen/control/files/batch',
    method: 'delete',
    data: { fileIds }
  })
}
export function snapshotImageUrl(fileId) {
  const base = (process.env.VUE_APP_BASE_ORIGIN || (typeof window !== 'undefined' ? window.location.origin : '')).replace(/\/$/, '')
  return `${base}${withApiPrefix(`/api/bigscreen/control/files/${encodeURIComponent(fileId)}/content`)}`
}

export function stopIntercom(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/intercom/stop`,
    method: 'post',
    headers: sessionHeaders,
    skipErrorMessage: true
  })
}
export function startCameraIntercom(data) {
  return request({
    url: `/api/bigscreen/control/robots/${data.robotId}/cameras/${data.deviceId}/video/intercom/start`,
    method: 'post',
    data: {
      quality: data.quality,
      reuse: true
    },
    headers: sessionHeaders,
    skipErrorMessage: true
  })
}

export function startSessionIntercom(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/intercom/start`,
    method: 'post',
    headers: sessionHeaders,
    skipErrorMessage: true
  })
}
export function heartbeatIntercom(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/intercom/heartbeat`,
    method: 'post',
    headers: sessionHeaders,
    skipErrorMessage: true
  })
}

export function getFilePlayUrl(fileId) {
  return request({
    url: `/api/bigscreen/control/files/${fileId}/play-url`,
    method: 'post',
    headers
  }).then(response => ({
    ...response,
    playUrl: withBigscreenApiPrefix(response && response.playUrl)
  }))
}

// ==============================================================远程控制=================================================================
// 本体控制
// 双光云台
export function getControlProfile(robotId) {
  return request({
    url: `/api/bigscreen/control/robots/${robotId}/control-profile`,
    method: 'get'
  })
}

export function acquireControl(robotId, data) {
  return request({
    url: `/api/bigscreen/control/robots/${robotId}/control-sessions/acquire`,
    method: 'post',
    data
  })
}

export function takeoverControl(robotId, data) {
  return request({
    url: `/api/bigscreen/control/robots/${robotId}/control-sessions/takeover`,
    method: 'post',
    data
  })
}

export function releaseControl(robotId, controlSessionId, data) {
  return request({
    url: `/api/bigscreen/control/robots/${robotId}/control-sessions/${controlSessionId}/release`,
    method: 'post',
    data: data || {}
  })
}

export function createConfirmToken(robotId, data) {
  return request({
    url: `/api/bigscreen/control/robots/${robotId}/commands/confirm-token`,
    method: 'post',
    data
  })
}

export function sendEquipmentCommand(robotId, data) {
  return request({
    url: `/api/bigscreen/control/robots/${robotId}/commands`,
    method: 'post',
    data
  })
}

export function startLiveRecording(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/recordings/start`,
    method: 'post',
    headers
  })
}

export function stopLiveRecording(sessionId, fileId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/recordings/${fileId}/stop`,
    method: 'post',
    headers
  })
}
export function getActiveLiveRecording(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/recordings/active`,
    method: 'get',
    headers
  })
}

// 控制模式 导航模式 NAVIGATION 手动模式 MANUAL
export function setControlMode(data) {
  return request({
    url: `/api/bigscreen/control/robots/${data.robotId}/control-mode`,
    method: 'post',
    data: {
      controlMode: data.controlMode,
      controlSessionId: data.controlSessionId,
      observedStateSeq: data.observedStateSeq
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
