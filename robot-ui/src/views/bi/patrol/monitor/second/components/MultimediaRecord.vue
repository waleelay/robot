<template>
  <div class="multimedia-record">
    <div v-if="!hideHeader" class="card-title title-284-37 flx-justify-between flx-align-center pr4">
      <div class="text">多媒体记录</div>
      <div class="media-tab mt2 flex flx-align-center">
        <div
          v-for="item in tabList"
          :key="item.value"
          class="media-tab-item"
          :class="{ 'is-active': tabIndex === item.value }"
          @click="handleChangeTab(item.value)"
        >
          {{ item.label }}
        </div>
        <div class="ml10 curp" @click="openMore" :title="`查看更多${tabIndex ? '视频记录' : '图片记录'}`">
          <svg-icon icon-class="right" style="font-size: 14px; color: #fff" />
        </div>
      </div>
    </div>
    <div class="media-list common-scroll mt10 p10 hp193">
      <div
        v-for="(item, index) in displayList"
        :key="item.fileId"
        class="media-item d-flex curp"
        :class="{ 'mt10': index > 0 }"
        @click="openItem(item)"
      >
        <div class="thumb flx-center">
          <img
            v-if="item.fileType === 'IMAGE'"
            :src="item.customUrl"
            :alt="item.fileName || item.fileId"
            class="w100 h100"
          >
          <template v-else>
            <video
              :ref="`listPlayer_${item.fileId}`"
              class="w100 h100"
              muted
              playsinline
              preload="metadata"
            />
            <div class="oper-video visible">
              <img src="@/assets/images/new-bi/play-b.svg" alt="">
            </div>
          </template>
        </div>
        <div class="meta flex1 ml10">
          <div class="title text-ellipsis mt6" :title="getItemTitle(item)">{{ getItemTitle(item) }}</div>
          <div class="time mt9">{{ formatDateTime(item.uploadedAt || item.createdAt) }}</div>
        </div>
      </div>
      <Empty v-if="!displayList.length" width="126px" :opacity="0.7" textColor="#BEE1FF" text="暂无多媒体记录" />
    </div>
    <MultimediaDetail v-if="!hideHeader" ref="multimediaDetailRef" @deleted="handleDeleted" />
  </div>
</template>

<script>
import { mapState } from 'vuex'
import Hls from 'hls.js'
import { getFiles, getFilePlayUrl } from '../../../../../../api/media.js'
import MultimediaDetail from './MultimediaDetail.vue'
import Empty from '../../../../components/Empty.vue'

