<template>
  <div class="machine-container robot-container new" :class="{ visible }" :style="positionStyle" ref="containerRef">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box">
      <div class="top m4 flx-justify-between">
        <div class="flx-align-center">
          <div class="title ml10">{{currenRobot?.name  || '-'}}</div>
          <div class="status ml10" :class="currenRobot?.statusClass || ''">{{ currenRobot?.customStatusName || currenRobot?.status || '-' }}</div>
        </div>
        <div class="close mr10" @click="onClose()">
          <svg-icon icon-class="close"></svg-icon>
        </div>
      </div>
      <div class="info-content pr10 pl10 flex flex-wrap mt15">
        <div class="item wp156">
          装备类型：<span class="value">{{ currenRobot?.type || '-' }}</span>
        </div>
        <div class="item wp149 ml26">
          当前电量：<span class="value">{{ currenRobot?.battery || '-' }}%</span>
        </div>
        <div class="item wp156 mt10">
          装备型号：<span class="value">{{ currenRobot?.model || '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          是否告警：<span class="value">{{ currenRobot?.alarmLevel === 'none' ? '否' : '是' }}</span>
        </div>
        <div class="item wp156 mt10">
          控制模型：<span class="value">{{ currenRobot?.controlMode === '手动模式' ? '手动控制' : currenRobot?.controlMode === '导航模式' ? '自动控制' : '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          上装设备：<span class="value">{{ currenRobot?.mountedDeviceCount || 0 }}个</span>
        </div>
        <div class="item wp156 mt10">
          当前速度：<span class="value">{{ currenRobot?.speed || 0 }}m/s</span>
        </div>
        <div class="mt10 with-divider w100"></div>
        <div v-for="(task, index) in taskList" :key="task.taskId" class="mt10 task flex" :class="task.status" :title="task?.statusName">
          <div class="item wp156 text-ellipsis" :title="task?.name || ''">
            <span class="wp60 tar">任务{{index + 1}}：</span><span class="value">{{ task?.name || '-' }}</span>
          </div>
          <div class="item wp149 ml26">
            任务时段：<span class="value">{{ task?.timeRange || '-' }}</span>
          </div>
        </div>
      </div>
      <!-- 固定摄像头：仅显示/关闭画面；视频区始终占位，避免高度变化导致相对装备错位 -->
      <template v-if="isFixedCamera">
        <div class="btns mt10 ml0 flx-align-center flex-wrap wp360" style="margin-top: -10px !important">
          <el-button type="primary" class="mt20" :disabled="videoToggling" @click="toggleFixedCameraVideo">
            {{ videoVisible ? '关闭画面' : '显示画面' }}
          </el-button>
        </div>
        <div class="fixed-camera-video mt10 mb20 flx-center">
          <div class="fixed-camera-video__inner flx-center">
            <video
              v-show="videoVisible && fixedCameraInfo?.key"
              :id="fixedCameraVideoId"
              class="fixed-camera-video__stream"
              autoplay
              muted
              playsinline
              preload="auto"
            />
            <audio
              v-if="fixedCameraInfo?.key"
              :id="prefixId + fixedCameraInfo.key + '-audio'"
              autoplay
            />
            <div
              v-if="videoVisible && fixedCameraInfo && !fixedCameraInfo.hasVideo"
              class="fixed-camera-video__status flx-center flex-column"
            >
              <svg-icon :icon-class="videoStatusIcon" style="font-size: 16px;" />
              <span class="mt2">{{ videoStatusText }}</span>
            </div>
            <div v-else-if="!videoVisible" class="fixed-camera-video__placeholder flx-center">
              暂无画面
            </div>
          </div>
        </div>
      </template>
      <div v-else class="btns mt10 mb20 ml0 flx-align-center flex-wrap wp360" style="margin-top: -10px !important">
        <el-button v-if="showAnimate && showControl" type="primary" class="mt20" @click="$emit('showControlPart')">远程控制</el-button>
        <!-- <el-button type="primary" class="mt20" @click="$emit('showSlam', true)">SLAM地图</el-button> -->
        <el-button v-if="showAnimate && showControl && currenRobot?.runningTaskId && globalMapId === 'gis'" type="primary" class="mt20" @click="$emit('showSlam', true)">SLAM地图</el-button>
        <el-button v-if="showAnimate && showControl" type="primary" class="mt20" @click="onShutdown()">一键返航</el-button>
        <el-button v-if="showAnimate && showControl" type="primary" class="mt20" @click="onStartup()">退出充电桩</el-button>
        <!-- <el-button type="primary" @click="onAddTask()">添加任务</el-button> -->
        <el-button v-if="hasTaskPath || globalMapId === 'gis'" type="primary" class="mt20" @click="togglePath()">显示路径</el-button>
        <!-- <el-button v-if="globalMapId === 'gis'" type="primary" class="mt20" @click="$emit('showArea', true)">显示区域</el-button> -->
      </div>
    </div>
    <!-- <div class="guideline wp157 hp29 mt9 ml161">
      <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
    </div> -->
    <img
      v-if="!showAnimate"
      ref="guidelineRef"
      width="197"
      height="47"
      class="robot1-guideline"
      src="@/assets/images/new-bi/guideline.png"
      alt=""
    >
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import gsap from './gsap.js';
import { getDescArr } from '../../../../../utils/index.js';
export default {
  name: 'Modal',
  mixins: [gsap],
  data() {
    return {
      className: '',
      pathVisible: false,
      videoVisible: false,
      videoToggling: false,
      prefixId: 'robot1-fixed-camera-',
      // 当前弹窗内已开流的相机，切换/关闭时按此引用停流，避免 selectedRobotId 已变更关错流
      playingCamera: null,
    }
  },
  computed: {
    // 指挥中心由父组件 popupStyle 定位，避免 mixin 的 left/top:0 覆盖锚点偏移
    positionStyle() {
      if (!this.showAnimate) return {}
      return {
        left: this.position.left + 'px',
        top: this.position.top + 'px'
      }
    },
    showControl() {
      return this.selectedRobot?.status === 'online'
    },
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    cameras() {
      return this.$store.getters['websocketRobot/getCameras'] || {}
    },
    ...mapState('websocketExtraData', ['robotBaseInfo', 'taskData', 'taskPathPoints', 'globalMapId']),
    currenRobot() {
      return this.robotBaseInfo?.[this.selectedRobotId] || {}
    },
    isFixedCamera() {
      const robot = this.currenRobot || this.selectedRobot || {}
      return robot.sourceType === 'FIXED_CAMERA'
        || robot.typeCode === 'FIXED_CAMERA'
        || robot.equipmentType === 'FIXED_CAMERA'
        || robot.type === 'FIXED_CAMERA'
        || robot.type === '固定摄像头'
    },
    // 固定摄像头主相机（取装备第一路，并合并 store 实时状态）
    fixedCameraInfo() {
      if (!this.isFixedCamera) return null
      const robot = this.selectedRobot || {}
      const basic = (robot.cameras || [])[0]
        || Object.values(this.cameras).find(item => String(item.robotId) === String(this.selectedRobotId))
      if (!basic) return null
      const key = basic.key || `${robot.robotId || this.selectedRobotId}-${basic.deviceId || basic.cameraId}-${basic.cameraId || basic.deviceId}`
      return { ...basic, ...(this.cameras[key] || {}), key }
    },
    fixedCameraVideoId() {
      return this.fixedCameraInfo?.key ? `${this.prefixId}${this.fixedCameraInfo.key}` : ''
    },
    videoStatusIcon() {
      const status = this.fixedCameraInfo?.status
      if (status === 'FAILED' || status === 'TIMEOUT' || status === 'offline') return 'unlink1'
      return 'loading'
    },
    videoStatusText() {
      const info = this.fixedCameraInfo || {}
      if (info.hasVideo) return ''
      if (info.session) return '连接中'
      if (info.status === 'FAILED' || info.status === 'TIMEOUT' || info.status === 'offline') return '连接失败'
      return '连接中'
    },
    taskList() {
      const { task = [] } = this.currenRobot || {}
      return getDescArr(task?.map(item => this.taskData?.[item.taskId] || item) || [], 'timestamp')
    },
    // 装备关联任务路径有点位时才显示「显示路径」按钮
    hasTaskPath() {
      const taskId = this.currenRobot?.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return false
      const pathData = this.taskPathPoints?.[taskId]
      if (!pathData || !Array.isArray(pathData.pathPoints) || !pathData.pathPoints.length) return false
      const mapId = this.globalMapId
      if (mapId && mapId !== 'gis' && pathData.mapId != null && pathData.mapId !== '' &&
        String(pathData.mapId) !== String(mapId)) {
        return false
      }
      return true
    },
  },
  watch: {
    hasTaskPath(val) {
      if (!val && this.pathVisible) {
        this.pathVisible = false
        this.$emit('showPath', false)
      }
    },
    // 指挥中心 Left 会反复把全局 prefixId 改回 home-video，需在 track 就绪后主动挂到本弹窗 video
    'fixedCameraInfo.remoteVideoTrack'(track) {
      if (this.videoVisible && track) this.attachFixedCameraTrack()
    },
    'fixedCameraInfo.hasVideo'(val) {
      if (this.videoVisible && val) this.attachFixedCameraTrack()
    }
  },
  beforeDestroy() {
    this.clearAttachRetry()
    this.stopFixedCameraVideo()
  },
  methods: {
    ...mapActions('websocketRobot', ['setSelectedRobotId', 'startCamera', 'stopCamera', 'setPrefixId']),
    onShutdown() {
      // this.$emit('shutdown')
    },
    onStartup() {
      // this.$emit('startup')
    },
    async onClose() {
      await this.stopFixedCameraVideo()
      this.visible = false
      this.pathVisible = false
      this.$emit('showPath', false)
      this.handleGlobalClick(null, false)
      this.setSelectedRobotId('')
      this.$emit('clear')
    },
    togglePath() {
      this.pathVisible = !this.pathVisible
      this.$emit('showPath', this.pathVisible)
    },
    async toggleFixedCameraVideo() {
      if (this.videoToggling) return
      if (this.videoVisible) {
        await this.stopFixedCameraVideo()
        return
      }
      await this.startFixedCameraVideo()
    },
    attachFixedCameraTrack() {
      const camera = this.cameras[this.playingCamera?.key] || this.fixedCameraInfo
      if (!camera?.key) return false
      const video = document.getElementById(this.prefixId + camera.key)
      const audio = document.getElementById(this.prefixId + camera.key + '-audio')
      let attached = false
      if (camera.remoteVideoTrack && video && typeof camera.remoteVideoTrack.attach === 'function') {
        camera.remoteVideoTrack.attach(video)
        video.play?.().catch?.(() => {})
        attached = true
      }
      if (camera.remoteAudioTrack && audio && typeof camera.remoteAudioTrack.attach === 'function') {
        camera.remoteAudioTrack.attach(audio)
      }
      return attached
    },
    clearAttachRetry() {
      if (this._attachRetryTimer) {
        clearInterval(this._attachRetryTimer)
        this._attachRetryTimer = null
      }
    },
    scheduleAttachRetry() {
      this.clearAttachRetry()
      let tries = 0
      this._attachRetryTimer = setInterval(() => {
        tries += 1
        const ok = this.attachFixedCameraTrack()
        if (ok || tries >= 15 || !this.videoVisible) this.clearAttachRetry()
      }, 200)
    },
    async startFixedCameraVideo() {
      if (!this.isFixedCamera || this.videoToggling) return
      const camera = this.fixedCameraInfo
      if (!camera?.key) {
        this.$message?.warning?.('未找到可播放的摄像头')
        return
      }
      this.videoToggling = true
      const prevPrefixId = this.$store.state.websocketRobot?.prefixId
      try {
        this.videoVisible = true
        this.playingCamera = camera
        this.setPrefixId(this.prefixId)
        await this.$nextTick()
        const robot = {
          ...(this.selectedRobot || {}),
          ...(this.currenRobot || {}),
          sourceType: 'FIXED_CAMERA',
          status: (this.selectedRobot?.status || this.currenRobot?.status) === 'offline' ? 'offline' : 'online'
        }
        await this.startCamera({ robot, camera })
        // 不等 TrackSubscribed 时的全局 prefixId（可能已被指挥中心 Left 覆盖），主动挂载
        this.attachFixedCameraTrack()
        await this.$nextTick()
        this.attachFixedCameraTrack()
        this.scheduleAttachRetry()
      } catch (error) {
        this.videoVisible = false
        this.playingCamera = null
        this.clearAttachRetry()
        this.$message?.error?.(error?.message || '开启画面失败')
      } finally {
        // 播放中仍保留本弹窗 prefix，便于断线重连；若此前有其它页面 prefix 则在关闭时恢复
        this._prevPrefixId = prevPrefixId
        this.videoToggling = false
      }
    },
    async stopFixedCameraVideo() {
      this.clearAttachRetry()
      const camera = this.playingCamera || this.fixedCameraInfo
      this.videoVisible = false
      if (!camera?.key) {
        this.playingCamera = null
        return
      }
      this.videoToggling = true
      try {
        const latest = this.cameras[camera.key] || camera
        const video = document.getElementById(this.prefixId + camera.key)
        if (latest.remoteVideoTrack && video && typeof latest.remoteVideoTrack.detach === 'function') {
          latest.remoteVideoTrack.detach(video)
        }
        if (video) video.srcObject = null
        await this.stopCamera(latest)
      } catch (error) {
        // ignore
      } finally {
        this.playingCamera = null
        this.videoToggling = false
        if (this._prevPrefixId != null && this._prevPrefixId !== this.prefixId) {
          this.setPrefixId(this._prevPrefixId)
        }
        this._prevPrefixId = null
      }
    },
    async show(e, robot) {
      this.$emit('showControlPart', false)
      if (this.selectedRobotId === robot?.robotId || !e) {
        await this.stopFixedCameraVideo()
        this.pathVisible = false
        this.$emit('showPath', false)
        this.setSelectedRobotId('')
        this.handleGlobalClick(e, false)
        this.$emit('clear', [])
      } else {
        // 切换装备时关闭路径线 / 固定摄像头画面，不影响 MapTool 点位
        await this.stopFixedCameraVideo()
        this.pathVisible = false
        this.$emit('showPath', false)
        this.visible = true
        this.setSelectedRobotId(robot?.robotId)
        this.handleGlobalClick(e, true)
        this.$emit('clear', [robot?.robotId])
      }
    }
  },
}
</script>

<style lang="scss" scoped>
.btns {
  .el-button:first-child {
    margin-left: 10px;
  }
}
.fixed-camera-video {
  width: 340px;
  height: 200px;
  margin-left: 10px;
  margin-right: 10px;
  border-radius: 4px;
  border: 1px solid #005FCF;
  background: rgba(4, 24, 65, 0.20);
  backdrop-filter: blur(2.5px);
  box-sizing: border-box;

  &__inner {
    position: relative;
    width: 320px;
    height: 180px;
    overflow: hidden;
  }

  &__stream {
    width: 320px;
    height: 180px;
    object-fit: contain;
    background: #000;
  }

  &__status {
    position: absolute;
    inset: 0;
    color: #1A5683;
    font-family: YouSheBiaoTiHei;
    font-size: 12px;
    line-height: 16px;
    letter-spacing: 0.34px;
    pointer-events: none;
  }

  &__placeholder {
    position: absolute;
    inset: 0;
    color: rgba(190, 225, 255, 0.55);
    font-family: "Microsoft YaHei";
    font-size: 12px;
    line-height: 16px;
    pointer-events: none;
  }
}
.machine-container.robot-container.new {
  position: fixed;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  will-change: left, top, transform, scale, opacity, backdrop-filter;
  &.visible {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    backdrop-filter: blur(15px);
  }
  .box {
    width: min-content;
    .info-content {
      .item {
        color: rgba(255, 255, 255, 0.80);
        font-family: "Microsoft YaHei";
        line-height: 18px;
        .value {
          color: #FFF;
        }
      }
      .with-divider {
        border-top: 1px solid #5DA7FF;
      }
      .task {
        &.pending {
          .value {
            color: #FF7734;
          }
        }
        &.running {
          .value {
            color: #25FF6E;
          }
        }
      }
    }
  }
  .robot1-guideline {
    position: absolute;
    bottom: -47px;
    left: 0;
    width: 197px;
    height: 47px;
    pointer-events: none;
  }
}
</style>
