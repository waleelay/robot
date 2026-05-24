<template>
  <div class="page">
    <header class="topbar">
      <h1>实时视频调试台</h1>
      <el-tag :type="wsConnected ? 'success' : 'info'">
        WebSocket {{ wsConnected ? '已连接' : '未连接' }}
      </el-tag>
    </header>

    <main class="workspace">
      <aside class="robot-list">
        <div class="list-title">机器人设备</div>
        <button
          v-for="robot in robots"
          :key="robot.robotId"
          class="robot-item"
          :class="{ active: selectedRobotId === robot.robotId }"
          @click="selectedRobotId = robot.robotId"
        >
          <span>{{ robot.name }}</span>
          <small>{{ robot.robotId }} · {{ robot.status || 'offline' }}</small>
        </button>
      </aside>

      <section class="video-area">
        <div class="area-header">
          <div>
            <h2>{{ selectedRobot.name }}</h2>
            <span>{{ selectedRobot.type }} · {{ selectedRobot.status || 'offline' }} · {{ selectedRobot.cameras.length }} 路摄像头</span>
          </div>
          <div class="area-actions">
            <el-radio-group v-model="gridMode" size="small">
              <el-radio-button :label="1">1宫格</el-radio-button>
              <el-radio-button :label="4">4宫格</el-radio-button>
              <el-radio-button :label="9">9宫格</el-radio-button>
            </el-radio-group>
            <el-button size="small" :disabled="selectedRobot.status !== 'online'" @click="startRobot(selectedRobot)">全部观看</el-button>
            <el-button size="small" @click="stopRobot(selectedRobot)">全部停止</el-button>
          </div>
        </div>

        <div class="camera-grid" :class="'grid-' + gridMode">
          <div v-for="camera in displayedCameras" :key="camera.key" class="camera-card">
            <div class="video-stage">
              <video :ref="camera.key" autoplay playsinline muted />
              <div class="video-topbar">
                <div>
                  <strong>{{ camera.name }}</strong>
                  <span>{{ camera.deviceId }} · {{ camera.channel }}</span>
                </div>
                <el-tag size="mini" :type="statusType(camera.status)">
                  {{ camera.status || '未观看' }}
                </el-tag>
              </div>
              <div v-if="!camera.hasVideo" class="empty-video">
                {{ camera.session ? '等待 LiveKit Track' : '点击播放开始观看' }}
              </div>
              <div class="video-tools">
                <el-button
                  v-if="!camera.session || camera.stopped"
                  size="mini"
                  icon="el-icon-video-play"
                  :loading="camera.loading"
                  :disabled="selectedRobot.status !== 'online'"
                  @click="startCamera(selectedRobot, camera)"
                >观看</el-button>
                <el-button
                  v-else
                  size="mini"
                  icon="el-icon-video-pause"
                  :loading="camera.loading"
                  @click="stopCamera(camera)"
                >停止</el-button>
                <el-button
                  size="mini"
                  icon="el-icon-camera"
                  :disabled="!camera.session || !camera.hasVideo"
                  @click="snapshotCamera(camera)"
                >抓拍</el-button>
              </div>
              <div class="video-bottombar">
                <span>{{ camera.session ? camera.session.sessionId : '无会话' }}</span>
                <span>{{ camera.viewerCount || 0 }} 人观看</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <aside class="event-panel">
        <div class="event-head">
          <span>事件日志</span>
          <el-button size="mini" @click="events = []">清空</el-button>
        </div>
        <div class="event-log">
          <div v-for="(item, index) in events" :key="index" class="event-item">
            {{ item }}
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<script>
import { Room, RoomEvent } from 'livekit-client'
import {
  createSnapshot,
  createVideoSession,
  getRobots,
  getViewerToken,
  heartbeatVideoSession,
  restartVideoSession,
  stopVideoSession
} from './api/media'

