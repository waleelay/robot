import axios from 'axios'

const client = axios.create({
  baseURL: process.env.VUE_APP_API_BASE || '',
  timeout: 15000,
  headers: {
    'X-User-Id': 'u1001',
    'X-Org-Id': 'org001',
    'X-Roles': 'MEDIA_VIEWER,MEDIA_OPERATOR'
  }
})

export function createVideoSession(data) {
  return client.post('/api/media/video-sessions', data).then(res => res.data)
}

export function getViewerToken(sessionId) {
  return client.post(`/api/media/video-sessions/${sessionId}/token`).then(res => res.data)
}

export function stopVideoSession(sessionId) {
  return client.post(`/api/media/video-sessions/${sessionId}/stop`).then(res => res.data)
}

export function switchChannel(sessionId, data) {
  return client.post(`/api/media/video-sessions/${sessionId}/switch-channel`, data).then(res => res.data)
}

export function createSnapshot(sessionId, data) {
  return client.post(`/api/media/video-sessions/${sessionId}/snapshots`, data).then(res => res.data)
}

export function getSessionEvents(sessionId) {
  return client.get(`/api/media/video-sessions/${sessionId}/events`).then(res => res.data)
}

export function mockClientAcked(sessionId) {
  return client.post(`/api/media/video-sessions/${sessionId}/_mock/client-acked`).then(res => res.data)
}

export function mockTrackPublished(sessionId, trackSid) {
  return client.post(`/api/media/video-sessions/${sessionId}/_mock/track-published/${trackSid}`).then(res => res.data)
}
