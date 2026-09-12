const VIEWER_RECONNECT_MAX_DELAY_MS = 30000

export function isSameLiveKitTrack(current, eventTrack) {
  if (!current || !eventTrack) return false
  if (current === eventTrack) return true
  return Boolean(current.sid && eventTrack.sid && current.sid === eventTrack.sid)
}

export function viewerReconnectDelay(attempt) {
  const exponent = Math.max(0, Number(attempt) || 0)
  return Math.min(1000 * (2 ** exponent), VIEWER_RECONNECT_MAX_DELAY_MS)
}
