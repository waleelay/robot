<template>
  <!-- v-for="index in splitType"
  :key="index" -->
  <div
    class="item"
    :class="className + ' ' + (splitType === 1 ? 'one' : splitType === 4 ? 'four' : splitType === 6 ? 'six' : 'nine') + (dragOver ? ' drop-active' : '')"
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
    
    <video autoplay muted preload="auto" :id="prefixId + ZQL_videosInfos[`slot_${index}`]?.key" :style="{ display: ZQL_videosInfos[`slot_${index}`] ? 'block' : 'none' }" class="w100 h100">
      <!-- <source src="https://mv6.music.tc.qq.com/C2FD55D4D97F547644795652173F9C0B8DD20B57BBF1ACC9CD0841D3ECA6F9A80BFCEDBD225832C0D87DE7BAFC9C39ADZZqqmusic_default__v21501a351/qmmv_0b53puaj6aaaquagbodg4zuvi7iat56qbh2a.f9934.m3u8" type="video/mp4"> -->
    </video>
    <audio :id="prefixId + ZQL_videosInfos[`slot_${index}`]?.key + '-audio'" autoplay />
    <!-- <canvas class="canvas-shuju" :id="`${prefixId}canvasslot_${index}`" style="z-index: 1; position: absolute;cursor: pointer;"></canvas> -->
    <template v-if="ZQL_videosInfos[`slot_${index}`]">
      <div v-if="recordingActive" class="recording flx-align-center" @click="toggleLiveRecording(cameraInfo)" title="点击停止录制">
        <span class="symbol" :class="{ 'is-active': recordingActive }"></span>
        <span class="ml6">{{ recordingTime }}</span>
      </div>
      <div class="top flx-justify-between w100 pr10 pl10 pt10 pb18">
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
        <div v-if="!cameraTitleOnly" class="flx-center">
          <VideoInfo :className="{ one: splitType === 1, four: splitType === 4, nine: splitType === 9  }" :cameraKey="ZQL_videosInfos[`slot_${index}`]?.key" />
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
            @playPauseVideo="$emit('playPauseVideo')"
            @toggleFullscreen="$emit('toggleFullscreen', `slot_${index}`)"
            @removeVideo="$emit('removeVideo', $event)"
            @refreshVideo="$emit('refreshVideo', $event)"
            :ref="`videoToolRefslot_${index}`"
            :className="{ one: splitType === 1, four: splitType === 4, nine: splitType === 9  }"
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
import { formatTiming } from '@/utils/index.js';
import { ROBOT_TYPE_INFO } from '@/constants/robot';
export default {
  name: 'VideoBox',
  components: { VideoTool, VideoInfo },
  mixins: [mixin],
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
    cameraInfo() {
      return this.cameras?.[this.ZQL_videosInfos[`slot_${this.index}`]?.key] || {}
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
    // 固定摄像头：不展示控制中心 / 控制器
    isFixedCamera() {
      const video = this.ZQL_videosInfos[`slot_${this.index}`] || {}
      if (video.sourceType === 'FIXED_CAMERA' || this.cameraInfo.sourceType === 'FIXED_CAMERA') return true
      const robot = this.currentRobot
      return robot.typeCode === 'FIXED_CAMERA'
        || robot.type === 'FIXED_CAMERA'
        || robot.type === '固定摄像头'
    },
    showControl() {
      return !this.selectedRobotId || (this.selectedRobotId && (this.className.includes('six-1') || this.splitType === 1 || this.splitType === 4))
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
    }
  },
  mounted() {
    document.addEventListener('fullscreenchange', this.handleFullScreenChange)
    document.addEventListener('webkitfullscreenchange', this.handleFullScreenChange)
  },
  methods: {
    ...mapActions('dragVideo', ['setSplitType']),
    ...mapActions('websocketRobot', ['toggleLiveRecording', 'setSelectedRobotId', 'stopCamera']),
    async goControlCenter(robotId) {
      for (const [index, key] of Object.keys(this.activeCameras).entries()) {
        if (this.activeCameras[key]?.camera) {
          await this.stopCamera(this.activeCameras[key].camera);
        }
      }
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
    // 判断视频是否正在播放
    videoStatus(slotKey) {
      const videoInfo = this.ZQL_videosInfos[slotKey];
      if (!videoInfo) return false;
      if (videoInfo.status === 'STREAMING') {
        return videoInfo.isPaused ? 'paused' : 'playing'
      } else return 'stopped'
    },
  },
  watch: {
    recordingActive(newVal) {
      if (newVal) {
        if (this.resetTimer) clearTimeout(this.resetTimer)
        this.recordTimer = setInterval(() => {
          this.seconds = this.seconds + 1
          this.recordingTime = formatTiming(this.seconds)
        }, 1000)
      } else {
        if (this.seconds) {
          if (this.recordTimer) clearInterval(this.recordTimer)
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
