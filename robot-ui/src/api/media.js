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
  if (data.sourceType === 'FIXED_CAMERA') {
    return request({
      url: `/api/bigscreen/control/fixed-cameras/${data.robotId}/video/start`,
      method: 'post',
      data: payload
    })
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
export function stopVideoSession(sessionId, options = {}) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/stop`,
    method: 'post',
    ...options
  })
}

// 视频会话心跳
export function heartbeatVideoSession(sessionId) {
  return request({
    url: `/api/bigscreen/control/video-sessions/${sessionId}/heartbeat`,
    method: 'post',
    timeout: 4000,
    skipErrorMessage: true
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

const MANUAL_MEDIA_SOURCES = Object.freeze({
  IMAGE: 'WEB_SNAPSHOT',
  VIDEO: 'LIVEKIT_EGRESS'
})

export function getManualMediaFiles(fileType, params = {}) {
  return getFiles({ ...params, fileType, source: MANUAL_MEDIA_SOURCES[fileType] })
}

export function fileDownloadUrl(fileId, inline = false) {
  return request({
    url: `/api/bigscreen/control/files/${encodeURIComponent(fileId)}/download-url`,
    method: 'post',
    params: inline ? { inline: true } : undefined
  }).then(response => {
    const nested = response && response.data
    const rawDownloadUrl = (response && response.downloadUrl) || (nested && nested.downloadUrl) || (response && response.url) || (nested && nested.url)
    const downloadUrl = withBigscreenApiPrefix(rawDownloadUrl)
    const next = { ...response, downloadUrl }
    if (nested && typeof nested === 'object') {
      next.data = { ...nested, downloadUrl }
    }
    return next
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

export async function getFileInlineUrl(fileId) {
  const response = await fileDownloadUrl(fileId, true)
  return {
    url: response?.downloadUrl || response?.data?.downloadUrl || '',
    expiresAt: response?.expiresAt || response?.data?.expiresAt || null
  }
}

export async function createFileObjectUrl(fileId) {
  return (await getFileInlineUrl(fileId)).url
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
    timeout: 4000,
    skipErrorMessage: true
  })
}

export function getFilePlayUrl(fileId) {
  return request({
    url: `/api/bigscreen/control/files/${fileId}/play-url`,
    method: 'post',
    headers
  }).then(response => {
    const nested = response && response.data
    const rawPlayUrl = (response && response.playUrl) || (nested && nested.playUrl)
    const playUrl = withBigscreenApiPrefix(rawPlayUrl)
    const next = { ...response, playUrl }
    if (nested && typeof nested === 'object') {
      next.data = { ...nested, playUrl }
    }
    return next
  })
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
    headers,
    timeout: 4000,
    skipErrorMessage: true
  })
}

// 控制模式请求与状态统一使用“导航模式”“手动模式”
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
