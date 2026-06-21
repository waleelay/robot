import store from '@/store';
import bigScreen from './bigScreen'; // 导入 store 实例
import { Message } from 'element-ui';
import { Room, RoomEvent, Track } from "livekit-client"
import {
  createSnapshot,
  createVideoSession,
  getRobots,
  getViewerToken,
  heartbeatVideoSession,
  heartbeatIntercom,
  restartVideoSession,
  startSessionIntercom,
  startCameraIntercom,
  stopIntercom,
  stopVideoSession,
  getControlProfile,
  getRecordings,
  takeoverControl,
  acquireControl,
} from "../../api/media"
import Vue from 'vue';

// 定义 WebSocket 模块的初始状态
const state = {
  websocket: null, // 存储 WebSocket 实例
  manualClosing: false, // 主动关闭标记（防止替换连接时触发重复重连）
  reconnectAttempts: 0, // 记录重连次数
  maxReconnectAttempts: 5, // 最大重连次数
  isConnected: false, // 标识是否已经连接
  basic: [], // 存储实时基础状态
  issue_task: [], // 下发导航任务
  cancel_task: [], // 取消导航任务
  search_task: [], // 查询导航任务状态
  motion_control: [], // 运动控制
  power: [], // 电量信息
  videoBlob: null, // 存储接收的视频流数据
  rollCall: {}, // 存储大屏左边的信息
  cruiseDate: {},
  onlineState: 1,
  nomoveTimes: 0,
  personRollCall: [], //显示三秒的点名信息
  firePersonError: [], //显示三秒的异常信息
  lastConsumedFirePersonErrorId: null, // 最近一次已弹出的告警ID，避免页面切换后重复弹窗
  refreshWarningTime: '',
  statistics: {},
  videoUrl: '',
  vehicle: {}, //车辆管控信息
  
  // ============ Media 相关状态 ============
  wsConnected: false, // 媒体服务 WebSocket 连接状态
  mediaSocket: null, // 媒体服务 WebSocket 实例
  robots: [], // 机器人列表
  cameras: {}, // 全局摄像头索引 { [cameraKey]: camera }
  camerasRevision: 0,
  heartbeatTimer: null, // 心跳定时器
  stoppedSessionIds: new Set(), // 已停止的会话ID集合
  selectedRobotId: '', // 当前选中的机器人ID
  activeCameras: {}, // 存储当前激活的摄像头 { [key]: { robot, camera } }
  
  ptzAutoRotateState: {},
  audioState: {},
  controlProfiles: {},
  recordingMode: false,
  recordingsLoading: false,
  recordings: [],
  selectedRecording: null,
  prefixId: '',
  recordingTab: 'manual',
  controlSessions: {}
};

function cameraKey(robotId, camera) {
  return camera.key || `${robotId}-${camera.deviceId || camera.cameraId}-${camera.channel || 'visible'}`
}

function toBasicCamera(camera) {
  return {
    cameraId: camera.cameraId || camera.deviceId,
    channel: camera.channel || 'visible',
    deviceId: camera.deviceId || camera.cameraId,
    groupType: camera.groupType,
    name: camera.name,
    quality: camera.quality || 'sub',
    status: camera.status || 'offline'
  }
}

function toBasicRobot(robot) {
  return Object.assign({}, robot, {
    cameras: (robot.cameras || []).map(toBasicCamera)
  })
}

function indexCameras(robots) {
  return (robots || []).reduce((result, robot) => {
    (robot.cameras || []).forEach(camera => {
      result[cameraKey(robot.robotId, camera)] = camera
    })
    return result
  }, {})
}

