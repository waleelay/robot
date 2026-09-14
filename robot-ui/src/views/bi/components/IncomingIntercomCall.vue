<template>
  <div class="intercom-call-host">
    <section
      v-if="displayCall"
      ref="callWindow"
      class="intercom-call-window"
      :class="[`is-${callMode}`, { 'is-resizing': !!resizeState }]"
      :style="windowStyle"
      role="dialog"
      aria-modal="false"
      aria-label="语音电话"
    >
      <header class="call-window-header" @pointerdown="startDrag">
        <div class="header-title">
          <h2>{{ isFieldCall ? '现场视频' : '语音电话' }}</h2>
          <span v-if="waitingCallCount" class="waiting-call-badge">还有 {{ waitingCallCount }} 通来电</span>
        </div>
        <div class="header-actions">
          <button
            v-if="callMode === 'audio'"
            class="enable-video-button"
            type="button"
            :disabled="!isFieldCall && activeIncomingCall.videoLoading"
            @click.stop="enableVideo"
          >
            {{ !isFieldCall && activeIncomingCall.videoLoading ? '开启中...' : '开启画面' }}
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
            v-if="!isFieldCall"
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
            <span>{{ videoLoadingHint }}</span>
          </div>
        </div>
        <div class="video-call-info w100">
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
          <div v-if="!isFieldCall" class="labeled-action">
            <button class="round-action is-local" type="button" title="跳转控制中心" @click="openRemoteControl">
              <svg-icon icon-class="control" />
            </button>
            <span>控制中心</span>
          </div>
        </div>
      </div>

      <!-- 资源管理器式边缘/角落拉伸热区 -->
      <div
        v-for="edge in resizeEdges"
        :key="edge"
        class="resize-handle"
        :class="`resize-handle--${edge}`"
        @pointerdown.stop.prevent="startResize($event, edge)"
      />
    </section>
  </div>
</template>

<script>
import { mapActions, mapGetters, mapState } from 'vuex'

const SCREEN_WIDTH = 1920
const SCREEN_HEIGHT = 1080
const WINDOW_MARGIN = 16
/** 与巡逻巡查 Robot1 弹窗右侧对齐（距右边缘 82px） */
const WINDOW_MARGIN_RIGHT = 82
const COMPACT_SIZE = { width: 358, height: 152 }
const AUDIO_SIZE = { width: 410, height: 152 }// width: 394, height: 152
const VIDEO_SIZE = { width: 324, height: 382 }
const RESIZE_EDGES = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw']
const RESIZE_CURSOR = {
  n: 'ns-resize',
  s: 'ns-resize',
  e: 'ew-resize',
  w: 'ew-resize',
  ne: 'nesw-resize',
  sw: 'nesw-resize',
  nw: 'nwse-resize',
  se: 'nwse-resize'
}

