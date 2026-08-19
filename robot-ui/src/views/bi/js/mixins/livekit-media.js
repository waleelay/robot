import {
  applyUserPausedToElement,
  attachTrackRespectingUserPause
} from '../utils/livekit-user-pause'

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
        if (next) attachTrackRespectingUserPause(next, el, true)
        else if (el) el.srcObject = null
      })
    },
    attachLiveKitVideo() {
      attachTrackRespectingUserPause(
        this.cameraInfo && this.cameraInfo.remoteVideoTrack,
        this.$refs.videoEl,
        true
      )
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
      applyUserPausedToElement(
        this.$refs.videoEl,
        this.cameraInfo && this.cameraInfo.remoteVideoTrack,
        paused
      )
    }
  }
}
