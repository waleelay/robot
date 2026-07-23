import store from '@/store'
import { Message } from 'element-ui'
import { Room, RoomEvent, Track, VideoQuality } from 'livekit-client'
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
  getActiveLiveRecording,
  startLiveRecording,
  stopLiveRecording
} from '../../api/media'
import Vue from 'vue'
import { errorMessage } from '../../utils'

const DEVICE_STATE_CACHE_KEY = 'robot-media-device-state-cache-v2'
// 定义 WebSocket 模块的初始状态
const state = {
  websocket: null, // 存储 WebSocket 实例
  manualClosing: false, // 主动关闭标记（防止替换连接时触发重复重连）
  reconnectAttempts: 0, // 记录重连次数
  maxReconnectAttempts: 5, // 最大重连次数

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

  audioState: {},
  controlProfiles: {},
  controlProfileLoading: {},
  recordingMode: false,
  recordingsLoading: false,
  recordings: [],
  selectedRecording: null,
  selectedSnapshot: null,
  prefixId: '',
  recordingTab: 'manual',
  controlSessions: {},
  snapshotTime: 0,
  recordTime: 0,
  deviceStateCache: readDeviceStateCache(),
  incomingCalls: [],
  activeIncomingCall: null,
  callOperationPending: false
}

function cameraKey(robotId, camera) {
  return camera.key || `${robotId}-${camera.deviceId || camera.cameraId}`
}
function currentCameraState(camera) {
  return allCameras().find(item => item.key === camera.key) || camera
}

function toBasicCamera(camera, robotId, key) {
  return {
    cameraId: camera.cameraId || camera.deviceId,
    deviceId: camera.deviceId || camera.cameraId,
    groupType: camera.groupType,
    groupTypeName: groupTypeText(camera.groupType),
    name: camera.name,
    quality: camera.quality || 'sub',
    status: camera.status || 'offline',
    key,
    robotId
  }
}

function toBasicRobot(robot) {
  return Object.assign({}, robot, {
    cameras: (robot.cameras || []).map(camera => toBasicCamera(camera, robot.robotId, cameraKey(robot.robotId, camera)))
  })
}

function indexCameras(robots) {
  return (robots || []).reduce((result, robot) => {
    (robot.cameras || []).forEach(camera => {
      result[cameraKey(robot.robotId, camera)] = { ...camera, ...state.cameras[cameraKey(robot.robotId, camera)], robotId: robot.robotId }
    })
    return result
  }, {})
}

