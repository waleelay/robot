<template>
  <!-- v-for="index in splitType"
  :key="index" -->
  <div
    class="item"
    :class="[
      className,
      splitType === 1 ? 'one' : splitType === 4 ? 'four' : splitType === 6 ? 'six' : 'nine',
      { 'drop-active': dragOver, 'is-page-fullscreen-cell': isPageFullscreen, 'is-control-open': controlOpen }
    ]"
    :id="`${prefixId}slot_${index}`"
    @dragover.prevent="onDragOver"
    @dragenter.prevent="onDragEnter"
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
    style="box-shadow: 0 0 1px 1px rgba(29,149,255,.36) inset"
  >
    <!-- 通过键值对获取当前索引的设备，键名为 `slot_${index}` -->
    <!-- 谷歌火狐不支持非静音播放，需要设置静音muted -->
    <!-- poster="./../../assets/images/new-bi/video-bg1.png" -->
    <!-- :id="`${prefixId}videoslot_${index}`" -->
    <!-- 装备列表时默认显示第0个摄像头视频 -->
    
    <video
      ref="videoEl"
      autoplay
      muted
      playsinline
      preload="auto"
      :id="prefixId + ZQL_videosInfos[`slot_${index}`]?.key"
      :style="{ display: ZQL_videosInfos[`slot_${index}`] ? 'block' : 'none' }"
      class="w100 h100"
    >
      <!-- <source src="https://mv6.music.tc.qq.com/C2FD55D4D97F547644795652173F9C0B8DD20B57BBF1ACC9CD0841D3ECA6F9A80BFCEDBD225832C0D87DE7BAFC9C39ADZZqqmusic_default__v21501a351/qmmv_0b53puaj6aaaquagbodg4zuvi7iat56qbh2a.f9934.m3u8" type="video/mp4"> -->
    </video>
    <audio :id="prefixId + ZQL_videosInfos[`slot_${index}`]?.key + '-audio'" autoplay />
    <!-- <canvas class="canvas-shuju" :id="`${prefixId}canvasslot_${index}`" style="z-index: 1; position: absolute;cursor: pointer;"></canvas> -->
    <template v-if="ZQL_videosInfos[`slot_${index}`]">
      <div v-if="recordingActive" class="recording flx-align-center" @click="toggleLiveRecording(cameraInfo)" :title="recordingTitle">
        <span class="symbol" :class="{ 'is-active': recordingActive }"></span>
        <span class="ml6">{{ recordingTime }}</span>
      </div>
      <div
        class="top flx-justify-between w100 pl10 pt10 pb18"
        :class="isFullscreenTopRightCell ? 'fullscreen-exit-gutter' : 'pr10'"
      >
        <!-- ---{{ ZQL_videosInfos[`slot_${index}`].status }} -->
        <!-- 二级监控：仅左上角摄像头名称；一级保留装备图标/名称 + 右上角 VideoInfo -->
        <div class="title flx-center">
          <template v-if="cameraTitleOnly">
            <span>{{ ZQL_videosInfos[`slot_${index}`].name || '-' }}</span>
          </template>
          <template v-else>
            <svg-icon :icon-class="ROBOT_TYPE_INFO[currentRobot?.type]?.icon || 'robot'" style="color: #0BF9FE; font-size: 16px" />
            <span class="ml10">{{ currentRobot?.name || '-' }}<template v-if="!isFixedCamera">-{{ ZQL_videosInfos[`slot_${index}`].name }}</template></span>
          </template>
        </div>
        <div v-if="!cameraTitleOnly && !isFixedCamera" class="flx-center">
          <VideoInfo :className="overlaySizeClass" :cameraKey="ZQL_videosInfos[`slot_${index}`]?.key" />
        </div>
      </div>
      <div class="bottom flx-justify-between w100 pr10 pl10" style="z-index: 2;">
        <div :ref="`dropdownRefslot_${index}`">
          <el-button v-if="!selectedRobotId && !isFixedCamera" type="primary" class="video-btn ml10" @click="goControlCenter(ZQL_videosInfos[`slot_${index}`].robotId)">
            <svg-icon icon-class="system" class="mr4"></svg-icon>控制中心
          </el-button>
        </div>
        <div class="flx-center">
          <VideoTool
            :idName="`${prefixId}slot_${index}`"
            :slotKey="`slot_${index}`"
            :videoStatus="videoStatus(`slot_${index}`)"
            :cameraKey="ZQL_videosInfos[`slot_${index}`]?.key"
            @updateDropdownStyle="updateDropdownStyle"
            @playPauseVideo="toggleUserPaused"
            @toggleFullscreen="$emit('toggleFullscreen', `slot_${index}`)"
            @removeVideo="$emit('removeVideo', $event)"
            @refreshVideo="$emit('refreshVideo', $event)"
            @control-visible-change="controlOpen = $event"
            :ref="`videoToolRefslot_${index}`"
            :className="overlaySizeClass"
            :showControl="showControl && !isFixedCamera" />
        </div>
      </div>
      <div
        v-if="showStatusOverlay(ZQL_videosInfos[`slot_${index}`])"
        class="w100 h100 flx-center flex-column"
        style="position: absolute; top: 0; left: 0; color: #1A5683">
        <svg-icon :icon-class="isConnectingStatus(ZQL_videosInfos[`slot_${index}`]) ? 'loading' : 'unlink1' " style="font-size: 22px;" />
        <span class="mt10" style="font-family: YouSheBiaoTiHei; font-size: 16.978px; line-height: 22px; letter-spacing: 0.34px;">
          {{ isConnectingStatus(ZQL_videosInfos[`slot_${index}`]) ? '正在连接' : '未连接' }}
        </span>
      </div>
    </template>
    <!-- 空设备占位 -->
    <template v-else>
      <div class="w100 h100 flex-column flx-center empty-device">
        <img src="@/assets/images/new-bi/video-empty.png" alt="" width="76px" height="68px">
        <div class="mt10">
          拖拽左侧卡片的设备 可观看视频
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import VideoInfo from '../../../components/VideoInfo.vue';
import VideoTool from '../../../components/VideoTool.vue';
import { mapActions, mapState } from 'vuex';
import mixin from './drag-mixin';
import livekitMedia from '@/views/bi/js/mixins/livekit-media';
import { formatTiming } from '@/utils/index.js';
import { ROBOT_TYPE_INFO } from '@/constants/robot';
export default {
  name: 'VideoBox',
  components: { VideoTool, VideoInfo },
  mixins: [mixin, livekitMedia],
  props: {
    splitType: {
      type: Number,
      default: 1
    },
    prefixId: {
      type: String,
      default: 'test-video-div'
    },
    ZQL_videosInfos: {
      type: Object,
      default: () => ({})
    },
    videoIndex: {
      type: Number,
      default: 0
    },
    className: {
      type: String,
      default: ''
    },
    // 页面全屏：名称常显，底部工具条悬停显示
    isPageFullscreen: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    ...mapState('websocketRobot', ['cameras', 'selectedRobotId', 'robots']),
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
    index() {
      return this.videoIndex
    },
    // 全屏退出按钮在视口右上角，仅右上格需要给电量/状态标签让位
    isFullscreenTopRightCell() {
      if (!this.isPageFullscreen) return false
      if (this.splitType === 1) return this.index === 1
      if (this.splitType === 4) return this.index === 2
      if (this.splitType === 6) return this.index === 2
      if (this.splitType === 9) return this.index === 3
      return false
    },
    // 六分屏小格与九分屏同尺寸，沿用 nine 的图标/标签/文字
    isSixSmallCell() {
      return this.splitType === 6 && this.index !== 1
    },
    overlaySizeClass() {
      return {
        one: this.splitType === 1,
        four: this.splitType === 4,
        nine: this.splitType === 9 || this.isSixSmallCell
      }
    },
    cameraInfo() {
      return this.cameras?.[this.ZQL_videosInfos[`slot_${this.index}`]?.key] || {}
    },
    cameraMediaKey() {
      return this.ZQL_videosInfos[`slot_${this.index}`]?.key || ''
    },
    currentRobot() {
      const video = this.ZQL_videosInfos[`slot_${this.index}`] || {}
      const robotId = video.robotId || this.cameraInfo.robotId
      return this.robotBaseInfo?.[robotId]
        || video.robot
        || (this.robots || []).find(item => String(item.robotId) === String(robotId))
        || {}
    },
    recordingActive() {
      return this.cameraInfo.recordingActive
    },
    recordingTitle() {
      return this.cameraInfo.recordingOwned ? '点击停止录制' : '其他浏览器正在录制'
    },
    // 固定摄像头：不展示控制中心 / 控制器
    isFixedCamera() {
      const video = this.ZQL_videosInfos[`slot_${this.index}`] || {}
      if (video.sourceType === 'FIXED_CAMERA' || this.cameraInfo.sourceType === 'FIXED_CAMERA') return true
      const robot = this.currentRobot
      return robot.typeCode === 'FIXED_CAMERA'
        || robot.type === 'FIXED_CAMERA'
        || robot.type === '固定摄像头'
    },
    // 九分屏、六分屏小格不展示控制器；其余分屏按控制中心场景决定
    showControl() {
      if (this.splitType === 9 || this.isSixSmallCell) return false
      return !this.selectedRobotId
        || this.className.includes('six-1')
        || this.splitType === 1
        || this.splitType === 4
    },
    /** 二级监控（深度控制）：顶部仅显示摄像头名称 */
    cameraTitleOnly() {
      return String(this.prefixId || '').endsWith('-second')
    }
  },
  data() {
    return {
      isFullscreen: false,
      seconds: 0,
      recordTimer: null,
      resetTimer: null,
      recordingTime: formatTiming(0),
      ROBOT_TYPE_INFO,
      controlOpen: false,
    }
  },
  mounted() {
    document.addEventListener('fullscreenchange', this.handleFullScreenChange)
    document.addEventListener('webkitfullscreenchange', this.handleFullScreenChange)
    if (this.recordingActive) this.startRecordTimer()
  },
  methods: {
    ...mapActions('dragVideo', ['setSplitType']),
    ...mapActions('websocketRobot', ['toggleLiveRecording', 'setSelectedRobotId', 'setControlCenterReturnTo', 'stopCamera']),
    startRecordTimer() {
      if (this.recordTimer) clearInterval(this.recordTimer)
      const recording = this.cameraInfo.activeRecording || {}
      const initialSeconds = Number.isFinite(Number(recording.elapsedSeconds))
        ? Math.max(0, Number(recording.elapsedSeconds))
        : 0
      const startedAt = performance.now()
      const update = () => {
        this.seconds = initialSeconds + Math.max(0, Math.floor((performance.now() - startedAt) / 1000))
        this.recordingTime = formatTiming(this.seconds)
      }
      update()
      this.recordTimer = setInterval(update, 1000)
    },
    async goControlCenter(robotId) {
      for (const [index, key] of Object.keys(this.activeCameras).entries()) {
        if (this.activeCameras[key]?.camera) {
          await this.stopCamera(this.activeCameras[key].camera);
        }
      }
      this.setControlCenterReturnTo(null)
      this.setSelectedRobotId(robotId)
    },
    handleFullScreenChange(e) {
      const idName = `${this.prefixId}slot_${this.index}`
      this.isFullscreen = document.fullscreenElement === document.getElementById(idName) || 
        document.webkitFullscreenElement === document.getElementById(idName)
    },
    // 处理全屏切换 append-to-body的影响
    updateDropdownStyle(isInit) {
      this.$nextTick(() => {
        const container1 = this.$refs[`dropdownRefslot_${this.index}`];
        const menu = this.$refs[`dropdownMenuRefslot_${this.index}`];
        if (container1 && menu && menu.popperElm) {
          // if (this.isFullscreen || isInit) {
          //   console.log(1);
            
            menu.popperElm.classList.add('top_unset', 'left_unset')
            menu.popperElm.style.bottom = '35px'
            container1.appendChild(menu.popperElm)
          // } else {
          //   console.log(2);
          //   // menu.popperElm.classList.remove('top_unset', 'left_unset')
          //   // menu.popperElm.style.bottom = 'unset'
          // }
        } else {
          // console.log(3);
          // menu.popperElm.classList.remove('top_unset', 'left_unset')
          // menu.popperElm.style.bottom = 'unset'
        }
      })
    },
    // 状态
    statusType(status) {
      if (status === 'STREAMING') return 'success'
      if (status === 'FAILED' || status === 'TIMEOUT' || status === 'offline') return 'danger'
      if (status === 'REQUESTING_CLIENT' || status === 'ROOM_READY') return 'warning'
      return 'info'
    },
    showStatusOverlay(videoInfo) {
      return !(this.cameraInfo.hasVideo || videoInfo?.hasVideo)
    },
    isConnectingStatus(videoInfo) {
      if (this.cameraInfo.connecting || this.cameraInfo.restarting || videoInfo?.loading) return true
      return ['INIT', 'REQUESTING_CLIENT', 'ROOM_READY', 'STREAMING', 'INTERRUPTED', 'IDLE_WAIT']
        .includes(videoInfo?.status)
    },
    // 有实时画面即可播放/暂停；不依赖 session 的 STREAMING 文案
    videoStatus(slotKey) {
      const videoInfo = this.ZQL_videosInfos[slotKey]
      if (!videoInfo) return false
      const camera = this.cameras?.[videoInfo.key] || this.cameraInfo || {}
      const live = camera.hasVideo
        || camera.remoteVideoTrack
        || videoInfo.hasVideo
        || videoInfo.remoteVideoTrack
        || videoInfo.status === 'STREAMING'
      if (!live) return 'stopped'
      return videoInfo.isPaused ? 'paused' : 'playing'
    },
  },
  watch: {
    recordingActive(newVal) {
      if (newVal) {
        if (this.resetTimer) clearTimeout(this.resetTimer)
        this.startRecordTimer()
      } else {
        if (this.recordTimer) {
          clearInterval(this.recordTimer)
          this.recordTimer = null
        }
        if (this.seconds) {
          this.resetTimer = setTimeout(() => {
            this.seconds = 0
            this.recordingTime = formatTiming(0)
          }, 2000);
        }
      }
    }
  },
  beforeDestroy() {
    document.removeEventListener('fullscreenchange', this.handleFullScreenChange)
    document.removeEventListener('webkitfullscreenchange', this.handleFullScreenChange)
    if (this.recordTimer) clearInterval(this.recordTimer)
    if (this.resetTimer) clearTimeout(this.resetTimer)
  }
}
</script>

<style lang="scss" scoped>
.item {
  /* 限制绝对定位控制盘溢出，避免撑出祖先滚动条导致画面“收缩” */
  overflow: hidden;

  /* 视频铺满时盖住 inset 阴影，用上层描边保证边框可见 */
  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    border: 1px solid rgba(29, 149, 255, 0.5);
    pointer-events: none;
    z-index: 3;
    box-sizing: border-box;
  }

  /* 控制器打开时：全屏格子仍保持底部工具条可见 */
  &.is-control-open .bottom {
    opacity: 1 !important;
    pointer-events: auto !important;
  }
}

/* 页面全屏：名称常显，底部工具条默认隐藏、悬停该格时显示 */
.item.is-page-fullscreen-cell {
  min-height: 100%;

  .top {
    opacity: 1;
  }

  /* 覆盖 pr10 !important：给右上角退出按钮让位 */
  .top.fullscreen-exit-gutter {
    padding-right: 72px !important;
  }

  .empty-device {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    height: 100% !important;
  }

  .bottom {
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s ease;
  }

  &:hover .bottom {
    opacity: 1;
    pointer-events: auto;
  }
}
</style>