// 定义用于修改状态的 mutations，通过这个地方判断是什么类型的信息，然后页面调用不同的信息
const mutations = {
  // 设置 WebSocket 实例
  setWebSocket(state, websocket) {
    state.websocket = websocket;
  },
  setManualClosing(state, value) {
    state.manualClosing = value;
  },
  setLastConsumedFirePersonErrorId(state, id) {
    state.lastConsumedFirePersonErrorId = id;
  },
  // 设置机器人列表
  setRobots(state, robots) {
    state.robots = robots
  },
  // 更新单个机器人状态
  updateRobot(state, robot) {
    const index = state.robots.findIndex(r => r.robotId === robot.robotId)
    if (index >= 0) {
      state.robots.splice(index, 1, robot)
    } else {
      state.robots.push(robot)
    }
  },
  setCameras(state, cameras) {
    state.cameras = cameras
    state.camerasRevision += 1
  },
  setCamera(state, camera) {
    if (!camera || !camera.key) return
    camera._revision = (camera._revision || 0) + 1
    state.cameras = { ...state.cameras, [camera.key]: camera }

    const robotIndex = state.robots.findIndex(item => item.robotId === camera.robotId)
    if (robotIndex < 0) return
    const robot = state.robots[robotIndex]
    const cameraIndex = (robot.cameras || []).findIndex(item =>
      (item.deviceId || item.cameraId) === (camera.deviceId || camera.cameraId) &&
      (item.channel || 'visible') === (camera.channel || 'visible')
    )
    if (cameraIndex < 0 || robot.cameras[cameraIndex].status === camera.status) return
    const cameras = robot.cameras.slice()
    cameras[cameraIndex] = { ...cameras[cameraIndex], status: camera.status }
    state.robots.splice(robotIndex, 1, { ...robot, cameras })
  },
  // 设置选中的机器人ID
  setSelectedRobotId(state, robotId) {
    state.selectedRobotId = robotId
  },
  // 设置控制配置文件
  setControlProfiles(state, { robotId, profile }) {
    console.log('setControlProfiles', robotId, profile);
    
    state.controlProfiles = { ...state.controlProfiles, [robotId]: profile }
  },
  setPrefixId(state, prefixId) {
    state.prefixId = prefixId
  },
  // 设置媒体服务 WebSocket 连接状态
  setWsConnected(state, status) {
    state.wsConnected = status
  },
  // 设置媒体服务 WebSocket 实例
  setMediaSocket(state, socket) {
    state.mediaSocket = socket
  },
  setConnected(state, status) {
    state.isConnected = status;
  },
  incrementReconnectAttempts(state) {
    state.reconnectAttempts++;
  },
  resetReconnectAttempts(state) {
    state.reconnectAttempts = 0;
  },
  refreshWarning(state) {
    state.refreshWarningTime = +new Date();
  },
  setVehicleInfo: (state, data) => {
    state.vehicle = data
  },
  setActiveCamera(state, { key, robot, camera }) {
    Vue.set(state.activeCameras, key, { robot, camera });
    // state.activeCameras = { ...state.activeCameras, [key]: { robot, camera } };
  },
  removeActiveCamera(state, key) {
    Vue.delete(state.activeCameras, key);
  },
  setAudioState(state, { key, volume, muted }) {
    Vue.set(state.audioState, key, { volume, muted });
  },
  // 清空回放视频
  destroyRecordedHls() {
    if (state.recordedHls) {
      state.recordedHls.destroy()
      state.recordedHls = null
    }
    // const player = this.$refs.recordedPlayer
    // if (player) {
    //   player.pause()
    //   player.removeAttribute('src')
    //   player.load()
    // }
  },
}

// ============ 导出 actions ============
// ============ 工具函数 ============
// 用于将机器人数据转换为状态对象
function toRobotState(robot) {
  return Object.assign({}, robot, {
    name: robot.name || robot.robotId,
    type: robot.type || '机器人',
    controlMode: robot.controlMode || 'MANUAL',
    stateSeq: robot.stateSeq || 0,
    status: robot.status || robot.onlineStatus || 'offline',
    cameras: (robot.cameras || []).map(camera => Object.assign(
      {},
      cameraState(robot.robotId, camera.deviceId || camera.cameraId, camera.name || camera.cameraId, camera.channel || 'visible', camera.groupType),
      camera,
      {
        cameraId: camera.cameraId || camera.deviceId,
        quality: camera.quality || 'sub',
        status: robot.status === 'online' ? camera.status : 'offline'
      }
    ))
  })
}

function groupTypeText(groupType) {
  return {
    body: '本体',
    dual_gimbal: '双光云台',
    arm: '机械臂'
  }[groupType] || groupType || '未分组'
}

// 用于将相机数据转换为状态对象
function cameraState(robotId, deviceId, name, channel, groupType) {
  return {
    key: `${robotId}-${deviceId}-${channel}`,
    robotId,
    deviceId,
    name,
    groupType: groupType || 'body',
    groupTypeName: groupTypeText(groupType),
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
    recordingActive: false,
    recordingBusy: false,
    activeRecording: null,
    latencyMs: null,
    latencyLevel: 'unknown',
    statsTimer: null,
    statsTrack: null,
    statsRoom: null,
    stopping: false,
    stopped: false,
    restarting: false,
    connecting: false,
    disconnecting: false,
    qualityChanging: false,
    room: null,
    session: null,
    status: 'offline',
    viewerCount: 0,
    remoteAudioTrack: null,
    remoteVideoTrack: null
  }
}

// 用于合并会话数据
function mergeSession(camera, update) {
  const next = Object.assign({}, camera.session || {}, update || {})
  if (!update || !update.viewerToken) next.viewerToken = camera.session && camera.session.viewerToken
  if (!update || !update.livekitUrl) next.livekitUrl = camera.session && camera.session.livekitUrl
  if (!update || !update.roomName) next.roomName = camera.session && camera.session.roomName
  return next
}

// 获取所有相机
function allCameras() {
  return Object.values(state.cameras)
}

// 判断是否已停止会话
function isStoppedSession(camera, sessionId) {
  return camera.stopping || camera.stopped || state.stoppedSessionIds.has(sessionId)
}

// 判断是否应该从事件附加
function shouldAttachFromEvent(event, camera) {
  if (!['video.session.streaming', 'video.track.published'].includes(event.event)) return false
  if (!event.data || event.data.status !== 'STREAMING') return false
  if (camera.hasVideo) return false
  return !camera.room || camera.room.state === 'disconnected'
}

