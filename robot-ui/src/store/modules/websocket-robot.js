import store from '@/store'
import { Message } from 'element-ui'
import { Room, RoomEvent, Track, VideoQuality } from 'livekit-client'
import {
  createSnapshot,
  createVideoSession,
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
  acquireControl,
  getActiveLiveRecording,
  startLiveRecording,
  stopLiveRecording,
  mediaClientId
} from '../../api/media'
import Vue from 'vue'
import { errorMessage } from '../../utils'
import { bearerToken } from '@/auth'

const DEVICE_STATE_CACHE_KEY = 'robot-media-device-state-cache-v2'
const controlProfileInflight = {}
// 定义 WebSocket 模块的初始状态
const state = {
  websocket: null, // 存储 WebSocket 实例
  manualClosing: false, // 主动关闭标记（防止替换连接时触发重复重连）
  reconnectAttempts: 0, // 记录重连次数
  maxReconnectAttempts: 5, // 最大重连次数

  // ============ Media 相关状态 ============
  wsConnected: false, // 媒体服务 WebSocket 连接状态
  mediaSocket: null, // 媒体服务 WebSocket 实例
  mediaReconnectTimer: null,
  robots: [], // 机器人列表
  cameras: {}, // 全局摄像头索引 { [cameraKey]: camera }
  camerasRevision: 0,
  heartbeatTimer: null, // 心跳定时器
  stoppedSessionIds: new Set(), // 已停止的会话ID集合
  selectedRobotId: '', // 当前选中的机器人ID
  controlCenterReturnTo: null, // 进入控制中心前的页面，返回时回到该界面
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
  return camera.key || `${robotId}-${camera.deviceId}-${camera.cameraId}`
}
function currentCameraState(camera) {
  return allCameras().find(item => item.key === camera.key) || camera
}

