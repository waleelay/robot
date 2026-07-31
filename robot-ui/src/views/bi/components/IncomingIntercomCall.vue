<template>
  <div class="intercom-call-host">
    <section
      v-if="displayCall"
      ref="callWindow"
      class="intercom-call-window"
      :class="`is-${callMode}`"
      :style="windowStyle"
      role="dialog"
      aria-modal="false"
      aria-label="语音电话"
    >
      <header class="call-window-header" @pointerdown="startDrag">
        <div class="header-title">
          <h2>语音电话</h2>
          <span v-if="waitingCallCount" class="waiting-call-badge">还有 {{ waitingCallCount }} 通来电</span>
        </div>
        <div class="header-actions">
          <button
            v-if="callMode === 'audio'"
            class="enable-video-button"
            type="button"
            :disabled="activeIncomingCall.videoLoading"
            @click.stop="enableVideo"
          >
            {{ activeIncomingCall.videoLoading ? '开启中...' : '开启画面' }}
          </button>
          <button
            class="header-icon-button"
            type="button"
            :disabled="operationPending"
            :title="callMode === 'ringing' ? '拒接' : '挂断'"
            @click.stop="closeCall"
          >
            <svg-icon icon-class="close" />
          </button>
        </div>
      </header>

      <div v-if="callMode === 'ringing'" class="compact-call-content">
        <img class="robot-illustration" src="@/assets/images/new-bi/car.png" width="78" height="78" alt="">
        <div class="compact-call-info ml5">
          <span class="name text-ellipsis" :title="robotName">{{ robotName }}</span>
          <span class="desc mt10">{{ ringingDescription }}</span>
        </div>
        <div class="compact-call-actions incoming-actions ml13">
          <button
            class="round-action is-danger"
            type="button"
            :disabled="operationPending"
            title="拒接"
            @click="reject"
          >
            <svg-icon icon-class="intercom-hangup" />
          </button>
          <button
            class="round-action is-answer"
            type="button"
            :disabled="operationPending || manualIntercomActive"
            :title="manualIntercomActive ? '当前正在通话，请先结束当前通话' : '接听'"
            @click="accept"
          >
            <svg-icon icon-class="intercom-answer" />
          </button>
        </div>
      </div>

      <div v-else-if="callMode === 'audio'" class="compact-call-content">
        <img class="robot-illustration" src="@/assets/images/new-bi/car.png" width="78" height="78" alt="">
        <div class="compact-call-info ml5">
          <span class="name text-ellipsis" :title="robotName">{{ robotName }}</span>
          <span class="desc mt10">{{ formattedDuration }}</span>
        </div>
        <div class="compact-call-actions audio-actions ml13">
          <button
            class="round-action is-danger"
            type="button"
            :disabled="operationPending"
            title="挂断"
            @click="hangup"
          >
            <svg-icon icon-class="intercom-hangup" />
          </button>
          <button
            class="round-action is-local"
            :class="{ 'is-muted': activeIncomingCall.micMuted }"
            type="button"
            :title="activeIncomingCall.micMuted ? '恢复本地麦克风' : '静音本地麦克风'"
            @click="toggleMicrophone"
          >
            <svg-icon :icon-class="activeIncomingCall.micMuted ? 'mic-off-fill' : 'mic-fill'" />
          </button>
          <button
            class="round-action is-local"
            :class="{ 'is-muted': activeIncomingCall.speakerMuted }"
            type="button"
            :title="activeIncomingCall.speakerMuted ? '恢复本地扬声器' : '静音本地扬声器'"
            @click="toggleSpeaker"
          >
            <svg-icon :icon-class="activeIncomingCall.speakerMuted ? 'volume-mute-fill' : 'volume-fill'" />
          </button>
          <button
            class="round-action is-local"
            type="button"
            title="跳转控制中心"
            @click="openRemoteControl"
          >
            <svg-icon icon-class="control" />
          </button>
        </div>
      </div>

      <div v-else class="video-call-content">
        <div class="call-video-shell">
          <video ref="callVideo" autoplay playsinline muted />
          <span class="close-video" @click="disableVideo">关闭画面</span>
          <div v-if="!videoReady" class="video-loading">
            <i class="el-icon-loading" />
            <span>{{ activeIncomingCall.videoLoading ? '正在开启主摄像头...' : '等待视频画面...' }}</span>
          </div>
        </div>
        <div class="video-call-info">
          <span class="name text-ellipsis" :title="robotName">{{ robotName }}</span>
          <span class="desc">{{ formattedDuration }}</span>
        </div>
        <div class="video-call-actions">
          <div class="labeled-action">
            <button
              class="round-action is-danger"
              type="button"
              :disabled="operationPending"
              title="挂断"
              @click="hangup"
            >
              <svg-icon icon-class="intercom-hangup" />
            </button>
            <span>结束</span>
          </div>
          <div class="labeled-action">
            <button
              class="round-action is-local"
              :class="{ 'is-muted': activeIncomingCall.micMuted }"
              type="button"
              :title="activeIncomingCall.micMuted ? '恢复本地麦克风' : '静音本地麦克风'"
              @click="toggleMicrophone"
            >
              <svg-icon :icon-class="activeIncomingCall.micMuted ? 'mic-off-fill' : 'mic-fill'" />
            </button>
            <span>麦克风</span>
            <!-- <span>{{ activeIncomingCall.micMuted ? '取消静音' : '静音' }}</span> -->
          </div>
          <div class="labeled-action">
            <button
              class="round-action is-local"
              :class="{ 'is-muted': activeIncomingCall.speakerMuted }"
              type="button"
              :title="activeIncomingCall.speakerMuted ? '恢复本地扬声器' : '静音本地扬声器'"
              @click="toggleSpeaker"
            >
              <svg-icon :icon-class="activeIncomingCall.speakerMuted ? 'volume-mute-fill' : 'volume-fill'" />
            </button>
            <!-- <span>{{ activeIncomingCall.speakerMuted ? '恢复扬声器' : '扬声器' }}</span> -->
            <span>扬声器</span>
          </div>
          <div class="labeled-action">
            <button class="round-action is-local" type="button" title="跳转控制中心" @click="openRemoteControl">
              <svg-icon icon-class="control" />
            </button>
            <span>控制中心</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex'

