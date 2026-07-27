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
            {{ activeIncomingCall.videoLoading ? '开启中...' : '开启视频' }}
          </button>
          <button
            class="header-icon-button"
            type="button"
            :disabled="operationPending"
            :title="callMode === 'ringing' ? '拒接' : '挂断'"
            @click.stop="closeCall"
          >
            <svg-icon icon-class="close1" />
          </button>
        </div>
      </header>

      <div v-if="callMode === 'ringing'" class="compact-call-content">
        <img class="robot-illustration" src="@/assets/images/new-bi/intercom-robot.png" alt="">
        <div class="compact-call-info">
          <strong>{{ robotName }}</strong>
          <span>{{ ringingDescription }}</span>
        </div>
        <div class="compact-call-actions incoming-actions">
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
        <img class="robot-illustration" src="@/assets/images/new-bi/intercom-robot.png" alt="">
        <div class="compact-call-info">
          <strong>{{ robotName }}</strong>
          <span>{{ formattedDuration }}</span>
        </div>
        <div class="compact-call-actions audio-actions">
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
            <svg-icon :icon-class="activeIncomingCall.micMuted ? 'mic-off' : 'mic'" />
          </button>
          <button
            class="round-action is-local"
            :class="{ 'is-muted': activeIncomingCall.speakerMuted }"
            type="button"
            :title="activeIncomingCall.speakerMuted ? '恢复本地扬声器' : '静音本地扬声器'"
            @click="toggleSpeaker"
          >
            <svg-icon :icon-class="activeIncomingCall.speakerMuted ? 'volume-mute' : 'volume-l'" />
          </button>
        </div>
      </div>

      <div v-else class="video-call-content">
        <div class="call-video-shell">
          <video ref="callVideo" autoplay playsinline muted />
          <button
            class="close-video-button"
            type="button"
            title="关闭视频"
            aria-label="关闭视频"
            @click="disableVideo"
          >
            <svg-icon icon-class="close1" />
          </button>
          <div v-if="!videoReady" class="video-loading">
            <i class="el-icon-loading" />
            <span>{{ activeIncomingCall.videoLoading ? '正在开启主摄像头...' : '等待视频画面...' }}</span>
          </div>
        </div>
        <div class="video-call-info">
          <strong>{{ robotName }}</strong>
          <span>{{ formattedDuration }}</span>
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
              <svg-icon :icon-class="activeIncomingCall.micMuted ? 'mic-off' : 'mic'" />
            </button>
            <span>{{ activeIncomingCall.micMuted ? '取消静音' : '静音' }}</span>
          </div>
          <div class="labeled-action">
            <button
              class="round-action is-local"
              :class="{ 'is-muted': activeIncomingCall.speakerMuted }"
              type="button"
              :title="activeIncomingCall.speakerMuted ? '恢复本地扬声器' : '静音本地扬声器'"
              @click="toggleSpeaker"
            >
              <svg-icon :icon-class="activeIncomingCall.speakerMuted ? 'volume-mute' : 'volume-l'" />
            </button>
            <span>{{ activeIncomingCall.speakerMuted ? '恢复扬声器' : '扬声器' }}</span>
          </div>
          <div class="labeled-action">
            <button class="round-action is-local" type="button" title="跳转上装控制" @click="openRemoteControl">
              <svg-icon icon-class="control" />
            </button>
            <span>上装控制</span>
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
const WINDOW_MARGIN = 32
const COMPACT_SIZE = { width: 716, height: 304 }
const VIDEO_SIZE = { width: 648, height: 764 }

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
      return this.callMode === 'video' ? VIDEO_SIZE : COMPACT_SIZE
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
  border: 2px solid #2c8eff;
  background:
    linear-gradient(118deg, rgba(17, 59, 113, 0.68), rgba(2, 19, 40, 0.94)),
    #06182e;
  box-shadow: 0 14px 42px rgba(0, 18, 45, 0.55), inset 0 0 44px rgba(43, 134, 255, 0.12);
  pointer-events: auto;
  user-select: none;
}

.call-window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80px;
  margin: 18px 18px 0;
  padding: 0 22px;
  box-sizing: border-box;
  background: linear-gradient(90deg, rgba(40, 130, 246, 0.9), rgba(15, 43, 86, 0.58));
  cursor: move;
  touch-action: none;

  h2 {
    margin: 0;
    font-size: 32px;
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0;
  }
}

.header-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 18px;
}

