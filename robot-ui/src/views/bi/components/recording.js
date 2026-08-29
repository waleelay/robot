import Hls from "hls.js"
import { getManualMediaFiles } from "../../../api/media"
import { getCachedFilePlayUrl } from "@/utils/file-object-url-cache"
import { durationFromVideoElement, durationText, errorMessage } from "../../../utils"

export default {
  data() {
    return {
      recordings: [],
      recordingsLoading: false,
      recordingData: {},
      refPrefix: 'recordedPlayer',
      recordingLoadSeq: 0,
    }
  },
  methods: {
    durationText,
    // 录像列表只拉 READY 状态，确保用户点开后一定能拿到可播放的 HLS 地址。
    async loadRecordings(isUpdate) {
      this.recordingsLoading = true
      try {
        const params = {
          // robotId: 'robot-001',
          status: 'READY',
          page: this.recordInfo.page,
          size: this.recordInfo.size,
        }
        const response = await getManualMediaFiles('VIDEO', params)
        const items = response.items || []
        this.recordInfo.total = response.total || 0
        if (isUpdate) {
          this.updateRecordings(items)
        } else {
          this.recordings = items
        }
      } catch (error) {
        this.$message.error(errorMessage(error))
      } finally {
        this.recordingsLoading = false
      }
    },
    async getPlayers() {
      const loadSeq = ++this.recordingLoadSeq
      await this.loadRecordings()
      if (loadSeq !== this.recordingLoadSeq) return
      for (const recording of this.recordings) {
        if (loadSeq !== this.recordingLoadSeq) return
        this.recordingData[recording.fileId] = {
          ...recording,
          recordedHls: null,
          player: null,
          // startSecs: this.getTotalTime(this.videoObj.startTime, recording.recordedStartedAt)
        }
        await this.playRecording(recording, loadSeq)
      }
    },
    async playRecording(recording, loadSeq) {
      if (recording.status !== 'READY') return
      const seq = loadSeq != null ? loadSeq : this.recordingLoadSeq
      try {
        const playback = await getCachedFilePlayUrl(recording.fileId)
        if (seq !== this.recordingLoadSeq) return
        const ref = this.$refs[`${this.refPrefix}_${recording.fileId}`]
        let player = Array.isArray(ref) ? ref[0] : ref
        if (!player) return
        let recordedHls = null
        this.destroyRecordedHls(recording.fileId)
        this.selectedRecording = recording
        player.muted = true
        player.playsInline = true
        player.preload = 'auto'
        const playUrl = playback.playUrl
        if (Hls.isSupported()) {
          recordedHls = new Hls({
            autoStartLoad: true,
            startFragPrefetch: true
          })
          recordedHls.on(Hls.Events.MANIFEST_PARSED, () => {
            this.primeVideoPoster(player)
          })
          recordedHls.loadSource(playUrl)
          recordedHls.attachMedia(player)
        } else if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = playUrl
          player.addEventListener('loadeddata', () => this.primeVideoPoster(player), { once: true })
        } else {
          throw new Error('当前浏览器不支持 HLS 播放')
        }
        if (seq !== this.recordingLoadSeq) {
          if (recordedHls) recordedHls.destroy()
          return
        }
        if (!this.recordingData[recording.fileId]) {
          if (recordedHls) recordedHls.destroy()
          return
        }
        this.recordingData[recording.fileId].player = player
        this.recordingData[recording.fileId].recordedHls = recordedHls
        this.bindRecordingDuration(player, recording)
      } catch (error) {
        if (seq !== this.recordingLoadSeq) return
        this.$message.error(errorMessage(error))
      }
    },
    bindRecordingDuration(player, recording) {
      if (!player || !recording || !recording.fileId) return
      const applyDuration = () => {
        const seconds = durationFromVideoElement(player)
        if (!seconds) return
        const index = this.recordings.findIndex(item => item.fileId === recording.fileId)
        if (index !== -1 && this.recordings[index].durationSeconds !== seconds) {
          this.$set(this.recordings, index, Object.assign({}, this.recordings[index], { durationSeconds: seconds }))
        }
        if (this.recordingData[recording.fileId]) {
          this.recordingData[recording.fileId].durationSeconds = seconds
        }
      }
      player.addEventListener('loadedmetadata', applyDuration)
      player.addEventListener('durationchange', applyDuration)
      applyDuration()
    },
    primeVideoPoster(player) {
      if (!player || player.dataset.posterPrimed === 'true') return
      player.muted = true
      player.playsInline = true
      const finish = () => {
        player.dataset.posterPrimed = 'true'
        if (player.pause) player.pause()
        try {
          if (!player.currentTime) player.currentTime = 0.001
        } catch (error) {
          // 部分浏览器在未就绪时不允许 seek。
        }
      }
      const playPromise = player.play && player.play()
      if (playPromise && playPromise.then) {
        playPromise.then(finish).catch(() => {
          try {
            if (!player.currentTime) player.currentTime = 0.001
          } catch (error) {}
        })
      } else {
        finish()
      }
    },
    primeAllRecordingPosters() {
      Object.keys(this.recordingData || {}).forEach(fileId => {
        const data = this.recordingData[fileId]
        if (data && data.player) {
          delete data.player.dataset.posterPrimed
          this.primeVideoPoster(data.player)
        }
      })
    },
    destroyRecordedHls(fileId) {
      const data = this.recordingData[fileId]
      if (!data) return
      if (data.recordedHls) {
        data.recordedHls.destroy()
        data.recordedHls = null
      }
      if (data.player) {
        data.player.pause()
        data.player.removeAttribute('src')
        data.player.load()
        data.player = null
      }
    },
    destroyAllRecordedHls() {
      this.recordingLoadSeq += 1
      Object.keys(this.recordingData || {}).forEach(fileId => {
        this.destroyRecordedHls(fileId)
      })
    },
    playPause(fileId) {
      const { player } = this.recordingData[fileId]
      if (!player) return
      if (player.paused) {
        player.play()
      } else {
        player.pause()
      }
      console.log(333, player.paused);
      this.recordingData = Object.assign({}, this.recordingData, {[fileId]: { ...this.recordingData[fileId], player}})
      
    },
    getTotalTime(startTime, endTime) {
      // return this.timeToSeconds(endTime) - this.timeToSeconds(startTime)
      return (
        (new Date(endTime).getTime() - new Date(startTime).getTime()) / 1000
      );
    }
  }
}
