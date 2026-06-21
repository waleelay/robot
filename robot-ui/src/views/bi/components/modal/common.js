import { mapActions } from "vuex";
import { events, getFullscreenStatus, handleKeydown, toggleFullscreen } from "../../../../utils/fullscreen";

export default {
  data() {
    return {
      ZQL_videosInfos: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的视频信息
      ZQL_playingSource: {},
      isFullscreen: false,
      started: false
    }
  },
  mounted() {
    this.addEventListeners()
    this.checkFullscreenStatus()
  },
  methods: {
    ...mapActions('websocketRobot', ['startCamera', 'stopCamera']),
    toggleFullscreen,
    async updateInfo() {
      // console.log('%c更新===================================================================================================', 'color: #00f');
      for (const robotIndex of this.robotList.keys()) {
        const robot = this.robotList[robotIndex]
        for (const cameraIndex of robot.cameras.keys()) {
          const camera = robot.cameras[cameraIndex]
          const key = `${robot.robotId}_${robotIndex}_${cameraIndex}`
          if (!this.ZQL_playingSource) {
            this.ZQL_playingSource = { }
            this.ZQL_videosInfos = { }
          }
          this.$set(this.ZQL_videosInfos, key, { robot, ...this.ZQL_videosInfos[key], ...camera});            
          this.$set(this.ZQL_playingSource, key, camera.key);
        }
      }
      if (!this.started) return
      this.started = false
      await this.startAll()
    },
    rebindCameraTracks(cameras) {
      this.$nextTick(() => {
        const cameraList = cameras || []
        cameraList.forEach(camera => {
          if (!camera) return
          const video = document.getElementById(this.prefixId + camera.key)
          const audio = document.getElementById(this.prefixId + camera.key + '-audio')
          if (camera.remoteVideoTrack && video) camera.remoteVideoTrack.attach(video)
          if (camera.remoteAudioTrack && audio) camera.remoteAudioTrack.attach(audio)
        })
      })
    },
    async startAll() {
      // console.log(123)
      for (const cameraKey in this.ZQL_playingSource) {
        // cameraKey === 'robot-001_0_0'
        const robot = Object.assign({}, this.ZQL_videosInfos[cameraKey].robot)
        const camera = Object.assign({}, this.ZQL_videosInfos[cameraKey])
        await this.startCamera({ robot, camera })
      }
    },
    async stopAll() {
      this.$set(this, 'ZQL_playingSource', null);
      this.$set(this, 'ZQL_videosInfos', null);
      for (const robot of this.robotList) {
        for (const camera of robot.cameras) {
          console.log('关闭');
          await this.stopCamera(camera)
        }
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
  beforeDestroy() {
    // 移除键盘事件监听
    this.removeEventListeners();
  }
}
