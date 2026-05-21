<template>
  <div class="page">
    <header class="topbar">
      <h1>实时视频调试台</h1>
      <el-tag :type="wsConnected ? 'success' : 'info'">
        WebSocket {{ wsConnected ? '已连接' : '未连接' }}
      </el-tag>
    </header>

    <main class="layout">
      <section class="panel">
        <div class="panel__header">会话参数</div>
        <div class="panel__body">
          <el-form label-position="top" size="small">
            <el-form-item label="机器人 ID">
              <el-input v-model="form.robotId" />
            </el-form-item>
            <el-form-item label="设备 ID">
              <el-input v-model="form.deviceId" />
            </el-form-item>
            <el-form-item label="视频通道">
              <el-radio-group v-model="form.channel">
                <el-radio-button label="visible">可见光</el-radio-button>
                <el-radio-button label="thermal">热成像</el-radio-button>
                <el-radio-button label="fusion">融合</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="清晰度">
              <el-radio-group v-model="form.quality">
                <el-radio-button label="sub">视频墙</el-radio-button>
                <el-radio-button label="main">单路高清</el-radio-button>
                <el-radio-button label="auto">自动</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="当前会话">
              <el-input :value="session && session.sessionId" readonly placeholder="尚未创建" />
            </el-form-item>
            <el-form-item label="RTSP 地址">
              <el-input v-model="source.rtspUrl" />
            </el-form-item>
            <div class="actions">
              <el-button @click="saveSource">保存媒体源</el-button>
              <el-button @click="loadSources">媒体源列表</el-button>
              <el-button type="primary" :loading="loading" @click="startSession">开始观看</el-button>
              <el-button :disabled="!session" @click="stopSession">停止观看</el-button>
              <el-button :disabled="!session" @click="switchToVisible">切可见光</el-button>
              <el-button :disabled="!session" @click="switchToThermal">切热成像</el-button>
              <el-button :disabled="!session" @click="snapshot">抓拍</el-button>
              <el-button :disabled="!session" @click="loadSnapshots">查询抓拍</el-button>
              <el-button :disabled="!session" @click="loadSessionEvents">查询事件</el-button>
              <el-button @click="loadActiveSessions">视频墙列表</el-button>
              <el-button :disabled="!session" @click="mockStreaming">模拟推流成功</el-button>
            </div>
          </el-form>
        </div>
      </section>

      <section class="panel">
        <div class="panel__header">
          <span>实时画面</span>
          <el-tag v-if="session" size="small">{{ session.status }}</el-tag>
        </div>
        <div class="video-stage">
          <video ref="video" autoplay playsinline muted />
          <div v-if="!hasVideo" class="empty-video">
            {{ session ? '等待 LiveKit Track 订阅' : '请创建实时视频会话' }}
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel__header">
          <span>事件日志</span>
          <el-button size="mini" @click="events = []">清空</el-button>
        </div>
        <div class="panel__body">
          <div class="event-log">
            <div v-for="(item, index) in events" :key="index" class="event-item">
              {{ item }}
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script>
import { Room, RoomEvent } from 'livekit-client'
import {
  createSnapshot,
  createVideoSession,
  getActiveVideoSessions,
  getMediaSources,
  getSessionEvents,
  getSessionSnapshots,
  mockClientAcked,
  mockTrackPublished,
  restartVideoSession,
  saveMediaSource,
  stopVideoSession,
  switchChannel
} from './api/media'

