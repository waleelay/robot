<template>
  <div class="custom-snapshot-div">
    <div class="list p10 hp112 common-scroll" style="display: inline-flex; width: 1410px;" :class="{'show-page': showPage}">
      <div class="page page-pre flx-center" @click="handleChangePage('pre')" :class="{ 'show-pre': showPre }">
        <svg-icon icon-class="d-left"></svg-icon>
      </div>
      <div class="page page-next flx-center" @click="handleChangePage('next')" :class="{ 'show-next': showNext }">
        <svg-icon icon-class="d-right"></svg-icon>
      </div>
      <div
        v-for="(item, index) in snapShotInfo.snapshotList"
        :key="item.fileId"
        :style="{ display: tabIndex === 0 ? 'inline-flex' : 'none', marginLeft: index !== 0 ? '15.4px' : 0 }"
        class="item flx-center wp160 hp90 curp"
        @click="openDetail(item, 0)"
      >
        <el-tooltip class="w100 h100" effect="dark" placement="top" popper-class="recording-popper">
          <div slot="content" class="flex-column">
            <div>装备名称：{{ robotBaseInfo?.[item.robotId]?.name || '-' }}</div>
            <div>抓拍相机：{{ getCameraName(item.robotId, item.deviceId || '-') }}</div>
            <div>抓拍时间：{{ item.uploadedAt }}</div>
            <div>抓拍位置：{{ getLocationText(item) }}</div>
          </div>
          <img
            class="w100 h100"
            style="height: auto; max-height: 100%; object-fit: cover;"
            :alt="item.fileName || item.fileId"
            :src="item.customUrl"
          >
        </el-tooltip>
      </div>
      <div
        v-for="(recording, index) in recordings"
        :style="{ display: tabIndex === 1 ? 'inline-flex' : 'none', marginLeft: index !== 0 ? '15.4px' : 0 }"
        :key="recording.fileId"
        class="item flx-center wp160 hp90 curp"
        :id="'recording' + recording.fileId"
        @click="openDetail(recording, 1)"
      >
        <el-tooltip class="w100 h100" effect="dark" placement="top" popper-class="recording-popper">
          <div slot="content" class="flex-column">
            <div>装备名称：{{ robotBaseInfo?.[recording.robotId]?.name || '-' }}</div>
            <div>录制相机：{{ getCameraName(recording.robotId, recording.deviceId || recording.cameraId || '-') || '-' }}</div>
            <div>录制时间：{{ recording.createdAt }}</div>
            <div>录制时长：{{ durationText(recording.durationSeconds) }}</div>
          </div>
          <video :ref="`${refPrefix}_${recording.fileId}`" playsinline muted preload="auto" class="w100 h100" />
        </el-tooltip>
        <div class="oper-video visible">
          <img src="../../../assets/images/new-bi/play-b.svg" alt="">
        </div>
      </div>

    </div>
    <MultimediaDetail ref="multimediaDetailRef" @deleted="handleDeleted" />
  </div>
</template>

<script>
import { mapState } from 'vuex';
import recordMixin from './recording.js'
import {
  getManualMediaFiles
} from '../../../api/media.js';
import { getCachedFileObjectUrl, invalidateCachedFile } from '@/utils/file-object-url-cache'
import videoUtils from '../../../utils/videoUtils.js'
import { resolveCameraName } from '../../../utils/index.js'
import MultimediaDetail from '../patrol/monitor/second/components/MultimediaDetail.vue'
export default {
  name: 'Snapshot',
  components: { MultimediaDetail },
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
      refPrefix: 'recordedPlayer1',
      snapshotLoadSeq: 0
    }
  },
  computed: {
    ...mapState('websocketRobot', ['snapshotTime', 'recordTime', 'cameras']),
    ...mapState('websocketExtraData', ['robotBaseInfo']),
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
    openDetail(item, tabIndex) {
      this.$refs.multimediaDetailRef?.open({
        item,
        tabIndex,
        simple: true
      })
    },
    // 详情内删除后刷新列表（图片 + 视频）
    async handleDeleted(item) {
      if (item?.fileId) invalidateCachedFile(item.fileId)
      await this.refreshList()
      this.$emit('deleted')
    },
    async refreshList() {
      await this.getSnapData()
      this.destroyAllRecordedHls()
      this.recordingData = {}
      this.recordings = []
      this.recordInfo.page = 0
      await this.$nextTick()
      await this.getPlayers()
    },
    async handleChangePage(type) {
      const key = this.tabIndex === 0 ? 'snapShotInfo' : 'recordInfo'
      this[key].page = type === 'pre' ? this[key].page - 1 : this[key].page + 1
      this.tabIndex === 0 ? this.getSnapData() : await this.getPlayers()
    },
    async getSnapData() {
      const loadSeq = ++this.snapshotLoadSeq
      try {
        const res = await getManualMediaFiles('IMAGE', { page: this.snapShotInfo.page, size: this.snapShotInfo.size, status: 'READY' }) || {}
        const items = res.items || []
        const urls = await Promise.all(items.map(item =>
          getCachedFileObjectUrl(item.fileId).catch(() => '')
        ))
        if (loadSeq !== this.snapshotLoadSeq) return
        this.snapShotInfo.snapshotList = items.map((item, index) => ({
          ...item,
          customUrl: urls[index]
        }))
        this.snapShotInfo.total = res.total || 0
      } catch (e) {
        if (loadSeq !== this.snapshotLoadSeq) return
        this.snapShotInfo.snapshotList = []
        this.snapShotInfo.total = 0
      }
    },
    async updateRecordings(items) {
      const recordings = items.filter(item => !this.recordingData[item.fileId])
      this.recordings = [...recordings, ...this.recordings]
      await this.updatePlayers(recordings)
    },
    async updatePlayers(recordings) {
      const loadSeq = this.recordingLoadSeq
      for (const recording of recordings) {
        if (loadSeq !== this.recordingLoadSeq) return
        this.recordingData[recording.fileId] = {
          ...recording,
          recordedHls: null,
          player: null,
        }
        await this.playRecording(recording, loadSeq)
      }
    },
    getCameraName(robotId, deviceId) {
      return resolveCameraName(this.cameras, robotId, deviceId)
    },
    getLocationText(item) {
      const robot = this.robotBaseInfo?.[item.robotId]
      return robot?.location?.address
        || robot?.locationName
        || item.location?.address
        || '-'
    }
  },
  async mounted() {
    this.getSnapData()
    if (this.tabIndex === 1) await this.getPlayers()
  },
  watch: {
    tabIndex(newVal) {
      if (newVal === 1) {
        this.$nextTick(async () => {
          if (!this.recordings.length) await this.getPlayers()
          else this.primeAllRecordingPosters()
        })
      }
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
    this.snapshotLoadSeq += 1
    this.destroyAllRecordedHls()
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
  cursor: pointer;
  video {
    object-fit: cover;
    background: #001428;
  }
  .oper-video {
    position: absolute;
    top: 0;
    right: 0;
    left: 0;
    bottom: 0;
    opacity: 0;
    transition: all 0.3s ease-in-out;
    margin: auto;
    width: 26px;
    height: 26px;
    pointer-events: none;
    &.visible {
      opacity: 1;
    }
    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }
  &:hover .oper-video {
    opacity: 1;
  }
}
</style>
