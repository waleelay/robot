function attachTrack(track, el) {
  if (!track || !el || typeof track.attach !== 'function') return
  track.attach(el)
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
    }
  }
}