export default {
  name: 'App',
  data() {
    return {
      loading: false,
      wsConnected: false,
      hasVideo: false,
      stopping: false,
      restarting: false,
      room: null,
      socket: null,
      session: null,
      form: {
        robotId: 'robot-001',
        deviceId: 'gimbal-001',
        channel: 'visible',
        quality: 'sub',
        reuse: false
      },
      source: {
        rtspUrl: 'rtsp://192.168.124.204:8554/camera01'
      },
      events: []
    }
  },
  mounted() {
    this.connectWebSocket()
  },
  beforeDestroy() {
    if (this.socket) this.socket.close()
    if (this.room) this.room.disconnect()
  },
  methods: {
    async startSession() {
      this.loading = true
      this.log('CLICK startSession', this.form)
      try {
        const session = await createVideoSession(this.form)
        this.session = session
        this.log('API createVideoSession', session)
        await this.connectLiveKit(session)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        this.log('ERROR createVideoSession', this.errorMessage(error))
      } finally {
        this.loading = false
      }
    },
    async saveSource() {
      const source = await saveMediaSource({
        robotId: this.form.robotId,
        deviceId: this.form.deviceId,
        channel: this.form.channel,
        quality: this.form.quality === 'auto' ? 'sub' : this.form.quality,
        rtspUrl: this.source.rtspUrl,
        enabled: true,
        name: `${this.form.robotId}-${this.form.deviceId}-${this.form.channel}-${this.form.quality}`
      })
      this.log('API saveMediaSource', source)
    },
    async loadSources() {
      const sources = await getMediaSources()
      this.log('API mediaSources', sources)
    },
    async stopSession() {
      if (!this.session) return
      try {
        this.stopping = true
        this.restarting = false
        const stopped = await stopVideoSession(this.session.sessionId)
        this.log('API stopVideoSession', stopped)
        if (this.room) {
          await this.room.disconnect()
          this.room = null
          this.hasVideo = false
        }
        this.session = stopped
        this.stopping = false
      } catch (error) {
        this.stopping = false
        this.$message.error(this.errorMessage(error))
      }
    },
    async switchToVisible() {
      await this.switchChannel('visible')
    },
    async switchToThermal() {
      await this.switchChannel('thermal')
    },
    async switchChannel(channel) {
      if (!this.session) return
      const updated = await switchChannel(this.session.sessionId, {
        channel,
        quality: this.form.quality
      })
      this.session = updated
      this.form.channel = channel
      this.log('API switchChannel', updated)
    },
    async snapshot() {
      if (!this.session) return
      const preview = this.capturePreview()
      const response = await createSnapshot(this.session.sessionId, {
        trackSid: this.session.trackSid || 'TR_pending',
        reason: 'manual_abnormal',
        remark: '调试台手动抓拍',
        clientCapturedAt: new Date().toISOString(),
        previewImageHash: preview ? String(preview.length) : null
      })
      this.log('API snapshot', response)
      this.$message.success('已提交抓拍任务')
    },
    async mockStreaming() {
      if (!this.session) return
      await mockClientAcked(this.session.sessionId)
      const updated = await mockTrackPublished(this.session.sessionId, 'TR_debug_' + Date.now())
      this.session = updated
      this.log('API mockTrackPublished', updated)
    },
    async loadSessionEvents() {
      if (!this.session) return
      const eventLogs = await getSessionEvents(this.session.sessionId)
      this.log('API sessionEvents', eventLogs)
    },
    async loadSnapshots() {
      if (!this.session) return
      const snapshots = await getSessionSnapshots(this.session.sessionId)
      this.log('API snapshots', snapshots)
    },
    async loadActiveSessions() {
      const sessions = await getActiveVideoSessions()
      this.log('API activeSessions', sessions)
    },
    async connectLiveKit(session) {
      if (!session.viewerToken || !session.livekitUrl) {
        this.log('LiveKit skipped', '缺少 viewerToken 或 livekitUrl')
        return
      }
      if (this.room) {
        await this.room.disconnect()
      }
      const room = new Room()
      room.on(RoomEvent.TrackSubscribed, (track) => {
        if (track.kind !== 'video') return
        track.attach(this.$refs.video)
        this.hasVideo = true
        this.log('LiveKit TrackSubscribed', track.sid || track.name)
      })
      room.on(RoomEvent.TrackUnsubscribed, (track) => {
        track.detach()
        this.hasVideo = false
        this.log('LiveKit TrackUnsubscribed', track.sid || track.name)
        this.restartCurrentSession()
      })
      room.on(RoomEvent.Disconnected, () => {
        this.hasVideo = false
        this.log('LiveKit Disconnected', session.roomName)
        if (!this.stopping) {
          this.restartCurrentSession()
        }
      })
      await room.connect(session.livekitUrl, session.viewerToken)
      this.room = room
      this.log('LiveKit connected', session.roomName)
    },
    connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/media`
      const socket = new WebSocket(url)
      socket.onopen = () => {
        this.wsConnected = true
        this.log('WebSocket connected', url)
      }
      socket.onclose = () => {
        this.wsConnected = false
        this.log('WebSocket closed', url)
      }
      socket.onmessage = (message) => {
        const event = JSON.parse(message.data)
        this.syncSessionEvent(event)
        this.log('WS event', event)
      }
      this.socket = socket
    },
    syncSessionEvent(event) {
      if (!event || !event.data || !event.data.sessionId || !this.session) return
      if (event.data.sessionId !== this.session.sessionId) return
      if (event.data.robotId && event.data.status) {
        this.session = Object.assign({}, this.session, event.data)
        if (event.data.status === 'STREAMING' && this.room && this.room.state === 'disconnected') {
          this.connectLiveKit(this.session)
        }
      }
    },
    async restartCurrentSession() {
      if (this.stopping) return
      if (!this.session || this.session.status === 'CLOSED') return
      if (!['STREAMING', 'INTERRUPTED'].includes(this.session.status)) return
      if (this.restarting) return
      try {
        this.restarting = true
        const updated = await restartVideoSession(this.session.sessionId)
        this.session = Object.assign({}, this.session, updated)
        this.log('API restartVideoSession', updated)
      } catch (error) {
        this.log('ERROR restartVideoSession', this.errorMessage(error))
      } finally {
        setTimeout(() => {
          this.restarting = false
        }, 5000)
      }
    },
    capturePreview() {
      const video = this.$refs.video
      if (!video || !video.videoWidth) return null
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      return canvas.toDataURL('image/jpeg', 0.9)
    },
    log(title, payload) {
      const line = `[${new Date().toLocaleTimeString()}] ${title}\n${typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)}`
      this.events.unshift(line)
    },
    errorMessage(error) {
      return error && error.response && error.response.data
        ? error.response.data.message || error.response.data.code
        : error.message || '请求失败'
    }
  }
}
</script>