function activeRecordingInProgress(camera) {
  return camera.recordingActive || (camera.activeRecording && camera.activeRecording.status === 'RECORDING')
}
function intercomInProgress(camera) {
  return camera.intercomActive || (camera.intercomStatus && !['IDLE', 'FAILED'].includes(camera.intercomStatus))
}
function effectiveCameraQuality(camera, value) {
  const quality = value || camera.quality || 'auto'
  if (quality === 'main' || quality === 'sub') return quality
  return this.gridMode === 1 ? 'main' : 'sub'
}
function firstVideoPublication(room) {
  for (const participant of room.remoteParticipants.values()) {
    for (const publication of participant.trackPublications.values()) {
      if (publication.track && publication.track.kind === 'video') return publication
    }
  }
  return null
}
function latencyLevel(camera) {
  if (!Number.isFinite(camera.latencyMs)) return 'unknown'
  if (camera.latencyMs < 80) return 'good'
  if (camera.latencyMs < 200) return 'warn'
  return 'bad'
}
function selectedCandidatePairRtt(stats) {
  let selectedPairId = null
  stats.forEach(report => {
    if (report.type === 'transport' && report.selectedCandidatePairId) {
      selectedPairId = report.selectedCandidatePairId
    }
  })
  if (selectedPairId && stats.get) {
    const selected = stats.get(selectedPairId)
    if (selected && Number.isFinite(selected.currentRoundTripTime)) return selected.currentRoundTripTime
  }
  let fallback = null
  stats.forEach(report => {
    if (fallback !== null || report.type !== 'candidate-pair') return
    const selected = report.selected || report.nominated || report.state === 'succeeded'
    if (selected && Number.isFinite(report.currentRoundTripTime)) fallback = report.currentRoundTripTime
  })
  return fallback
}

