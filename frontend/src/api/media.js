import axios from 'axios'

// 每个浏览器标签页拥有独立 clientId，但同一标签页刷新后保持不变。
// 这对控制权续用很重要：刷新页面不能被后端误判成另一个终端。
const clientIdKey = 'robot-media-client-id'
const existingClientId = window.sessionStorage.getItem(clientIdKey)
const clientId = existingClientId || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
if (!existingClientId) {
  window.sessionStorage.setItem(clientIdKey, clientId)
}
export const mediaClientId = clientId

// 调试台暂时用请求头模拟网关/登录态注入的用户上下文。
// 后端 CurrentUserResolver 会从这些头里解析 user/org/role/clientId。
const client = axios.create({
  baseURL: process.env.VUE_APP_API_BASE || '',
  timeout: 15000,
  headers: {
    'X-User-Id': 'u1001',
    'X-Org-Id': 'org001',
    'X-Roles': 'MEDIA_VIEWER,MEDIA_OPERATOR',
    'X-Client-Id': clientId
  }
})

// 控制侧的“开始观看”入口。
// 返回值是媒体服务中的 VideoSessionResponse，可能是新建会话，也可能复用已有会话。
export function createVideoSession(data) {
  const payload = {
    channel: data.channel,
    quality: data.quality,
    reuse: data.reuse,
    clientRequestId: data.clientRequestId
  }
  return client.post(`/api/control/robots/${data.robotId}/cameras/${data.deviceId}/video/start`, payload).then(res => res.data)
}

// 大屏统一从全景地图聚合接口获取机器人列表；BFF 内部会合并控制侧机器人数据。
export function getRobots() {
  return client.get('/api/bigscreen/panorama/overview').then(res => (res.data && res.data.devices) || [])
}

export function getControlProfile(robotId) {
  return client.get(`/api/control/robots/${robotId}/control-profile`).then(res => res.data)
}

export function acquireControl(robotId, data) {
  return client.post(`/api/control/robots/${robotId}/control-sessions/acquire`, data).then(res => res.data)
}

export function takeoverControl(robotId, data) {
  return client.post(`/api/control/robots/${robotId}/control-sessions/takeover`, data).then(res => res.data)
}

export function releaseControl(robotId, controlSessionId, data) {
  return client.post(`/api/control/robots/${robotId}/control-sessions/${controlSessionId}/release`, data || {}).then(res => res.data)
}

export function createConfirmToken(robotId, data) {
  return client.post(`/api/control/robots/${robotId}/commands/confirm-token`, data).then(res => res.data)
}

export function sendEquipmentCommand(robotId, data) {
  return client.post(`/api/control/robots/${robotId}/commands`, data).then(res => res.data)
}

// 拉取当前仍可观看/复用的实时视频会话，用于调试台恢复页面时展示活跃会话。
export function getActiveVideoSessions() {
  return client.get('/api/control/video-sessions/active').then(res => res.data)
}

// 浏览器真正连接 LiveKit 前需要先换取短期 viewer token。
// token 不长期放在前端状态里，失效或重连时重新请求。
export function getViewerToken(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/token`).then(res => res.data)
}

// 从摄像头入口发起对讲。若尚无视频会话，后端会创建一个仅承载对讲的房间。
export function startCameraIntercom(data) {
  return client.post(`/api/control/robots/${data.robotId}/cameras/${data.deviceId}/video/intercom/start`, {
    channel: data.channel,
    quality: data.quality,
    reuse: true
  }).then(res => res.data)
}

// 在已有视频会话上打开对讲，复用同一个 LiveKit Room，避免视频和音频拆到不同房间。
export function startSessionIntercom(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/intercom/start`).then(res => res.data)
}

// 对讲心跳用于维持“当前操作员占用”状态，超时后后端会释放并通知机器人停止音频桥。
export function heartbeatIntercom(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/intercom/heartbeat`).then(res => res.data)
}

export function stopIntercom(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/intercom/stop`).then(res => res.data)
}

export function stopVideoSession(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/stop`).then(res => res.data)
}

// 观看心跳维持 viewerCount；后端扫到心跳过期后会把 viewer 标记离开。
export function heartbeatVideoSession(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/heartbeat`).then(res => res.data)
}

// 前端检测到 LiveKit 断开或 track 消失时触发重启，后端会重新下发机器人推流命令。
export function restartVideoSession(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/restart`).then(res => res.data)
}

export function switchChannel(sessionId, data) {
  return client.post(`/api/control/video-sessions/${sessionId}/switch-channel`, data).then(res => res.data)
}

export function createSnapshot(sessionId, data) {
  return client.post(`/api/control/video-sessions/${sessionId}/snapshots`, data).then(res => res.data)
}

export function createSnapshotFile(sessionId, data) {
  return client.post(`/api/control/video-sessions/${sessionId}/snapshots/file`, data, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 30000
  }).then(res => res.data)
}

export function getSessionSnapshots(sessionId) {
  return client.get(`/api/control/video-sessions/${sessionId}/snapshots`).then(res => res.data)
}

export function getCameraSnapshots(robotId, deviceId) {
  return client.get(`/api/control/robots/${robotId}/cameras/${deviceId}/snapshots`).then(res => res.data)
}

export function snapshotImageUrl(robotId, deviceId, snapshotId) {
  const base = process.env.VUE_APP_API_BASE || ''
  return `${base}/api/control/robots/${robotId}/cameras/${deviceId}/snapshots/${snapshotId}/image`
}

export function getSessionEvents(sessionId) {
  return client.get(`/api/control/video-sessions/${sessionId}/events`).then(res => res.data)
}

export function mockTrackPublished(sessionId, trackSid) {
  return client.post(`/internal/media/video-sessions/${sessionId}/_mock/track-published/${trackSid}`).then(res => res.data)
}

// 录像列表只展示 READY 项给回放页；上传中、转码中等状态通常由机器人侧或后台任务处理。
export function getRecordings(params) {
  return client.get('/api/control/recordings', { params }).then(res => res.data)
}

// 回放 URL 是后端签名后的 HLS 入口，m3u8/ts 子资源会在后端继续校验 token。
export function getRecordingPlayUrl(recordingId) {
  return client.post(`/api/control/recordings/${recordingId}/play-url`).then(res => res.data)
}

export function startLiveRecording(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/recordings/start`).then(res => res.data)
}

export function stopLiveRecording(sessionId, recordingId) {
  return client.post(`/api/control/video-sessions/${sessionId}/recordings/${recordingId}/stop`).then(res => res.data)
}

export function getActiveLiveRecording(sessionId) {
  return client.get(`/api/control/video-sessions/${sessionId}/recordings/active`).then(res => res.data)
}
