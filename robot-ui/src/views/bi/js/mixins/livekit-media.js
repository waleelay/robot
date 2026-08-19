function isUserPaused(el) {
  return !!(el && el.dataset && el.dataset.userPaused === '1')
}

function setUserPausedFlag(el, paused) {
  if (!el) return
  if (paused) el.dataset.userPaused = '1'
  else delete el.dataset.userPaused
}

function listVideoMediaTracks(el, livekitTrack) {
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

function setMediaTracksEnabled(el, livekitTrack, enabled) {
  listVideoMediaTracks(el, livekitTrack).forEach(item => {
    item.enabled = enabled
  })
}

function attachTrack(track, el) {
  if (!track || !el || typeof track.attach !== 'function') return
  track.attach(el)
  if (isUserPaused(el)) {
    setMediaTracksEnabled(el, track, false)
    if (typeof el.pause === 'function') el.pause()
    return
  }
  setMediaTracksEnabled(el, track, true)
  if (typeof el.play === 'function') el.play().catch(() => {})
}

function detachTrack(track, el) {
  if (track && el && typeof track.detach === 'function') {
    track.detach(el)
  }
  if (el) el.srcObject = null
}

// 格子 video 对 store 里的 LiveKit 视频轨做响应式挂载：DOM 出现或 track 到达后补挂，销毁时摘掉。
export default {
  watch: {
    'cameraInfo.remoteVideoTrack'(next, previous) {
      this.syncLiveKitVideoTrack(next, previous)
    },
    cameraMediaKey() {
      this.$nextTick(() => this.attachLiveKitVideo())
    }
  },
  mounted() {
    this.attachLiveKitVideo()
  },
  beforeDestroy() {
    this.detachLiveKitVideo()
  },
  methods: {
    syncLiveKitVideoTrack(next, previous) {
      this.$nextTick(() => {
        const el = this.$refs.videoEl
        if (previous && previous !== next) detachTrack(previous, el)
        if (next) attachTrack(next, el)
        else if (el) el.srcObject = null
      })
    },
    attachLiveKitVideo() {
      attachTrack(this.cameraInfo && this.cameraInfo.remoteVideoTrack, this.$refs.videoEl)
    },
    detachLiveKitVideo() {
      detachTrack(this.cameraInfo && this.cameraInfo.remoteVideoTrack, this.$refs.videoEl)
    },
    /** LiveKit 实时流 pause() 停不住画面，需同时关掉 MediaStreamTrack */
    toggleUserPaused() {
      const slotKey = `slot_${this.index}`
      const videoInfo = this.ZQL_videosInfos && this.ZQL_videosInfos[slotKey]
      if (!videoInfo) return
      const pausing = !videoInfo.isPaused
      this.$set(videoInfo, 'isPaused', pausing)
      this.applyUserPaused(pausing)
    },
    applyUserPaused(paused) {
      const el = this.$refs.videoEl
      const track = this.cameraInfo && this.cameraInfo.remoteVideoTrack
      setUserPausedFlag(el, paused)
      setMediaTracksEnabled(el, track, !paused)
      if (!el) return
      if (paused) {
        if (typeof el.pause === 'function') el.pause()
      } else if (typeof el.play === 'function') {
        el.play().catch(() => {})
      }
    }
  }
}
