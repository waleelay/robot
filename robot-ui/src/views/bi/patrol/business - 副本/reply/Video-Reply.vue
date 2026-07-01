<template>
  <div class="ml10 flex1 h100">
    <div class="recording-workspace d-flex">
      <div class="recording-list wp180">
        <div class="recording-head">
          <el-tabs v-model="recordingTab" class="recording-tabs" @tab-click="loadRecordings">
            <el-tab-pane label="手动录像" name="manual" />
            <el-tab-pane label="巡逻录像" name="patrol" />
          </el-tabs>
          <el-button size="mini" :loading="recordingsLoading" @click="loadRecordings">刷新</el-button>
        </div>
        <button
          v-for="recording in recordings"
          :key="recording.recordingId"
          class="recording-item mt5"
          :class="{ active: selectedRecording && selectedRecording.recordingId === recording.recordingId }"
          @click="playRecording(recording)"
        >
          <span>{{ recording.fileName }}</span>
          <small>{{ recording.deviceId }} · {{ durationText(recording.durationSeconds) }} · {{ recording.status }} · {{ recording.sourceType }}</small>
        </button>
        <div v-if="!recordings.length && !recordingsLoading" class="recording-empty">暂无录像记录</div>
      </div>
      <div class="recording-player ml20 d-flex flex-wrap" style="margin-top: -10px; margin-left: 10px">
        <div v-for="recording in recordings" :key="recording.recordingId" class="wp320 hp180 mt10 ml10">
          <video :ref="`recordedPlayer_${recording.recordingId}`" class="w100 h100" muted />
          <span style="color: #fff" class="mt2">{{ recording.recordedStartedAt }}-{{ durationText(recording.durationSeconds) }}</span>
          <!-- <div v-if="!selectedRecording" class="empty-video">选择 READY 录像开始回放</div> -->
        </div>
        <Slam class="mt10 ml10 wp640 hp360" />
      </div>
    </div>
    <div class="mt20 custom-controls p15 flex-column">
      <el-slider
        v-model="videoObj.inputValue"
        :min="0"
        :max="videoObj.max"
        :step="videoObj.step"
        @input="handleInputTimeLine"
        :format-tooltip="e => formatTime(parseFloat(e))"
        class="pr100 pl100"
      />
      <div class="mt20 flx-center">
        <div class="time-display" style="color: #fff">
          <span>总时长： {{ videoObj.max }}秒</span>
          <span class="current-time ml20">当前时间：{{ videoObj.currentTime }}</span> / <span id="duration">结束时间：{{ videoObj.endTime }}</span>
        </div>
        <div class="flx-center ml20 rate">
          <span v-for="rate in rateList" :key="rate" class="rate-item" @click="changeSpeed(rate)" :class="{ 'is-active': videoObj.step * 10 === rate, 'ml10': rate !== 0.5 }">{{ rate }}X</span>
        </div>
        <el-button type="primary" @click="playPauseVideo" class="play-btn ml20">播放/暂停{{ videoObj.step }}</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import { getRecordingPlayUrl, getRecordings } from '../../../../../api/media.js';
import { errorMessage } from '../../../../../utils/index.js';
import Hls from 'hls.js'
import videoReply from './Video-Reply'
import Slam from './Slam.vue';