export default {
  name: 'MultimediaRecord',
  components: { MultimediaDetail, Empty },
  props: {
    hideHeader: { type: Boolean, default: false },
    tabIndex: { type: Number, default: 0 }
  },
  data() {
    return {
      tabList: [
        { label: '图片', value: 0 },
        { label: '视频', value: 1 }
      ],
      snapshotList: [],
      recordings: [],
      videoPlayers: {}
    }
  },
  computed: {
    ...mapState('websocketRobot', ['snapshotTime', 'recordTime']),
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    displayList() {
      return this.tabIndex === 0 ? this.snapshotList : this.recordings
    }
  },
  watch: {
    snapshotTime() { this.getSnapData() },
    recordTime() { this.getVideoData() },
    selectedRobotId() { this.refreshList() },
    tabIndex(val) {
      if (val === 1) this.$nextTick(() => this.bindVideoPlayers())
    }
  },
  mounted() { this.refreshList() },
  beforeDestroy() { this.destroyVideoPlayers() },
  methods: {
    handleChangeTab(index) { this.$emit('update:tabIndex', index) },
    async refreshList() { await Promise.all([this.getSnapData(), this.getVideoData()]) },
    async getSnapData() {
      try {
        const params = { page: 0, size: 20, fileType: 'IMAGE', status: 'READY' }
        if (this.selectedRobotId) params.robotId = this.selectedRobotId
        const res = await getFiles(params) || {}
        const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
        this.snapshotList = (res.items || []).map(item => ({
          ...item, fileType: item.fileType || 'IMAGE',
          customUrl: `${preUrl}/api/control/files/${item.fileId}/content`
        }))
      } catch (e) { this.snapshotList = [] }
    },
    async getVideoData() {
      this.destroyVideoPlayers()
      try {
        const params = { page: 0, size: 20, fileType: 'VIDEO', status: 'READY' }
        if (this.selectedRobotId) params.robotId = this.selectedRobotId
        const res = await getFiles(params) || {}
        const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
        this.recordings = (res.items || []).map(item => ({
          ...item, fileType: item.fileType || 'VIDEO',
          customUrl: `${preUrl}/api/control/files/${item.fileId}/content`
        }))
        if (this.tabIndex === 1) this.$nextTick(() => this.bindVideoPlayers())
      } catch (e) { this.recordings = [] }
    },
    async bindVideoPlayers() {
      for (const recording of this.recordings) await this.playRecording(recording)
    },
    async playRecording(recording) {
      if (!recording?.fileId || recording.status !== 'READY') return
      try {
        const playback = await getFilePlayUrl(recording.fileId)
        const ref = this.$refs[`listPlayer_${recording.fileId}`]
        const player = Array.isArray(ref) ? ref[0] : ref
        if (!player) return
        this.destroyVideoPlayer(recording.fileId)
        let recordedHls = null
        const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
        player.muted = true
        player.controls = false
        if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = `${preUrl}${playback.playUrl}`
        } else if (Hls.isSupported()) {
          recordedHls = new Hls()
          recordedHls.loadSource(playback.playUrl)
          recordedHls.attachMedia(player)
        }
        this.videoPlayers[recording.fileId] = { player, recordedHls }
      } catch (e) {}
    },
    destroyVideoPlayer(fileId) {
      const data = this.videoPlayers[fileId]
      if (!data) return
      if (data.recordedHls) data.recordedHls.destroy()
      if (data.player) {
        data.player.pause()
        data.player.removeAttribute('src')
        data.player.load()
      }
      delete this.videoPlayers[fileId]
    },
    destroyVideoPlayers() { Object.keys(this.videoPlayers).forEach(id => this.destroyVideoPlayer(id)) },
    getCameraName(robotId, deviceId) {
      return this.robotBaseInfo?.[robotId]?.cameras?.find(item => item.deviceId === deviceId)?.name || ''
    },
    getItemTitle(item) {
      const cameraName = this.getCameraName(item.robotId, item.deviceId || item.cameraId)
      if (cameraName) return `抓拍设备：${cameraName}`
      const robotName = this.robotBaseInfo?.[item.robotId]?.name
      if (robotName) return `装备名称：${robotName}`
      return item.fileName || '未命名文件'
    },
    formatDateTime(val) {
      if (!val) return '-'
      const d = new Date(val)
      if (Number.isNaN(d.getTime())) {
        return String(val).replace('T', ' ').replace(/-/g, '.').slice(0, 19)
      }
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}  ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    openMore() {
      const payload = { tabIndex: this.tabIndex, list: this.displayList, simple: false, hideSearch: true }
      if (this.hideHeader) {
        this.$emit('open-detail', payload)
        return
      }
      this.$refs.multimediaDetailRef?.open(payload)
    },
    openItem(item) {
      const payload = { item, tabIndex: this.tabIndex, simple: true, hideSearch: true }
      if (this.hideHeader) {
        this.$emit('open-detail', payload)
        return
      }
      this.$refs.multimediaDetailRef?.open(payload)
    },
    handleDeleted() {
      this.refreshList()
      this.$emit('deleted')
    }
  }
}
</script>

<style lang="scss" scoped>
.multimedia-record { width: 100%; min-width: 0; }
.media-tab {
  border: 1px solid #334465;
  .media-tab-item {
    padding: 2px 10px; color: #ADBDD1; text-align: center;
    font-family: "Alibaba PuHuiTi"; font-size: 14px; line-height: 19px; letter-spacing: 0.857px;
    background: transparent; cursor: pointer;
    & + .media-tab-item { border-left: 1px solid #334465; }
    &.is-active { border: 1px solid #2E85C4; background: #003264; color: #4AB8FF; }
  }
}
.media-list {
  overflow-x: hidden; overflow-y: auto;
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142b 100%);
  box-shadow: inset 0 0 20px 0 rgba(33, 108, 149, 0.3);
}
.media-item {
  height: 51px;
  cursor: pointer;
  .thumb {
    position: relative;
    width: 90px;
    height: 51px;
    flex-shrink: 0;
    overflow: hidden;
    background: #001529;
    > img,
    > video {
      width: 100%;
      height: 100%;
      object-fit: cover;
      pointer-events: none;
    }
    .oper-video {
      position: absolute;
      top: 0;
      right: 0;
      left: 0;
      bottom: 0;
      margin: auto;
      width: 20px;
      height: 20px;
      opacity: 0;
      transition: all 0.3s ease-in-out;
      pointer-events: none;
      img {
        width: 100%;
        height: 100%;
        object-fit: contain;
      }
      &.visible {
        opacity: 1;
      }
    }
  }
  &:hover .thumb .oper-video {
    opacity: 1;
  }
  .meta {
    min-width: 0;
    .title, .time {
      color: #fff; font-family: "Alibaba PuHuiTi"; font-size: 14px; line-height: 15.6px; letter-spacing: 1.114px;
    }
    .time { text-shadow: 0 1.3px 7.8px rgba(0, 0, 0, 0.3); }
  }
}
.empty { height: 100%; color: rgba(255, 255, 255, 0.5); font-size: 14px; }
.text-ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