const SCREEN_WIDTH = 1920
const SCREEN_HEIGHT = 1080
const WINDOW_MARGIN = 16
const COMPACT_SIZE = { width: 358, height: 152 }
const AUDIO_SIZE = { width: 410, height: 152 }// width: 394, height: 152
const VIDEO_SIZE = { width: 324, height: 382 }

export default {
  name: 'IncomingIntercomCall',
  data() {
    return {
      now: Date.now(),
      timer: null,
      position: { x: 0, y: 0 },
      dragState: null,
      hangupPending: false
    }
  },
  computed: {
    ...mapState('websocketRobot', [
      'incomingCalls',
      'activeIncomingCall',
      'callOperationPending',
      'cameras'
    ]),
    currentCall() {
      return this.incomingCalls[0] || null
    },
    displayCall() {
      return this.activeIncomingCall || this.currentCall
    },
    callMode() {
      if (!this.activeIncomingCall) return 'ringing'
      return this.activeIncomingCall.videoEnabled ? 'video' : 'audio'
    },
    windowSize() {
      return this.callMode === 'video' ? VIDEO_SIZE : this.callMode === 'audio' ? AUDIO_SIZE : COMPACT_SIZE
    },
    windowStyle() {
      return {
        width: `${this.windowSize.width}px`,
        height: `${this.windowSize.height}px`,
        transform: `translate3d(${this.position.x}px, ${this.position.y}px, 0)`
      }
    },
    operationPending() {
      return this.callOperationPending || this.hangupPending
    },
    robotName() {
      return this.displayCall.robotName || this.displayCall.robotId || '机器人'
    },
    ringingDescription() {
      if (this.manualIntercomActive) return '当前正在通话，来电等待'
      return this.displayCall.reason || '邀请你进行通话'
    },
    waitingCallCount() {
      return Math.max(0, this.incomingCalls.length - (this.callMode === 'ringing' ? 1 : 0))
    },
    manualIntercomActive() {
      if (this.activeIncomingCall) return false
      return Object.values(this.cameras).some(camera => camera && camera.intercomActive)
    },
    activeCamera() {
      if (!this.activeIncomingCall) return null
      return this.cameras[this.activeIncomingCall.cameraKey] || null
    },
    remoteVideoTrack() {
      return this.activeCamera && this.activeCamera.remoteVideoTrack
    },
    videoReady() {
      return Boolean(this.remoteVideoTrack)
    },
    formattedDuration() {
      const connectedAt = Number(this.activeIncomingCall && this.activeIncomingCall.connectedAtEpochMillis)
      if (!Number.isFinite(connectedAt)) return '00:00'
      const totalSeconds = Math.max(0, Math.floor((this.now - connectedAt) / 1000))
      const hours = Math.floor(totalSeconds / 3600)
      const minutes = Math.floor((totalSeconds % 3600) / 60)
      const seconds = totalSeconds % 60
      const mmss = `${this.pad(minutes)}:${this.pad(seconds)}`
      return hours > 0 ? `${this.pad(hours)}:${mmss}` : mmss
    }
  },
  watch: {
    'displayCall.callId': {
      immediate: true,
      handler(callId, oldCallId) {
        if (callId && callId !== oldCallId) this.positionWindow()
      }
    },
    callMode() {
      this.positionWindow()
      this.attachVideoTrack()
    },
    remoteVideoTrack(next, previous) {
      const video = this.$refs.callVideo
      if (previous && video && typeof previous.detach === 'function') previous.detach(video)
      this.attachVideoTrack()
    }
  },
  mounted() {
    this.timer = window.setInterval(() => { this.now = Date.now() }, 500)
    window.addEventListener('pointermove', this.drag)
    window.addEventListener('pointerup', this.stopDrag)
    window.addEventListener('pointercancel', this.stopDrag)
  },
  beforeDestroy() {
    window.clearInterval(this.timer)
    window.removeEventListener('pointermove', this.drag)
    window.removeEventListener('pointerup', this.stopDrag)
    window.removeEventListener('pointercancel', this.stopDrag)
    const video = this.$refs.callVideo
    if (this.remoteVideoTrack && video && typeof this.remoteVideoTrack.detach === 'function') {
      this.remoteVideoTrack.detach(video)
    }
  },
  methods: {
    ...mapActions('websocketRobot', [
      'acceptIncomingCall',
      'rejectIncomingCall',
      'hangupIncomingCall',
      'toggleIncomingCallMicrophone',
      'toggleIncomingCallSpeaker',
      'enableIncomingCallVideo',
      'disableIncomingCallVideo',
      'setSelectedRobotId'
    ]),
    pad(value) {
      return String(value).padStart(2, '0')
    },
    positionWindow() {
      this.position = {
        x: SCREEN_WIDTH - this.windowSize.width - WINDOW_MARGIN,
        y: SCREEN_HEIGHT - this.windowSize.height - WINDOW_MARGIN
      }
    },
    startDrag(event) {
      if (event.button !== undefined && event.button !== 0) return
      if (event.target.closest('button')) return
      const host = this.$el
      const rect = host.getBoundingClientRect()
      this.dragState = {
        startX: event.clientX,
        startY: event.clientY,
        originX: this.position.x,
        originY: this.position.y,
        scaleX: rect.width ? rect.width / SCREEN_WIDTH : 1,
        scaleY: rect.height ? rect.height / SCREEN_HEIGHT : 1
      }
      event.preventDefault()
    },
    drag(event) {
      if (!this.dragState) return
      const x = this.dragState.originX + (event.clientX - this.dragState.startX) / this.dragState.scaleX
      const y = this.dragState.originY + (event.clientY - this.dragState.startY) / this.dragState.scaleY
      this.position = {
        x: Math.round(Math.max(0, Math.min(SCREEN_WIDTH - this.windowSize.width, x))),
        y: Math.round(Math.max(0, Math.min(SCREEN_HEIGHT - this.windowSize.height, y)))
      }
    },
    stopDrag() {
      this.dragState = null
    },
    accept() {
      if (this.currentCall) this.acceptIncomingCall(this.currentCall.callId)
    },
    reject() {
      if (this.currentCall) this.rejectIncomingCall(this.currentCall.callId)
    },
    closeCall() {
      if (this.callMode === 'ringing') {
        this.reject()
      } else {
        this.hangup()
      }
    },
    async hangup() {
      if (this.hangupPending) return
      this.hangupPending = true
      const video = this.$refs.callVideo
      if (this.remoteVideoTrack && video && typeof this.remoteVideoTrack.detach === 'function') {
        this.remoteVideoTrack.detach(video)
      }
      try {
        await this.hangupIncomingCall()
      } finally {
        this.hangupPending = false
      }
    },
    toggleMicrophone() {
      this.toggleIncomingCallMicrophone()
    },
    toggleSpeaker() {
      this.toggleIncomingCallSpeaker()
    },
    async enableVideo() {
      await this.enableIncomingCallVideo()
      this.attachVideoTrack()
    },
    async disableVideo() {
      const video = this.$refs.callVideo
      if (this.remoteVideoTrack && video && typeof this.remoteVideoTrack.detach === 'function') {
        this.remoteVideoTrack.detach(video)
      }
      await this.disableIncomingCallVideo()
    },
    attachVideoTrack() {
      if (this.callMode !== 'video' || !this.remoteVideoTrack) return
      this.$nextTick(() => {
        const video = this.$refs.callVideo
        if (!video) return
        this.remoteVideoTrack.attach(video)
        video.play().catch(() => {})
      })
    },
    async openRemoteControl() {
      if (!this.activeIncomingCall) return
      if (this.activeIncomingCall.videoEnabled) {
        await this.disableVideo()
      }
      await this.setSelectedRobotId(this.activeIncomingCall.robotId)
      if (this.$route.name !== 'biPatrolMonitor') {
        await this.$router.push({ name: 'biPatrolMonitor' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.intercom-call-host {
  position: absolute;
  z-index: 4000;
  top: 0;
  left: 0;
  width: 1920px;
  height: 1080px;
  pointer-events: none;
}

.intercom-call-window {
  position: absolute;
  top: 0;
  left: 0;
  overflow: hidden;
  box-sizing: border-box;
  color: #fff;
  border: 1px solid #2c8eff;
  background: linear-gradient(
    180deg,
    rgba(4, 60, 149, 0.4) 0%,
    rgba(4, 33, 68, 0.3) 5.5%,
    rgba(4, 23, 62, 0.32) 51.5%,
    rgba(7, 45, 94, 0.31) 92.6%,
    rgba(4, 62, 151, 0.4) 100%
  );
  box-shadow: 0 7px 21px rgba(0, 18, 45, 0.55);
  backdrop-filter: blur(15px);
  pointer-events: auto;
  user-select: none;
}

.call-window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  margin: 9px 9px 0;
  padding-left: 10px;
  box-sizing: border-box;
  background: linear-gradient(90deg, #2C8EFF -0.18%, rgba(0, 13, 59, 0.19) 94.39%);
  cursor: move;
  touch-action: none;

  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0;
  }
}

.header-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}

.waiting-call-badge {
  display: inline-flex;
  align-items: center;
  height: 16px;
  padding: 0 5px;
  color: #d7edff;
  border: 1px solid rgba(84, 181, 255, 0.72);
  border-radius: 2px;
  background: rgba(4, 35, 72, 0.72);
  font-size: 8px;
  line-height: 1;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 5px;
}

button {
  font-family: "Microsoft YaHei", sans-serif;
}

.header-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  color: #4ab8ff;
  border: 0;
  background: transparent;
  cursor: pointer;

  .svg-icon {
    font-size: 16px;
  }
}

.enable-video-button {
  min-width: 52px;
  height: 21px;
  padding: 0 7px;
  color: #d7edff;
  border: 0;
  border-radius: 2px;
  background: rgba(38, 84, 152, 0.50);
  font-size: 10px;
  cursor: pointer;
}

button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.compact-call-content {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr) auto;
  align-items: center;
  height: 102px;
  padding: 12px 10px 12px 14px;
  box-sizing: border-box;
}

.robot-illustration {
  display: block;
  object-fit: contain;
}

.compact-call-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  font-family: "Microsoft YaHei", sans-serif;

  .name {
    font-size: 18px;
    font-weight: 600;
    line-height: 17.517px;
  }

  .desc {
    color: #d7edff;
    font-size: 14px;
    line-height: 17.517px;
  }
}

.compact-call-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
}