export default {
  name: 'IncomingIntercomCall',
  data() {
    return {
      now: Date.now(),
      timer: null,
      position: { x: 0, y: 0 },
      /** 用户拉伸后的尺寸；切模式时清空，回到各模式默认大小 */
      customSize: null,
      dragState: null,
      resizeState: null,
      resizeEdges: RESIZE_EDGES,
      hangupPending: false,
      _ringtone: null
    }
  },
  computed: {
    ...mapState('websocketRobot', {
      robotIncomingCalls: 'incomingCalls',
      robotActiveIncomingCall: 'activeIncomingCall',
      robotCallOperationPending: 'callOperationPending',
      cameras: 'cameras'
    }),
    ...mapState('fieldCall', {
      fieldIncomingCalls: 'incomingCalls',
      fieldActiveIncomingCall: 'activeIncomingCall',
      fieldCallOperationPending: 'callOperationPending'
    }),
    ...mapGetters('fieldCall', ['fieldRemoteVideoTrack']),
    incomingCalls() {
      return [...this.fieldIncomingCalls, ...this.robotIncomingCalls]
    },
    activeIncomingCall() {
      return this.fieldActiveIncomingCall || this.robotActiveIncomingCall
    },
    isFieldCall() {
      const call = this.activeIncomingCall || this.currentCall
      return Boolean(call && call.source === 'mobile-app')
    },
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
    defaultWindowSize() {
      if (this.callMode === 'video') return { ...VIDEO_SIZE }
      if (this.callMode === 'audio') return { ...AUDIO_SIZE }
      return { ...COMPACT_SIZE }
    },
    minWindowSize() {
      const base = this.defaultWindowSize
      return {
        width: Math.max(280, Math.floor(base.width * 0.85)),
        height: Math.max(140, Math.floor(base.height * 0.85))
      }
    },
    windowSize() {
      if (this.customSize && this.customSize.width && this.customSize.height) {
        return {
          width: this.customSize.width,
          height: this.customSize.height
        }
      }
      return this.defaultWindowSize
    },
    windowStyle() {
      const style = {
        width: `${this.windowSize.width}px`,
        height: `${this.windowSize.height}px`,
        transform: `translate3d(${this.position.x}px, ${this.position.y}px, 0)`
      }
      if (this.resizeState) {
        style.cursor = RESIZE_CURSOR[this.resizeState.edge] || 'default'
      }
      return style
    },
    operationPending() {
      return this.robotCallOperationPending || this.fieldCallOperationPending || this.hangupPending
    },
    robotName() {
      return this.displayCall.robotName || this.displayCall.displayName || this.displayCall.robotId || '机器人'
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
      if (this.fieldActiveIncomingCall || this.robotActiveIncomingCall) return true
      return Object.values(this.cameras).some(camera => camera && camera.intercomActive)
    },
    activeCamera() {
      if (!this.robotActiveIncomingCall) return null
      return this.cameras[this.robotActiveIncomingCall.cameraKey] || null
    },
    remoteVideoTrack() {
      if (this.fieldActiveIncomingCall) return this.fieldRemoteVideoTrack
      return this.activeCamera && this.activeCamera.remoteVideoTrack
    },
    videoReady() {
      return Boolean(this.remoteVideoTrack)
    },
    videoLoadingHint() {
      if (this.isFieldCall) {
        return this.activeIncomingCall && this.activeIncomingCall.videoLoading
          ? '正在连接现场画面...'
          : '等待手机画面...'
      }
      return this.activeIncomingCall && this.activeIncomingCall.videoLoading
        ? '正在开启主摄像头...'
        : '等待视频画面...'
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
    },
    /** 振铃中（有来电窗且尚未接听） */
    isRinging() {
      return Boolean(this.displayCall) && this.callMode === 'ringing'
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
      this.syncRingtone()
    },
    isRinging: {
      immediate: true,
      handler() {
        this.syncRingtone()
      }
    },
    displayCall(next, prev) {
      if (prev && !next) {
        this.playHangupTone()
      }
    },
    remoteVideoTrack(next, previous) {
      const video = this.$refs.callVideo
      if (previous && video && typeof previous.detach === 'function') previous.detach(video)
      this.attachVideoTrack()
    }
  },
  mounted() {
    this.timer = window.setInterval(() => { this.now = Date.now() }, 500)
    window.addEventListener('pointermove', this.onPointerMove)
    window.addEventListener('pointerup', this.onPointerUp)
    window.addEventListener('pointercancel', this.onPointerUp)
    this.syncRingtone()
  },
  beforeDestroy() {
    window.clearInterval(this.timer)
    window.removeEventListener('pointermove', this.onPointerMove)
    window.removeEventListener('pointerup', this.onPointerUp)
    window.removeEventListener('pointercancel', this.onPointerUp)
    this.stopRingtone()
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
      'setSelectedRobotId',
      'setControlCenterReturnTo'
    ]),
    ...mapActions('fieldCall', [
      'acceptFieldCall',
      'rejectFieldCall',
      'hangupFieldCall',
      'toggleFieldMic',
      'toggleFieldSpeaker',
      'enableFieldVideo',
      'disableFieldVideo'
    ]),
    syncRingtone() {
      if (this.isRinging) {
        this.startRingtone()
      } else {
        this.stopRingtone()
      }
    },
    startRingtone() {
      if (this._ringtone && !this._ringtone.paused) return
      try {
        if (!this._ringtone) {
          // eslint-disable-next-line global-require
          const src = require('@/assets/sounds/ring_incoming.wav')
          this._ringtone = new Audio(src)
          this._ringtone.loop = true
          this._ringtone.volume = 0.85
        }
        const play = this._ringtone.play()
        if (play && typeof play.catch === 'function') {
          play.catch(err => {
            console.warn('[incoming-call] ringtone play blocked', err)
          })
        }
      } catch (err) {
        console.warn('[incoming-call] ringtone start failed', err)
      }
    },
    stopRingtone() {
      if (!this._ringtone) return
      try {
        this._ringtone.pause()
        this._ringtone.currentTime = 0
      } catch (err) {
        // ignore
      }
    },
    playHangupTone() {
      this.stopRingtone()
      try {
        // eslint-disable-next-line global-require
        const src = require('@/assets/sounds/call_hangup.wav')
        const audio = new Audio(src)
        audio.volume = 0.9
        const play = audio.play()
        if (play && typeof play.catch === 'function') {
          play.catch(err => {
            console.warn('[incoming-call] hangup tone blocked', err)
          })
        }
      } catch (err) {
        console.warn('[incoming-call] hangup tone failed', err)
      }
    },
    pad(value) {
      return String(value).padStart(2, '0')
    },
    positionWindow() {
      this.customSize = null
      this.position = {
        x: SCREEN_WIDTH - this.windowSize.width - WINDOW_MARGIN_RIGHT,
        y: SCREEN_HEIGHT - this.windowSize.height - WINDOW_MARGIN
      }
    },
    getHostScale() {
      const host = this.$el
      const rect = host && host.getBoundingClientRect ? host.getBoundingClientRect() : null
      return {
        scaleX: rect && rect.width ? rect.width / SCREEN_WIDTH : 1,
        scaleY: rect && rect.height ? rect.height / SCREEN_HEIGHT : 1
      }
    },
    startDrag(event) {
      if (event.button !== undefined && event.button !== 0) return
      if (event.target.closest('button')) return
      if (this.resizeState) return
      const { scaleX, scaleY } = this.getHostScale()
      this.dragState = {
        startX: event.clientX,
        startY: event.clientY,
        originX: this.position.x,
        originY: this.position.y,
        scaleX,
        scaleY
      }
      event.preventDefault()
    },
    startResize(event, edge) {
      if (event.button !== undefined && event.button !== 0) return
      const { scaleX, scaleY } = this.getHostScale()
      this.dragState = null
      this.resizeState = {
        edge,
        startX: event.clientX,
        startY: event.clientY,
        originX: this.position.x,
        originY: this.position.y,
        originW: this.windowSize.width,
        originH: this.windowSize.height,
        scaleX,
        scaleY
      }
      if (event.currentTarget && event.currentTarget.setPointerCapture) {
        try { event.currentTarget.setPointerCapture(event.pointerId) } catch (e) { /* ignore */ }
      }
    },
    onPointerMove(event) {
      if (this.resizeState) {
        this.applyResize(event)
        return
      }
      if (!this.dragState) return
      const x = this.dragState.originX + (event.clientX - this.dragState.startX) / this.dragState.scaleX
      const y = this.dragState.originY + (event.clientY - this.dragState.startY) / this.dragState.scaleY
      this.position = {
        x: Math.round(Math.max(0, Math.min(SCREEN_WIDTH - this.windowSize.width, x))),
        y: Math.round(Math.max(0, Math.min(SCREEN_HEIGHT - this.windowSize.height, y)))
      }
    },
    applyResize(event) {
      const state = this.resizeState
      if (!state) return
      const dx = (event.clientX - state.startX) / state.scaleX
      const dy = (event.clientY - state.startY) / state.scaleY
      const minW = this.minWindowSize.width
      const minH = this.minWindowSize.height
      const maxW = SCREEN_WIDTH - WINDOW_MARGIN
      const maxH = SCREEN_HEIGHT - WINDOW_MARGIN
      let width = state.originW
      let height = state.originH
      let x = state.originX
      let y = state.originY
      const edge = state.edge

      if (edge.includes('e')) width = state.originW + dx
      if (edge.includes('s')) height = state.originH + dy
      if (edge.includes('w')) {
        width = state.originW - dx
        x = state.originX + dx
      }
      if (edge.includes('n')) {
        height = state.originH - dy
        y = state.originY + dy
      }

      width = Math.max(minW, Math.min(maxW, width))
      height = Math.max(minH, Math.min(maxH, height))

      // 从左/上边拉伸时，按最终尺寸回推原点，避免贴边夹紧后位置漂移
      if (edge.includes('w')) x = state.originX + state.originW - width
      if (edge.includes('n')) y = state.originY + state.originH - height

      x = Math.max(0, Math.min(SCREEN_WIDTH - width, x))
      y = Math.max(0, Math.min(SCREEN_HEIGHT - height, y))

      this.customSize = {
        width: Math.round(width),
        height: Math.round(height)
      }
      this.position = {
        x: Math.round(x),
        y: Math.round(y)
      }
    },
    onPointerUp() {
      this.dragState = null
      this.resizeState = null
    },
    accept() {
      if (!this.currentCall) return
      this.stopRingtone()
      if (this.currentCall.source === 'mobile-app') {
        this.acceptFieldCall(this.currentCall.callId)
        return
      }
      this.acceptIncomingCall(this.currentCall.callId)
    },
    reject() {
      if (!this.currentCall) return
      this.stopRingtone()
      if (this.currentCall.source === 'mobile-app') {
        this.rejectFieldCall(this.currentCall.callId)
        return
      }
      this.rejectIncomingCall(this.currentCall.callId)
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
      this.playHangupTone()
      const video = this.$refs.callVideo
      if (this.remoteVideoTrack && video && typeof this.remoteVideoTrack.detach === 'function') {
        this.remoteVideoTrack.detach(video)
      }
      try {
        if (this.fieldActiveIncomingCall) {
          await this.hangupFieldCall()
        } else {
          await this.hangupIncomingCall()
        }
      } finally {
        this.hangupPending = false
      }
    },
    toggleMicrophone() {
      if (this.fieldActiveIncomingCall) {
        this.toggleFieldMic()
        return
      }
      this.toggleIncomingCallMicrophone()
    },
    toggleSpeaker() {
      if (this.fieldActiveIncomingCall) {
        this.toggleFieldSpeaker()
        return
      }
      this.toggleIncomingCallSpeaker()
    },
    async enableVideo() {
      if (this.fieldActiveIncomingCall) {
        await this.enableFieldVideo()
        this.$nextTick(() => this.attachVideoTrack())
        return
      }
      await this.enableIncomingCallVideo()
      this.attachVideoTrack()
    },
    async disableVideo() {
      const video = this.$refs.callVideo
      if (this.remoteVideoTrack && video && typeof this.remoteVideoTrack.detach === 'function') {
        this.remoteVideoTrack.detach(video)
      }
      if (this.fieldActiveIncomingCall) {
        await this.disableFieldVideo()
        return
      }
      await this.disableIncomingCallVideo()
    },
    attachVideoTrack() {
      if (this.callMode !== 'video' || !this.remoteVideoTrack) return
      this.$nextTick(() => {
        const video = this.$refs.callVideo
        if (!video) return
        const track = this.remoteVideoTrack
        try {
          // 重新挂载，避免已 attach 到其它节点导致黑屏
          if (typeof track.detach === 'function') track.detach()
          track.attach(video)
          video.muted = true
          video.playsInline = true
          video.autoplay = true
          const play = video.play()
          if (play && typeof play.catch === 'function') play.catch(() => {})
        } catch (err) {
          console.error('[IncomingIntercomCall] attachVideoTrack', err)
        }
      })
    },
    async openRemoteControl() {
      if (!this.activeIncomingCall || this.isFieldCall) return
      if (this.activeIncomingCall.videoEnabled) {
        await this.disableVideo()
      }
      if (this.$route.name !== 'biPatrolMonitor') {
        this.setControlCenterReturnTo(this.$route.fullPath)
        await this.setSelectedRobotId(this.activeIncomingCall.robotId)
        await this.$router.push({ name: 'biPatrolMonitor' })
        return
      }
      this.setControlCenterReturnTo(null)
      await this.setSelectedRobotId(this.activeIncomingCall.robotId)
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
  display: flex;
  flex-direction: column;
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

  &.is-resizing {
    // 拉伸时禁止选中/误触内部按钮
    * {
      pointer-events: none;
    }
    .resize-handle {
      pointer-events: auto;
    }
  }
}

