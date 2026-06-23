<template>
  <div>
    <div class="flx-center p10 custom-video-tool" :class="className">
      <!-- <div class="p6 pixel">
        <div class="w100 list">
          <div v-for="item in pixel.pixelTypes" :key="item.value" class="item" @click="handleChangePixel(item)" :class="{'is-active': pixel.currentPixel === item.value}">{{ item.label }}</div>
        </div>
        <span>{{ pixel.currentPixelLabel }}</span>
      </div> -->
      <div :ref="`dropdownRef${slotKey}_pixel`" v-if="!qualitySelectDisabled">
        <el-dropdown class="custom-dropdown pixel-dropdown" trigger="hover" placement="top">
          <span class="el-dropdown-link">
            {{ pixel.pixelTypes.find(item => item.value === cameraInfo.quality)?.label || '自动' }}
          </span>
          <el-dropdown-menu :ref="`dropdownMenuRef${slotKey}_pixel`" slot="dropdown" :append-to-body="false" class="custom-dropdown-menu pixel-dropdown-menu">
            <el-dropdown-item v-for="item in pixel.pixelTypes"
              :key="item.value"
              class="item"
              :disabled="qualitySelectDisabled"
              @change="changeCameraQuality(cameraInfo)"
              :class="{'is-active': cameraInfo.quality === item.value}">{{ item.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
      <div :ref="`dropdownRef${slotKey}_volume`">
        <el-dropdown class="custom-dropdown volume-dropdown" trigger="hover" placement="top" @visible-change="handleVolumeVisibleChange">
          <!-- <span class="el-dropdown-link">
            {{ pixel.currentPixelLabel }}
          </span> -->
          <span @click="toggleMute" :title="volumeValue">
            <svg-icon :icon-class="volumeIconClass" />
          </span>
          <el-dropdown-menu :ref="`dropdownMenuRef${slotKey}_volume`" slot="dropdown" :append-to-body="false" class="custom-dropdown-menu volume-dropdown-menu">
            <el-dropdown-item>
              <div class="info flx-center">
                <span class="value">{{ volumeInfo.currentVolume }}</span>
                <input 
                  type="range" 
                  min="0" 
                  max="100" 
                  v-model="volumeInfo.currentVolume"
                  class="mt10 mb10 vertical-slider" 
                  @input="updateVolume"
                  id="volumeSlider"
                  :style="{'--value-percent': `${volumeInfo.currentVolume}%`}"
                />
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
      <!-- <div class="volume ml20">
        <div class="info flx-center">
          <span class="value">{{ volumeInfo.currentVolume }}</span>
          <input 
            type="range" 
            min="0" 
            max="100" 
            v-model="volumeInfo.currentVolume"
            class="mt10 mb10 vertical-slider" 
            @input="updateVolume"
            id="volumeSlider"
            :style="{'--value-percent': `${volumeInfo.currentVolume}%`}"
          />
        </div>
        <span @click="toggleMute" :title="volumeValue">
          <svg-icon :icon-class="volumeIconClass" />
        </span>
      </div> -->
      <div @click="openSnapModal()" title="抓拍" :style="{ 'pointer-events': canSnapshot ? 'auto' : 'none' }"><svg-icon icon-class="camera" /></div>
      <div
        @click="toggleLiveRecording(cameraInfo)"
        title="录像"
        :style="{ color: cameraInfo.recordingActive ? '#21C8FF' : '#CAD4E0', 'pointer-events': canRecord ? 'auto' : 'none' }"
      >
        <svg-icon icon-class="record" />
      </div>
      <div :title="openMic ? '关闭麦克风' : '打开麦克风'" @click="toggleMic()">
        <svg-icon :icon-class="openMic ? 'mic' : 'mic-off'" />
      </div>
      <div @click="$refs.controlInnerRef.visible = !$refs.controlInnerRef.visible">
        <svg-icon title="控制器" icon-class="control" />
      </div>
      <div v-if="!videoStatus || videoStatus === 'stopped'" title="刷新" @click="refreshVideo()">
        <svg-icon icon-class="refresh" />
      </div>
      <div v-if="['paused', 'playing'].includes(videoStatus)" :title="videoStatus === 'paused' ? `播放-${videoStatus}` : `暂停-${videoStatus}`" @click="playPauseVideo()">
        <svg-icon :icon-class="videoStatus === 'paused' ? 'play' : 'pause'" />
      </div>
      <div :title="isFullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen()">
        <svg-icon :icon-class="isFullscreen ? 'close-fullscreen' : 'fullscreen1'" />
      </div>
      <div title="移除" @click="$emit('removeVideo', slotKey)">
        <svg-icon icon-class="delete" />
      </div>
    </div>
    <!-- <Snap ref="snapModalRef" :idName="idName" /> -->
    <ControlInner ref="controlInnerRef" :cameraInfo="cameraInfo" />
  </div>
</template>

<script>
// import Snap from './modal/Snap.vue';
import videoUtils from './../../..//utils/videoUtils.js'
import ControlInner from './ControlInner.vue';
import { createSnapshotFile, snapshotImageUrl } from '../../../api/media.js';
import { mapActions, mapState } from 'vuex';
export default {
  name: 'VideoTool',
  components: {
    // Snap,
    ControlInner,
  },
  props: {
    slotKey: {
      type: String,
      default: ''
    },
    idName: {
      type: String,
      default: ''
    },
    className: {
      type: Object,
      default: () => ({ one: true }),
    },
    videoStatus: {
      type: [String, Boolean],
      default: false
    },
    cameraKey: {
      type: String,
      default: '',
    },
  },
  mixins: [videoUtils],
  computed: {
    ...mapState('websocketRobot', ['cameras']),
    cameraInfo() {
      return this.cameras?.[this.cameraKey] || {}
    },
    canSnapshot() {
      return !!this.cameraInfo.session && this.cameraInfo.watching && !this.cameraInfo.stopping && !this.cameraInfo.stopped
    },
    canRecord() {
      return !!this.cameraInfo.session && this.cameraInfo.watching && !this.cameraInfo.stopping && !this.cameraInfo.stopped
    },
    qualitySelectDisabled() {
      return this.cameraInfo.qualityChanging
          || this.activeRecordingInProgress
          || this.intercomInProgress
    },
    activeRecordingInProgress() {
      return this.cameraInfo.recordingActive || (this.cameraInfo.activeRecording && this.cameraInfo.activeRecording.status === 'RECORDING')
    },
    intercomInProgress() {
      return this.cameraInfo.intercomActive || (this.cameraInfo.intercomStatus && !['IDLE', 'FAILED'].includes(this.cameraInfo.intercomStatus))
    },
  },
  methods: {
    ...mapActions('websocketRobot', ['toggleLiveRecording']),
    // 播放暂停
    playPauseVideo() {
      // 切换状态
      // this.isPlay = !this.isPlay;
      // 发送事件：1 表示暂停，0 表示播放
      this.$emit('playPauseVideo');
    },
    // 刷新视频
    refreshVideo() {
      this.$emit('refreshVideo', this.slotKey);
    },
    // =========================================抓拍=======================================
    // 抓拍弹框
    openSnapModal1() {
      this.$refs.snapModalRef.showModal();
    },
    async openSnapModal() {
      const camera = this.cameraInfo
      if (!camera.session) return
      const capturedAt = new Date().toISOString()
      const blob = await this.captureFrameBlob()
      if (!blob) {
        this.$message.warning('当前画面不可抓拍')
        return
      }
      const form = new FormData()
      form.append('trackSid', camera.session.trackSid || 'TR_pending')
      form.append('reason', 'manual_abnormal')
      form.append('remark', `${camera.name} 手动抓拍`)
      form.append('clientCapturedAt', capturedAt)
      form.append('previewImageHash', `${blob.size}`)
      form.append('file', blob, `${camera.robotId}-${camera.deviceId}-${Date.now()}.jpg`)
      const response = await createSnapshotFile(camera.session.sessionId, form)
      console.log('API snapshot', response)
      this.showSnapshotSuccess(camera, response)
    },
    captureFrameBlob() {
      const video = document.getElementById(this.idName).querySelector('video')
      if (!video || !video.videoWidth || !video.videoHeight) return Promise.resolve(null)
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      return new Promise(resolve => {
        canvas.toBlob(blob => resolve(blob), 'image/jpeg', 0.88)
      })
    },
    showSnapshotSuccess(camera, snapshot) {
      if (!snapshot || !snapshot.snapshotId) {
        this.$message.success('抓拍已保存')
        return
      }
      const url = snapshotImageUrl(snapshot.snapshotId)
      this.$message({
        type: 'success',
        customClass: 'snapshot-message',
        dangerouslyUseHTMLString: true,
        message: `抓拍已保存 <a class="message-link" href="${this.escapeHtml(url)}" target="_blank" rel="noopener noreferrer">查看</a>`
      })
    },
    escapeHtml(value) {
      return String(value).replace(/[&<>"']/g, char => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      }[char]))
    }
  }
}
</script>

<style lang="scss" scoped>

</style>