.audio-actions {
  gap: 10px;
}

.round-action {
  display: inline-flex;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: #159aff;
  border: none;
  border-radius: 50%;
  background: #021328;
  box-shadow: 0 0 6.892px 0 #159AFF inset;
  cursor: pointer;

  .svg-icon {
    width: 18px;
    height: 18px;
  }

  &.is-danger {
    color: #fe0b0b;
    background: #280202;
    box-shadow: 0 0 8px 0 #FE0B0B inset;
  }

  &.is-answer {
    color: #0bf9fe;
    background: #021F28;
    box-shadow: 0 0 8px 0 #0BF9FE inset;
  }

  // &.is-muted {
  //   color: #8aa8bf;
  //   filter: saturate(0.55);
  // }
}

.video-call-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.call-video-shell {
  position: relative;
  width: 304px;
  height: 171px;
  margin-top: 10px;
  overflow: hidden;
  background: #020b16;
  box-shadow: 0 5px 14px rgba(0, 9, 24, 0.48);

  video {
    display: block;
    width: 100%;
    height: 100%;
    background: #020b16;
    object-fit: contain;
  }
}

.close-video {
  position: absolute;
  z-index: 2;
  right: 10px;
  bottom: 8px;
  padding: 6px;
  border-radius: 2px;
  background: #0E1627;
  color: #d7edff;
  color: #FFF;
  text-align: center;
  font-family: "Alibaba PuHuiTi";
  font-size: 12px;
  line-height: 12px; /* 100% */
  letter-spacing: 0.857px;
  cursor: pointer;
}

.video-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #aedcff;
  background: rgba(2, 11, 22, 0.82);
  font-size: 9px;
}

.video-call-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 20px;
  font-family: "Microsoft YaHei", sans-serif;
  .name {
    max-width: 280px;
    font-size: 14px;
    font-weight: 600;
    line-height: 17.517px;
  }

  .desc {
    color: #d7edff;
    font-size: 14px;
    line-height: 17.517px;
  }
}

.video-call-actions {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 23px;
  width: 100%;
  margin-top: 20px;
}

.labeled-action {
  display: flex;
  flex: 0 0 40px;
  flex-direction: column;
  align-items: center;
  gap: 6px;

  > span {
    color: #d7edff;
    font-size: 12px;
    line-height: 1.2;
    text-align: center;
    white-space: nowrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .intercom-call-window {
    transition: none;
  }
}
</style>
