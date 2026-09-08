import { mapActions } from "vuex";
import { events, getFullscreenStatus, handleKeydown, toggleFullscreen } from "../../../../../utils/fullscreen";

export default {
  data() {
    return {
      ZQL_videosInfos: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的视频信息
      ZQL_playingSource: {},
      isFullscreen: false,
      started: false,
      disabled: false
    }
  },
  mounted() {
    this.addEventListeners()
    this.checkFullscreenStatus()
  },
  methods: {
    ...mapActions('websocketRobot', ['startCamera', 'stopCamera']),
    toggleFullscreen,
    streamViewerIds() {
      const consumerId = this.streamConsumerId || this.prefixId || 'default'
      const prefixId = this.prefixId
      return { consumerId, prefixId }
    },
    async updateInfo() {
      if (this.disabled) return
      // console.log('%c更新+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++', 'color: #00f');
      for (const cameraIndex of this.robot?.cameras?.keys() || []) {
          const camera = this.robot.cameras[cameraIndex]
          const key = `${this.robot.robotId}_${cameraIndex}`
          if (!this.ZQL_playingSource) {
            this.ZQL_playingSource = { }
            this.ZQL_videosInfos = { }
          }
          this.$set(this.ZQL_videosInfos, key, { ...this.ZQL_videosInfos[key], robot: this.robot, ...camera});
          this.$set(this.ZQL_playingSource, key, camera.key);
        }
      if (!this.started) return
      this.started = false
      await this.startAll()
    },
    rebindCameraTracks(cameras) {
      this.$nextTick(() => {
        (cameras || []).forEach(camera => {
          if (!camera) return
          const video = document.getElementById(this.prefixId + camera.key)
          const audio = document.getElementById(this.prefixId + camera.key + '-audio')
          if (camera.remoteVideoTrack && video) camera.remoteVideoTrack.attach(video)
          if (camera.remoteAudioTrack && audio) camera.remoteAudioTrack.attach(audio)
        })
      })
    },
    async startAll() {
      const { consumerId, prefixId } = this.streamViewerIds()
      for (const cameraKey in this.ZQL_playingSource) {
        const robot = Object.assign({}, this.ZQL_videosInfos[cameraKey].robot)
        const camera = Object.assign({}, this.ZQL_videosInfos[cameraKey])
        await this.startCamera({ robot, camera, consumerId, prefixId })
      }
    },
    async stopAll() {
      const { consumerId, prefixId } = this.streamViewerIds()
      const cameras = [...(this.robot?.cameras || [])]
      this.$set(this, 'ZQL_playingSource', null);
      this.$set(this, 'ZQL_videosInfos', null);
      for (const camera of cameras) {
        if (!camera?.key) continue
        await this.stopCamera({ ...camera, consumerId, prefixId })
      }
    },
    // 检查全屏状态
    checkFullscreenStatus() {
      this.isFullscreen = getFullscreenStatus()
    },
    addEventListeners() {
      events.forEach(event => {
        document.removeEventListener(event, this.checkFullscreenStatus)
      })
      document.removeEventListener('keydown', e => handleKeydown(e, this.isFullscreen))
    },
    removeEventListeners() {
      events.forEach(event => {
        document.removeEventListener(event, this.checkFullscreenStatus)
      })
      document.removeEventListener('keydown', e => handleKeydown(e, this.isFullscreen))
    }
  },
  async beforeDestroy() {
    this.removeEventListeners();
    if (this.started || this.visible) {
      try {
        await this.stopAll()
      } catch (e) {
        console.warn('[RemoteControl] beforeDestroy stopAll failed', e)
      }
      this.started = false
    }
  }
}