function cameraState(robotId, deviceId, name, channel) {
  return {
    key: `${robotId}-${deviceId}-${channel}`,
    robotId,
    deviceId,
    name,
    channel,
    quality: 'sub',
    loading: false,
    hasVideo: false,
    stopping: false,
    stopped: false,
    restarting: false,
    connecting: false,
    disconnecting: false,
    room: null,
    session: null,
    status: 'offline',
    viewerCount: 0
  }
}

export default {
  name: 'App',
  data() {
    return {
      wsConnected: false,
      socket: null,
      gridMode: 4,
      heartbeatTimer: null,
      stoppedSessionIds: new Set(),
      selectedRobotId: 'robot-001',
      robots: [
        {
          robotId: 'robot-001',
          name: '松灵四轮机器人',
          type: '轮式机器人',
          status: 'offline',
          cameras: [
            cameraState('robot-001', 'gimbal-001', '前向双光云台', 'visible'),
            cameraState('robot-001', 'gimbal-002', '后向广角相机', 'visible'),
            cameraState('robot-001', 'gimbal-003', '机械臂腕部相机', 'visible')
          ]
        },
        {
          robotId: 'robot-002',
          name: '云深处四足机器狗',
          type: '四足机器人',
          status: 'offline',
          cameras: [
            cameraState('robot-002', 'gimbal-001', '头部双光云台', 'visible'),
            cameraState('robot-002', 'gimbal-002', '腹部导航相机', 'visible'),
            cameraState('robot-002', 'gimbal-003', '尾部避障相机', 'visible')
          ]
        }
      ],
      events: []
    }
  },
  computed: {
    selectedRobot() {
      return this.robots.find(item => item.robotId === this.selectedRobotId) || this.robots[0]
    },
    displayedCameras() {
      return this.selectedRobot.cameras.slice(0, this.gridMode)
    }
  },
  mounted() {
    this.loadRobots()
    this.connectWebSocket()
    this.heartbeatTimer = setInterval(this.heartbeatViewers, 5000)
  },
  beforeDestroy() {
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.socket) this.socket.close()
    this.allCameras().forEach(camera => {
      if (camera.room) camera.room.disconnect()
    })
  },
  methods: {
    async startRobot(robot) {
      if (robot.status !== 'online') return
      for (const camera of robot.cameras) {
        await this.startCamera(robot, camera)
      }
    },
    async stopRobot(robot) {
      for (const camera of robot.cameras) {
        await this.stopCamera(camera)
      }
    },
    heartbeatViewers() {
      this.allCameras().forEach(camera => {
        if (camera.session && !camera.stopped && !camera.stopping) {
          heartbeatVideoSession(camera.session.sessionId)
            .then(session => {
              if (camera.session && camera.session.sessionId === session.sessionId) {
                camera.viewerCount = session.viewerCount
              }
            })
            .catch(() => {})
        }
      })
    },
    async startCamera(robot, camera) {
      if (robot.status !== 'online') return
      camera.loading = true
      camera.stopped = false
      camera.stopping = false
      camera.restarting = false
      this.log('CLICK startCamera', {
        robotId: robot.robotId,
        deviceId: camera.deviceId,
        channel: camera.channel,
        quality: camera.quality
      })
      try {
        const session = await createVideoSession({
          robotId: robot.robotId,
          deviceId: camera.deviceId,
          channel: camera.channel,
          quality: camera.quality,
          reuse: true
        })
        camera.session = this.mergeSession(camera, session)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        this.stoppedSessionIds.delete(camera.session.sessionId)
        this.log('API createVideoSession', camera.session)
        await this.connectLiveKit(camera)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        this.log('ERROR createVideoSession', this.errorMessage(error))
      } finally {
        camera.loading = false
      }
    },
    async stopCamera(camera) {
      if (!camera.session) return
      camera.loading = true
      camera.stopping = true
      camera.stopped = true
      camera.restarting = false
      camera.disconnecting = true
      try {
        const sessionId = camera.session.sessionId
        this.stoppedSessionIds.add(sessionId)
        const stopped = await stopVideoSession(sessionId)
        this.log('API stopVideoSession', stopped)
        if (camera.room) {
          const oldRoom = camera.room
          camera.room = null
          await oldRoom.disconnect()
        }
        camera.hasVideo = false
        if (camera.session && camera.session.sessionId === sessionId) {
          camera.session = null
          camera.status = ''
          camera.viewerCount = stopped.viewerCount || 0
        }
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        camera.disconnecting = false
        camera.stopping = false
        camera.loading = false
      }
    },
    async snapshotCamera(camera) {
      if (!camera.session) return
      const response = await createSnapshot(camera.session.sessionId, {
        trackSid: camera.session.trackSid || 'TR_pending',
        reason: 'manual_abnormal',
        remark: `${camera.name} 手动抓拍`,
        clientCapturedAt: new Date().toISOString(),
        previewImageHash: this.previewHash(camera)
      })
      this.log('API snapshot', response)
      this.$message.success('已提交抓拍任务')
    },
    async connectLiveKit(camera, refreshToken) {
      if (camera.connecting || !camera.session) return
      camera.connecting = true
      try {
        if (refreshToken || !camera.session.viewerToken || !camera.session.livekitUrl) {
          const token = await getViewerToken(camera.session.sessionId)
          camera.session = this.mergeSession(camera, {
            livekitUrl: token.livekitUrl,
            roomName: token.roomName,
            viewerToken: token.token
          })
        }
        if (!camera.session.viewerToken || !camera.session.livekitUrl) return
        if (camera.room) {
          camera.disconnecting = true
          const oldRoom = camera.room
          camera.room = null
          await oldRoom.disconnect()
          camera.disconnecting = false
        }
        const room = new Room()
        const sessionId = camera.session.sessionId
        room.on(RoomEvent.TrackSubscribed, (track) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          if (track.kind !== 'video') return
          track.attach(this.videoElement(camera))
          camera.hasVideo = true
          this.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
        })
        room.on(RoomEvent.TrackUnsubscribed, (track) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          track.detach()
          camera.hasVideo = false
          this.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
          if (!this.isStoppedSession(camera, sessionId)) this.restartCamera(camera)
        })
        room.on(RoomEvent.Disconnected, () => {
          if (camera.room !== room || camera.disconnecting) return
          camera.hasVideo = false
          this.log('LiveKit Disconnected', camera.name)
          if (!this.isStoppedSession(camera, sessionId)) this.restartCamera(camera)
        })
        camera.room = room
        await room.connect(camera.session.livekitUrl, camera.session.viewerToken)
        this.log('LiveKit connected', `${camera.name} ${camera.session.roomName}`)
      } catch (error) {
        camera.room = null
        camera.hasVideo = false
        this.log('ERROR LiveKit connect', this.errorMessage(error))
      } finally {
        camera.disconnecting = false
        camera.connecting = false
      }
    },
    connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/control`
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
        this.syncRobotEvent(event)
        this.syncSessionEvent(event)
        this.log('WS event', event)
      }
      this.socket = socket
    },
    async loadRobots() {
      const robots = await getRobots()
      if (!robots.length) return
      this.robots = robots.map(this.toRobotState)
      if (!this.robots.find(robot => robot.robotId === this.selectedRobotId)) {
        this.selectedRobotId = this.robots[0].robotId
      }
    },
    syncRobotEvent(event) {
      if (!event || !event.data || !event.data.robotId) return
      if (event.event !== 'robot.client.online' && event.event !== 'robot.client.offline') return
      const incoming = this.toRobotState(event.data)
      const index = this.robots.findIndex(robot => robot.robotId === incoming.robotId)
      if (index >= 0) {
        const existing = this.robots[index]
        const previous = new Map(existing.cameras.map(camera => [camera.deviceId, camera]))
        incoming.cameras = incoming.cameras.map(camera => {
          const old = previous.get(camera.deviceId)
          if (!old) return camera
          if (incoming.status === 'offline' && old.room) {
            old.disconnecting = true
            old.room.disconnect()
          }
          return Object.assign(camera, {
            session: old.session,
            room: incoming.status === 'online' ? old.room : null,
            hasVideo: incoming.status === 'online' ? old.hasVideo : false,
            status: incoming.status === 'online' ? old.status : 'offline',
            viewerCount: old.viewerCount,
            stopped: old.stopped,
            stopping: old.stopping,
            restarting: old.restarting,
            connecting: old.connecting,
            disconnecting: old.disconnecting
          })
        })
        this.$set(this.robots, index, incoming)
      } else {
        this.robots.push(incoming)
      }
    },
    syncSessionEvent(event) {
      if (!event || !event.data || !event.data.sessionId) return
      const camera = this.allCameras().find(item => item.session && item.session.sessionId === event.data.sessionId)
      if (!camera || camera.stopped) return
      if (this.stoppedSessionIds.has(event.data.sessionId)) return
      if (event.data.robotId && event.data.status) {
        camera.session = this.mergeSession(camera, event.data)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        if (this.shouldAttachFromEvent(event, camera)) {
          this.connectLiveKit(camera, true)
        }
      }
    },
    async restartCamera(camera) {
      if (camera.stopping || camera.stopped || camera.restarting) return
      if (!camera.session || camera.session.status === 'CLOSED') return
      if (this.stoppedSessionIds.has(camera.session.sessionId)) return
      if (!['STREAMING', 'INTERRUPTED'].includes(camera.session.status)) return
      try {
        camera.restarting = true
        const updated = await restartVideoSession(camera.session.sessionId)
        camera.session = this.mergeSession(camera, updated)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        this.log('API restartVideoSession', updated)
      } catch (error) {
        this.log('ERROR restartVideoSession', this.errorMessage(error))
      } finally {
        setTimeout(() => {
          camera.restarting = false
        }, 5000)
      }
    },
    isStoppedSession(camera, sessionId) {
      return camera.stopping || camera.stopped || this.stoppedSessionIds.has(sessionId)
    },
    shouldAttachFromEvent(event, camera) {
      if (!['video.session.streaming', 'video.track.published'].includes(event.event)) return false
      if (!event.data || event.data.status !== 'STREAMING') return false
      if (camera.hasVideo) return false
      return !camera.room || camera.room.state === 'disconnected'
    },
    videoElement(camera) {
      const ref = this.$refs[camera.key]
      return Array.isArray(ref) ? ref[0] : ref
    },
    previewHash(camera) {
      const video = this.videoElement(camera)
      if (!video || !video.videoWidth) return null
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      return String(canvas.toDataURL('image/jpeg', 0.7).length)
    },
    mergeSession(camera, update) {
      const next = Object.assign({}, camera.session || {}, update || {})
      if (!update || !update.viewerToken) next.viewerToken = camera.session && camera.session.viewerToken
      if (!update || !update.livekitUrl) next.livekitUrl = camera.session && camera.session.livekitUrl
      if (!update || !update.roomName) next.roomName = camera.session && camera.session.roomName
      return next
    },
    allCameras() {
      return this.robots.reduce((items, robot) => items.concat(robot.cameras), [])
    },
    toRobotState(robot) {
      return {
        robotId: robot.robotId,
        clientId: robot.clientId,
        name: robot.name || robot.robotId,
        type: robot.type || '机器人',
        status: robot.status || 'offline',
        cameras: (robot.cameras || []).map(camera => Object.assign(
          cameraState(robot.robotId, camera.deviceId || camera.cameraId, camera.name || camera.cameraId, camera.channel || 'visible'),
          {
            cameraId: camera.cameraId || camera.deviceId,
            quality: camera.quality || 'sub',
            status: robot.status === 'online' ? camera.status || '' : 'offline'
          }))
      }
    },
    statusType(status) {
      if (status === 'STREAMING') return 'success'
      if (status === 'FAILED' || status === 'TIMEOUT' || status === 'offline') return 'danger'
      if (status === 'REQUESTING_CLIENT' || status === 'ROOM_READY') return 'warning'
      return 'info'
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

<style scoped>
.page {
  min-height: 100vh;
  background: #eef2f7;
  color: #1f2937;
}

.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #dfe5ef;
}

.topbar h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 300px;
  gap: 12px;
  padding: 12px;
  height: calc(100vh - 56px);
  overflow: hidden;
}

.robot-list,
.video-area,
.event-panel {
  background: #ffffff;
  border: 1px solid #dfe5ef;
  border-radius: 4px;
  min-height: 0;
}

.robot-list {
  padding: 12px;
  overflow: auto;
}

.list-title {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 10px;
}

.robot-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  margin-bottom: 8px;
  text-align: left;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
}

.robot-item.active {
  background: #eef6ff;
  border-color: #409eff;
}

.robot-item span {
  font-size: 14px;
  font-weight: 600;
}

.robot-item small {
  color: #64748b;
}

.video-area {
  padding: 12px;
  min-width: 0;
  overflow: hidden;
}

.area-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  min-height: 42px;
}

.area-header h2 {
  margin: 0 0 4px;
  font-size: 16px;
}

.area-header span {
  color: #64748b;
  font-size: 12px;
}

.area-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.camera-grid {
  display: grid;
  gap: 8px;
  padding: 6px;
  border: 2px solid #1e9bff;
  background: #f8fbff;
  height: calc(100vh - 132px);
  overflow: hidden;
}

.camera-grid.grid-1 {
  grid-template-columns: minmax(360px, 1fr);
  grid-auto-rows: minmax(0, 1fr);
}

.camera-grid.grid-4 {
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  grid-auto-rows: minmax(0, 1fr);
}

.camera-grid.grid-9 {
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  grid-auto-rows: minmax(0, 1fr);
}

.camera-card {
  border: 1px solid #1e9bff;
  border-radius: 2px;
  overflow: hidden;
  background: #0f172a;
  min-height: 0;
}

.video-stage {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #111827;
}

.video-stage video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.video-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  min-height: 30px;
  padding: 6px 8px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  color: #ffffff;
  background: linear-gradient(180deg, rgba(2, 8, 23, 0.82), rgba(2, 8, 23, 0));
  z-index: 2;
}

.video-topbar strong,
.video-topbar span {
  display: block;
}

.video-topbar strong {
  font-size: 12px;
  line-height: 16px;
  font-weight: 600;
}

.video-topbar span {
  font-size: 11px;
  color: #cbd5e1;
}

.empty-video {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  font-size: 13px;
  pointer-events: none;
}

.video-tools {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: flex;
  gap: 4px;
  z-index: 3;
}

.video-tools .el-button {
  margin-left: 0;
  padding: 5px 7px;
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.32);
  background: rgba(15, 23, 42, 0.78);
}

.video-tools .el-button:hover,
.video-tools .el-button:focus {
  color: #ffffff;
  border-color: #409eff;
  background: rgba(30, 64, 175, 0.9);
}

.video-bottombar {
  position: absolute;
  right: 8px;
  bottom: 8px;
  max-width: 58%;
  height: 24px;
  padding: 0 6px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: #e2e8f0;
  font-size: 11px;
  background: rgba(15, 23, 42, 0.72);
  border-radius: 2px;
  z-index: 2;
}

.video-bottombar span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.event-head {
  height: 42px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5eaf2;
}

.event-log {
  padding: 8px;
  overflow: auto;
  max-height: none;
  flex: 1;
}

.event-item {
  white-space: pre-wrap;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  padding: 8px;
  margin-bottom: 8px;
  border-radius: 4px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 200px minmax(0, 1fr);
    height: auto;
    overflow: visible;
  }

  .video-area {
    overflow: visible;
  }

  .event-panel {
    grid-column: 1 / -1;
    min-height: 260px;
  }

  .camera-grid,
  .camera-grid.grid-9 {
    grid-template-columns: repeat(2, minmax(220px, 1fr));
    height: auto;
  }

  .camera-card {
    aspect-ratio: 16 / 9;
  }
}

@media (max-width: 760px) {
  .workspace {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .camera-grid,
  .camera-grid.grid-4,
  .camera-grid.grid-9 {
    grid-template-columns: 1fr;
    height: auto;
  }

  .camera-card {
    aspect-ratio: 16 / 9;
  }
}
</style>
