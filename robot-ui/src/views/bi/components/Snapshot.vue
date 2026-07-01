<template>
  <div class="custom-snapshot-div">
    <div class="list p10 hp112 common-scroll" style="display: inline-flex; width: 1410px;" :class="{'show-page': showPage}">
      <div class="page page-pre flx-center" @click="handleChangePage('pre')" :class="{ 'show-pre': showPre }">
        <svg-icon icon-class="d-left"></svg-icon>
      </div>
      <div class="page page-next flx-center" @click="handleChangePage('next')" :class="{ 'show-next': showNext }">
        <svg-icon icon-class="d-right"></svg-icon>
      </div>
      <div v-for="(item, index) in snapShotInfo.snapshotList" :key="item.snapshotId" :style="{ display: tabIndex === 0 ? 'inline-flex' : 'none', marginLeft: index !== 0 ? '15.4px' : 0 }" class="item flx-center wp160 hp90">
        <!-- <img :src="`https://192.168.124.77:4443/api/control/snapshots/${item.snapshotId}/image`" alt="" class="w100" style="height: auto; max-height: 100%;"> -->
        <el-image
          class="w100"
          style="height: auto; max-height: 100%;"
          :src="`https://192.168.124.77:4443/api/control/snapshots/${item.snapshotId}/image`" 
          :preview-src-list="srcList">
        </el-image>
      </div>
      <div v-for="(recording, index) in recordings" :style="{ display: tabIndex === 1 ? 'inline-flex' : 'none', marginLeft: index !== 0 ? '15.4px' : 0 }" :key="recording.recordingId" class="item flx-center wp160 hp90">
        <video :ref="`${refPrefix}_${recording.recordingId}`" controls playsinline class="w100 h100" muted />
      </div>
      
    </div>
  </div>
</template>

<script>
import { getSnapshotList } from '@/api/media'
import { mapState } from 'vuex';
import recordMixin from './recording.js'
export default {
  name: 'Snapshot',
  props: {
    tabIndex: {
      type: Number,
      default: 0
    }
  },
  mixins: [recordMixin],
  data() {
    return {
      snapShotInfo: {
        page: 0,
        pageSize: 8,
        total: 0,
        snapshotList: []
      },
      recordInfo: {
        page: 0,
        pageSize: 8,
        total: 0,
      },
      refPrefix: 'recordedPlayer1'
    }
  },
  computed: {
    ...mapState('websocketRobot', ['snapshotTime', 'recordTime']),
    srcList() {
      return (this.snapShotInfo.snapshotList || []).map(item => {
        return `https://192.168.124.77:4443/api/control/snapshots/${item.snapshotId}/image`
      })
    },
    showPage() {
      return (this.tabIndex === 0 && this.snapShotInfo.total > this.snapShotInfo.pageSize) || (this.tabIndex === 1 && this.recordInfo.total > this.recordInfo.pageSize)
    },
    showPre() {
      return (this.tabIndex === 0 && this.snapShotInfo.page > 0) || (this.tabIndex === 1 && this.recordInfo.page > 0)
    },
    showNext() {
      return (this.tabIndex === 0 && (this.snapShotInfo.page + 1) * this.snapShotInfo.pageSize < this.snapShotInfo.total) || (this.tabIndex === 1 && (this.recordInfo.page + 1) * this.recordInfo.pageSize < this.recordInfo.total)
    }
  },
  methods: {
    async handleChangePage(type) {
      const key = this.tabIndex === 0 ? 'snapShotInfo' : 'recordInfo'
      this[key].page = type === 'pre' ? this[key].page - 1 : this[key].page + 1
      this.tabIndex === 0 ? this.getSnapData() : await this.getPlayers()
    },
    async getSnapData() {
      const res = await getSnapshotList({ page: this.snapShotInfo.page, pageSize: this.snapShotInfo.pageSize }) || {}
      this.snapShotInfo.snapshotList = res.items || []
      this.snapShotInfo.total = res.total || 0
    },
    async updateRecordings(items) {
      const recordings = this.recordingTab === 'patrol'
            ? items.filter(item => item.sourceType !== 'LIVEKIT_EGRESS' && !this.recordingData[item.recordingId])
            : items.filter(item => !this.recordingData[item.recordingId])
      this.recordings = [...recordings, ...this.recordings]
      await this.updatePlayers(recordings)
    },
    async updatePlayers(recordings) {
      for (const recording of recordings) {
        this.recordingData[recording.recordingId] = {
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
</style>