.waiting-call-badge {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 10px;
  color: #d7edff;
  border: 1px solid rgba(84, 181, 255, 0.72);
  border-radius: 4px;
  background: rgba(4, 35, 72, 0.72);
  font-size: 16px;
  line-height: 1;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 24px;
}

button {
  font-family: "Microsoft YaHei", sans-serif;
}

.header-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  color: #4ab8ff;
  border: 0;
  background: transparent;
  cursor: pointer;

  .svg-icon {
    width: 32px;
    height: 32px;
  }
}

.enable-video-button {
  min-width: 104px;
  height: 44px;
  padding: 0 14px;
  color: #d7edff;
  border: 0;
  border-radius: 4px;
  background: rgba(36, 96, 178, 0.62);
  font-size: 20px;
  cursor: pointer;
}

button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.compact-call-content {
  display: grid;
  grid-template-columns: 150px minmax(220px, 1fr) auto;
  align-items: center;
  height: 202px;
  padding: 0 26px 0 34px;
  box-sizing: border-box;
}

.robot-illustration {
  display: block;
  width: 132px;
  height: 132px;
  object-fit: contain;
}

.compact-call-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  padding-left: 12px;

  strong {
    overflow: hidden;
    font-size: 36px;
    font-weight: 500;
    line-height: 1.25;
    letter-spacing: 0;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 14px;
    color: #d7edff;
    font-size: 28px;
    line-height: 1.2;
    letter-spacing: 0;
  }
}

.compact-call-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 38px;
  padding-left: 22px;
}

.audio-actions {
  gap: 26px;
}

.round-action {
  display: inline-flex;
  flex: 0 0 72px;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  padding: 0;
  color: #159aff;
  border: 1px solid rgba(21, 154, 255, 0.82);
  border-radius: 50%;
  background: radial-gradient(circle at 50% 35%, rgba(7, 89, 150, 0.96), #021328 72%);
  box-shadow: inset 0 0 18px rgba(21, 154, 255, 0.55), 0 0 10px rgba(21, 154, 255, 0.28);
  cursor: pointer;

  .svg-icon {
    width: 36px;
    height: 36px;
  }

  &.is-danger {
    color: #fe0b0b;
    border-color: rgba(255, 28, 28, 0.84);
    background: radial-gradient(circle at 50% 35%, #720808, #280202 72%);
    box-shadow: inset 0 0 18px rgba(255, 0, 0, 0.6), 0 0 10px rgba(255, 0, 0, 0.28);
  }

  &.is-answer {
    color: #0bf9fe;
    border-color: rgba(11, 249, 254, 0.82);
    background: radial-gradient(circle at 50% 35%, #057f89, #021f28 72%);
    box-shadow: inset 0 0 18px rgba(11, 249, 254, 0.55), 0 0 10px rgba(11, 249, 254, 0.28);
  }

  &.is-muted {
    color: #8aa8bf;
    border-color: rgba(93, 139, 174, 0.7);
    filter: saturate(0.55);
  }
}

.video-call-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.call-video-shell {
  position: relative;
  width: 608px;
  height: 342px;
  margin-top: 20px;
  overflow: hidden;
  background: #020b16;
  box-shadow: 0 10px 28px rgba(0, 9, 24, 0.48);

  video {
    display: block;
    width: 100%;
    height: 100%;
    background: #020b16;
    object-fit: contain;
  }
}

.close-video-button {
  position: absolute;
  z-index: 2;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  padding: 0;
  color: #d7edff;
  border: 1px solid rgba(124, 202, 255, 0.74);
  border-radius: 50%;
  background: rgba(2, 19, 40, 0.82);
  box-shadow: 0 4px 14px rgba(0, 9, 24, 0.45);
  cursor: pointer;

  .svg-icon {
    width: 24px;
    height: 24px;
  }
}

.video-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #aedcff;
  background: rgba(2, 11, 22, 0.82);
  font-size: 18px;
}

.video-call-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 34px;

  strong {
    max-width: 560px;
    overflow: hidden;
    font-size: 30px;
    font-weight: 500;
    line-height: 1.3;
    letter-spacing: 0;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 2px;
    color: #d7edff;
    font-size: 30px;
    line-height: 1.2;
  }
}

.video-call-actions {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 46px;
  width: 100%;
  margin-top: 40px;
}

.labeled-action {
  display: flex;
  flex: 0 0 80px;
  flex-direction: column;
  align-items: center;
  gap: 12px;

  > span {
    color: #d7edff;
    font-size: 24px;
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