function readDeviceStateCache() {
  try {
    const raw = window.localStorage.getItem(DEVICE_STATE_CACHE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch (error) {
    return {}
  }
}

function cloneVehicleLightState(state) {
  const vehicleLightState = Object.assign({
      front: { mode: 'OFF', brightness: 50 },
      rear: { mode: 'OFF', brightness: 50 }
    }, store.state.websocketRobot.deviceStateCache?.vehicleLightState || {})
  const source = state || vehicleLightState
  return {
    front: Object.assign({}, source.front),
    rear: Object.assign({}, source.rear)
  }
}

function vehicleLightStatusPart(part) {
  const vehicleLightModeOptions = [
    { value: 'OFF', label: '常关', code: 0 },
    { value: 'ON', label: '常开', code: 1 },
    { value: 'BREATH', label: '呼吸', code: 2 },
    { value: 'CUSTOM', label: '自定义', code: 3 }
  ]
  if (!part || typeof part !== 'object') return null
  if (part.mode === undefined && part.modeCode === undefined) return null
  const mode = part.mode || vehicleLightModeOptions.find(item => item.code === part.modeCode)?.value || 'OFF'
  const brightness = part.brightness !== undefined ? part.brightness : part.customValue
  return {
    mode,
    brightness: mode === 'CUSTOM' ? Math.max(0, Math.min(100, Number(brightness) || 0)) : 0
  }
}

// 定义用于修改状态的 mutations，通过这个地方判断是什么类型的信息，然后页面调用不同的信息
const mutations = {
  // 设置 WebSocket 实例
  setWebSocket(state, websocket) {
    state.websocket = websocket
  },
  setManualClosing(state, value) {
    state.manualClosing = value
  },
  setLastConsumedFirePersonErrorId(state, id) {
    state.lastConsumedFirePersonErrorId = id
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
    state.cameras = { ...state.cameras, [camera.key]: camera }
    const robotIndex = state.robots.findIndex(item => item.robotId === camera.robotId)
    if (robotIndex < 0) return
    const robot = state.robots[robotIndex]
    const cameraIndex = (robot.cameras || []).findIndex(item =>
      (item.deviceId || item.cameraId) === (camera.deviceId || camera.cameraId)
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
    state.controlProfiles = { ...state.controlProfiles, [robotId]: profile }
  },
  SET_PROFILE_LOADING(state, { robotId, loading }) {
    state.controlProfileLoading = { ...state.controlProfileLoading, [robotId]: loading }
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
  incrementReconnectAttempts(state) {
    state.reconnectAttempts++
  },
  resetReconnectAttempts(state) {
    state.reconnectAttempts = 0
  },
  refreshWarning(state) {
    state.refreshWarningTime = +new Date()
  },
  setVehicleInfo: (state, data) => {
    state.vehicle = data
  },
  setActiveCamera(state, { key, robot, camera }) {
    Vue.set(state.activeCameras, key, { robot, camera })
  },
  removeActiveCamera(state, key) {
    Vue.delete(state.activeCameras, key)
  },
  SET_AUDIO_STATE(state, { key, volume, muted }) {
    state.audioState = { ...state.audioState, [key]: { volume, muted }}
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
  SET_SNAPSHOT_TIME(state, time) {
    state.snapshotTime = time
  },
  SET_RECORD_TIME(state, time) {
    state.recordTime = time
  },
  SET_DEVICE_STATE_CACHE(state, cache) {
    state.deviceStateCache = cache
  },
  SET_INCOMING_CALLS(state, calls) {
    state.incomingCalls = calls.map(withCallReceipt)
  },
  UPSERT_INCOMING_CALL(state, call) {
    const calls = state.incomingCalls.filter(item => item.callId !== call.callId)
    state.incomingCalls = [...calls, withCallReceipt(call)]
      .sort((left, right) => String(left.expiresAt).localeCompare(String(right.expiresAt)))
  },
  REMOVE_INCOMING_CALL(state, callId) {
    state.incomingCalls = state.incomingCalls.filter(item => item.callId !== callId)
  },
  SET_ACTIVE_INCOMING_CALL(state, call) {
    state.activeIncomingCall = call
  },
  SET_CALL_OPERATION_PENDING(state, pending) {
    state.callOperationPending = pending
  }
}

function withCallReceipt(call) {
  return { ...call, receivedAtEpochMillis: Date.now() }
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
    status: robot.status || 'offline',
    cameras: (robot.cameras || []).map(camera => Object.assign(
      {},
      camera,
      cameraState(robot.robotId, camera.deviceId || camera.cameraId, camera.name || camera.cameraId, camera.groupType),
      {
        cameraId: camera.cameraId || camera.deviceId,
        groupType: camera.groupType || 'body',
        groupTypeName: groupTypeText(camera.groupType),
        quality: camera.quality || 'sub',
        status: robot.status === 'online' ? (camera.status || '') : 'offline'
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
function cameraState(robotId, deviceId, name, groupType) {
  return {
    key: `${robotId}-${deviceId}`,
    robotId,
    deviceId,
    name,
    groupType: groupType || 'body',
    groupTypeName: groupTypeText(groupType),
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
  return store.state.dragVideo.splitType === 1 ? 'main' : 'sub'
}
function firstVideoPublication(room) {
  for (const participant of room.remoteParticipants.values()) {
    for (const publication of participant.trackPublications.values()) {
      if (publication.track && publication.track.kind === 'video') return publication
    }
  }
  return null
}

function detachRoomFromVideo(room, video) {
  if (!room || !video) return
  room.remoteParticipants.forEach(participant => {
    participant.trackPublications.forEach(publication => {
      if (publication.track && typeof publication.track.detach === 'function') {
        publication.track.detach(video)
      }
    })
  })
}
function prepareReplacementVideo(camera, track, publication, oldRoom, prefixId) {
  if (publication && typeof publication.setVideoQuality === 'function') {
    publication.setVideoQuality(VideoQuality.HIGH)
  }
  const warmup = document.createElement('video')
  warmup.autoplay = true
  warmup.muted = true
  warmup.playsInline = true
  Object.assign(warmup.style, {
    position: 'fixed',
    left: '-2px',
    top: '-2px',
    width: '1px',
    height: '1px',
    opacity: '0',
    pointerEvents: 'none'
  })
  document.body.appendChild(warmup)
  track.attach(warmup)
  return waitForVideoReady(warmup)
    .then(() => {
      const video = document.getElementById(prefixId + camera.key)
      if (video) {
        detachRoomFromVideo(oldRoom, video)
        track.attach(video)
      }
    })
    .finally(() => {
      if (typeof track.detach === 'function') track.detach(warmup)
      warmup.remove()
    })
}
function waitForVideoReady(video) {
  if (video.readyState >= 2 && video.videoWidth > 0) return Promise.resolve(true)
  video.play().catch(() => {})
  return new Promise((resolve) => {
    const onReady = () => {
      if (video.videoWidth > 0 || video.readyState >= 2) cleanup(resolve)
    }
    const timeout = setTimeout(() => cleanup(() => resolve(false)), 12000)
    const interval = setInterval(onReady, 250)
    const cleanup = (done) => {
      clearTimeout(timeout)
      clearInterval(interval)
      video.removeEventListener('loadedmetadata', onReady)
      video.removeEventListener('loadeddata', onReady)
      video.removeEventListener('canplay', onReady)
      video.removeEventListener('playing', onReady)
      video.removeEventListener('resize', onReady)
      done(true)
    }
    video.addEventListener('loadedmetadata', onReady)
    video.addEventListener('loadeddata', onReady)
    video.addEventListener('canplay', onReady)
    video.addEventListener('playing', onReady)
    video.addEventListener('resize', onReady)
  })
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

function getLiveKitUrl(livekitUrl) {
  return window.location.protocol === 'https:' ? `wss://${window.location.host}/livekit` : livekitUrl
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
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/control`
    const socket = new WebSocket(url)
    // const socket = new WebSocket('wss://192.168.124.115:8080/ws/control')
    socket.onopen = () => {
      commit('setWsConnected', true)
      dispatch('startHeartbeat')
      socket.send(JSON.stringify({
        type: 'video.intercom.call.query',
        requestId: `call-query-${Date.now()}`,
        payload: {}
      }))
      // console.log('Media WebSocket connected', url)
    }
    socket.onclose = () => {
      commit('setWsConnected', false)
      clearInterval(state.heartbeatTimer)
      // console.log('Media WebSocket closed', url)
    }
    socket.onmessage = (message) => {
      const event = JSON.parse(message.data)
      dispatch('syncRobotEvent', event)
      dispatch('websocketExtraData/syncRobot', event, { root: true })
      dispatch('syncSessionEvent', event)
      dispatch('syncControlEvent', event)
      dispatch('syncIntercomCallEvent', event)
    }
    commit('setMediaSocket', socket)
  },
  syncIntercomCallEvent({ commit, state, dispatch }, event) {
    if (!event) return
    if (event.type === 'video.intercom.call.list') {
      commit('SET_INCOMING_CALLS', Array.isArray(event.payload) ? event.payload : [])
      return
    }
    if (event.event === 'video.intercom.call.incoming' && event.data) {
      commit('UPSERT_INCOMING_CALL', event.data)
      return
    }
    if (event.event === 'video.intercom.call.status' && event.data) {
      if (event.data.status === 'RINGING') {
        commit('UPSERT_INCOMING_CALL', event.data)
      } else {
        commit('REMOVE_INCOMING_CALL', event.data.callId)
      }
      if (state.activeIncomingCall && state.activeIncomingCall.callId === event.data.callId &&
          ['ENDED', 'FAILED'].includes(event.data.status)) {
        dispatch('clearActiveIncomingCall', event.data)
      }
      return
    }
    if (event.type === 'video.intercom.call.accepted' && event.payload) {
      commit('SET_CALL_OPERATION_PENDING', false)
      commit('REMOVE_INCOMING_CALL', event.payload.call.callId)
      dispatch('activateIncomingIntercom', event.payload)
      return
    }
    if (event.type === 'video.intercom.call.rejected') {
      commit('SET_CALL_OPERATION_PENDING', false)
      commit('REMOVE_INCOMING_CALL', event.payload.callId)
      return
    }
    if (event.type === 'video.intercom.call.operation-failed') {
      commit('SET_CALL_OPERATION_PENDING', false)
      Message.error((event.payload && event.payload.message) || '来电操作失败')
    }
  },
  sendIntercomCallOperation({ commit, state }, { action, callId }) {
    if (!state.mediaSocket || state.mediaSocket.readyState !== WebSocket.OPEN) {
      Message.error('控制通道未连接')
      return
    }
    commit('SET_CALL_OPERATION_PENDING', true)
    state.mediaSocket.send(JSON.stringify({
      type: `video.intercom.call.${action}`,
      requestId: `call-${action}-${Date.now()}`,
      payload: { callId }
    }))
  },
  acceptIncomingCall({ dispatch }, callId) {
    dispatch('sendIntercomCallOperation', { action: 'accept', callId })
  },
  rejectIncomingCall({ dispatch }, callId) {
    dispatch('sendIntercomCallOperation', { action: 'reject', callId })
  },
  async activateIncomingIntercom({ commit, state, dispatch }, { call, intercom }) {
    let camera = allCameras().find(item => item.robotId === call.robotId && item.deviceId === call.deviceId)
    if (!camera) {
      camera = cameraState(call.robotId, call.deviceId, call.cameraName || call.deviceId, 'body')
    } else {
      camera = { ...camera }
    }
    commit('setSelectedRobotId', call.robotId)
    camera.intercomBusy = true
    try {
      await dispatch('applyIntercomResponse', { camera, response: intercom })
      commit('SET_ACTIVE_INCOMING_CALL', { ...call, cameraKey: camera.key, sessionId: intercom.sessionId })
    } catch (error) {
      camera.intercomActive = false
      camera.intercomStatus = 'IDLE'
      camera.intercomToken = null
      if (camera.room) {
        await Promise.resolve(camera.room.disconnect()).catch(() => {})
        camera.room = null
      }
      await stopIntercom(intercom.sessionId).catch(() => {})
      Message.error(errorMessage(error))
    } finally {
      camera.intercomBusy = false
      commit('setCamera', camera)
    }
  },
  async clearActiveIncomingCall({ commit, state }) {
    const active = state.activeIncomingCall
    if (active && active.cameraKey && state.cameras[active.cameraKey]) {
      const camera = { ...state.cameras[active.cameraKey] }
      if (camera.room) {
        await Promise.resolve(camera.room.disconnect()).catch(() => {})
      }
      camera.room = null
      camera.session = null
      camera.intercomActive = false
      camera.intercomStatus = 'IDLE'
      camera.intercomToken = null
      camera.hasAudio = false
      commit('setCamera', camera)
    }
    commit('SET_ACTIVE_INCOMING_CALL', null)
  },
  async hangupIncomingCall({ state, dispatch, commit }) {
    const active = state.activeIncomingCall
    if (!active) return
    const camera = state.cameras[active.cameraKey]
    if (camera) await dispatch('hangupIntercom', { ...camera })
    commit('SET_ACTIVE_INCOMING_CALL', null)
  },
  // 处理机器人在线/离线事件
  syncRobotEvent({ commit, state, dispatch }, event) {
    const data = event && (event.data || event.payload)
    if (!data || !data.robotId) return
    if (event.event !== 'robot.state' && event.type !== 'robot.state') return
    const incoming = toRobotState(data)
    dispatch('mergeControlProfileDevices', { robotId: incoming.robotId, devices: incoming.devices })
    dispatch('syncDeviceStatesFromDevices', { robotId: incoming.robotId, devices: incoming.devices })
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
          remoteVideoTrack: old.remoteVideoTrack || null
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
      console.error('控制命令被拒绝', event)
      Message.error((event.payload && event.payload.message) || '控制命令被拒绝')
    }
    return
  },
  syncAudioStatesFromDevices({ commit, state }, { robotId, devices, options = {}}) {
    if (!robotId || !Array.isArray(devices)) return
    devices
      .filter(device => ['SPEAKER', 'CLIENT_AUDIO', 'VOLUME_CONTROL', 'INTERCOM'].includes(device.deviceType))
      .forEach(device => {
        const status = device.status || device.runtimeStatus || {}
        if (status.volume === undefined && status.volumePercent === undefined && status.muted === undefined) return
        const key = `${robotId}:${device.deviceId}`
        const next = Object.assign({}, state.audioState[key] || {})
        const volume = status.volume === undefined ? status.volumePercent : status.volume
        if (volume !== undefined && !(options.preserveExisting && next.volume !== undefined)) {
          next.volume = volume
        }
        if (status.muted !== undefined && !(options.preserveExisting && next.muted !== undefined)) {
          next.muted = status.muted
        }
        commit('SET_AUDIO_STATE', { key, ...next })
      })
  },
  mergeControlProfileDevices({ commit, state, dispatch }, {robotId, devices}) {
    if (!robotId || !Array.isArray(devices)) return
    const profile = state.controlProfiles[robotId]
    if (!profile || !Array.isArray(profile.devices)) return
    const incoming = new Map(devices.map(device => [device.deviceId, device]))
    const merged = profile.devices.map(device => {
      const next = incoming.get(device.deviceId)
      if (!next) return device
      return Object.assign({}, device, next, {
        controlProfile: Object.assign({}, device.controlProfile || {}, next.controlProfile || {})
      })
    })
    commit('setControlProfiles', { robotId, profile: Object.assign({}, profile, { devices: merged }) })
  },
  syncDeviceStatesFromDevices({ commit, state, dispatch }, {robotId, devices, options = {}}) {
    if (!robotId || !Array.isArray(devices)) return
    dispatch('syncAudioStatesFromDevices', {robotId, devices, options})
    if (robotId !== state.selectedRobotId) return
    const obj = {}
    devices.forEach(device => {
      const status = device.status || device.runtimeStatus || {}
      if (device.deviceType === 'LAUNCHER' && status.safetySwitchEnabled !== undefined &&
          !(options.preserveExisting && state.deviceStateCache?.launcherSafety[device.deviceId] !== undefined)) {
        obj['launcherSafety'] = { ...state.deviceStateCache?.launcherSafety || {}, [device.deviceId]: !!status.safetySwitchEnabled }
      }
      if (device.deviceType === 'WARNING_LIGHT' && (status.powerOn !== undefined || status.enabled !== undefined) &&
          !(options.preserveExisting && state.deviceStateCache?.warningLightState[device.deviceId] !== undefined)) {
        obj['warningLightState'] = { ...state.deviceStateCache?.warningLightState || {}, [device.deviceId]: !!(status.powerOn === undefined ? status.enabled : status.powerOn) }
      }
      const ptzKey = `${robotId}:${device.deviceId}`
      if (device.deviceType === 'DUAL_LIGHT_PTZ' && status.autoRotateEnabled !== undefined &&
          !(options.preserveExisting && state.deviceStateCache?.ptzAutoRotateState[ptzKey] !== undefined)) {
        obj['ptzAutoRotateState'] = { ...state.deviceStateCache?.ptzAutoRotateState || {}, [ptzKey]: !!status.autoRotateEnabled }
      }
      if (device.deviceType === 'VEHICLE_LIGHT' && !(options.preserveExisting && state.deviceStateCache?.vehicleLightStateReady)) {
        const next = cloneVehicleLightState()
        const front = vehicleLightStatusPart(status.front)
        const rear = vehicleLightStatusPart(status.rear)
        if (front) next.front = front
        if (rear) next.rear = rear
        if (front || rear) {
          obj['vehicleLightState'] = next
          obj['confirmedVehicleLightState'] = cloneVehicleLightState(next)
          obj['vehicleLightStateReady'] = true
        }
      }
    })
    dispatch('persistDeviceStateCache', {
      ...state.deviceStateCache,
      audioState: state.audioState,
      ...obj
    })
  },
  async ensureControlSession({ commit, state }, { device, action }) {
    if (!device) throw new Error('未找到控制设备')
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
          // const intercomActive = !['IDLE', 'FAILED'].includes(camera.intercomStatus)
          // changed = camera.intercomActive !== intercomActive || changed
          // camera.intercomActive = intercomActive
        } catch (_) {}
      }
      if (changed) {
        commit('setCamera', camera)
      }
    }
  },
  // 启动摄像头
  async startCamera({ commit, state, dispatch }, { robot, camera }) {
    if (camera.recordingActive) {
      await dispatch('stopCameraRecording', camera)
    }
    console.log('%cstartCamera+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++', 'color: #0f0', state.prefixId + camera.key, robot.robotId, robot.status)
    const camera1 = { ...camera }
    if (robot.status !== 'online') return
    camera1.loading = true
    camera1.stopped = false
    camera1.stopping = false
    camera1.restarting = false
    camera1.watching = true
    try {
      const session = await createVideoSession({
        robotId: robot.robotId,
        deviceId: camera.deviceId,
        quality: camera.quality,
        reuse: true
      })
      camera1.session = mergeSession(camera1, session)
      camera1.status = camera1.session.status
      camera1.viewerCount = camera1.session.viewerCount
      state.stoppedSessionIds.delete(camera1.session.sessionId)
      // console.log('API createVideoSession', camera1.session)
      if (!camera1.room || !camera1.intercomActive) {
        await dispatch('connectLiveKit', { camera: camera1 })
      }
    } catch (error) {
      console.error('ERROR createVideoSession', error.message || '请求失败')
    } finally {
      camera1.loading = false
      commit('setCamera', camera1)
      commit('setActiveCamera', { key: camera1.key, robot, camera: camera1 })
    }
  },

  // 停止摄像头
  async stopCamera({ commit, state, dispatch }, data) {
    let camera = state.cameras[data.key]
    if (!camera) return
    camera = { ...camera }
    if (camera.recordingActive) {
      await dispatch('stopCameraRecording', camera)
    }
    if (!camera.session) return
    camera.loading = true
    camera.stopping = true
    camera.stopped = true
    camera.restarting = false
    camera.disconnecting = true
    try {
      const sessionId = camera.session.sessionId
      if (!camera.intercomActive) state.stoppedSessionIds.add(sessionId)
      console.log('%cstopCamera+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++', 'color: #f0f')
      const stopped = await stopVideoSession(sessionId)
      // console.log('API stopVideoSession', stopped)
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
      console.error('ERROR stopVideoSession', error.message || '请求失败')
    } finally {
      camera.disconnecting = false
      camera.stopping = false
      camera.loading = false
      commit('setCamera', camera)
      commit('removeActiveCamera', camera.key)
    }
  },

  async toggleIntercom({ commit, state, dispatch }, { robotId, camera }) {
    if (camera.intercomActive) {
      await dispatch('hangupIntercom', camera)
    } else {
      await dispatch('startIntercom', { robotId, camera })
    }
  },
  async startIntercom({ commit, state, dispatch }, { robotId, camera }) {
    camera.intercomBusy = true
    try {
      const response = camera.session
        ? await startSessionIntercom(camera.session.sessionId)
        : await startCameraIntercom({
          robotId,
          deviceId: camera.deviceId,
          quality: camera.quality
        })
      await dispatch('applyIntercomResponse', { camera, response })
      // console.log('API startIntercom', response)
    } catch (error) {
      camera.intercomActive = false
      console.error('ERROR startIntercom', errorMessage(error))
      Message.error(errorMessage(error))
    } finally {
      camera.intercomBusy = false
      commit('setCamera', camera)
    }
  },
  async applyIntercomResponse({ dispatch }, { camera, response }) {
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
    if (!camera.room) throw new Error('对讲媒体连接失败')
    if (camera.room) {
      await camera.room.localParticipant.setMicrophoneEnabled(true, {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }, {
        name: 'audio.operator.mic'
      })
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
      camera.remoteAudioTrack = null
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
      // console.log('API stopIntercom', response)
    } catch (error) {
      console.error('ERROR stopIntercom', errorMessage(error))
      Message.error(errorMessage(error))
    } finally {
      camera.intercomBusy = false
      commit('setCamera', camera)
    }
  },

  // 连接 LiveKit 会话
  async connectLiveKit({ commit, dispatch }, { camera, refreshToken, connectionToken }) {
    // console.log('connectLiveKit================================', camera.intercomActive)

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
      const livekitUrl = getLiveKitUrl(camera.session.livekitUrl)
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
          // console.log('TrackSubscribed', camera.key, state.prefixId + camera.key)
          track.attach(document.getElementById(state.prefixId + camera.key))
          camera.remoteVideoTrack = track
          camera.hasVideo = true
        } else if (track.kind === 'audio') {
          camera.remoteAudioTrack = track
          const audioId = state.prefixId + camera.key + '-audio'
          let audioElement = document.getElementById(audioId)
          if (!audioElement) {
            audioElement = track.attach()
            audioElement.id = audioId
            audioElement.style.display = 'none'
            document.body.appendChild(audioElement)
          } else {
            track.attach(audioElement)
          }
          camera.hasAudio = true
        }
        const { remoteVideoTrack, hasVideo, remoteAudioTrack, hasAudio } = camera
        commit('setCamera', { ...state.cameras?.[camera.key], remoteVideoTrack, hasVideo, remoteAudioTrack, hasAudio })
      })
      room.on(RoomEvent.TrackUnsubscribed, (track) => {
        if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
        track.detach()
        if (track.kind === 'audio') camera.hasAudio = false
        if (track.kind === 'video') camera.hasVideo = false
        const { hasVideo, hasAudio } = camera
        // console.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
        if (track.kind === 'video' && camera.watching && !isStoppedSession(camera, sessionId)) {
          dispatch('restartCamera', { ...camera, hasVideo, hasAudio })
        } else {
          commit('setCamera', { ...state.cameras?.[camera.key], hasVideo, hasAudio })
        }
      })
      room.on(RoomEvent.Disconnected, () => {
        if (camera.room !== room || camera.disconnecting) return
        const newC = Object.assign({}, state.cameras?.[camera.key] || camera)
        camera.hasVideo = false
        // console.log('LiveKit Disconnected', camera.name)
        if (camera.watching && !isStoppedSession(camera, sessionId)) {
          dispatch('restartCamera', { ...camera, hasVideo: false })
        } else {
          commit('setCamera', { ...state.cameras?.[camera.key], hasVideo: false })
        }
      })
      camera.room = room
      await room.connect(livekitUrl, token)
      // console.log('LiveKit connected', `${camera.name} ${camera.session.roomName}`)
    } catch (error) {
      camera.room = null
      camera.hasVideo = false
      console.error('ERROR LiveKit connect', error.message || '请求失败')
    } finally {
      camera.disconnecting = false
      camera.connecting = false
      commit('setCamera', camera)
    }
  },

  // 重启摄像头
  async restartCamera({ commit, dispatch, state }, camera) {
    if (camera.recordingActive) {
      await dispatch('stopCameraRecording', camera)
    }
    if (camera.stopping || camera.stopped || camera.restarting) return
    if (!camera.session || camera.session.status === 'CLOSED') return
    if (state.stoppedSessionIds.has(camera.session.sessionId)) return
    if (!['STREAMING', 'INTERRUPTED'].includes(camera.session.status)) return
    try {
      camera.restarting = true
      const updated = await restartVideoSession(camera.session.sessionId)
      camera.session = mergeSession(camera, updated)
      camera.status = camera.session.status
      camera.viewerCount = camera.session.viewerCount
      // console.log('API restartVideoSession', updated)
    } catch (error) {
      console.error('ERROR restartVideoSession', error.message || '请求失败')
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
    const key = camera.key

    if (state.activeCameras[key]) {
      // 已激活，停止视频
      await dispatch('stopCamera', camera)
      commit('removeActiveCamera', key)
    } else {
      // 未激活，启动视频
      await dispatch('startCamera', { robot, camera })
      commit('setActiveCamera', { key, robot, camera })
    }
  },
  setSelectedRobotId({ commit, dispatch }, payload) {
    commit('setSelectedRobotId', payload)
    if (state.recordingMode) {
      state.selectedRecording = null
      state.selectedSnapshot = null
      commit('destroyRecordedHls')
      // commit('loadRecordings')
    }
    if (payload) dispatch('loadControlProfile', payload)
  },
  setPrefixId({ commit }, payload) {
    commit('setPrefixId', payload)
  },
  async loadControlProfile({ commit, state, dispatch }, robotId) {
    if (!robotId) return
    commit('SET_PROFILE_LOADING', { robotId, loading: true })
    try {
      const profile = await getControlProfile(robotId)
      commit('setControlProfiles', { robotId, profile })
      const index = state.robots.findIndex(robot => robot.robotId === robotId)
      if (index >= 0) {
        state.robots.splice(index, 1, Object.assign({}, state.robots[index], {
          controlMode: profile.controlMode,
          stateSeq: profile.stateSeq
        }))
        dispatch('syncDeviceStatesFromDevices', { robotId, devices: profile.devices, options: {preserveExisting: true} })
      }
    } catch (error) {
      console.error('ERROR getControlProfile', error)
    } finally {
      commit('SET_PROFILE_LOADING', { robotId, loading: false })
    }
  },
  async changeCameraQuality({ commit, state, dispatch }, camera) {
    if (!camera.session || !camera.watching || camera.stopped) return
    if (activeRecordingInProgress(camera)) {
      Message.warning('请先停止录像后再切换清晰度')
      return
    }
    if (intercomInProgress(camera)) {
      Message.warning('请先关闭对讲后再切换清晰度')
      return
    }
    const nextQuality = effectiveCameraQuality(camera)
    const currentQuality = camera.session.quality || effectiveCameraQuality(camera)
    if (nextQuality === currentQuality) return
    dispatch('switchCameraQuality', { camera, quality: nextQuality })
  },
  async switchCameraQuality({ commit, state, dispatch }, { camera, quality }) {
    const oldSession = camera.session
    const oldRoom = camera.room
    if (!oldSession || camera.qualityChanging) return
    camera.qualityChanging = true
    let nextSession = null
    let nextRoom = null
    try {
      const createdSession = await createVideoSession({
        robotId: camera.robotId,
        deviceId: camera.deviceId,
        quality,
        reuse: true
      })
      const viewerToken = await getViewerToken(createdSession.sessionId)
      nextSession = Object.assign({}, createdSession, {
        livekitUrl: viewerToken.livekitUrl || createdSession.livekitUrl,
        roomName: viewerToken.roomName || createdSession.roomName,
        viewerToken: viewerToken.token,
        quality
      })
      try {
        nextRoom = await dispatch('connectReplacementLiveKit', { camera, session: nextSession, room: oldRoom })
      } catch (connectError) {
        console.error('WARN retry replacement quality session', errorMessage(connectError))
        const restartedSession = await restartVideoSession(nextSession.sessionId)
        const refreshedToken = await getViewerToken(restartedSession.sessionId)
        nextSession = Object.assign({}, restartedSession, {
          livekitUrl: refreshedToken.livekitUrl || restartedSession.livekitUrl,
          roomName: refreshedToken.roomName || restartedSession.roomName,
          viewerToken: refreshedToken.token,
          quality
        })
        nextRoom = await dispatch('connectReplacementLiveKit', { camera, session: nextSession, room: oldRoom })
      }

      camera.room = nextRoom
      camera.session = Object.assign({}, nextSession)
      camera.status = nextSession.status
      camera.viewerCount = nextSession.viewerCount
      camera.hasVideo = true
      const publication = firstVideoPublication(nextRoom)
      if (publication && publication.track) dispatch('startLatencyStats', { camera, track: publication.track, room: nextRoom })
      camera.stopped = false
      camera.stopping = false
      state.stoppedSessionIds.delete(nextSession.sessionId)
      if (oldRoom) {
        camera.disconnecting = true
        Promise.resolve(oldRoom.disconnect()).catch(error => {
          console.error('ERROR disconnect old quality room')
          Message.error(errorMessage(error))
        })
        camera.disconnecting = false
      }
      state.stoppedSessionIds.add(oldSession.sessionId)
      stopVideoSession(oldSession.sessionId).catch(error => {
        console.error('ERROR stop old quality session')
        Message.error(errorMessage(error))
      })
      // console.log('API switchCameraQuality', {
      //   from: oldSession.quality,
      //   to: quality,
      //   oldSessionId: oldSession.sessionId,
      //   newSessionId: nextSession.sessionId
      // })
    } catch (error) {
      if (nextRoom) {
        await Promise.resolve(nextRoom.disconnect()).catch(() => {})
      }
      if (nextSession && nextSession.sessionId) {
        stopVideoSession(nextSession.sessionId).catch(() => {})
      }
      Message.error(`清晰度切换失败：`, errorMessage(error))
      console.error('ERROR switchCameraQuality', errorMessage(error))
    } finally {
      dispatch('resetQualityChanging', camera)
    }
  },
  resetQualityChanging({ commit }, camera) {
    camera.disconnecting = false
    camera.qualityChanging = false
    const current = currentCameraState(camera)
    current.disconnecting = false
    current.qualityChanging = false
    commit('setCamera', current)
  },
  startLatencyStats({ commit, state, dispatch }, { camera, track, room = camera.room }) {
    dispatch('stopLatencyStats', camera)
    camera.statsTrack = track
    camera.statsRoom = room
    camera.latencyMs = null
    camera.latencyLevel = 'unknown'
    const sample = async() => {
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
    commit('setCamera', camera)
  },
  stopLatencyStats(camera) {
    if (camera.statsTimer) clearInterval(camera.statsTimer)
    camera.statsTimer = null
    camera.statsTrack = null
    camera.statsRoom = null
    camera.latencyMs = null
    camera.latencyLevel = 'unknown'
  },
  async videoStatsReport({ dispatch }, track, room) {
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
    const pairRtt = selectedCandidatePairRtt(stats)
    if (Number.isFinite(pairRtt)) return Math.round(pairRtt * 1000)
    let receiverRtt = null
    let jitterDelay = null
    stats.forEach(report => {
      if (receiverRtt === null &&
          (report.type === 'remote-inbound-rtp' || report.type === 'remote-outbound-rtp') &&
          Number.isFinite(report.roundTripTime)) {
        receiverRtt = report.roundTripTime
      }
      if (jitterDelay === null &&
          report.type === 'inbound-rtp' &&
          report.kind === 'video' &&
          report.jitterBufferEmittedCount > 0 &&
          Number.isFinite(report.jitterBufferDelay)) {
        jitterDelay = report.jitterBufferDelay / report.jitterBufferEmittedCount
      }
    })
    const seconds = receiverRtt !== null ? receiverRtt : jitterDelay
    return Number.isFinite(seconds) ? Math.round(seconds * 1000) : null
  },
  connectReplacementLiveKit({ commit, state, dispatch }, { camera, session, oldRoom }) {
    const livekitUrl = getLiveKitUrl(session.livekitUrl)
    const token = session.viewerToken
    if (!token || !livekitUrl) {
      return Promise.reject(new Error('缺少新清晰度 LiveKit 连接信息'))
    }
    const room = new Room()
    const sessionId = session.sessionId
    return new Promise((resolve, reject) => {
      let settled = false
      const timeout = setTimeout(() => fail(new Error('等待新清晰度视频流超时')), 30000)
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
        prepareReplacementVideo(camera, track, publication, oldRoom, state.prefixId)
          .then(() => {
            camera.hasVideo = true
            commit('setCamera', { ...state.cameras?.[camera.key], hasVideo: true })
            // console.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
            done()
          })
          .catch(fail)
      })
      room.on(RoomEvent.Disconnected, () => {
        fail(new Error('新清晰度 LiveKit 连接已断开'))
      })
      room.connect(livekitUrl, token)
        .then(() => {
          const publication = firstVideoPublication(room)
          if (!publication || !publication.track) return
          prepareReplacementVideo(camera, publication.track, publication, oldRoom)
            .then(() => {
              camera.hasVideo = true
              commit('setCamera', { ...state.cameras?.[camera.key], hasVideo: true })
              // console.log('LiveKit TrackAttached', `${camera.name} ${publication.track.sid || publication.track.name}`)
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
      // console.log('startCameraRecording', camera.key, active)
      if (active) {
        camera.activeRecording = active
        camera.recordingActive = false
        Message.info('当前视频正在录制中')
        return
      }
      const recording = await startLiveRecording(camera.session.sessionId)
      camera.activeRecording = recording
      camera.recordingActive = recording && recording.status === 'UPLOADING'
      // console.log('API startLiveRecording', recording)
      Message.success('已开始录像')
    } catch (error) {
      const data = error && error.response && error.response.data
      if (data && data.code === 'RECORDING_ALREADY_ACTIVE') {
        console.error('startCameraRecording', camera.key, data.code)
        Message.info('当前视频正在录制中')
      } else {
        console.error('开始录像失败', error)
        Message.error(error)
      }
    } finally {
      camera.recordingBusy = false
      commit('setCamera', camera)
    }
  },
  async syncActiveRecording({ commit, state, dispatch }, camera) {
    if (!camera.session) return
    try {
      const recording = await getActiveLiveRecording(camera.session.sessionId)
      camera.activeRecording = recording
      if (!recording || recording.status !== 'UPLOADING') {
        camera.recordingActive = false
      }
    } catch (_) {
      camera.activeRecording = null
      camera.recordingActive = false
    } finally {
      commit('setCamera', camera)
    }
  },
  async stopCameraRecording({ commit, state, dispatch }, camera) {
    if (!camera.session || !camera.activeRecording || camera.recordingBusy) return
    camera.recordingBusy = true
    try {
      const recording = await stopLiveRecording(camera.session.sessionId, camera.activeRecording.fileId)
      camera.activeRecording = recording
      camera.recordingActive = false
      // console.log('API stopLiveRecording', recording)
      Message.success('录像已停止')
      dispatch('setRecordTime', new Date().toISOString())
      // TODO 获取新数据
      // if (this.recordingMode) await dispatch('loadRecordings')
    } catch (error) {
      console.error('停止录像失败', error)
      Message.error(error)
    } finally {
      camera.recordingBusy = false
      commit('setCamera', camera)
    }
  },
  setSnapshotTime({ commit }, time) {
    commit('SET_SNAPSHOT_TIME', time)
  },
  setRecordTime({ commit }, time) {
    commit('SET_RECORD_TIME', time)
  },
  setAudioState({ commit }, { key, volume, muted }) {
    commit('SET_AUDIO_STATE', { key, volume, muted })
  },
  persistDeviceStateCache({ commit, state }, payload) {
    const cache = {
      audioState: payload.audioState,
      launcherSafety: payload.launcherSafety,
      netGunSafety: payload.netGunSafety,
      warningLightState: payload.warningLightState,
      ptzAutoRotateState: payload.ptzAutoRotateState
    }
    if (payload.vehicleLightStateReady) {
      cache.vehicleLightState = payload.vehicleLightState
    }
    commit('SET_DEVICE_STATE_CACHE', cache)
    try {
      window.localStorage.setItem(DEVICE_STATE_CACHE_KEY, JSON.stringify(cache))
    } catch (error) {
      console.error('WARN persistDeviceStateCache', errorMessage(error))
    }
  }
}

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
  getControlProfiles: state => state.controlProfiles
}

// 导出 WebSocket 模块
export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
}

// 导出工具函数供外部使用
export {
  toRobotState,
  cameraState,
  mergeSession,
  isStoppedSession,
  shouldAttachFromEvent
}
