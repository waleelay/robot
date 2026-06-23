import { Room, RoomEvent, Track } from "livekit-client"
import { createVideoSession, getRobots, getViewerToken, heartbeatVideoSession, restartVideoSession, stopVideoSession } from "../../../api/media"
// import { robots } from "./constants"

export default {
  data() {
    return {
      wsConnected: false,
      socket: null,
      robots: [],
      heartbeatTimer: null,
      stoppedSessionIds: new Set(),
      selectedRobotId: ''
    }
  },
  computed: {
    selectedRobot() {
      return this.robots.find(item => item.robotId === this.selectedRobotId) || this.robots[0]
    },
    displayedCameras() {
      return this.selectedRobot.cameras.slice(0, 4)
    },
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
    // 视频会话心跳，获取视频在线观看数量
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
      console.log('CLICK startCamera', {
        robotId: robot.robotId,
        deviceId: camera.deviceId,
        quality: camera.quality
      })
      try {
        const session = await createVideoSession({
          robotId: robot.robotId,
          deviceId: camera.deviceId,
          quality: camera.quality,
          reuse: true
        })
        camera.session = this.mergeSession(camera, session)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        this.stoppedSessionIds.delete(camera.session.sessionId)
        console.log('API createVideoSession', camera.session)
        await this.connectLiveKit(camera)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        console.log('ERROR createVideoSession', this.errorMessage(error))
      } finally {
        camera.loading = false
      }
      Object.keys(this.ZQL_videosInfos).map(key => {
        if (this.ZQL_videosInfos[key]?.key === camera.key) {
          this.$set(this.ZQL_videosInfos, key, { ...this.ZQL_videosInfos[key], ...camera })
        }
      })
    },
    async stopCamera(camera) {
      console.log('=================stopCamera=================', camera, camera.session)
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
        console.log('API stopVideoSession', stopped)
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
      Object.keys(this.ZQL_videosInfos).map(key => {
        if (this.ZQL_videosInfos[key]?.key === camera.key) {
          this.$set(this.ZQL_videosInfos, key, { ...this.ZQL_videosInfos[key], ...camera })
        }
      })
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
      console.log('API snapshot', response)
      this.$message.success('已提交抓拍任务')
    },
    // 用于连接LiveKit会话
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
        const room = new Room({
          // adaptiveStream: true,  // 自动管理订阅质量[citation:1]
          // dynacast: true,        // 优化发布端性能[citation:7]
        })
        const sessionId = camera.session.sessionId
        room.on(RoomEvent.TrackSubscribed, (track) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          if (track.kind === Track.Kind.Video) {
            // track.attach(this.videoElement(camera))
            track.attach(document.getElementById(camera.key))
            console.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
            camera.remoteVideoTrack = track;
          } else if (track.kind === Track.Kind.Audio) {
            // 音频轨道自动播放，无需手动附加元素即可工作
            camera.remoteAudioTrack = track;
          }
        })
        room.on(RoomEvent.TrackUnsubscribed, (track) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          track.detach()
          camera.hasVideo = false
          console.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
          if (!this.isStoppedSession(camera, sessionId)) this.restartCamera(camera)
        })
        room.on(RoomEvent.Disconnected, () => {
          if (camera.room !== room || camera.disconnecting) return
          camera.hasVideo = false
          console.log('LiveKit Disconnected', camera.name)
          if (!this.isStoppedSession(camera, sessionId)) this.restartCamera(camera)
        })
        camera.room = room
        await room.connect(camera.session.livekitUrl, camera.session.viewerToken)
        console.log('LiveKit connected', `${camera.name} ${camera.session.roomName}`)
      } catch (error) {
        camera.room = null
        camera.hasVideo = false
        console.log('ERROR LiveKit connect', this.errorMessage(error))
      } finally {
        camera.disconnecting = false
        camera.connecting = false
      }
    },
    // 加载机器人列表
    async loadRobots() {
      const robots = await getRobots()
      if (robots && robots.length) {
        this.robots = robots.map(robot => this.toRobotState(robot))
      }
      if (!this.robots.find(robot => robot.robotId === this.selectedRobotId)) {
        this.selectedRobotId = this.robots[0].robotId
      }
    },
    // 连接WebSocket
    connectWebSocket() {
      // const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      // const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/control`
      const url = 'wss://192.168.124.77:4443/ws/control'
      const socket = new WebSocket(url)
      socket.onopen = () => {
        this.wsConnected = true
        console.log('WebSocket connected', url)
      }
      socket.onclose = () => {
        this.wsConnected = false
        console.log('WebSocket closed', url)
      }
      socket.onmessage = (message) => {
        const event = JSON.parse(message.data)
        this.syncRobotEvent(event)
        this.syncSessionEvent(event)
        // console.log('WS event', event)
      }
      this.socket = socket
    },
    // 用于处理WebSocket接收到的机器人在线/离线事件，更新机器人列表状态
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
            disconnecting: old.disconnecting,
            remoteAudioTrack: old.remoteAudioTrack || null,
            remoteVideoTrack: old.remoteVideoTrack || null,
          })
        })
        this.$set(this.robots, index, incoming)
      } else {
        this.robots.push(incoming)
      }
    },
    // 用于处理WebSocket接收到的会话状态更新事件，更新会话状态
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
        console.log('API restartVideoSession', updated)
      } catch (error) {
        console.log('ERROR restartVideoSession', this.errorMessage(error))
      } finally {
        setTimeout(() => {
          camera.restarting = false
        }, 5000)
      }
    },
    // ==================================================================================公共方法转换===================================================================
    isStoppedSession(camera, sessionId) {
      return camera.stopping || camera.stopped || this.stoppedSessionIds.has(sessionId)
    },
    shouldAttachFromEvent(event, camera) {
      if (!['video.session.streaming', 'video.track.published'].includes(event.event)) return false
      if (!event.data || event.data.status !== 'STREAMING') return false
      if (camera.hasVideo) return false
      return !camera.room || camera.room.state === 'disconnected'
    },
    // 用于将机器人数据转换为状态对象
    toRobotState(robot) {
      return Object.assign({}, {
        ...robot,
        name: robot.name || robot.robotId,
        type: robot.type || '机器人',
        status: robot.status || 'offline',
        cameras: (robot.cameras || []).map(camera => Object.assign(
          {},
          camera,
          this.cameraState(robot.robotId, camera.deviceId || camera.cameraId, camera.name || camera.cameraId),
          {
            cameraId: camera.cameraId || camera.deviceId,
            quality: camera.quality || 'sub',
            status: robot.status === 'online' ? (camera.status || '') : 'offline'
          }
        )),
      })
    },
    // 用于将相机数据转换为状态对象
    cameraState(robotId, deviceId, name) {
      return {
        key: `${robotId}-${deviceId}`,
        robotId,
        deviceId,
        name,
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
    },
    // 用于合并会话数据，保留必要的字段
    mergeSession(camera, update) {
      const next = Object.assign({}, camera.session || {}, update || {})
      if (!update || !update.viewerToken) next.viewerToken = camera.session && camera.session.viewerToken
      if (!update || !update.livekitUrl) next.livekitUrl = camera.session && camera.session.livekitUrl
      if (!update || !update.roomName) next.roomName = camera.session && camera.session.roomName
      return next
    },
    videoElement(camera) {
      const ref = this.$refs[camera.key]
      return Array.isArray(ref) ? ref[0] : ref
    },
    // 用于获取错误消息
    errorMessage(error) {
      return error && error.response && error.response.data
        ? error.response.data.message || error.response.data.code
        : error.message || '请求失败'
    },
    // 用于生成相机预览哈希值，用于缓存
    previewHash(camera) {
      const video = this.videoElement(camera)
      if (!video || !video.videoWidth) return null
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      return String(canvas.toDataURL('image/jpeg', 0.7).length)
    },
    // 用于获取所有相机状态
    allCameras() {
      return this.robots.reduce((items, robot) => items.concat(robot.cameras), [])
    },
    // 状态
    statusType(status) {
      if (status === 'STREAMING') return 'success'
      if (status === 'FAILED' || status === 'TIMEOUT' || status === 'offline') return 'danger'
      if (status === 'REQUESTING_CLIENT' || status === 'ROOM_READY') return 'warning'
      return 'info'
    }
  }
}