export default {
  name: 'VideoReply',
  mixins: [videoReply],
  components: { Slam },
  computed: {
    ...mapState('websocketRobot', ['selectedRobot', 'selectedRobotId']),
    displayedCameras() {
      return this.selectedRobot.cameras.slice(0, this.gridMode)
    },
  },
  data() {
    return {
      gridMode: 4,
      recordingTab: 'manual',
      recordingsLoading: false,
      recordings: [],
      recordingData: {}, // { recordingId: { ..., playUrl, hls } } }
      selectedRecording: null,
      recordedHls: null
    }
  },
  async mounted() {
    this.getInitData()
    await this.getPlayers()
    
    // this.loadRecordings()
    // setTimeout(() => {
    //   console.log(this.$refs);
    // }, 2000)
  },
  methods: {
    // 录像列表只拉 READY 状态，确保用户点开后一定能拿到可播放的 HLS 地址。
    async loadRecordings() {
      this.recordingsLoading = true
      try {
        const params = {
          robotId: 'robot-001',
          status: 'READY',
          page: 0,
          size: 20
        }
        if (this.recordingTab === 'manual') {
          params.sourceType = 'LIVEKIT_EGRESS'
        }
        const response = await getRecordings(params)
        const items = response.items || []
        this.recordings = this.recordingTab === 'patrol'
          ? items.filter(item => item.sourceType !== 'LIVEKIT_EGRESS')
          : items
      } catch (error) {
        this.$message.error(errorMessage(error))
      } finally {
        this.recordingsLoading = false
      }
    },
    async getPlayers() {
      await this.loadRecordings()
      for (const recording of this.recordings) {
        this.recordingData[recording.recordingId] = {
          ...recording,
          recordedHls: null,
          player: null,
          startSecs: this.getTotalTime(this.videoObj.startTime, recording.recordedStartedAt)
        }
        await this.playRecording(recording)
      }
    },
    async playRecording(recording) {
      if (recording.status !== 'READY') return
      try {
        const playback = await getRecordingPlayUrl(recording.recordingId)
        const ref = this.$refs[`recordedPlayer_${recording.recordingId}`]
        let player = Array.isArray(ref) ? ref[0] : ref
        player.loop = false
        let recordedHls = null
        this.destroyRecordedHls(recording.recordingId)
        this.selectedRecording = recording
        if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = 'https://192.168.124.77:4443' + playback.playUrl
        } else if (Hls.isSupported()) {
          recordedHls = new Hls()
          recordedHls.loadSource(playback.playUrl)
          recordedHls.attachMedia(player)
        } else {
          throw new Error('当前浏览器不支持 HLS 播放')
        }
        this.recordingData[recording.recordingId].player = player
        this.recordingData[recording.recordingId].recordedHls = recordedHls
        // await player.play().catch(() => {})
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    destroyRecordedHls(recordingId) {
      const { recordedHls, player } = this.recordingData[recordingId]
      if (recordedHls) {
        recordedHls.destroy()
        recordedHls = null
      }
      if (player) {
        player.pause()
        player.removeAttribute('src')
        player.load()
      }
    },
    durationText(seconds) {
      if (!seconds) return '--:--'
      const hours = Math.floor(seconds / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      const remainder = seconds % 60
      const text = `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
      return hours > 0 ? `${hours}:${text}` : text
    },
  }
}
</script>

<style lang="scss" scoped>
.rate {
  padding: 6px 20px;
  color: rgba(255, 255, 255, 0.7);
  font-family: "Microsoft YaHei";
  font-size: 14px;
  line-height: 19px;
  .is-active {
    color: #FFF;
    background: #2574D5;
  }
}
::v-deep {
  .el-input {
    padding: 0 !important;
    &__inner {
      height: 8px;
      padding: 0 !important;
      line-height: 8px;
      // border: 1px solid #2f2f2f;
      background: transparent;
    }
    input[type="range"] {
      width: 100%;
      height: 8px;
      // border: 1px solid #2f2f2f;
      // appearance: none;
      -webkit-appearance: none;
      background: #ddd;
      border-radius: 4px;
      outline: none;
      &::-webkit-slider-runnable-track {
        // background: #0079fe;
      }
      &::-webkit-slider-thumb {
        // appearance: none;
        -webkit-appearance: none;
        width: 16px;
        height: 16px;
        background: #0079fe;
        border-radius: 50%;
        cursor: pointer;
        transition: all 0.2s;
        // border: 1px solid #0079fe;
        margin-top: -4px;
      }
      &::-moz-range-thumb {
        width: 16px;
        height: 16px;
        background: #0079fe;
        border-radius: 50%;
        cursor: pointer;
        transition: all 0.2s;
        margin-top: -3px;
      }
    }
    /* 填充进度部分的样式 */
    input[type="range"]::-webkit-slider-runnable-track {
      height: 10px;
      background: linear-gradient(
        to right,
        #0079fe var(--fill-percent),
        #ddd var(--fill-percent)
      );
      // background: linear-gradient(to right, #0079fe 50%, #ddd 50%);
      border-radius: 4px;
    }
  }
}
input[type="range"] {
  height: 16px;
  border: 1px solid #2f2f2f;
  appearance: none;
  background: transparent;
  &::-webkit-slider-runnable-track {
    background: #0079fe;
  }
  &::-moz-range-track {
    // accent-color: #0079fe;
  }
  &::-webkit-slider-thumb {
    appearance: none;
    background: #0079fe;
  }
  &::-webkit-slider-runnable-track {
    // background-image: linear-gradient(
    //   to right,
    //   #4CAF50 0%,
    //   #4CAF50 ${percentage}%,
    //   #ddd ${percentage}%,
    //   #ddd 100%
    // );
  }
}


.custom-controls {
  background: #232834;
  border-radius: 5px;

  .timeline {
    flex-grow: 1;
  }
  .time-display {
    // width: 435px;
    font-family: monospace;
    font-size: 14px;
    text-align: center;
  }
  .play-btn {
    padding: 10px 15px;
    cursor: pointer;
  }
  .current-time,
  #duration {
    display: inline-block;
    // width: 135px;
    text-align: left;
  }
}
</style>
