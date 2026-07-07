<template>
  <div
    class="item w100 h100"
    :class="className"
    :id="`${prefixId}slot_${index}`"
    @click="$emit('select', index)"
    style="position: relative; box-shadow: 0 0 1px 1px rgba(29,149,255,.36) inset"
  >
    <video autoplay muted preload="auto" :id="prefixId + ZQL_videosInfos?.[index]?.key" :style="{ display: ZQL_videosInfos?.[index] ? 'block' : 'none' }" class="w100 h100"></video>
    <audio :id="prefixId + ZQL_videosInfos?.[index]?.key + '-audio'" autoplay />
    <template v-if="ZQL_videosInfos?.[index]">
      <div
        v-if="statusType(ZQL_videosInfos?.[index]?.status) !== 'success'"
        class="w100 h100 flx-center flex-column"
        style="position: absolute; top: 0; left: 0; color: #1A5683">
        <svg-icon :icon-class="statusType(ZQL_videosInfos?.[index]?.status) === 'warning' ? 'loading' : 'unlink1' " style="font-size: 16px;" />
        <span class="mt2" style="font-family: YouSheBiaoTiHei; font-size: 12px; line-height: 16px; letter-spacing: 0.34px;">
          <!-- {{ statusType(ZQL_videosInfos?.[index]?.status) === 'danger' ? '连接失败' : '' }} -->
          {{ ZQL_videosInfos?.[index]?.hasVideo ? '' : ZQL_videosInfos?.[index]?.session ? '连接中' : statusType(ZQL_videosInfos?.[index]?.status) === 'danger' ? '连接失败' : '' }}
        </span>

      </div>
    </template>
    <!-- 空设备占位 -->
    <template v-else>
      <div class="w100 h100 flex-column flx-center empty-device">
        未加载
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'VideoBox',
  props: {
    prefixId: {
      type: String,
      default: 'task-robot-video-div'
    },
    ZQL_videosInfos: {
      type: Object,
      default: () => ({})
    },
    videoIndex: {
      type: [Number, String],
      default: 0
    },
    className: {
      type: String,
      default: ''
    },
  },
  computed: {
    index() {
      return this.videoIndex
    }
  },
  data() {
    return {
      isFullscreen: false
    }
  },
  mounted() {
    document.addEventListener('fullscreenchange', this.handleFullScreenChange)
    document.addEventListener('webkitfullscreenchange', this.handleFullScreenChange)
  },
  methods: {
    goControlCenter(robotId) {
      // 清空当前页面camera视频
      this.setSelectedRobotId(robotId)
    },
    handleFullScreenChange(e) {
      const idName = `${this.prefixId}slot_${this.index}`
      this.isFullscreen = document.fullscreenElement === document.getElementById(idName) || 
        document.webkitFullscreenElement === document.getElementById(idName)
    },
    // 状态
    statusType(status) {
      if (status === 'STREAMING') return 'success'
      if (status === 'FAILED' || status === 'TIMEOUT' || status === 'offline') return 'danger'
      if (status === 'REQUESTING_CLIENT' || status === 'ROOM_READY') return 'warning'
      return 'info'
    },
    // 判断视频是否正在播放
    videoStatus(slotKey) {
      const videoInfo = this.ZQL_videosInfos[slotKey];
      const idName = `${this.prefixId}${videoInfo.key}`
      const videoEle = document.getElementById(idName)
      if (!videoInfo || !videoEle) return false;
      if (videoInfo.status === 'STREAMING') {
        return videoEle.paused ? 'paused' : 'playing'
      } else return 'stopped'
    },
  },
  beforeDestroy() {
    document.removeEventListener('fullscreenchange', this.handleFullScreenChange)
    document.removeEventListener('webkitfullscreenchange', this.handleFullScreenChange)
  }
}
</script>
