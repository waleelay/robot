<template>
  <div>
    <div class="flx-center p10 custom-video-tool" :class="className">
      <!-- 视频清晰度 -->
      <!-- <div :ref="`dropdownRef${slotKey}_pixel`" :style="{ display: !qualitySelectDisabled ? 'block' : 'none' }">
        <el-dropdown class="custom-dropdown pixel-dropdown" trigger="hover" placement="top" @command="changeQuality">
          <span class="el-dropdown-link">
            {{ pixel.pixelTypes.find(item => item.value === cameraInfo.quality)?.label || '自动' }}
          </span>
          <el-dropdown-menu :ref="`dropdownMenuRef${slotKey}_pixel`" slot="dropdown" :append-to-body="false" class="custom-dropdown-menu pixel-dropdown-menu">
            <el-dropdown-item v-for="item in pixel.pixelTypes"
              :key="item.value"
              class="item"
              :disabled="qualitySelectDisabled"
              :command="item.value"
              :class="{'is-active': cameraInfo.quality === item.value}">{{ item.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div> -->
      <template :style="{ display: canSnapshot ? 'block' : 'none' }">
        <!-- 音量 -->
        <!-- <div :ref="`dropdownRef${slotKey}_volume`">
          <el-dropdown class="custom-dropdown volume-dropdown" trigger="hover" :hide-on-click="false" placement="top" @visible-change="handleVolumeVisibleChange">
            <span @click="toggleMute" :title="volumeValue">
              <svg-icon :icon-class="volumeIconClass" />
            </span>
            <el-dropdown-menu :ref="`dropdownMenuRef${slotKey}_volume`" slot="dropdown" :append-to-body="false" class="custom-dropdown-menu volume-dropdown-menu">
              <el-dropdown-item>
                <div class="info flx-center">
                  <span class="value mt10">{{ volumeInfo.currentVolume }}</span>
                  <el-slider
                    :min="0"
                    :max="100"
                    v-model="volumeInfo.currentVolume"
                    class="mt10 mb5 vertical-slider"
                    vertical
                    @input="updateVolume"
                    @change="updateVolume"
                    height="83px"
                    :show-tooltip="false"
                    :style="{'--value-percent': `${volumeInfo.currentVolume}%`}"
                    id="volumeSlider" />
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div> -->
        <div @click="openSnapModal()" title="抓拍" :style="{ 'pointer-events': canSnapshot ? 'auto' : 'none' }"><svg-icon icon-class="camera" /></div>
        <div
          v-if="canRecord"
          @click="toggleLiveRecording(cameraInfo)"
          :title="cameraInfo.recordingActive ? '停止录制' : '录制'"
          :style="{ 'pointer-events': canRecord ? 'auto' : 'none' }"
        >
          <svg-icon icon-class="record" :style="{ color: cameraInfo.recordingActive ? '#21C8FF' : '#CAD4E0' }" />
        </div>
        <!-- <div :title="openMic ? '关闭麦克风' : '打开麦克风'" @click="toggleMic()">
          <svg-icon :icon-class="openMic ? 'mic' : 'mic-off'" />
        </div> -->
        <div v-if="showCameraControl" @click="$refs.controlInnerRef.visible = !$refs.controlInnerRef.visible">
          <svg-icon title="控制器" icon-class="control" />
        </div>
      </template>
      <div v-if="!videoStatus || videoStatus === 'stopped'" title="刷新" @click="refreshVideo()">
        <svg-icon icon-class="refresh" />
      </div>
      <div v-if="canSnapshot && ['paused', 'playing'].includes(videoStatus)" :title="videoStatus === 'paused' ? '播放' : '暂停'" @click="playPauseVideo()">
        <svg-icon :icon-class="videoStatus === 'paused' ? 'play' : 'pause'" />
      </div>
      <div :title="isFullscreen ? '退出视频全屏' : '视频全屏'" @click="toggleFullscreen()">
        <svg-icon :icon-class="isFullscreen ? 'close-fullscreen' : 'fullscreen1'" />
      </div>
      <div title="移除" @click="$emit('removeVideo', slotKey)">
        <svg-icon icon-class="delete" />
      </div>
    </div>
    <!-- <Snap ref="snapModalRef" :idName="idName" /> -->
    <ControlInner v-if="showCameraControl" ref="controlInnerRef" :cameraInfo="cameraInfo" />
    <MultimediaDetail ref="multimediaDetailRef" />
  </div>
</template>

<script>
// import Snap from './modal/Snap.vue';
import videoUtils from './../../../utils/videoUtils.js'
import ControlInner from './ControlInner.vue';
import MultimediaDetail from '../patrol/monitor/second/components/MultimediaDetail.vue';
import { uploadFile } from '../../../api/media.js';
import { mapActions, mapState } from 'vuex';
import { none } from 'ol/centerconstraint';
export default {
  name: 'VideoTool',
  components: {
    // Snap,
    ControlInner,
    MultimediaDetail,
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
    showControl: {
      type: Boolean,
      default: false
    }
  },
  mixins: [videoUtils],
  computed: {
    ...mapState('websocketRobot', ['cameras', 'robots']),
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    cameraInfo() {
      return this.cameras?.[this.cameraKey] || {}
    },
    // 固定摄像头不可遥控，不展示控制器
    isFixedCamera() {
      const info = this.cameraInfo || {}
      if (info.sourceType === 'FIXED_CAMERA') return true
      const robotId = info.robotId
      const robot = this.robotBaseInfo?.[robotId]
        || (this.robots || []).find(item => String(item.robotId) === String(robotId))
        || {}
      return robot.typeCode === 'FIXED_CAMERA'
        || robot.type === 'FIXED_CAMERA'
        || robot.type === '固定摄像头'
    },
    showCameraControl() {
      return this.showControl
        && !this.isFixedCamera
        && ['single_gimbal', 'dual_gimbal', 'body'].includes(this.cameraInfo.groupType)
    },
    canSnapshot() {
      return this.cameraInfo.remoteVideoTrack
      // return !!this.cameraInfo.session && this.cameraInfo.watching && !this.cameraInfo.stopping && !this.cameraInfo.stopped
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
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['toggleLiveRecording', 'setSnapshotTime', 'changeCameraQuality']),
    changeQuality(quality) {
      this.changeCameraQuality({ ...this.cameraInfo, quality })
    },
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
      form.append('fileType', 'IMAGE')
      form.append('robotId', camera.robotId)
      form.append('deviceId', camera.deviceId)
      form.append('sourceFileId', `web-snapshot/${camera.robotId}/${camera.deviceId}/${Date.now()}/${blob.size}`)
      form.append('metadata', JSON.stringify({
        source: 'WEB_SNAPSHOT',
        sessionId: camera.session.sessionId,
        trackSid: camera.session.trackSid || 'TR_pending',
        channel: camera.channel,
        capturedAt,
        remark: `${camera.name} 手动抓拍`
      }))
      form.append('file', blob, `${camera.robotId}-${camera.deviceId}-${Date.now()}.jpg`)
      const response = await uploadFile(form)
      console.log('API snapshot', response)
      this.showSnapshotSuccess(camera, response)
      this.setSnapshotTime(capturedAt)
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
    async showSnapshotSuccess(camera, snapshot) {
      if (!snapshot || !snapshot.fileId) {
        this.$message.success('抓拍已保存')
        return
      }
      // 弹窗内用 blob 直链预览（axios 带 token），避免新标签页无法携带 Authorization 的问题。
      const h = this.$createElement
      this.$message({
        type: 'success',
        customClass: 'snapshot-message',
        duration: 5000,
        message: h('div', {}, [
          '抓拍已保存 ',
          h('a', {
            class: 'message-link',
            on: { click: () => this.openSnapshotDetail(snapshot) }
          }, '查看')
        ])
      })
    },
    openSnapshotDetail(snapshot) {
      const ref = this.$refs.multimediaDetailRef
      if (!ref) return
      ref.open({ item: snapshot, tabIndex: 0, simple: true })
    }
  },
  watch: {
    canSnapshot: {
      handler(newVal, oldVal) {
        if (newVal) {
          // this.updateDropdownStyle('volume', true);
        }
      },
      immediate: true
    },
    qualitySelectDisabled: {
      handler(newVal, oldVal) {
        if (!newVal) {
          // this.updateDropdownStyle('pixel', true);
        }
      },
      immediate: true
    }
  }
}
</script>

<style lang="scss" scoped>

</style>
