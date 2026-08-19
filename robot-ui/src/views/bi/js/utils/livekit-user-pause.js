/** LiveKit 用户暂停：pause()停不住画面，需同时关掉 MediaStreamTrack */

export function isUserPaused(el) {
  return !!(el && el.dataset && el.dataset.userPaused === '1')
}

export function setUserPausedFlag(el, paused) {
  if (!el) return
  if (paused) el.dataset.userPaused = '1'
  else delete el.dataset.userPaused
}

export function listVideoMediaTracks(el, livekitTrack) {
  const tracks = []
  const live = livekitTrack && livekitTrack.mediaStreamTrack
  if (live) tracks.push(live)
  const stream = el && el.srcObject
  if (stream && typeof stream.getVideoTracks === 'function') {
    stream.getVideoTracks().forEach(item => {
      if (tracks.indexOf(item) < 0) tracks.push(item)
    })
  }
  return tracks
}

export function setMediaTracksEnabled(el, livekitTrack, enabled) {
  listVideoMediaTracks(el, livekitTrack).forEach(item => {
    item.enabled = enabled
  })
}

/** attach 后遵守用户暂停；play=false 时不自动 play */
export function attachTrackRespectingUserPause(track, el, play = true) {
  if (!track || !el || typeof track.attach !== 'function') return false
  track.attach(el)
  if (isUserPaused(el)) {
    setMediaTracksEnabled(el, track, false)
    if (typeof el.pause === 'function') el.pause()
    return true
  }
  setMediaTracksEnabled(el, track, true)
  if (play && typeof el.play === 'function') el.play().catch(() => {})
  return true
}

export function applyUserPausedToElement(el, livekitTrack, paused) {
  setUserPausedFlag(el, paused)
  setMediaTracksEnabled(el, livekitTrack, !paused)
  if (!el) return
  if (paused) {
    if (typeof el.pause === 'function') el.pause()
  } else if (typeof el.play === 'function') {
    el.play().catch(() => {})
  }
}
