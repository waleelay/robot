<template>
  <div class="custom-snapshot-div">
    <div class="list p10 hp112 common-scroll" style="display: inline-flex; width: 1410px;" :class="{'show-page': showPage}">
      <div class="page page-pre flx-center" @click="handleChangePage('pre')" :class="{ 'show-pre': showPre }">
        <svg-icon icon-class="d-left"></svg-icon>
      </div>
      <div class="page page-next flx-center" @click="handleChangePage('next')" :class="{ 'show-next': showNext }">
        <svg-icon icon-class="d-right"></svg-icon>
      </div>
      <div v-for="(item, index) in snapShotInfo.snapshotList" :key="item.fileId" :style="{ display: tabIndex === 0 ? 'inline-flex' : 'none', marginLeft: index !== 0 ? '15.4px' : 0 }" class="item flx-center wp160 hp90">
        <!-- <img :src="`https://192.168.124.77:4443/api/control/snapshots/${item.fileId}/image`" alt="" class="w100" style="height: auto; max-height: 100%;"> -->
        <el-image
          class="w100"
          style="height: auto; max-height: 100%;" 
          :alt="item.fileName || item.fileId"
          :src="item.customUrl"
          :preview-src-list="srcList">
        </el-image>
      </div>
      <div v-for="(recording, index) in recordings" :style="{ display: tabIndex === 1 ? 'inline-flex' : 'none', marginLeft: index !== 0 ? '15.4px' : 0 }" :key="recording.fileId" class="item flx-center wp160 hp90" :id="'recording' + recording.fileId">
        <video :ref="`${refPrefix}_${recording.fileId}`" playsinline class="w100 h100" muted />
        <div class="bottom flx-align-center" style="justify-content: end;">
          <div :title="recordingData?.[recording.fileId]?.player?.paused ? '播放' : '暂停'" @click="playPause(recording.fileId)" class="snapshot-icon">
            <!-- <svg-icon :icon-class="recordingData?.[recording.fileId]?.player?.paused ? 'play' : 'pause'" /> -->
            <svg-icon :icon-class="recordingData?.[recording.fileId]?.player?.paused ? 'play' : 'pause'" />
          </div>
          <div :title="isFullscreen ? '退出全屏' : '全屏'" @click="toggle('recording' + recording.fileId)" class="snapshot-icon ml10">
            <svg-icon :icon-class="isFullscreen ? 'close-fullscreen' : 'fullscreen1'" />
          </div>
        </div>
      </div>
      
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import recordMixin from './recording.js'
import { getFiles, snapshotImageUrl } from '../../../api/media.js';
import { previewImageBlob } from '../../../api/new-bi.js';
import videoUtils from '../../../utils/videoUtils.js'
export default {
  name: 'Snapshot',
  props: {
    tabIndex: {
      type: Number,
      default: 0
    }
  },
  mixins: [recordMixin, videoUtils],
  data() {
    return {
      snapShotInfo: {
        page: 0,
        size: 8,
        total: 0,
        snapshotList: []
      },
      recordInfo: {
        page: 0,
        size: 8,
        total: 0,
      },
      refPrefix: 'recordedPlayer1'
    }
  },
  computed: {
    ...mapState('websocketRobot', ['snapshotTime', 'recordTime']),
    srcList() {
      return (this.snapShotInfo.snapshotList || []).map(item => {
        return item.customUrl
      })
    },
    showPage() {
      return (this.tabIndex === 0 && this.snapShotInfo.total > this.snapShotInfo.size) || (this.tabIndex === 1 && this.recordInfo.total > this.recordInfo.size)
    },
    showPre() {
      return (this.tabIndex === 0 && this.snapShotInfo.page > 0) || (this.tabIndex === 1 && this.recordInfo.page > 0)
    },
    showNext() {
      return (this.tabIndex === 0 && (this.snapShotInfo.page + 1) * this.snapShotInfo.size < this.snapShotInfo.total) || (this.tabIndex === 1 && (this.recordInfo.page + 1) * this.recordInfo.size < this.recordInfo.total)
    },
  },
  methods: {
    getVideoPaused(id) {
      const ele = document.getElementById(id)
      return ele? ele.paused : true
    },
    toggle(id) {
      this.idName = id
      this.toggleFullscreen()
    },
    async handleChangePage(type) {
      const key = this.tabIndex === 0 ? 'snapShotInfo' : 'recordInfo'
      this[key].page = type === 'pre' ? this[key].page - 1 : this[key].page + 1
      this.tabIndex === 0 ? this.getSnapData() : await this.getPlayers()
    },
    async getSnapData() {
      const res = await getFiles({ robotId: 'robot-001', page: this.snapShotInfo.page, size: this.snapShotInfo.size, fileType: 'IMAGE', status: 'READY' }) || {}
      this.snapShotInfo.snapshotList = (res.items || []).map(item => {
        const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
        return { ...item, customUrl: `${preUrl}/api/control/files/${item.fileId}/content` }
      })      
      this.snapShotInfo.total = res.total || 0
    },
    async updateRecordings(items) {
      const recordings = this.recordingTab === 'patrol'
            ? items.filter(item => item.sourceType !== 'LIVEKIT_EGRESS' && !this.recordingData[item.fileId])
            : items.filter(item => !this.recordingData[item.fileId])
      this.recordings = [...recordings, ...this.recordings]
      await this.updatePlayers(recordings)
    },
    async updatePlayers(recordings) {
      for (const recording of recordings) {
        this.recordingData[recording.fileId] = {
          ...recording,
          recordedHls: null,
          player: null,
          // startSecs: this.getTotalTime(this.videoObj.startTime, recording.recordedStartedAt)
        }
        await this.playRecording(recording)
      }
    }
  },
  async mounted() {
    this.getSnapData()
    await this.getPlayers()
  },
  watch: {
    tabIndex(newVal, oldVal) {
      // if (newVal === 0) {
      //   this.$nextTick(() => {
      //     this.getSnapData()
      //   })
      // }
    },
    snapshotTime(newVal, oldVal) {
      if (newVal) {
        this.$nextTick(() => {
          this.getSnapData()
        })
      }
    },
    recordTime(newVal, oldVal) {
      if (newVal) {
        this.$nextTick(async () => {
          await this.loadRecordings(true)
        })
      }
    }
  },
  beforeDestroy() {
  }
}
</script>

<style lang="scss" scoped>
.list {
  position: relative;
}
.page {
  position: absolute;
  top: 10px;
  width: 18px;
  height: calc(100% - 20px);
  // border-radius: 4px;
  background: #002859;
  border: 1px solid rgba(0, 95, 207, 0.50);
  filter: drop-shadow(-12px 0 10px #192238);
  cursor: pointer;
  opacity: 0;
  transition: all 0.3s ease-in-out;
  z-index: 2;
  .svg-icon {
    color: #005FCF;
    font-size: 16px;
  }
  &.page-pre {
    left: 10px;
    border-top-right-radius: 0px;
    border-bottom-right-radius: 0px;
    opacity: 0;
  }
  &.page-next {
    right: 10px;
    border-top-left-radius: 0px;
    border-bottom-left-radius: 0px;
    opacity: 0;
  }
};
.show-page:hover {
  .page {
    // opacity: 1;
    &.show-pre, &.show-next {
      opacity: 1;
    }
  }
}

.item {
  position: relative;
  .bottom {
    position: absolute;
    bottom: 10px;
    right: 10px;
    .snapshot-icon {
      font-size: 16px;
      color: #CAD4E0;
      cursor: pointer;
      &:hover {
        color: #21c8ff;
      }
    }
  }
}
</style>