/* 四边 + 四角拉伸热区（类似资源管理器） */
.resize-handle {
  position: absolute;
  z-index: 20;
  background: transparent;
  touch-action: none;

  &--n,
  &--s {
    left: 8px;
    right: 8px;
    height: 6px;
    cursor: ns-resize;
  }
  &--n { top: 0; }
  &--s { bottom: 0; }

  &--e,
  &--w {
    top: 8px;
    bottom: 8px;
    width: 6px;
    cursor: ew-resize;
  }
  &--e { right: 0; }
  &--w { left: 0; }

  &--ne,
  &--nw,
  &--se,
  &--sw {
    width: 12px;
    height: 12px;
  }
  &--ne {
    top: 0;
    right: 0;
    cursor: nesw-resize;
  }
  &--nw {
    top: 0;
    left: 0;
    cursor: nwse-resize;
  }
  &--se {
    bottom: 0;
    right: 0;
    cursor: nwse-resize;
  }
  &--sw {
    bottom: 0;
    left: 0;
    cursor: nesw-resize;
  }
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
  flex: 1;
  min-height: 102px;
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
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-height: 0;
  width: 100%;
  padding-bottom: 12px;
  box-sizing: border-box;
}

.call-video-shell {
  position: relative;
  flex: 1 1 auto;
  width: calc(100% - 20px);
  min-height: 120px;
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
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  margin-top: 12px;
  font-family: "Microsoft YaHei", sans-serif;
  .name {
    max-width: calc(100% - 24px);
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
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: center;
  gap: 23px;
  width: 100%;
  margin-top: 12px;
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