function isIntercomAlreadyStoppedError(error) {
  const data = error && error.response && error.response.data
  return Boolean(error && error.response && error.response.status === 409 &&
    data && data.code === 'INVALID_STATE' &&
    data.message === '当前用户未持有对讲权限')
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

function mergeSnapshotWithLive(snapshot, existing, storeState) {
  const fromSnapshot = toRobotState(snapshot)
  if (!isFixedCameraEquipment(snapshot, snapshot.cameras)
      && !(Array.isArray(snapshot.cameras) && snapshot.cameras.length)) {
    fromSnapshot.cameras = listStoredCameras(storeState, fromSnapshot.robotId)
  }
  if (!existing) return fromSnapshot
  return Object.assign({}, fromSnapshot, {
    status: existing.status,
    cameras: (existing.cameras && existing.cameras.length) ? existing.cameras : fromSnapshot.cameras,
    battery: existing.battery !== undefined && existing.battery !== null ? existing.battery : fromSnapshot.battery,
    controlMode: existing.controlMode || fromSnapshot.controlMode,
    controlModeName: existing.controlModeName || fromSnapshot.controlModeName,
    devices: existing.devices || fromSnapshot.devices,
    fault: existing.fault !== undefined ? existing.fault : fromSnapshot.fault,
    speed: existing.speed !== undefined && existing.speed !== null ? existing.speed : fromSnapshot.speed
  })
}

function listStoredCameras(storeState, robotId) {
  const existing = (storeState.robots || []).find(item => String(item.robotId) === String(robotId))
  if (existing && Array.isArray(existing.cameras) && existing.cameras.length) {
    return existing.cameras
  }
  return Object.values(storeState.cameras || {}).filter(item => String(item && item.robotId) === String(robotId))
}

function mergeCamerasIndex(existing, robots) {
  const robotIds = new Set((robots || []).map(item => String(item.robotId)))
  const next = {}
  Object.keys(existing || {}).forEach(key => {
    const camera = existing[key]
    if (camera && robotIds.has(String(camera.robotId))) {
      next[key] = camera
    }
  })
  ;(robots || []).forEach(robot => {
    (robot.cameras || []).forEach(camera => {
      const key = camera.key || cameraKey(robot.robotId, camera)
      next[key] = { ...camera, ...(next[key] || {}), robotId: robot.robotId, key }
    })
  })
  return next
}

function replaceRobotCamerasInIndex(existing, robotId, cameras) {
  const next = { ...(existing || {}) }
  Object.keys(next).forEach(key => {
    if (String(next[key]?.robotId) !== String(robotId)) return
    if (!(cameras || []).some(item => item && item.key === key)) {
      delete next[key]
    }
  })
  ;(cameras || []).forEach(camera => {
    if (camera && camera.key) next[camera.key] = camera
  })
  return next
}

function readDeviceStateCache() {
  try {
    const raw = window.localStorage.getItem(DEVICE_STATE_CACHE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch (error) {
    return {}
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
  setControlCenterReturnTo(state, path) {
    state.controlCenterReturnTo = path || null
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
  UPDATE_ACTIVE_INCOMING_CALL(state, update) {
    if (!state.activeIncomingCall) return
    state.activeIncomingCall = { ...state.activeIncomingCall, ...update }
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
  const controlMode = robot.controlMode === '手动模式' ? '手动模式' : '导航模式'
  return Object.assign({}, robot, {
    name: robot.name || robot.robotId,
    type: robot.type || '机器人',
    controlMode,
    controlModeName: robot.controlModeName || controlMode,
    stateSeq: robot.stateSeq || 0,
    status: robot.status || 'offline',
    cameras: (robot.cameras || []).map(camera => Object.assign(
      {},
      camera,
      cameraState(robot.robotId, camera.deviceId, camera.cameraId || camera.deviceId, camera.name || camera.cameraId, camera.groupType),
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
    single_gimbal: '单光云台',
    dual_gimbal: '双光云台',
    arm: '机械臂',
    fixed_camera: '固定摄像头'
  }[groupType] || groupType || '未分组'
}

function isFixedCameraEquipment(robot = {}, cameras = []) {
  const markers = [robot.sourceType, robot.typeCode, robot.equipmentType, robot.type]
  if (markers.some(value => value === 'FIXED_CAMERA' || value === '固定摄像头')) {
    return true
  }
  return (cameras || []).some(camera =>
    camera.groupType === 'fixed_camera' || camera.sourceType === 'FIXED_CAMERA'
  )
}

function resolveEquipmentRecord(state, rootState, robotId) {
  const id = String(robotId)
  const fromStore = (state.robots || []).find(item => String(item.robotId) === id) || {}
  const baseInfo = (rootState && rootState.websocketExtraData && rootState.websocketExtraData.robotBaseInfo) || {}
  const fromBase = baseInfo[robotId] || baseInfo[id] || {}
  const cameras = fromStore.cameras && fromStore.cameras.length
    ? fromStore.cameras
    : Object.values(state.cameras || {}).filter(item => String(item.robotId) === id)
  return { fromStore, fromBase, cameras }
}

// 用于将相机数据转换为状态对象
function cameraState(robotId, deviceId, cameraId, name, groupType) {
  return {
    key: `${robotId}-${deviceId}-${cameraId}`,
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
    remoteAudioElement: null,
    remoteVideoTrack: null,
    attachTargets: {}
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

// 异步后提交 camera 时合并 store，避免覆盖 TrackSubscribed 写入的 track 等字段
function mergeCameraFromStore(state, camera, patch) {
  if (!camera || !camera.key) return { ...camera, ...patch }
  return {
    ...camera,
    ...(state.cameras[camera.key] || {}),
    ...patch
  }
}

function mergeAttachTargets(...lists) {
  return Object.assign({}, ...lists.filter(item => item && typeof item === 'object'))
}

function uniqueAttachPrefixes(camera, state) {
  const fromTargets = camera && camera.attachTargets ? Object.values(camera.attachTargets) : []
  const list = fromTargets.length ? fromTargets : (state.prefixId ? [state.prefixId] : [])
  return [...new Set(list.filter(Boolean))]
}

function liveKitRoomReusable(camera) {
  if (!camera || !camera.session || camera.stopping || camera.stopped || camera.disconnecting) return false
  if (camera.connecting && !camera.room) return true
  if (!camera.room) return false
  const roomState = camera.room.state
  return !roomState || roomState === 'connected' || roomState === 'connecting'
    || roomState === 'reconnecting' || roomState === 'signalReconnecting'
}

const ATTACH_RETRY_MAX_FRAMES = 60
const attachRetryHandles = new Map()

function attachRetryKey(cameraKey, prefixId, kind) {
  return `${cameraKey}|${prefixId}|${kind}`
}

function cancelAttachRetry(cameraKey, prefixId, kind) {
  if (!cameraKey) return
  const keys = []
  attachRetryHandles.forEach((_, key) => {
    if (!key.startsWith(`${cameraKey}|`)) return
    if (prefixId && kind && key !== attachRetryKey(cameraKey, prefixId, kind)) return
    if (prefixId && !kind && !key.startsWith(`${cameraKey}|${prefixId}|`)) return
    if (!prefixId && kind && !key.endsWith(`|${kind}`)) return
    keys.push(key)
  })
  keys.forEach(key => {
    const handle = attachRetryHandles.get(key)
    if (handle) cancelAnimationFrame(handle)
    attachRetryHandles.delete(key)
  })
}

function cameraStillWantsAttach(camera, track, kind) {
  const latest = state.cameras[camera && camera.key]
  if (!latest || latest.stopping || latest.stopped) return false
  const currentTrack = kind === 'audio' ? latest.remoteAudioTrack : latest.remoteVideoTrack
  return currentTrack === track
}

function attachTrackToElement(track, elId, play) {
  if (!track || typeof track.attach !== 'function' || !elId) return false
  const el = document.getElementById(elId)
  if (!el) return false
  track.attach(el)
  const userPaused = !!(el.dataset && el.dataset.userPaused === '1')
  if (userPaused) {
    if (track.mediaStreamTrack) track.mediaStreamTrack.enabled = false
    if (el.srcObject && typeof el.srcObject.getVideoTracks === 'function') {
      el.srcObject.getVideoTracks().forEach(item => { item.enabled = false })
    }
    if (typeof el.pause === 'function') el.pause()
    return true
  }
  if (track.mediaStreamTrack) track.mediaStreamTrack.enabled = true
  if (play && typeof el.play === 'function') el.play().catch(() => {})
  return true
}

function scheduleAttachRetry(track, camera, prefixId, kind) {
  if (!track || !camera || !camera.key || !prefixId) return
  const key = attachRetryKey(camera.key, prefixId, kind)
  const existing = attachRetryHandles.get(key)
  if (existing) cancelAnimationFrame(existing)
  let frames = 0
  const tick = () => {
    attachRetryHandles.delete(key)
    if (!cameraStillWantsAttach(camera, track, kind)) return
    const elId = kind === 'audio' ? prefixId + camera.key + '-audio' : prefixId + camera.key
    if (attachTrackToElement(track, elId, kind !== 'audio')) return
    frames += 1
    if (frames >= ATTACH_RETRY_MAX_FRAMES) return
    attachRetryHandles.set(key, requestAnimationFrame(tick))
  }
  attachRetryHandles.set(key, requestAnimationFrame(tick))
}

function attachCameraMedia(camera, prefixId) {
  if (!camera || !camera.key || !prefixId) return
  if (camera.remoteVideoTrack && typeof camera.remoteVideoTrack.attach === 'function') {
    const attached = attachTrackToElement(camera.remoteVideoTrack, prefixId + camera.key, true)
    if (!attached) scheduleAttachRetry(camera.remoteVideoTrack, camera, prefixId, 'video')
  }
  if (camera.remoteAudioTrack && typeof camera.remoteAudioTrack.attach === 'function') {
    const attached = attachTrackToElement(camera.remoteAudioTrack, prefixId + camera.key + '-audio', false)
    if (!attached) scheduleAttachRetry(camera.remoteAudioTrack, camera, prefixId, 'audio')
  }
}

function detachCameraMedia(camera, prefixId) {
  if (!camera || !camera.key || !prefixId) return
  cancelAttachRetry(camera.key, prefixId)
  const video = document.getElementById(prefixId + camera.key)
  if (camera.remoteVideoTrack && video && typeof camera.remoteVideoTrack.detach === 'function') {
    camera.remoteVideoTrack.detach(video)
  }
  if (video) video.srcObject = null
  const audio = document.getElementById(prefixId + camera.key + '-audio')
  if (camera.remoteAudioTrack && audio && typeof camera.remoteAudioTrack.detach === 'function') {
    camera.remoteAudioTrack.detach(audio)
  }
}

function attachTrackToCameraTargets(track, camera, storeState, kind) {
  if (!track || typeof track.attach !== 'function') return
  const prefixes = uniqueAttachPrefixes(camera, storeState)
  for (const prefixId of prefixes) {
    const elId = kind === 'audio' ? prefixId + camera.key + '-audio' : prefixId + camera.key
    const attached = attachTrackToElement(track, elId, kind !== 'audio')
    if (!attached) scheduleAttachRetry(track, camera, prefixId, kind)
  }
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
function prepareReplacementVideo(camera, track, publication, oldRoom) {
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
      const prefixes = uniqueAttachPrefixes(camera, state)
      prefixes.forEach(prefixId => {
        const video = document.getElementById(prefixId + camera.key)
        if (video) detachRoomFromVideo(oldRoom, video)
      })
      attachTrackToCameraTargets(track, camera, state, 'video')
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
  async loadRobots({ commit, state }, payload) {
    const robots = payload
    if (robots && robots.length) {
      const fullRobots = robots.map(robot => {
        const existing = (state.robots || []).find(item => String(item.robotId) === String(robot.robotId))
        return mergeSnapshotWithLive(robot, existing, state)
      })
      const nextRobots = fullRobots.map(toBasicRobot)
      const ids = new Set(nextRobots.map(item => String(item.robotId)))
      ;(state.robots || []).forEach(robot => {
        if (!ids.has(String(robot.robotId))) nextRobots.push(robot)
      })
      commit('setCameras', mergeCamerasIndex(state.cameras, nextRobots))
      commit('setRobots', nextRobots)
    }
    // if (!state.robots.find(robot => robot.robotId === state.selectedRobotId)) {
    //   commit('setSelectedRobotId', state.robots[0]?.robotId || '')
    // }
    // await dispatch('loadControlProfile', robots[0].robotId)
  },

  // 连接媒体服务 WebSocket
  async connectMediaWebSocket({ commit, state, dispatch }) {
    if (state.mediaSocket &&
        [WebSocket.CONNECTING, WebSocket.OPEN].includes(state.mediaSocket.readyState)) {
      return
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/bigscreen`
    const socketUrl = new URL(url, window.location.href)
    socketUrl.searchParams.set('clientId', mediaClientId)
    const accessToken = await bearerToken()
    if (accessToken) {
      socketUrl.searchParams.set('access_token', accessToken)
    }
    const socket = new WebSocket(socketUrl.toString())
    socket.onopen = () => {
      if (state.mediaReconnectTimer) {
        clearTimeout(state.mediaReconnectTimer)
        state.mediaReconnectTimer = null
      }
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
      if (state.mediaSocket === socket) {
        commit('setMediaSocket', null)
      }
      if (!state.mediaReconnectTimer && window.location.pathname.startsWith('/bi/')) {
        state.mediaReconnectTimer = setTimeout(() => {
          state.mediaReconnectTimer = null
          dispatch('connectMediaWebSocket')
        }, 2000)
      }
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
      camera = cameraState(call.robotId, call.deviceId, call.cameraId || call.deviceId, call.cameraName || call.deviceId, 'body')
    } else {
      camera = { ...camera }
    }
    commit('setSelectedRobotId', call.robotId)
    camera.intercomBusy = true
    try {
      await dispatch('applyIntercomResponse', { camera, response: intercom })
      // LiveKit may deliver an existing video track while connectLiveKit is awaiting room.connect().
      // Keep that newer store state instead of overwriting it with the pre-connect camera snapshot.
      camera = { ...camera, ...(state.cameras[camera.key] || {}) }
      commit('SET_ACTIVE_INCOMING_CALL', {
        ...call,
        cameraKey: camera.key,
        sessionId: intercom.sessionId,
        connectedAtEpochMillis: Date.now(),
        micMuted: false,
        speakerMuted: false,
        videoEnabled: false,
        videoLoading: false
      })
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
      commit('setCamera', mergeCameraFromStore(state, camera, {
        intercomBusy: false,
        intercomActive: camera.intercomActive,
        intercomStatus: camera.intercomStatus,
        intercomToken: camera.intercomToken,
        room: camera.room
      }))
    }
  },
  async clearActiveIncomingCall({ commit, state }) {
    const active = state.activeIncomingCall
    if (active && active.cameraKey && state.cameras[active.cameraKey]) {
      const camera = { ...state.cameras[active.cameraKey] }
      const audioElement = camera.remoteAudioElement
      const keepWatching = Boolean(state.activeCameras[active.cameraKey] && camera.watching)
      if (camera.room) {
        await Promise.resolve(camera.room.localParticipant.setMicrophoneEnabled(false)).catch(() => {})
      }
      if (!keepWatching && camera.watching && camera.session) {
        try {
          await stopVideoSession(camera.session.sessionId)
          state.stoppedSessionIds.add(camera.session.sessionId)
        } catch (_) {}
      }
      if (!keepWatching && camera.room) {
        await Promise.resolve(camera.room.disconnect()).catch(() => {})
      }
      if (camera.remoteAudioTrack && typeof camera.remoteAudioTrack.detach === 'function') {
        camera.remoteAudioTrack.detach()
      }
      if (audioElement && typeof audioElement.remove === 'function') audioElement.remove()
      camera.intercomActive = false
      camera.intercomStatus = 'IDLE'
      camera.intercomToken = null
      camera.hasAudio = false
      camera.remoteAudioTrack = null
      camera.remoteAudioElement = null
      if (!keepWatching) {
        camera.room = null
        camera.session = null
        camera.hasVideo = false
        camera.watching = false
        camera.remoteVideoTrack = null
      }
      commit('setCamera', camera)
    }
    commit('SET_ACTIVE_INCOMING_CALL', null)
  },
  async hangupIncomingCall({ state, dispatch, commit }) {
    const active = state.activeIncomingCall
    if (!active) return
    const camera = state.cameras[active.cameraKey]
    if (camera) {
      const stopped = await dispatch('hangupIntercom', {
        ...camera,
        session: {
          ...(camera.session || {}),
          sessionId: active.sessionId
        }
      })
      if (!stopped) return
      const current = state.cameras[active.cameraKey]
      const keepWatching = Boolean(state.activeCameras[active.cameraKey])
      if (current && current.watching && !keepWatching) {
        await dispatch('stopCamera', current)
      }
    } else {
      try {
        await stopIntercom(active.sessionId)
      } catch (error) {
        if (!isIntercomAlreadyStoppedError(error)) {
          console.error('ERROR stopIntercom', errorMessage(error))
          Message.error('挂断失败，请稍后重试')
          return
        }
      }
    }
    commit('SET_ACTIVE_INCOMING_CALL', null)
  },
  async toggleIncomingCallMicrophone({ commit, state }) {
    const active = state.activeIncomingCall
    const camera = active && state.cameras[active.cameraKey]
    if (!active || !camera || !camera.room) return
    const muted = !active.micMuted
    const participant = camera.room.localParticipant
    const publication = participant.getTrackPublication(Track.Source.Microphone)
    try {
      if (publication) {
        await (muted ? publication.mute() : publication.unmute())
      } else if (!muted) {
        await participant.setMicrophoneEnabled(true, {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }, {
          name: 'audio.operator.mic'
        })
      }
      commit('UPDATE_ACTIVE_INCOMING_CALL', { micMuted: muted })
    } catch (error) {
      Message.error(muted ? '麦克风静音失败' : '麦克风恢复失败')
    }
  },
  toggleIncomingCallSpeaker({ commit, state }) {
    const active = state.activeIncomingCall
    const camera = active && state.cameras[active.cameraKey]
    if (!active || !camera) return
    const muted = !active.speakerMuted
    const audioElement = camera.remoteAudioElement ||
      document.querySelector(`audio[data-intercom-session-id="${active.sessionId}"]`)
    if (audioElement) audioElement.muted = muted
    commit('UPDATE_ACTIVE_INCOMING_CALL', { speakerMuted: muted })
  },
  async enableIncomingCallVideo({ commit, state, dispatch }) {
    const active = state.activeIncomingCall
    if (!active || active.videoEnabled || active.videoLoading) return
    let camera = state.cameras[active.cameraKey]
    if (!camera) return
    camera = { ...camera }
    commit('UPDATE_ACTIVE_INCOMING_CALL', { videoEnabled: true, videoLoading: true })
    try {
      const session = await createVideoSession({
        robotId: active.robotId,
        deviceId: active.deviceId,
        quality: active.quality || camera.quality || 'sub',
        reuse: true
      })
      // A reused room can publish its existing track before this HTTP request returns.
      camera = { ...camera, ...(state.cameras[active.cameraKey] || {}) }
      camera.session = mergeSession(camera, session)
      camera.status = camera.session.status
      camera.viewerCount = camera.session.viewerCount
      camera.watching = true
      camera.stopped = false
      camera.stopping = false
      state.stoppedSessionIds.delete(camera.session.sessionId)
      commit('setCamera', camera)
      if (!camera.room) {
        await dispatch('connectLiveKit', {
          camera,
          refreshToken: false,
          connectionToken: camera.intercomToken
        })
      }
      if (camera.remoteVideoTrack) {
        commit('UPDATE_ACTIVE_INCOMING_CALL', { videoLoading: false })
      }
    } catch (error) {
      commit('UPDATE_ACTIVE_INCOMING_CALL', { videoEnabled: false, videoLoading: false })
      Message.error(errorMessage(error))
    }
  },
  async disableIncomingCallVideo({ commit, state }) {
    const active = state.activeIncomingCall
    if (!active || !active.videoEnabled) return
    const storedCamera = state.cameras[active.cameraKey]
    if (storedCamera) {
      const camera = { ...storedCamera }
      const keepWatching = Boolean(state.activeCameras[active.cameraKey])
      if (!keepWatching && camera.watching && camera.session) {
        camera.watching = false
        state.stoppedSessionIds.add(camera.session.sessionId)
        commit('setCamera', camera)
        try {
          const session = await stopVideoSession(camera.session.sessionId)
          camera.session = mergeSession(camera, session)
          camera.viewerCount = session.viewerCount || 0
        } catch (error) {
          console.error('ERROR close incoming call video', errorMessage(error))
        }
        commit('setCamera', camera)
      }
    }
    commit('UPDATE_ACTIVE_INCOMING_CALL', {
      videoEnabled: false,
      videoLoading: false
    })
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
    const hasRealtimeCameras = Array.isArray(data.cameras) && data.cameras.length > 0
    if (index >= 0) {
      const existing = state.robots[index]
      if (!hasRealtimeCameras) {
        incoming.cameras = existing.cameras || []
      } else {
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
            remoteAudioElement: old.remoteAudioElement || null,
            remoteVideoTrack: old.remoteVideoTrack || null
          })
        })
        commit('setCameras', replaceRobotCamerasInIndex(state.cameras, incoming.robotId, incoming.cameras))
      }
      commit('updateRobot', toBasicRobot({ ...existing, ...incoming }))
    } else {
      commit('setRobots', [...state.robots, toBasicRobot(incoming)])
      if (hasRealtimeCameras) {
        commit('setCameras', replaceRobotCamerasInIndex(state.cameras, incoming.robotId, incoming.cameras))
      }
    }
    dispatch('websocketExtraData/setRobotBaseInfo', {
      robotId: incoming.robotId,
      robotInfo: {
        name: incoming.name,
        type: incoming.type,
        status: incoming.status,
        battery: incoming.battery,
        controlMode: incoming.controlMode,
        controlModeName: incoming.controlModeName,
        speed: incoming.speed,
        fault: incoming.fault,
        lastHeartbeatAt: incoming.lastHeartbeatAt
      },
      fromRealtime: true
    }, { root: true })
  },

  patchRobotRealtime({ commit, state }, patch) {
    if (!patch || patch.robotId === undefined || patch.robotId === null || patch.robotId === '') return
    const existing = (state.robots || []).find(item => String(item.robotId) === String(patch.robotId))
    if (!existing) {
      commit('updateRobot', toBasicRobot(toRobotState({ ...patch, cameras: [] })))
      return
    }
    commit('updateRobot', {
      ...existing,
      status: patch.status ? patch.status : existing.status,
      battery: patch.battery !== undefined ? patch.battery : existing.battery,
      controlMode: patch.controlMode || existing.controlMode,
      controlModeName: patch.controlModeName || existing.controlModeName,
      speed: patch.speed !== undefined ? patch.speed : existing.speed,
      fault: patch.fault !== undefined ? patch.fault : existing.fault
    })
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
        const powerOn = status.powerOn === undefined ? status.enabled : status.powerOn
        obj['warningLightState'] = {
          ...state.deviceStateCache?.warningLightState || {},
          [device.deviceId]: Array.isArray(powerOn) ? powerOn.length > 0 && powerOn.every(Boolean) : !!powerOn
        }
      }
      const ptzKey = `${robotId}:${device.deviceId}`
      if (device.deviceType === 'DUAL_LIGHT_PTZ' && status.autoRotateEnabled !== undefined &&
          !(options.preserveExisting && state.deviceStateCache?.ptzAutoRotateState[ptzKey] !== undefined)) {
        obj['ptzAutoRotateState'] = { ...state.deviceStateCache?.ptzAutoRotateState || {}, [ptzKey]: !!status.autoRotateEnabled }
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
    if (device.deviceId === 'base' && state.selectedRobot.controlMode !== '手动模式') {
      throw new Error('请先将机器人切换到手动模式')
    }
    const session = await acquireControl(state.selectedRobotId, {
      scope: device.deviceId === 'base' ? 'ROBOT' : 'DEVICE',
      deviceIds: [device.deviceId],
      actions: [action],
      mode: 'EXCLUSIVE',
      reason: 'manual_teleop',
      ttlSeconds: 30
    })
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
    const activeIntercomSessionId = state.activeIncomingCall && state.activeIncomingCall.sessionId
    let activeIntercomHeartbeatAttempted = false
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
      const shouldHeartbeatIntercom = camera.session &&
        (camera.intercomActive || camera.session.sessionId === activeIntercomSessionId)
      if (shouldHeartbeatIntercom) {
        if (camera.session.sessionId === activeIntercomSessionId) activeIntercomHeartbeatAttempted = true
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
    if (activeIntercomSessionId && !activeIntercomHeartbeatAttempted) {
      try {
        await heartbeatIntercom(activeIntercomSessionId)
      } catch (_) {}
    }
  },
  // 启动摄像头。同一路可被多个画面消费：已有 LiveKit Room 时只挂到新的 video，不重连。
  async startCamera({ commit, state, dispatch }, { robot, camera, consumerId, prefixId }) {
    if (camera.recordingActive) {
      await dispatch('stopCameraRecording', camera)
    }
    const viewerId = consumerId || 'default'
    const attachPrefix = prefixId || state.prefixId
    const stored = state.cameras[camera.key] || {}
    const camera1 = {
      ...camera,
      ...stored,
      key: camera.key || stored.key,
      attachTargets: mergeAttachTargets(stored.attachTargets, camera.attachTargets, { [viewerId]: attachPrefix })
    }
    console.log('%cstartCamera+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++', 'color: #0f0', attachPrefix + camera1.key, robot.robotId, robot.status)
    if (robot.status !== 'online') return
    const reuseRoom = liveKitRoomReusable(camera1)
    if (!reuseRoom) camera1.loading = true
    camera1.stopped = false
    camera1.stopping = false
    camera1.restarting = false
    camera1.watching = true
    try {
      const session = await createVideoSession({
        robotId: robot.robotId,
        sourceType: robot.sourceType,
        deviceId: camera.deviceId,
        quality: camera.quality,
        reuse: true
      })
      camera1.session = mergeSession(camera1, session)
      camera1.status = camera1.session.status
      camera1.viewerCount = camera1.session.viewerCount
      state.stoppedSessionIds.delete(camera1.session.sessionId)
      // console.log('API createVideoSession', camera1.session)
      if (reuseRoom) {
        attachCameraMedia(camera1, attachPrefix)
      } else if (!camera1.room || !camera1.intercomActive) {
        await dispatch('connectLiveKit', { camera: camera1 })
      } else {
        attachCameraMedia(camera1, attachPrefix)
      }
    } catch (error) {
      console.error('ERROR createVideoSession', error.message || '请求失败')
    } finally {
      const next = mergeCameraFromStore(state, camera1, {
        loading: false,
        attachTargets: mergeAttachTargets(
          state.cameras[camera1.key]?.attachTargets,
          camera1.attachTargets
        )
      })
      commit('setCamera', next)
      commit('setActiveCamera', { key: next.key, robot, camera: next })
    }
  },

  // 停止摄像头。传入 consumerId 时，若仍有其他画面在用同一路流，只摘掉本画面，不关会话。
  async stopCamera({ commit, state, dispatch }, data) {
    let camera = state.cameras[data.key]
    if (!camera) return
    camera = { ...camera }
    const consumerId = data.consumerId
    const attachPrefix = data.prefixId
      || (consumerId && camera.attachTargets && camera.attachTargets[consumerId])
      || state.prefixId
    if (consumerId && camera.attachTargets) {
      const nextTargets = { ...camera.attachTargets }
      delete nextTargets[consumerId]
      detachCameraMedia(camera, attachPrefix)
      if (Object.keys(nextTargets).length > 0) {
        camera.attachTargets = nextTargets
        commit('setCamera', camera)
        return
      }
      camera.attachTargets = {}
    }
    if (camera.recordingActive) {
      await dispatch('stopCameraRecording', camera)
    }
    if (!camera.session) return
    camera.loading = true
    camera.stopping = true
    camera.stopped = true
    camera.restarting = false
    camera.disconnecting = true
    cancelAttachRetry(camera.key)
    try {
      const sessionId = camera.session.sessionId
      if (!camera.intercomActive) state.stoppedSessionIds.add(sessionId)
      console.log('%cstopCamera+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++', 'color: #f0f')
      const stopped = await stopVideoSession(sessionId)
      // console.log('API stopVideoSession', stopped)
      camera.watching = false
      camera.hasVideo = false
      const prefixes = uniqueAttachPrefixes({ ...camera, attachTargets: camera.attachTargets }, state)
      if (!prefixes.includes(attachPrefix) && attachPrefix) prefixes.push(attachPrefix)
      prefixes.forEach(prefixId => detachCameraMedia(camera, prefixId))
      camera.remoteVideoTrack = null
      camera.attachTargets = {}
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
    if (state.activeIncomingCall) {
      Message.warning('当前正在通话，请先结束当前通话')
      return
    }
    const otherIntercom = allCameras().find(item => item.key !== camera.key && item.intercomActive)
    if (otherIntercom) {
      Message.warning('当前正在与其他机器人通话，请先结束当前通话')
      return
    }
    if (state.incomingCalls.some(call => call.robotId === robotId)) {
      Message.warning('该机器人正在呼叫中心端，请通过来电窗口接听')
      return
    }
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
      commit('setCamera', mergeCameraFromStore(state, camera, {
        intercomBusy: false,
        intercomActive: camera.intercomActive
      }))
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
    if (!camera.session) return true
    const incomingSessionId = state.activeIncomingCall && state.activeIncomingCall.sessionId
    const holdingIntercom = Boolean(
      camera.intercomActive ||
      intercomInProgress(camera) ||
      (incomingSessionId && camera.session.sessionId === incomingSessionId)
    )
    if (!holdingIntercom) return true
    camera.intercomBusy = true
    const audioElement = camera.remoteAudioElement
    let response = null
    let stopped = false
    try {
      if (camera.room) {
        await Promise.resolve(camera.room.localParticipant.setMicrophoneEnabled(false)).catch(() => {})
      }
      response = await stopIntercom(camera.session.sessionId)
      stopped = true
      // console.log('API stopIntercom', response)
    } catch (error) {
      if (isIntercomAlreadyStoppedError(error)) {
        stopped = true
      } else {
        console.error('ERROR stopIntercom', errorMessage(error))
        Message.error('挂断失败，请稍后重试')
      }
    } finally {
      if (stopped) {
        camera.intercomActive = false
        camera.intercomStatus = 'IDLE'
        camera.intercomToken = null
        camera.hasAudio = false
        camera.remoteAudioTrack = null
        camera.remoteAudioElement = null
        if (camera.watching) {
          if (response) camera.session = mergeSession(camera, response)
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
      }
      if (audioElement && typeof audioElement.remove === 'function') audioElement.remove()
      camera.remoteAudioElement = null
      camera.intercomBusy = false
      commit('setCamera', camera)
    }
    return stopped
  },

  // 连接 LiveKit 会话
  async connectLiveKit({ commit, dispatch, state }, { camera, refreshToken, connectionToken }) {
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
      const currentCamera = () => {
        const stored = state.cameras[camera.key]
        if (stored && stored.room === room && stored.session && stored.session.sessionId === sessionId) {
          return { ...stored }
        }
        if (camera.room === room && camera.session && camera.session.sessionId === sessionId) {
          return { ...camera }
        }
        return null
      }
      room.on(RoomEvent.TrackSubscribed, (track) => {
        const current = currentCamera()
        if (!current) return
        if (track.kind === 'video') {
          current.remoteVideoTrack = track
          current.hasVideo = true
          if (current.watching) attachTrackToCameraTargets(track, current, state, 'video')
          if (state.activeIncomingCall &&
              state.activeIncomingCall.sessionId === sessionId &&
              state.activeIncomingCall.videoEnabled) {
            commit('UPDATE_ACTIVE_INCOMING_CALL', { videoLoading: false })
          }
        } else if (track.kind === 'audio') {
          current.remoteAudioTrack = track
          const prefixes = uniqueAttachPrefixes(current, state)
          let audioElement = null
          prefixes.forEach(prefixId => {
            const el = document.getElementById(prefixId + camera.key + '-audio')
            if (!el) return
            track.attach(el)
            el.dataset.intercomSessionId = sessionId
            el.muted = Boolean(state.activeIncomingCall &&
              state.activeIncomingCall.sessionId === sessionId &&
              state.activeIncomingCall.speakerMuted)
            audioElement = el
          })
          if (!audioElement) {
            const audioId = state.prefixId + camera.key + '-audio'
            audioElement = document.getElementById(audioId)
            if (!audioElement) {
              audioElement = track.attach()
              audioElement.id = audioId
              audioElement.style.display = 'none'
              document.body.appendChild(audioElement)
            } else {
              track.attach(audioElement)
            }
            audioElement.dataset.intercomSessionId = sessionId
            audioElement.muted = Boolean(state.activeIncomingCall &&
              state.activeIncomingCall.sessionId === sessionId &&
              state.activeIncomingCall.speakerMuted)
          }
          current.remoteAudioElement = audioElement
          current.hasAudio = true
        }
        commit('setCamera', current)
      })
      room.on(RoomEvent.TrackUnsubscribed, (track) => {
        const current = currentCamera()
        if (!current) return
        track.detach()
        if (track.kind === 'audio') {
          cancelAttachRetry(current.key, null, 'audio')
          const audioElement = current.remoteAudioElement
          if (audioElement && typeof audioElement.remove === 'function') audioElement.remove()
          current.hasAudio = false
          current.remoteAudioTrack = null
          current.remoteAudioElement = null
        }
        if (track.kind === 'video') {
          cancelAttachRetry(current.key, null, 'video')
          current.hasVideo = false
          current.remoteVideoTrack = null
          if (state.activeIncomingCall &&
              state.activeIncomingCall.sessionId === sessionId &&
              state.activeIncomingCall.videoEnabled) {
            commit('UPDATE_ACTIVE_INCOMING_CALL', { videoLoading: true })
          }
        }
        // console.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
        if (track.kind === 'video' && current.watching && !isStoppedSession(current, sessionId)) {
          dispatch('restartCamera', current)
        } else {
          commit('setCamera', current)
        }
      })
      room.on(RoomEvent.Disconnected, () => {
        const current = currentCamera()
        if (!current || current.disconnecting) return
        const audioElement = current.remoteAudioElement
        if (audioElement && typeof audioElement.remove === 'function') audioElement.remove()
        cancelAttachRetry(current.key)
        current.hasVideo = false
        current.hasAudio = false
        current.remoteVideoTrack = null
        current.remoteAudioTrack = null
        current.remoteAudioElement = null
        // console.log('LiveKit Disconnected', camera.name)
        if (current.watching && !isStoppedSession(current, sessionId)) {
          dispatch('restartCamera', current)
        } else {
          commit('setCamera', current)
        }
      })
      camera.room = room
      commit('setCamera', camera)
      await room.connect(livekitUrl, token)
      // console.log('LiveKit connected', `${camera.name} ${camera.session.roomName}`)
    } catch (error) {
      camera.room = null
      camera.hasVideo = false
      console.error('ERROR LiveKit connect', error.message || '请求失败')
    } finally {
      camera.disconnecting = false
      camera.connecting = false
      const latest = state.cameras[camera.key]
      if (latest && latest.room === camera.room) {
        commit('setCamera', { ...latest, disconnecting: false, connecting: false })
      } else if (latest && camera.room && latest.room && latest.room !== camera.room) {
        // store 已被更新的连接替换，勿用旧 camera 回写
      } else {
        // 合并 store，避免丢掉 TrackSubscribed 已写入的 track；room 已清空时同步清掉视频轨
        commit('setCamera', mergeCameraFromStore(state, camera, {
          disconnecting: false,
          connecting: false,
          room: camera.room,
          hasVideo: camera.hasVideo,
          ...(camera.room ? {} : { remoteVideoTrack: null })
        }))
      }
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
  startHeartbeat({ dispatch, state }) {
    if (state.heartbeatTimer) clearInterval(state.heartbeatTimer)
    dispatch('heartbeatViewers')
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
    if (!payload) commit('setControlCenterReturnTo', null)
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
  setControlCenterReturnTo({ commit }, payload) {
    commit('setControlCenterReturnTo', payload)
  },
  async loadControlProfile({ commit, state, dispatch, rootState }, robotId) {
    if (!robotId) return
    if (controlProfileInflight[robotId]) return controlProfileInflight[robotId]
    const { fromStore, fromBase, cameras } = resolveEquipmentRecord(state, rootState, robotId)
    // 固定摄像头不可遥控，不查询控制画像
    if (isFixedCameraEquipment(fromBase, cameras) || isFixedCameraEquipment(fromStore, cameras)) return
    const task = (async () => {
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
        return profile
      } catch (error) {
        console.error('ERROR getControlProfile', error)
      } finally {
        commit('SET_PROFILE_LOADING', { robotId, loading: false })
      }
    })()
    controlProfileInflight[robotId] = task
    try {
      return await task
    } finally {
      if (controlProfileInflight[robotId] === task) delete controlProfileInflight[robotId]
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
      if (publication && publication.track) {
        camera.remoteVideoTrack = publication.track
        dispatch('startLatencyStats', { camera, track: publication.track, room: nextRoom })
      }
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
        prepareReplacementVideo(camera, track, publication, oldRoom)
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
      vehicleLightEnabled: payload.vehicleLightEnabled,
      ptzAutoRotateState: payload.ptzAutoRotateState
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
  getControlCenterReturnTo: (state) => state.controlCenterReturnTo,
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
