const clientIdKey = 'robot-media-client-id'
const existingClientId = window.sessionStorage.getItem(clientIdKey)
const generatedClientId = `web-${Date.now()}-${Math.random().toString(16).slice(2)}`

export const mediaClientId = existingClientId || generatedClientId

if (!existingClientId) {
  window.sessionStorage.setItem(clientIdKey, mediaClientId)
}