// ============ 导出 actions ============
// 定义 actions 以便于进行异步操作
const actions = {  
  // ============ Media 相关 actions ============
  // 加载机器人列表
  async loadRobots({ commit, state, dispatch }, payload) {
    // const robots = await getRobots()
    const robots = payload
    if (robots && robots.length) {
      const fullRobots = robots.map(robot => toRobotState(robot))
      commit('setCameras', indexCameras(fullRobots))
      commit('setRobots', fullRobots.map(toBasicRobot))
    }
    // if (!state.robots.find(robot => robot.robotId === state.selectedRobotId)) {
    //   commit('setSelectedRobotId', state.robots[0]?.robotId || '')
    // }
    // await dispatch('loadControlProfile', robots[0].robotId)
  },
  
  
  // 连接媒体服务 WebSocket
  connectMediaWebSocket({ commit, state, dispatch }) {
    // const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/control`
    const url = 'wss://192.168.124.77:4443/ws/control'
    const socket = new WebSocket(url)
    socket.onopen = () => {
      commit('setWsConnected', true)
      dispatch('startHeartbeat')
      console.log('Media WebSocket connected', url)
    }
    socket.onclose = () => {
      commit('setWsConnected', false)
      clearInterval(state.heartbeatTimer)
      console.log('Media WebSocket closed', url)
    }
    socket.onmessage = (message) => {
      const event = JSON.parse(message.data)
      dispatch('syncRobotEvent', event)
      dispatch('syncSessionEvent', event)
      dispatch('syncControlEvent', event)
    }
    commit('setMediaSocket', socket)
  },
  // 处理机器人在线/离线事件
  syncRobotEvent({ commit, state, dispatch }, event) {
    const data = event && (event.data || event.payload)
    if (!data || !data.robotId) return
    if (event.event !== 'robot.state' && event.type !== 'robot.state') return
    const incoming = toRobotState(data)
    // console.log('12', existing)
    const index = state.robots.findIndex(robot => robot.robotId === incoming.robotId)
    if (index >= 0) {
      const existing = state.robots[index]
      incoming.cameras = incoming.cameras.map(camera => {
        const old = state.cameras[camera.key]
        if (!old) return camera
        if (incoming.status === 'offline' && old.room) {
          old.disconnecting = true
          dispatch('stopLatencyStats', old)
          old.room.disconnect()
        }
        return Object.assign(camera, {
          session: old.session,
          room: incoming.status === 'online' ? old.room : null,
          hasVideo: incoming.status === 'online' ? old.hasVideo : false,
          latencyMs: incoming.status === 'online' ? old.latencyMs : null,
          latencyLevel: incoming.status === 'online' ? old.latencyLevel : 'unknown',
          statsTimer: incoming.status === 'online' ? old.statsTimer : null,
          statsTrack: incoming.status === 'online' ? old.statsTrack : null,
          statsRoom: incoming.status === 'online' ? old.statsRoom : null,
          status: incoming.status === 'online' ? old.status : 'offline',
          viewerCount: old.viewerCount,
          watching: old.watching,
          hasAudio: old.hasAudio,
          quality: old.quality,
          qualityChanging: old.qualityChanging,
          activeRecording: old.activeRecording,
          recordingActive: old.recordingActive,
          recordingBusy: old.recordingBusy,
          intercomActive: old.intercomActive,
          intercomBusy: old.intercomBusy,
          intercomStatus: old.intercomStatus,
          intercomToken: old.intercomToken,
          stopped: old.stopped,
          stopping: old.stopping,
          restarting: old.restarting,
          connecting: old.connecting,
          disconnecting: old.disconnecting,
          remoteAudioTrack: old.remoteAudioTrack || null,
          remoteVideoTrack: old.remoteVideoTrack || null,
        })
      })
      incoming.cameras.forEach(camera => commit('setCamera', camera))
      commit('updateRobot', toBasicRobot({ ...existing, ...incoming }))
    } else {
      incoming.cameras.forEach(camera => commit('setCamera', camera))
      commit('setRobots', [...state.robots, toBasicRobot(incoming)])
    }
  },
  
  // 处理会话状态更新事件
  syncSessionEvent({ commit, state, dispatch }, event) {
    if (!event || !event.data || !event.data.sessionId) return
    const camera = allCameras().find(item => item.session && item.session.sessionId === event.data.sessionId)
    if (!camera || camera.stopped) return
    if (state.stoppedSessionIds.has(event.data.sessionId)) return
    if (event.data.robotId && event.data.status) {
      camera.session = mergeSession(camera, event.data)
      camera.status = camera.session.status
      camera.viewerCount = camera.session.viewerCount
      if (shouldAttachFromEvent(event, camera)) {
        dispatch('connectLiveKit', { camera, refreshToken: true })
      }
    }
    if (event.event.indexOf('video.intercom.') === 0) {
      camera.intercomStatus = event.data.intercomStatus || camera.intercomStatus
      camera.intercomActive = !['IDLE', 'FAILED'].includes(camera.intercomStatus)
    }
    commit('setCamera', camera)
  },

  syncControlEvent({ commit, state, dispatch }, event) {
    if (!event) return
    if (event.type === 'control.command.rejected') {
      Message.error((event.payload && event.payload.message) || '控制命令被拒绝')
    }
    return
  },
  syncAudioStatesFromDevices({ commit, state }, {robotId, devices}) {
    if (!robotId || !Array.isArray(devices)) return
    devices
      .filter(device => ['CLIENT_AUDIO', 'VOLUME_CONTROL', 'INTERCOM'].includes(device.deviceType))
      .forEach(device => {
        const status = device.status || device.runtimeStatus || {}
        if (status.volume === undefined && status.muted === undefined) return
        const key = `${robotId}:${device.deviceId}`
        commit('setAudioState', Object.assign({}, state.audioState[key] || { volume: 50, muted: false }, {
          volume: status.volume === undefined ? (state.audioState[key] && state.audioState[key].volume) || 50 : status.volume,
          muted: status.muted === undefined ? !!(state.audioState[key] && state.audioState[key].muted) : status.muted
        }))
      })
  },
  updateAudioState({ commit, state }, { key, volume, muted }) {
    commit('setAudioState', { key, volume, muted })
  },
  async ensureControlSession({ commit, state }, {device, action}) {
    if (!device) throw new Error('未找到控制设备')
      console.log(device, action);
      
    const key = `${state.selectedRobotId}:${device.deviceId}:${action}`
    if (state.controlSessions[key] && state.controlSessions[key].status === 'ACTIVE') {
      return state.controlSessions[key]
    }
    let session
    if (device.deviceId === 'base' && ['NAVIGATION', 'ASSISTED'].includes(state.selectedRobot.controlMode)) {
      session = await takeoverControl(state.selectedRobotId, {
        fromMode: state.selectedRobot.controlMode,
        toMode: 'MANUAL',
        scope: 'ROBOT',
        deviceIds: ['base'],
        actions: ['drive.velocity'],
        observedStateSeq: state.selectedRobot.stateSeq || 0,
        reason: 'manual_takeover'
      })
    } else {
      session = await acquireControl(state.selectedRobotId, {
        scope: device.deviceId === 'base' ? 'ROBOT' : 'DEVICE',
        deviceIds: [device.deviceId],
        actions: [action],
        mode: 'EXCLUSIVE',
        reason: 'manual_teleop',
        ttlSeconds: 30
      })
    }
    if (session.code) {
      const error = new Error(session.message || session.code)
      error.code = session.code
      throw error
    }
    state.controlSessions = Object.assign({}, state.controlSessions, { [key]: session })
    return session
  },
  // 视频会话心跳
  async heartbeatViewers({ state, commit }) {
    for (const camera of allCameras()) {
      let changed = false
      if (camera.session && !camera.stopped && !camera.stopping) {
        try {
          const session = camera.watching
            ? await heartbeatVideoSession(camera.session.sessionId)
            : camera.session
          if (camera.session && camera.session.sessionId === session.sessionId) {
            changed = camera.viewerCount !== session.viewerCount || changed
            camera.viewerCount = session.viewerCount
          }
        } catch (_) {}
      }
      if (camera.session && camera.intercomActive) {
        try {
          const response = await heartbeatIntercom(camera.session.sessionId)
          changed = camera.intercomStatus !== response.intercomStatus || changed
          camera.intercomStatus = response.intercomStatus
        } catch (_) {}
      }
      if (changed) commit('setCamera', camera)
    }
  },
  // 启动摄像头
  async startCamera({ commit, state, dispatch }, { robot, camera }) {
    if (robot.status !== 'online') return
    camera.loading = true
    camera.stopped = false
    camera.stopping = false
    camera.restarting = false
    camera.watching = true
    try {
      const session = await createVideoSession({
        robotId: robot.robotId,
        deviceId: camera.deviceId,
        channel: camera.channel,
        quality: camera.quality,
        reuse: true
      })
      camera.session = mergeSession(camera, session)
      camera.status = camera.session.status
      camera.viewerCount = camera.session.viewerCount
      state.stoppedSessionIds.delete(camera.session.sessionId)
      console.log('API createVideoSession', camera.session)
      if (!camera.room || !camera.intercomActive) {
        await dispatch('connectLiveKit', { camera })
      }
    } catch (error) {
      console.log('ERROR createVideoSession', error.message || '请求失败')
    } finally {
      camera.loading = false
      commit('setCamera', camera)
      commit('setActiveCamera', { key: camera.key, robot, camera });
    }
    // console.log('4')
  },
  
  // 停止摄像头
  async stopCamera({ commit, state }, camera) {
    // console.log('stopCamera', camera.session)
    if (!camera.session) return
    camera.loading = true
    camera.stopping = true
    camera.stopped = true
    camera.restarting = false
    camera.disconnecting = true
    try {
      const sessionId = camera.session.sessionId
      if (!camera.intercomActive) state.stoppedSessionIds.add(sessionId)
      console.log('stopCamera==================================================')
      const stopped = await stopVideoSession(sessionId)
      console.log('API stopVideoSession', stopped)
      camera.watching = false
      camera.hasVideo = false
      const video = document.getElementById(state.prefixId + camera.key)
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
      console.log('ERROR stopVideoSession', error.message || '请求失败')
    } finally {
      camera.disconnecting = false
      camera.stopping = false
      camera.loading = false
      commit('setCamera', camera)
      commit('removeActiveCamera', camera.key);
    }
  },

  async toggleIntercom({ commit, state, dispatch }, {robotId, camera}) {
    // console.log('toggleIntercom', robotId, camera)
    if (camera.intercomActive) {
      await dispatch('hangupIntercom', camera)
    } else {
      await dispatch('startIntercom', {robotId, camera})
    }
  },
  async startIntercom({ commit, state, dispatch }, {robotId, camera}) {
    camera.intercomBusy = true
    try {
      const response = camera.session
        ? await startSessionIntercom(camera.session.sessionId)
        : await startCameraIntercom({
          robotId,
          deviceId: camera.deviceId,
          channel: camera.channel,
          quality: camera.quality
        })
      camera.session = mergeSession(camera, {
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
        await dispatch('connectLiveKit', { camera, refreshToken: false, connectionToken: response.operatorToken })
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
      console.log('API startIntercom', response)
    } catch (error) {
      camera.intercomActive = false
      // Message.error(this.errorMessage(error))
      // console.log('ERROR startIntercom', this.errorMessage(error))
    } finally {
      camera.intercomBusy = false
      commit('setCamera', camera)
    }
  },
  async hangupIntercom({ commit, state, dispatch }, camera) {
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
        camera.session = mergeSession(camera, response)
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
      console.log('API stopIntercom', response)
    } catch (error) {
      // Message.error(this.errorMessage(error))
    } finally {
      camera.intercomBusy = false
      commit('setCamera', camera)
    }
  },
  
  // 连接 LiveKit 会话
  async connectLiveKit({ commit, dispatch }, { camera, refreshToken, connectionToken }) {
    if (camera.connecting || !camera.session) return
    camera.connecting = true
    try {
      if (!camera.intercomActive && (refreshToken || !camera.session.viewerToken || !camera.session.livekitUrl)) {
        const token = await getViewerToken(camera.session.sessionId)
        camera.session = mergeSession(camera, {
          livekitUrl: token.livekitUrl,
          roomName: token.roomName,
          viewerToken: token.token
        })
      }
      const token = connectionToken || (camera.intercomActive ? camera.intercomToken : camera.session.viewerToken)
      const livekitUrl = window.location.protocol === 'https:' ? `wss://${window.location.host}/livekit` : camera.session.livekitUrl
      if (!token || !livekitUrl) return
      if (camera.room) {
        camera.disconnecting = true
        const oldRoom = camera.room
        camera.room = null
        await oldRoom.disconnect()
        camera.disconnecting = false
      }
      const room = new Room({})
      const sessionId = camera.session.sessionId
      room.on(RoomEvent.TrackSubscribed, (track) => {
        if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
        if (track.kind === 'video' && camera.watching) {
          console.log('TrackSubscribed', camera.key)
          track.attach(document.getElementById(state.prefixId + camera.key))
          camera.remoteVideoTrack = track
          camera.hasVideo = true
        } else if (track.kind === 'audio') {
          camera.remoteAudioTrack = track
          track.attach(document.getElementById(state.prefixId + camera.key + '-audio'))
          camera.hasAudio = true
        }
        commit('setCamera', camera)
      })
      room.on(RoomEvent.TrackUnsubscribed, (track) => {
        if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
        track.detach()
        if (track.kind === 'audio') camera.hasAudio = false
        if (track.kind === 'video') camera.hasVideo = false
        commit('setCamera', camera)
        console.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
        if (track.kind === 'video' && camera.watching && !isStoppedSession(camera, sessionId)) {
          dispatch('restartCamera', camera)
        }
      })
      room.on(RoomEvent.Disconnected, () => {
        if (camera.room !== room || camera.disconnecting) return
        camera.hasVideo = false
        commit('setCamera', camera)
        console.log('LiveKit Disconnected', camera.name)
        if (camera.watching && !isStoppedSession(camera, sessionId)) dispatch('restartCamera', camera)
      })
      camera.room = room
      await room.connect(livekitUrl, token)
      console.log('LiveKit connected', `${camera.name} ${camera.session.roomName}`)
    } catch (error) {
      camera.room = null
      camera.hasVideo = false
      console.log('ERROR LiveKit connect', error.message || '请求失败')
    } finally {
      camera.disconnecting = false
      camera.connecting = false
      commit('setCamera', camera)
    }
  },
  
  // 重启摄像头
  async restartCamera({ commit, state }, camera) {
    if (camera.stopping || camera.stopped || camera.restarting) return
    if (!camera.session || camera.session.status === 'CLOSED') return
    if (state.stoppedSessionIds.has(camera.session.sessionId)) return
    if (!['STREAMING', 'INTERRUPTED'].includes(camera.session.status)) return
    try {
      console.log('restartCamera', '-------------------------------------------------')
      camera.restarting = true
      const updated = await restartVideoSession(camera.session.sessionId)
      camera.session = mergeSession(camera, updated)
      camera.status = camera.session.status
      camera.viewerCount = camera.session.viewerCount
      console.log('API restartVideoSession', updated)
    } catch (error) {
      console.log('ERROR restartVideoSession', error.message || '请求失败')
    } finally {
      setTimeout(() => {
        camera.restarting = false
        commit('setCamera', camera)
      }, 5000)
    }
  },
  // 启动心跳定时器
  startHeartbeat({ dispatch }) {
    // dispatch('heartbeatViewers')
    state.heartbeatTimer = setInterval(() => {
      dispatch('heartbeatViewers')
    }, 5000)
  },
  // 切换激活摄像头
  async toggleCamera({ commit, state, dispatch }, { robot, camera }) {
    const key = camera.key;
    
    if (state.activeCameras[key]) {
      // 已激活，停止视频
      await dispatch('stopCamera', camera);
      commit('removeActiveCamera', key);
    } else {
      // 未激活，启动视频
      await dispatch('startCamera', { robot, camera });
      commit('setActiveCamera', { key, robot, camera });
    }
  },
  setSelectedRobotId({ commit, dispatch }, payload) {
    commit('setSelectedRobotId', payload);
    if (state.recordingMode) {
      state.selectedRecording = null
      commit('destroyRecordedHls')
      // commit('loadRecordings')
    }
    if (payload) dispatch('loadControlProfile', payload)
  },
  setPrefixId({ commit }, payload) {
    commit('setPrefixId', payload)
  },
  // 在“实时观看”和“录像回放”两套工作区之间切换。
  // 回放使用独立 HLS 实例，离开回放页必须销毁，否则会继续拉取分片。
  async toggleRecordings({ commit, state, dispatch }) {
    state.recordingMode = !state.recordingMode
    if (state.recordingMode) {
      await dispatch('loadRecordings')
    } else {
      commit('destroyRecordedHls')
      state.selectedRecording = null
    }
  },
  // 录像列表只拉 READY 状态，确保用户点开后一定能拿到可播放的 HLS 地址。
  async loadRecordings({ commit, state }) {
    state.recordingsLoading = true
    try {
      const params = {
        robotId: state.selectedRobotId,
        status: 'READY',
        page: 0,
        size: 20
      }
      if (state.recordingTab === 'manual') {
        params.sourceType = 'LIVEKIT_EGRESS'
      }
      const response = await getRecordings(params)
      const items = response.items || []
      state.recordings = state.recordingTab === 'patrol'
        ? items.filter(item => item.sourceType !== 'LIVEKIT_EGRESS')
        : items
    } catch (error) {
      // Message.error(messageError(error))
    } finally {
      state.recordingsLoading = false
    }
  },
  async loadControlProfile({ commit, state, dispatch }, robotId) {
    if (!robotId) return
    try {
      const profile = await getControlProfile(robotId)
      commit('setControlProfiles', { robotId, profile })
      const index = state.robots.findIndex(robot => robot.robotId === robotId)
      if (index >= 0) {
        state.robots.splice(index, 1, Object.assign({}, state.robots[index], {
          controlMode: profile.controlMode,
          stateSeq: profile.stateSeq
        }))
        dispatch('syncAudioStatesFromDevices', { robotId, devices: profile.devices })
      }
    } catch (error) {
      console.log('ERROR getControlProfile', error)
    }
  },

  async changeCameraQuality({ commit, state, dispatch }, camera) {
    if (!camera.session || !camera.watching || camera.stopped) return
    if (activeRecordingInProgress(camera)) {
      // Message.warning('请先停止录像后再切换清晰度')
      return
    }
    if (intercomInProgress(camera)) {
      // Message.warning('请先关闭对讲后再切换清晰度')
      return
    }
    const nextQuality = effectiveCameraQuality(camera)
    const currentQuality = camera.session.quality || effectiveCameraQuality(camera)
    if (nextQuality === currentQuality) return
    dispatch('switchCameraQuality', camera, nextQuality)
  },
  async switchCameraQuality({ commit, state, dispatch }, camera, nextQuality) {
    const oldSession = camera.session
    const oldRoom = camera.room
    if (!oldSession || camera.qualityChanging) return
    camera.qualityChanging = true
    let nextSession = null
    let nextRoom = null
    try {
      nextSession = await createVideoSession({
        robotId: camera.robotId,
        deviceId: camera.deviceId,
        channel: camera.channel,
        quality: nextQuality,
        reuse: true
      })
      nextRoom = await dispatch('connectReplacementLiveKit', camera, nextSession, oldRoom)
      camera.room = nextRoom
      camera.session = Object.assign({}, nextSession)
      camera.status = nextSession.status
      camera.viewerCount = nextSession.viewerCount
      camera.hasVideo = true
      const publication = firstVideoPublication(nextRoom)
      if (publication && publication.track) dispatch('startLatencyStats', camera, publication.track, nextRoom)
      camera.stopped = false
      camera.stopping = false
      state.stoppedSessionIds.delete(nextSession.sessionId)
      if (oldRoom) {
        camera.disconnecting = true
        Promise.resolve(oldRoom.disconnect()).catch(error => {
          console.log('ERROR disconnect old quality room')// this.errorMessage(error)
        })
        camera.disconnecting = false
      }
      state.stoppedSessionIds.add(oldSession.sessionId)
      stopVideoSession(oldSession.sessionId).catch(error => {
        console.log('ERROR stop old quality session')// this.errorMessage(error)
      })
      console.log('API switchCameraQuality', {
        from: oldSession.quality,
        to: nextQuality,
        oldSessionId: oldSession.sessionId,
        newSessionId: nextSession.sessionId
      })
    } catch (error) {
      if (nextRoom) {
        await Promise.resolve(nextRoom.disconnect()).catch(() => {})
      }
      if (nextSession && nextSession.sessionId) {
        stopVideoSession(nextSession.sessionId).catch(() => {})
      }
      Message.error(`清晰度切换失败：`)
      console.log('ERROR switchCameraQuality', error)// this.errorMessage(error)
    } finally {
      commit('resetCameraQualityChanging', camera)
    }
  },
  startLatencyStats({commit, state, dispatch}, camera, track, room = camera.room) {
    dispatch('stopLatencyStats', camera)
    camera.statsTrack = track
    camera.statsRoom = room
    camera.latencyMs = null
    camera.latencyLevel = 'unknown'
    const sample = async () => {
      if (camera.statsTrack !== track || camera.statsRoom !== room || (room && camera.room !== room)) return
      try {
        const stats = await dispatch('videoStatsReport', track, room)
        const latencyMs = dispatch('estimateLatencyMs', stats)
        if (camera.statsTrack !== track || camera.statsRoom !== room || (room && camera.room !== room)) return
        camera.latencyMs = latencyMs
        camera.latencyLevel = latencyLevel(camera)
      } catch (error) {
        if (camera.statsTrack === track && camera.statsRoom === room) {
          camera.latencyMs = null
          camera.latencyLevel = 'unknown'
        }
      }
    }
    sample()
    camera.statsTimer = setInterval(sample, 1000)
  },
  stopLatencyStats(camera) {
    if (camera.statsTimer) clearInterval(camera.statsTimer)
    camera.statsTimer = null
    camera.statsTrack = null
    camera.statsRoom = null
    camera.latencyMs = null
    camera.latencyLevel = 'unknown'
  },
  async videoStatsReport({}, track, room) {
    const peerStats = await dispatch('peerConnectionStats', room)
    if (peerStats) return peerStats
    if (track && typeof track.getRTCStatsReport === 'function') {
      return track.getRTCStatsReport()
    }
    if (track && track.receiver && typeof track.receiver.getStats === 'function') {
      return track.receiver.getStats()
    }
    return null
  },
  async peerConnectionStats({}, room) {
    const manager = room && room.engine && room.engine.pcManager
    const transports = [
      manager && manager.subscriber,
      manager && manager.publisher
    ]
    for (const transport of transports) {
      if (transport && typeof transport.getStats === 'function') {
        const stats = await transport.getStats()
        if (stats) return stats
      }
    }
    return null
  },
  estimateLatencyMs(stats) {
    if (!stats || typeof stats.forEach !== 'function') return null
    const pairRtt = this.selectedCandidatePairRtt(stats)
    if (Number.isFinite(pairRtt)) return Math.round(pairRtt * 1000)
    let receiverRtt = null
    let jitterDelay = null
    stats.forEach(report => {
      if (receiverRtt === null
          && (report.type === 'remote-inbound-rtp' || report.type === 'remote-outbound-rtp')
          && Number.isFinite(report.roundTripTime)) {
        receiverRtt = report.roundTripTime
      }
      if (jitterDelay === null
          && report.type === 'inbound-rtp'
          && report.kind === 'video'
          && report.jitterBufferEmittedCount > 0
          && Number.isFinite(report.jitterBufferDelay)) {
        jitterDelay = report.jitterBufferDelay / report.jitterBufferEmittedCount
      }
    })
    const seconds = receiverRtt !== null ? receiverRtt : jitterDelay
    return Number.isFinite(seconds) ? Math.round(seconds * 1000) : null
  },
  connectReplacementLiveKit({ commit, state, dispatch }, camera, session, oldRoom) {
    const livekitUrl = this.liveKitConnectionUrl(session.livekitUrl)
    const token = session.viewerToken
    if (!token || !livekitUrl) {
      return Promise.reject(new Error('缺少新清晰度 LiveKit 连接信息'))
    }
    const room = new Room()
    const sessionId = session.sessionId
    return new Promise((resolve, reject) => {
      let settled = false
      const timeout = setTimeout(() => fail(new Error('等待新清晰度视频流超时')), 15000)
      const done = () => {
        if (settled) return
        settled = true
        clearTimeout(timeout)
        resolve(room)
      }
      const fail = (error) => {
        if (settled) return
        settled = true
        clearTimeout(timeout)
        Promise.resolve(room.disconnect()).catch(() => {})
        reject(error)
      }
      room.on(RoomEvent.TrackSubscribed, (track, publication) => {
        if (track.kind !== 'video') return
        this.prepareReplacementVideo(camera, track, publication, oldRoom)
            .then(() => {
              camera.hasVideo = true
              console.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
              done()
            })
            .catch(fail)
      })
      room.on(RoomEvent.Disconnected, () => {
        fail(new Error('新清晰度 LiveKit 连接已断开'))
      })
      room.connect(livekitUrl, token)
          .then(() => {
            const publication = this.firstVideoPublication(room)
            if (!publication || !publication.track) return
            this.prepareReplacementVideo(camera, publication.track, publication, oldRoom)
                .then(() => {
                  camera.hasVideo = true
                  console.log('LiveKit TrackAttached', `${camera.name} ${publication.track.sid || publication.track.name}`)
                  done()
                })
                .catch(fail)
          })
          .catch(fail)
    })
  },
  async toggleLiveRecording({ commit, state, dispatch }, camera) {
    if (camera.recordingActive) {
      await dispatch('stopCameraRecording', camera)
    } else {
      await dispatch('startCameraRecording', camera)
    }
  },
  async startCameraRecording({ commit, state, dispatch }, camera) {
    if (!camera.session || camera.recordingBusy) return
    camera.recordingBusy = true
    try {
      const active = await getActiveLiveRecording(camera.session.sessionId)
      if (active) {
        camera.activeRecording = active
        camera.recordingActive = false
        Message.info('当前视频正在录制中')
        return
      }
      const recording = await dispatch('startLiveRecording', camera.session.sessionId)
      camera.activeRecording = recording
      camera.recordingActive = recording && recording.status === 'RECORDING'
      console.log('API startLiveRecording', recording)
      Message.success('已开始录像')
    } catch (error) {
      const data = error && error.response && error.response.data
      if (data && data.code === 'RECORDING_ALREADY_ACTIVE') {
        Message.info('当前视频正在录制中')
      } else {
        Message.error(error)
      }
    } finally {
      camera.recordingBusy = false
    }
  },
  async syncActiveRecording(camera) {
    if (!camera.session) return
    try {
      const recording = await getActiveLiveRecording(camera.session.sessionId)
      camera.activeRecording = recording
      if (!recording || recording.status !== 'RECORDING') {
        camera.recordingActive = false
      }
    } catch (_) {
      camera.activeRecording = null
      camera.recordingActive = false
    }
  },
  async stopCameraRecording(camera) {
    if (!camera.session || !camera.activeRecording || camera.recordingBusy) return
    camera.recordingBusy = true
    try {
      const recording = await stopLiveRecording(camera.session.sessionId, camera.activeRecording.recordingId)
      camera.activeRecording = recording
      camera.recordingActive = false
      console.log('API stopLiveRecording', recording)
      Message.success('录像已停止')
      if (this.recordingMode) await dispatch('loadRecordings')
    } catch (error) {
      Message.error(error)
    } finally {
      camera.recordingBusy = false
    }
  },
};

// 定义 getters 以便于从状态中获取数据
const getters = {
  // ============ Media 相关 getters ============
  getRobots: (state) => state.robots,
  getCameras: (state) => state.cameras,
  getCamerasRevision: (state) => state.camerasRevision,
  getSelectedRobotId: (state) => state.selectedRobotId,
  getSelectedRobot: (state) => state.robots.find(item => item.robotId === state.selectedRobotId) || {},
  getDisplayedCameras: (state, getters) => {
    const selectedRobot = getters.getSelectedRobot
    return selectedRobot
      ? selectedRobot.cameras.slice(0, 4).map(camera =>
        state.cameras[cameraKey(selectedRobot.robotId, camera)] || camera
      )
      : []
  },
  getMediaSocket: (state) => state.mediaSocket,
  getWsConnected: (state) => state.wsConnected,
  getActiveCameras: state => state.activeCameras,
  getControlProfiles: state => state.controlProfiles,
};

// 导出 WebSocket 模块
export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters,
};

// 导出工具函数供外部使用
export {
  toRobotState,
  cameraState,
  mergeSession,
  isStoppedSession,
  shouldAttachFromEvent
}
