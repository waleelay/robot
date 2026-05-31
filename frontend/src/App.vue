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
          <small>{{ robot.robotId }} · {{ robot.status || 'offline' }} · 电量 {{ batteryText(robot.battery) }}</small>
        </button>
      </aside>

      <section class="video-area">
        <div class="area-header">
          <div>
            <h2>{{ selectedRobot.name }}</h2>
            <span>{{ selectedRobot.type }} · {{ selectedRobot.status || 'offline' }} · 电量 {{ batteryText(selectedRobot.battery) }} · {{ selectedRobot.cameras.length }} 路摄像头</span>
          </div>
          <div class="area-actions">
            <el-radio-group v-model="gridMode" size="small">
              <el-radio-button :label="1">1宫格</el-radio-button>
              <el-radio-button :label="4">4宫格</el-radio-button>
              <el-radio-button :label="9">9宫格</el-radio-button>
            </el-radio-group>
            <el-button size="small" :disabled="selectedRobot.status !== 'online'" @click="startRobot(selectedRobot)">全部观看</el-button>
            <el-button size="small" @click="stopRobot(selectedRobot)">全部停止</el-button>
            <el-button size="small" :type="recordingMode ? 'primary' : ''" @click="toggleRecordings">
              {{ recordingMode ? '返回实时' : '录像回放' }}
            </el-button>
          </div>
        </div>

        <div v-if="recordingMode" class="recording-workspace">
          <div class="recording-list">
            <div class="recording-head">
              <strong>巡逻录像</strong>
              <el-button size="mini" :loading="recordingsLoading" @click="loadRecordings">刷新</el-button>
            </div>
            <button
                v-for="recording in recordings"
                :key="recording.recordingId"
                class="recording-item"
                :class="{ active: selectedRecording && selectedRecording.recordingId === recording.recordingId }"
                @click="playRecording(recording)"
            >
              <span>{{ recording.fileName }}</span>
              <small>{{ recording.deviceId }} · {{ durationText(recording.durationSeconds) }} · {{ recording.status }}</small>
            </button>
            <div v-if="!recordings.length && !recordingsLoading" class="recording-empty">暂无录像记录</div>
          </div>
          <div class="recording-player">
            <video ref="recordedPlayer" controls playsinline />
            <div v-if="!selectedRecording" class="empty-video">选择 READY 录像开始回放</div>
          </div>
        </div>

        <div v-else class="camera-grid" :class="'grid-' + gridMode">
          <div v-for="camera in displayedCameras" :key="camera.key" class="camera-card">
            <div class="video-stage">
              <video :ref="camera.key" autoplay playsinline muted />
              <audio :ref="camera.key + '-audio'" autoplay />
              <div class="video-topbar">
                <div>
                  <strong>{{ camera.name }}</strong>
                  <span>{{ camera.deviceId }} · {{ groupTypeText(camera.groupType) }} · {{ camera.channel }}</span>
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
                <el-button
                    size="mini"
                    :icon="camera.intercomActive ? 'el-icon-turn-off-microphone' : 'el-icon-microphone'"
                    :loading="camera.intercomBusy"
                    :disabled="selectedRobot.status !== 'online'"
                    @click="toggleIntercom(selectedRobot, camera)"
                >{{ camera.intercomActive ? '挂断' : '通话' }}</el-button>
              </div>
              <div class="video-bottombar">
                <span>{{ camera.session ? camera.session.sessionId : '无会话' }}</span>
                <span>{{ camera.intercomActive ? '通话中' : (camera.viewerCount || 0) + ' 人观看' }}</span>
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
import Hls from 'hls.js'
import {
  createSnapshot,
  createVideoSession,
  getRobots,
  getRecordings,
  getRecordingPlayUrl,
  getViewerToken,
  heartbeatVideoSession,
  heartbeatIntercom,
  restartVideoSession,
  startCameraIntercom,
  startSessionIntercom,
  stopIntercom,
  stopVideoSession
} from './api/media'

