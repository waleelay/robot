<template>
  <div
    class="item w100 h100"
    :class="className"
    :id="`${prefixId}slot_${index}`"
    @click="$emit('select', index)"
    style="position: relative; box-shadow: 0 0 1px 1px rgba(29,149,255,.36) inset"
  >
    <!-- {{ ZQL_videosInfos?.[index]?.key }} -->
    <video autoplay muted preload="auto" :id="prefixId + ZQL_videosInfos?.[index]?.key" :style="{ display: ZQL_videosInfos?.[index] ? 'block' : 'none' }" class="w100 h100"></video>
    <audio :id="prefixId + ZQL_videosInfos?.[index]?.key + '-audio'" autoplay />
    <!-- <video autoplay muted preload="auto" :id="prefixId + ZQL_videosInfos?.[index]?.key" :style="{ display: ZQL_videosInfos?.[index] ? 'block' : 'none' }" class="w100 h100"></video>
    <audio :id="prefixId + ZQL_videosInfos?.[index]?.key + '-audio'" autoplay /> -->
    <!-- <canvas class="canvas-shuju" :id="`${prefixId}canvasslot_${index}`" style="z-index: 1; position: absolute;cursor: pointer;"></canvas> -->
    <template v-if="ZQL_videosInfos?.[index]">
      <!-- <div class="top flx-justify-between w100 pr10 pl10">
        <div class="title">数据源：{{ ZQL_videosInfos?.[index]?.name }}---{{ ZQL_videosInfos?.[index]?.status }}</div>
        <div class="flx-center">
          <VideoInfo :className="{ one: splitType === 1, four: splitType === 4, nine: splitType === 9  }" />
        </div>
      </div> -->
      <!-- <div class="bottom flx-justify-between w100 pr10 pl10" style="z-index: 2;">
        <div :ref="`dropdownRefslot_${index}`">
          <el-button v-if="showControlCenter" type="primary" class="video-btn ml10" @click="goControlCenter(ZQL_videosInfos?.[index]?.robotId)">
            <svg-icon icon-class="system" class="mr4"></svg-icon>控制中心
          </el-button>
        </div>
        <div class="flx-center">
          <VideoTool
            :idName="`${prefixId}slot_${index}`"
            :slotKey="index"
            :videoStatus="videoStatus(index)"
            @updateDropdownStyle="updateDropdownStyle"
            @playPauseVideo="$emit('playPauseVideo')"
            @toggleFullscreen="$emit('toggleFullscreen', index)"
            @removeVideo="$emit('removeVideo', $event)"
            @refreshVideo="$emit('refreshVideo', $event)"
            :ref="`videoToolRefslot_${index}`"
            :className="{ one: splitType === 1, four: splitType === 4, nine: splitType === 9  }" />
        </div>
      </div> -->
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
        <!-- <img src="@/assets/images/new-bi/video-empty.png" alt="" width="76px" height="68px">
        <div class="mt10">
          拖拽右侧卡片的设备 可观看视频
        </div> -->
      </div>
    </template>
  </div>
</template>

<script>
// import VideoTool from '../components/VideoTool.vue';
// import mixin from './drag-mixin';
export default {
  name: 'VideoBox',
  // components: { VideoTool, VideoInfo },
  // mixins: [mixin],
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
