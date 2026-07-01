import { getRecordingPlayUrl, getRecordings } from "../../../api/media"
import { errorMessage } from "../../../utils"

export default {
  data() {
    return {
      recordings: [],
      recordingsLoading: false,
      recordingData: {},
      refPrefix: 'recordedPlayer',
      recordingTab: 'manual',
    }
  },
  methods: {
    // 录像列表只拉 READY 状态，确保用户点开后一定能拿到可播放的 HLS 地址。
    async loadRecordings(isUpdate) {
      this.recordingsLoading = true
      try {
        const params = {
          // robotId: 'robot-001',
          status: 'READY',
          page: this.recordInfo.page,
          size: this.recordInfo.pageSize
        }
        // 手动录像只拉 LIVEKIT_EGRESS 类型的录像
        if (this.recordingTab === 'manual') {
          params.sourceType = 'LIVEKIT_EGRESS'
        }
        const response = await getRecordings(params)
        const items = response.items || []
        this.recordInfo.total = response.total || 0
        if (isUpdate) {
          this.updateRecordings(items)
        } else {
          this.recordings = this.recordingTab === 'patrol'
            ? items.filter(item => item.sourceType !== 'LIVEKIT_EGRESS')
            : items
        }
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
          // startSecs: this.getTotalTime(this.videoObj.startTime, recording.recordedStartedAt)
        }
        await this.playRecording(recording)
      }
    },
    async playRecording(recording) {
      if (recording.status !== 'READY') return
      try {
        const playback = await getRecordingPlayUrl(recording.recordingId)
        const ref = this.$refs[`${this.refPrefix}_${recording.recordingId}`]
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
    getTotalTime(startTime, endTime) {
      // return this.timeToSeconds(endTime) - this.timeToSeconds(startTime)
      return (
        (new Date(endTime).getTime() - new Date(startTime).getTime()) / 1000
      );
    },
  }
}