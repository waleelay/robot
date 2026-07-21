<template>
  <div>
    <el-dialog
      class="incoming-call-dialog"
      :visible="Boolean(currentCall)"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      width="360px"
      append-to-body
    >
      <div v-if="currentCall" class="incoming-call">
        <div class="call-icon"><svg-icon icon-class="mic-fill" /></div>
        <h3>{{ currentCall.robotName || currentCall.robotId }}</h3>
        <p>{{ currentCall.cameraName || currentCall.deviceId }}</p>
        <p class="reason">{{ currentCall.reason || '机器人请求人工对讲' }}</p>
        <div class="countdown">{{ countdown }} 秒后结束响铃</div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button :disabled="callOperationPending" @click="reject">拒接</el-button>
        <el-button type="primary" :loading="callOperationPending" @click="accept">接听</el-button>
      </span>
    </el-dialog>

    <div v-if="activeIncomingCall" class="active-call">
      <span class="active-dot" />
      <div class="active-call-info">
        <strong>{{ activeIncomingCall.robotName || activeIncomingCall.robotId }}</strong>
        <span>正在通话</span>
      </div>
      <el-button type="danger" size="mini" :loading="hangupPending" @click="hangup">挂断</el-button>
    </div>
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex'

export default {
  name: 'IncomingIntercomCall',
  data() {
    return {
      now: Date.now(),
      timer: null,
      hangupPending: false
    }
  },
  computed: {
    ...mapState('websocketRobot', ['incomingCalls', 'activeIncomingCall', 'callOperationPending']),
    currentCall() {
      return this.incomingCalls[0] || null
    },
    countdown() {
      if (!this.currentCall) return 0
      const remainingSeconds = Number(this.currentCall.remainingSeconds)
      const receivedAt = Number(this.currentCall.receivedAtEpochMillis)
      if (Number.isFinite(remainingSeconds) && Number.isFinite(receivedAt)) {
        const elapsedSeconds = (this.now - receivedAt) / 1000
        return Math.max(0, Math.ceil(remainingSeconds - elapsedSeconds))
      }
      const epochMillis = Number(this.currentCall.expiresAtEpochMillis)
      const expiresAt = Number.isFinite(epochMillis) && epochMillis > 0
        ? epochMillis
        : this.parseExpiresAt(this.currentCall.expiresAt)
      if (!Number.isFinite(expiresAt)) return 0
      return Math.max(0, Math.ceil((expiresAt - this.now) / 1000))
    }
  },
  mounted() {
    this.timer = window.setInterval(() => { this.now = Date.now() }, 500)
  },
  beforeDestroy() {
    window.clearInterval(this.timer)
  },
  methods: {
    ...mapActions('websocketRobot', ['acceptIncomingCall', 'rejectIncomingCall', 'hangupIncomingCall']),
    accept() {
      if (this.currentCall) this.acceptIncomingCall(this.currentCall.callId)
    },
    reject() {
      if (this.currentCall) this.rejectIncomingCall(this.currentCall.callId)
    },
    parseExpiresAt(value) {
      if (!value) return NaN
      const text = String(value)
      const normalized = text.includes('T') ? text : text.replace(' ', 'T')
      const withZone = /(Z|[+-]\d{2}:\d{2})$/.test(normalized) ? normalized : `${normalized}+08:00`
      return new Date(withZone).getTime()
    },
    async hangup() {
      this.hangupPending = true
      try {
        await this.hangupIncomingCall()
      } finally {
        this.hangupPending = false
      }
    }
  }
}
</script>

<style lang="scss">
.incoming-call-dialog {
  .el-dialog {
    border: 1px solid #1e4d91;
    border-radius: 6px;
    background: #071b33;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
  }
  .el-dialog__header { padding: 0; }
  .el-dialog__body { padding: 28px 28px 18px; }
  .el-dialog__footer { padding: 0 28px 28px; }
  .dialog-footer { display: flex; gap: 12px; }
  .dialog-footer .el-button { flex: 1; margin: 0; border-radius: 4px; }
}
.incoming-call {
  color: #fff;
  text-align: center;
  h3 { margin: 14px 0 6px; font-size: 20px; letter-spacing: 0; }
  p { margin: 4px 0; color: rgba(255, 255, 255, 0.72); }
  .reason { min-height: 20px; margin-top: 12px; color: #fff; }
  .countdown { margin-top: 16px; color: #72c9ff; font-size: 12px; }
}
.call-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  color: #fff;
  background: #1677c8;
  box-shadow: 0 0 18px rgba(21, 154, 255, 0.55);
  .svg-icon { font-size: 30px; }
}
.active-call {
  position: fixed;
  z-index: 3000;
  top: 88px;
  right: 28px;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 260px;
  padding: 12px 14px;
  color: #fff;
  border: 1px solid #1e4d91;
  border-radius: 6px;
  background: #071b33;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}
.active-dot { width: 8px; height: 8px; border-radius: 50%; background: #20c77a; box-shadow: 0 0 8px #20c77a; }
.active-call-info { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; }
.active-call-info strong { overflow: hidden; font-size: 14px; letter-spacing: 0; text-overflow: ellipsis; white-space: nowrap; }
.active-call-info span { color: rgba(255, 255, 255, 0.65); font-size: 12px; }
</style>
