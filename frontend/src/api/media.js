import axios from 'axios'

const clientId = `web-${Date.now()}-${Math.random().toString(16).slice(2)}`

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

export function createVideoSession(data) {
  const payload = {
    channel: data.channel,
    quality: data.quality,
    reuse: data.reuse,
    clientRequestId: data.clientRequestId
  }
  return client.post(`/api/control/robots/${data.robotId}/cameras/${data.deviceId}/video/start`, payload).then(res => res.data)
}

export function getRobots() {
  return client.get('/api/control/robots').then(res => res.data)
}

export function getActiveVideoSessions() {
  return client.get('/api/control/video-sessions/active').then(res => res.data)
}

export function getViewerToken(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/token`).then(res => res.data)
}

export function startCameraIntercom(data) {
  return client.post(`/api/control/robots/${data.robotId}/cameras/${data.deviceId}/video/intercom/start`, {
    channel: data.channel,
    quality: data.quality,
    reuse: true
  }).then(res => res.data)
}

export function startSessionIntercom(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/intercom/start`).then(res => res.data)
}

export function heartbeatIntercom(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/intercom/heartbeat`).then(res => res.data)
}

export function stopIntercom(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/intercom/stop`).then(res => res.data)
}

export function stopVideoSession(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/stop`).then(res => res.data)
}

export function heartbeatVideoSession(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/heartbeat`).then(res => res.data)
}

export function restartVideoSession(sessionId) {
  return client.post(`/api/control/video-sessions/${sessionId}/restart`).then(res => res.data)
}

export function switchChannel(sessionId, data) {
  return client.post(`/api/control/video-sessions/${sessionId}/switch-channel`, data).then(res => res.data)
}

export function createSnapshot(sessionId, data) {
  return client.post(`/api/control/video-sessions/${sessionId}/snapshots`, data).then(res => res.data)
}

export function getSessionSnapshots(sessionId) {
  return client.get(`/api/control/video-sessions/${sessionId}/snapshots`).then(res => res.data)
}

export function getSessionEvents(sessionId) {
  return client.get(`/api/control/video-sessions/${sessionId}/events`).then(res => res.data)
}

export function mockTrackPublished(sessionId, trackSid) {
  return client.post(`/internal/media/video-sessions/${sessionId}/_mock/track-published/${trackSid}`).then(res => res.data)
}