// 前端内部摄像头状态模型。
// 后端返回的是业务会话/设备字段，页面还需要额外保存 LiveKit Room、加载状态、
// 本地停止意图、对讲 token 等 UI/连接态，所以统一在这里初始化。
function cameraState(robotId, deviceId, name, channel, groupType) {
  return {
    key: `${robotId}-${deviceId}-${channel}`,
    robotId,
    deviceId,
    name,
    groupType: groupType || 'body',
    channel,
    quality: 'sub',
    loading: false,
    hasVideo: false,
    hasAudio: false,
    watching: false,
    intercomActive: false,
    intercomBusy: false,
    intercomStatus: 'IDLE',
    intercomToken: null,
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
          battery: null,
          status: 'offline',
          cameras: [
            cameraState('robot-001', 'gimbal-001', '前向双光云台', 'visible', 'dual_gimbal'),
            cameraState('robot-001', 'gimbal-002', '后向广角相机', 'visible', 'body'),
            cameraState('robot-001', 'gimbal-003', '机械臂腕部相机', 'visible', 'arm')
          ]
        },
        {
          robotId: 'robot-002',
          name: '云深处四足机器狗',
          type: '四足机器人',
          battery: null,
          status: 'offline',
          cameras: [
            cameraState('robot-002', 'gimbal-001', '头部双光云台', 'visible', 'dual_gimbal'),
            cameraState('robot-002', 'gimbal-002', '腹部导航相机', 'visible', 'body'),
            cameraState('robot-002', 'gimbal-003', '尾部避障相机', 'visible', 'body')
          ]
        }
      ],
      events: [],
      recordingMode: false,
      recordingsLoading: false,
      recordings: [],
      selectedRecording: null,
      recordedHls: null
    }
  },
  computed: {
    // 当前选中的机器人对象。设备列表从 MQTT 心跳刷新，找不到时兜底到第一台。
    selectedRobot() {
      return this.robots.find(item => item.robotId === this.selectedRobotId) || this.robots[0]
    },
    // 宫格数量只影响展示摄像头数量，不会改变机器人真实摄像头列表。
    displayedCameras() {
      return this.selectedRobot.cameras.slice(0, this.gridMode)
    }
  },
  watch: {
    // 回放列表按机器人过滤；切换机器人时清掉旧播放器，避免旧 HLS 继续占用网络。
    selectedRobotId() {
      if (this.recordingMode) {
        this.selectedRecording = null
        this.destroyRecordedHls()
        this.loadRecordings()
      }
    }
  },
  mounted() {
    // 页面启动时同时拉一次静态列表、订阅事件流，并开启 viewer/intercom 心跳。
    this.loadRobots()
    this.connectWebSocket()
    this.heartbeatTimer = setInterval(this.heartbeatViewers, 5000)
  },
  beforeDestroy() {
    // Vue 组件销毁时主动关闭所有长连接，避免 LiveKit/MQTT 状态仍以为浏览器在线。
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.socket) this.socket.close()
    this.destroyRecordedHls()
    this.allCameras().forEach(camera => {
      if (camera.room) camera.room.disconnect()
    })
  },
  methods: {
    // 在“实时观看”和“录像回放”两套工作区之间切换。
    // 回放使用独立 HLS 实例，离开回放页必须销毁，否则会继续拉取分片。
    async toggleRecordings() {
      this.recordingMode = !this.recordingMode
      if (this.recordingMode) {
        await this.loadRecordings()
      } else {
        this.destroyRecordedHls()
        this.selectedRecording = null
      }
    },
    // 录像列表只拉 READY 状态，确保用户点开后一定能拿到可播放的 HLS 地址。
    async loadRecordings() {
      this.recordingsLoading = true
      try {
        const response = await getRecordings({ robotId: this.selectedRobotId, status: 'READY', page: 0, size: 20 })
        this.recordings = response.items || []
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        this.recordingsLoading = false
      }
    },
    // 播放录像时先向后端换取签名 URL，再根据浏览器能力选择原生 HLS 或 hls.js。
    // Safari 可原生播放 m3u8；Chrome/Edge 走 hls.js。
    async playRecording(recording) {
      if (recording.status !== 'READY') return
      try {
        const playback = await getRecordingPlayUrl(recording.recordingId)
        const player = this.$refs.recordedPlayer
        this.destroyRecordedHls()
        this.selectedRecording = recording
        if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = playback.playUrl
        } else if (Hls.isSupported()) {
          this.recordedHls = new Hls()
          this.recordedHls.loadSource(playback.playUrl)
          this.recordedHls.attachMedia(player)
        } else {
          throw new Error('当前浏览器不支持 HLS 播放')
        }
        await player.play().catch(() => {})
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      }
    },
    // 销毁当前录像播放器，包括 hls.js 实例、video src 和缩略图 hover 状态。
    destroyRecordedHls() {
      if (this.recordedHls) {
        this.recordedHls.destroy()
        this.recordedHls = null
      }
      const player = this.$refs.recordedPlayer
      if (player) {
        player.pause()
        player.removeAttribute('src')
        player.load()
      }
    },
    durationText(seconds) {
      if (!seconds) return '--:--'
      const hours = Math.floor(seconds / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      const remainder = seconds % 60
      const text = `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
      return hours > 0 ? `${hours}:${text}` : text
    },
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
    // 周期性心跳同时承担两个职责：
    // 1. 让后端知道当前浏览器仍在观看，从而维持 viewerCount；
    // 2. 让后端知道对讲操作员仍在线，避免对讲被超时释放。
    heartbeatViewers() {
      this.allCameras().forEach(camera => {
        if (camera.session && !camera.stopped && !camera.stopping) {
          const heartbeat = camera.watching
              ? heartbeatVideoSession(camera.session.sessionId)
              : Promise.resolve(camera.session)
          heartbeat
              .then(session => {
                if (camera.session && camera.session.sessionId === session.sessionId) {
                  camera.viewerCount = session.viewerCount
                }
              })
              .catch(() => {})
        }
        if (camera.session && camera.intercomActive) {
          heartbeatIntercom(camera.session.sessionId)
              .then(response => {
                camera.intercomStatus = response.intercomStatus
              })
              .catch(() => {})
        }
      })
    },
    // 开始观看单路摄像头：先通过控制 API 创建/复用会话，再用返回的 token 连 LiveKit。
    async startCamera(robot, camera) {
      if (robot.status !== 'online') return
      camera.loading = true
      camera.stopped = false
      camera.stopping = false
      camera.restarting = false
      camera.watching = true
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
        if (!camera.room || !camera.intercomActive) {
          await this.connectLiveKit(camera)
        }
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        this.log('ERROR createVideoSession', this.errorMessage(error))
      } finally {
        camera.loading = false
      }
    },
    // 停止观看只表示当前浏览器离开。若还有其他 viewer 或对讲占用，后端会保留 Room。
    async stopCamera(camera) {
      if (!camera.session) return
      camera.loading = true
      camera.stopping = true
      camera.stopped = true
      camera.restarting = false
      camera.disconnecting = true
      try {
        const sessionId = camera.session.sessionId
        if (!camera.intercomActive) this.stoppedSessionIds.add(sessionId)
        const stopped = await stopVideoSession(sessionId)
        this.log('API stopVideoSession', stopped)
        camera.watching = false
        camera.hasVideo = false
        const video = this.videoElement(camera)
        if (video) video.srcObject = null
        if (camera.intercomActive) {
          camera.status = stopped.status
          camera.viewerCount = stopped.viewerCount || 0
          return
        }
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
    async toggleIntercom(robot, camera) {
      if (camera.intercomActive) {
        await this.hangupIntercom(camera)
      } else {
        await this.startIntercom(robot, camera)
      }
    },
    async startIntercom(robot, camera) {
      camera.intercomBusy = true
      try {
        const response = camera.session
            ? await startSessionIntercom(camera.session.sessionId)
            : await startCameraIntercom({
              robotId: robot.robotId,
              deviceId: camera.deviceId,
              channel: camera.channel,
              quality: camera.quality
            })
        camera.session = this.mergeSession(camera, {
          sessionId: response.sessionId,
          robotId: response.robotId,
          deviceId: response.deviceId,
          roomName: response.roomName,
          status: response.videoStatus,
          intercomStatus: response.intercomStatus,
          intercomAudioOnly: response.intercomAudioOnly,
          livekitUrl: response.livekitUrl
        })
        camera.intercomToken = response.operatorToken
        camera.intercomActive = true
        camera.intercomStatus = response.intercomStatus
        camera.stopped = false
        if (!camera.room) {
          await this.connectLiveKit(camera, false, response.operatorToken)
        }
        if (camera.room) {
          await camera.room.localParticipant.setMicrophoneEnabled(true, {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
          }, {
            name: 'audio.operator.mic'
          })
        }
        this.log('API startIntercom', response)
      } catch (error) {
        camera.intercomActive = false
        this.$message.error(this.errorMessage(error))
        this.log('ERROR startIntercom', this.errorMessage(error))
      } finally {
        camera.intercomBusy = false
      }
    },
    // 结束对讲时要分两种场景：
    // - 仍在观看视频：保留 LiveKit Room 和视频 track；
    // - 只是对讲：断开 Room 并清掉会话状态。
    async hangupIntercom(camera) {
      if (!camera.session) return
      camera.intercomBusy = true
      try {
        if (camera.room) {
          await camera.room.localParticipant.setMicrophoneEnabled(false)
        }
        const response = await stopIntercom(camera.session.sessionId)
        camera.intercomActive = false
        camera.intercomStatus = 'IDLE'
        camera.intercomToken = null
        camera.hasAudio = false
        if (camera.watching) {
          camera.session = this.mergeSession(camera, response)
        } else {
          if (camera.room) {
            camera.disconnecting = true
            try {
              await camera.room.disconnect()
            } finally {
              camera.disconnecting = false
            }
          }
          camera.room = null
          camera.session = null
          camera.status = ''
        }
        this.log('API stopIntercom', response)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        camera.intercomBusy = false
      }
    },
    // 手动抓拍只提交任务；真正抓图/保存由后端 SnapshotService 和机器人/媒体侧完成。
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
    // 连接 LiveKit 的核心逻辑。
    // 普通观看使用 viewerToken；对讲使用 operatorToken，并在同一个 Room 中发布浏览器麦克风。
    // 事件回调里会把远端 audio/video track 挂到对应 DOM 元素上。
    async connectLiveKit(camera, refreshToken, connectionToken) {
      if (camera.connecting || !camera.session) return
      camera.connecting = true
      try {
        if (!camera.intercomActive && (refreshToken || !camera.session.viewerToken || !camera.session.livekitUrl)) {
          const token = await getViewerToken(camera.session.sessionId)
          camera.session = this.mergeSession(camera, {
            livekitUrl: token.livekitUrl,
            roomName: token.roomName,
            viewerToken: token.token
          })
        }
        const token = connectionToken || (camera.intercomActive ? camera.intercomToken : camera.session.viewerToken)
        const livekitUrl = this.liveKitConnectionUrl(camera.session.livekitUrl)
        if (!token || !livekitUrl) return
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
          if (track.kind === 'audio') {
            track.attach(this.audioElement(camera))
            camera.hasAudio = true
          } else if (track.kind === 'video' && camera.watching) {
            track.attach(this.videoElement(camera))
            camera.hasVideo = true
          }
          this.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
        })
        room.on(RoomEvent.TrackUnsubscribed, (track) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          track.detach()
          if (track.kind === 'audio') camera.hasAudio = false
          if (track.kind === 'video') camera.hasVideo = false
          this.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
          if (track.kind === 'video' && camera.watching && !this.isStoppedSession(camera, sessionId)) {
            this.restartCamera(camera)
          }
        })
        room.on(RoomEvent.Disconnected, () => {
          if (camera.room !== room || camera.disconnecting) return
          camera.hasVideo = false
          this.log('LiveKit Disconnected', camera.name)
          if (camera.watching && !this.isStoppedSession(camera, sessionId)) this.restartCamera(camera)
        })
        camera.room = room
        await room.connect(livekitUrl, token)
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
    // 控制 WebSocket 接收后端发布的机器人上下线、视频状态、对讲状态等事件。
    // 这些事件用于修正本地 UI 状态，而不是替代 REST API 的命令结果。
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
    // 拉取机器人注册表，并转换成页面内部 cameraState，保证后续 UI 字段完整。
    async loadRobots() {
      const robots = await getRobots()
      if (!robots.length) return
      this.robots = robots.map(this.toRobotState)
      if (!this.robots.find(robot => robot.robotId === this.selectedRobotId)) {
        this.selectedRobotId = this.robots[0].robotId
      }
    },
    // 机器人上下线事件会刷新设备基础信息，但保留已有会话和 LiveKit 连接态。
    // 如果机器人离线，则主动断开对应 Room，避免页面误显示还在播放。
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
            watching: old.watching,
            hasAudio: old.hasAudio,
            intercomActive: old.intercomActive,
            intercomBusy: old.intercomBusy,
            intercomStatus: old.intercomStatus,
            intercomToken: old.intercomToken,
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
    // 会话事件来自后端状态机。当前页面只同步自己正在关注的 session，
    // 并忽略用户已经明确停止的 session，防止自动重连覆盖本地意图。
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
      if (event.event.indexOf('video.intercom.') === 0) {
        camera.intercomStatus = event.data.intercomStatus || camera.intercomStatus
        camera.intercomActive = !['IDLE', 'FAILED'].includes(camera.intercomStatus)
      }
    },
    // LiveKit 断开、track 取消订阅或后端标记 INTERRUPTED 后触发重启。
    // 用 restarting 做短时间节流，避免多个事件同时到达造成重复下发命令。
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
    audioElement(camera) {
      const ref = this.$refs[camera.key + '-audio']
      return Array.isArray(ref) ? ref[0] : ref
    },
    liveKitConnectionUrl(serverUrl) {
      // HTTPS 页面不能直接连 ws:// LiveKit；生产环境通过同域 /livekit 反代升级到 wss。
      if (window.location.protocol === 'https:') {
        return `wss://${window.location.host}/livekit`
      }
      return serverUrl
    },
    previewHash(camera) {
      // 只返回截图 dataURL 长度作为轻量 hash，避免把大图直接塞进抓拍请求。
      const video = this.videoElement(camera)
      if (!video || !video.videoWidth) return null
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      return String(canvas.toDataURL('image/jpeg', 0.7).length)
    },
    mergeSession(camera, update) {
      // 部分 API/事件只返回增量字段，保留已有 token/url/roomName，避免重连时丢上下文。
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
      // 将后端机器人 DTO 转为页面状态，同时给缺失字段补默认值。
      return {
        robotId: robot.robotId,
        clientId: robot.clientId,
        name: robot.name || robot.robotId,
        type: robot.type || '机器人',
        battery: robot.battery,
        status: robot.status || 'offline',
        cameras: (robot.cameras || []).map(camera => Object.assign(
            cameraState(robot.robotId, camera.deviceId || camera.cameraId, camera.name || camera.cameraId, camera.channel || 'visible', camera.groupType),
            {
              cameraId: camera.cameraId || camera.deviceId,
              quality: camera.quality || 'sub',
              status: robot.status === 'online' ? camera.status || '' : 'offline'
            }))
      }
    },
    batteryText(battery) {
      return battery === null || battery === undefined ? '--' : `${battery}%`
    },
    groupTypeText(groupType) {
      return {
        body: '本体',
        dual_gimbal: '双光云台',
        arm: '机械臂'
      }[groupType] || groupType || '未分组'
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
      let data = error && error.response && error.response.data
      if (typeof data === 'string') {
        try {
          data = JSON.parse(data)
        } catch (ignored) {
          return data || '请求失败'
        }
      }
      if (data && data.code === 'INVALID_STATE' && data.message === 'Intercom is occupied by another operator') {
        return '该视频画面正在通话中，请结束当前通话后重试'
      }
      return data && (data.message || data.code || data.error)
          ? data.message || data.code || data.error
          : (error && error.message) || '请求失败'
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

.recording-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 10px;
  height: calc(100vh - 132px);
  min-height: 0;
}

.recording-list {
  overflow: auto;
  padding: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.recording-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.recording-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 8px;
  padding: 10px;
  text-align: left;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #ffffff;
  cursor: pointer;
}

.recording-item.active {
  border-color: #409eff;
  background: #eef6ff;
}

.recording-item span {
  font-size: 13px;
  font-weight: 600;
}

.recording-item small,
.recording-empty {
  color: #64748b;
  font-size: 12px;
}

.recording-player {
  position: relative;
  min-height: 0;
  background: #111827;
}

.recording-player video {
  width: 100%;
  height: 100%;
  object-fit: contain;
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

.video-stage audio {
  display: none;
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

  .recording-workspace {
    grid-column: 1 / -1;
    grid-template-columns: 260px minmax(0, 1fr);
    height: 420px;
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

  .recording-workspace {
    display: block;
    height: auto;
  }

  .recording-list {
    max-height: 260px;
    margin-bottom: 8px;
  }

  .recording-player {
    height: 300px;
  }
}
</style>
