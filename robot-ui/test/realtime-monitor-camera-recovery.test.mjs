import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const read = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
const cameraHelpers = await import('data:text/javascript;base64,' + Buffer.from(
  read('views/bi/js/utils/pick-default-camera.js')
).toString('base64'))
const robotStateHelpers = await import('data:text/javascript;base64,' + Buffer.from(
  read('views/bi/js/utils/prefer-live-robot-fields.js')
).toString('base64'))
const trackRecovery = await import('data:text/javascript;base64,' + Buffer.from(
  read('views/bi/js/utils/livekit-track-recovery.js')
).toString('base64'))
const videoDisplayState = await import('data:text/javascript;base64,' + Buffer.from(
  read('views/bi/js/utils/video-display-state.js')
).toString('base64'))
const intercomOperationRegistry = await import('data:text/javascript;base64,' + Buffer.from(
  read('utils/intercom-operation-registry.js')
).toString('base64'))
const livekitLocalMedia = await import('data:text/javascript;base64,' + Buffer.from(
  read('utils/livekit-local-media.js')
).toString('base64'))
const mediaCallCoordinator = await import('data:text/javascript;base64,' + Buffer.from(
  read('utils/media-call-coordinator.js')
).toString('base64'))

function componentDefinition(path) {
  const source = read(path).split('<script>')[1].split('</script>')[0]
  const exports = {}
  vm.runInNewContext(require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code, {
    exports,
    console,
    document: { addEventListener() {}, removeEventListener() {}, body: { style: {} } },
    require: name => {
      if (name === 'vuex') return { mapActions: () => ({}), mapState: () => ({}) }
      if (name.includes('pick-default-camera')) return cameraHelpers
      if (name.includes('dragVideo')) return { onDragStart() {}, onDragEnd() {} }
      if (name.includes('fullscreen')) return { events: [], enterFullscreen() {}, exitFullscreen() {}, isElementFullscreen: () => false }
      if (name.includes('constants/robot')) return { ROBOT_TYPE_INFO: {} }
      if (name.endsWith('/utils')) return { getDescArr: () => [] }
      return {}
    }
  })
  return exports.default
}

function componentMethods(path) {
  return componentDefinition(path).methods
}

function attachMethods(context, methods, names) {
  names.forEach(name => { context[name] = methods[name] })
  return context
}

function loadMediaApi(request) {
  const compiled = require('@babel/core').transformSync(read('api/media.js'), {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  vm.runInNewContext(compiled, {
    exports,
    process: { env: {} },
    require: name => {
      if (name === '@/utils/request') return request
      if (name === '@/utils/media-client-id') return { mediaClientId: 'test-client' }
      if (name === '@/utils/api-url') return {
        BIGSCREEN_API_PREFIX: '/api/bigscreen',
        BIGSCREEN_CONTROL_API_PREFIX: '/api/bigscreen/control',
        BIGSCREEN_PANORAMA_API_PREFIX: '/api/bigscreen/panorama',
        withApiPrefix: value => value,
        withBigscreenApiPrefix: value => value
      }
      return {}
    }
  })
  return exports
}

function loadWebsocketRobot(apiOverrides = {}, runtimeOverrides = {}) {
  const source = read('store/modules/websocket-robot.js').replace(
    '// 导出 WebSocket 模块',
    'export const __testHooks = { scheduleViewerReconnect }\n\n// 导出 WebSocket 模块'
  )
  const compiled = require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  const api = {
    createVideoSession: async () => ({}),
    getActiveLiveRecording: async () => null,
    stopVideoSession: async () => ({ status: 'CLOSED', viewerCount: 0 }),
    mediaClientId: 'test-client',
    ...apiOverrides
  }
  vm.runInNewContext(compiled, {
    exports,
    console,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    requestAnimationFrame: callback => setTimeout(callback, 0),
    cancelAnimationFrame: clearTimeout,
    window: { localStorage: { getItem: () => null, setItem() {} } },
    document: { getElementById: () => null, createElement: () => ({}) },
    require: name => {
      if (name === '@/store') return { dispatch() {}, state: {} }
      if (name === 'element-ui') return { Message: { warning() {}, error() {}, success() {} } }
      if (name === 'livekit-client') {
        return {
          Room: class {},
          RoomEvent: {},
          Track: { Kind: {}, Source: {} },
          VideoQuality: {}
        }
      }
      if (name === '../../api/media') return api
      if (name === 'vue') return { set(target, key, value) { target[key] = value } }
      if (name === '../../utils') return { errorMessage: error => String(error) }
      if (name === '../../utils/error-feedback') return { notifyActionError() {} }
      if (name === '@/auth') return { bearerToken: () => '' }
      if (name.includes('prefer-live-robot-fields')) return robotStateHelpers
      if (name.includes('pick-default-camera')) return cameraHelpers
      if (name.includes('livekit-track-recovery')) return trackRecovery
      if (name.includes('livekit-user-pause')) return { attachTrackRespectingUserPause: () => true }
      if (name.includes('intercom-operation-registry')) return intercomOperationRegistry
      if (name.includes('livekit-local-media')) return livekitLocalMedia
      if (name.includes('media-call-coordinator')) return mediaCallCoordinator
      if (name.includes('call-terminal-registry')) return {
        CallTerminalRegistry: class {
          remember() {}
          has() { return false }
          clear() {}
        }
      }
      if (name.includes('media-websocket-reconnect')) {
        return {
          mediaReconnectDelay: () => 0,
          isSustainedAuthorizationFailure: () => false,
          shouldReconnectMedia: () => false
        }
      }
      return {}
    },
    ...runtimeOverrides
  })
  exports.default.__testHooks = exports.__testHooks
  return exports.default
}

function cameraActionContext(actions, state) {
  const commit = (type, payload) => {
    if (type === 'setCamera') state.cameras[payload.key] = payload
    if (type === 'setActiveCamera') state.activeCameras[payload.key] = payload
    if (type === 'removeActiveCamera') delete state.activeCameras[payload]
  }
  const context = { state, commit }
  context.dispatch = (type, payload) => actions[type](context, payload)
  return context
}

test('首次自动播放排除固定摄像头，并在请求前按实际宫格容量截断', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/TaskListTree.vue')
  const robots = Array.from({ length: 11 }, (_, index) => ({
    robotId: `robot-${index + 1}`,
    typeCode: 'WHEELED_ROBOT',
    cameras: [{ key: `robot-${index + 1}-body`, groupType: 'body' }]
  }))
  const fixedCamera = {
    robotId: 'fixed-1',
    sourceType: 'FIXED_CAMERA',
    cameras: [{ key: 'fixed-1-camera', groupType: 'fixed_camera' }]
  }
  const started = []
  const context = {
    equipmentInfo: { online: { list: [fixedCamera, ...robots] } },
    cameras: {},
    hasLoad: false,
    splitType: 4,
    routeTaskId: () => null,
    isFixedCameraRobot: methods.isFixedCameraRobot,
    splitTypeForCount: methods.splitTypeForCount,
    setSplitType(value) { this.splitType = value },
    async waitTicks() {},
    async handleClickRobot(robot) { started.push(robot.robotId) }
  }

  await methods.executePlay.call(context)

  assert.equal(context.splitType, 9)
  assert.equal(started.length, 9)
  assert.equal(started.includes('fixed-1'), false)
})

test('移动装备视频可达性明确允许故障状态并拒绝离线或未知状态', () => {
  assert.equal(cameraHelpers.isRobotMediaReachable('online'), true)
  assert.equal(cameraHelpers.isRobotMediaReachable({ status: 'fault' }), true)
  assert.equal(cameraHelpers.isRobotMediaReachable('offline'), false)
  assert.equal(cameraHelpers.isRobotMediaReachable(''), false)
})

test('故障装备允许首次启动视频会话', async () => {
  let createCalls = 0
  const module = loadWebsocketRobot({
    createVideoSession: async () => {
      createCalls += 1
      return { sessionId: 'session-fault', roomName: 'room-fault', status: 'STREAMING', viewerCount: 1 }
    }
  })
  const camera = {
    key: 'camera-fault',
    robotId: 'robot-fault',
    deviceId: 'camera-fault',
    quality: 'sub',
    attachTargets: {}
  }
  const state = {
    cameras: { [camera.key]: camera },
    activeCameras: {},
    stoppedSessionIds: new Set(),
    prefixId: ''
  }
  const context = cameraActionContext(module.actions, state)
  const originalDispatch = context.dispatch
  context.dispatch = async (type, payload) => {
    if (type === 'connectLiveKit') {
      payload.camera.room = { state: 'connected' }
      context.commit('setCamera', { ...payload.camera })
      return
    }
    return originalDispatch(type, payload)
  }

  const result = await module.actions.performStartCamera(context, {
    robot: { robotId: 'robot-fault', status: 'fault' },
    camera
  })

  assert.equal(createCalls, 1)
  assert.equal(result.session.sessionId, 'session-fault')
  assert.equal(state.activeCameras[camera.key].robot.status, 'fault')
})

test('装备从在线变为故障时保留正在播放的 Room 和媒体状态', () => {
  const module = loadWebsocketRobot()
  const room = { disconnect() { throw new Error('故障状态不应断开 Room') } }
  const camera = {
    key: 'robot-1-camera-1-camera-1',
    robotId: 'robot-1',
    deviceId: 'camera-1',
    cameraId: 'camera-1',
    status: 'STREAMING',
    room,
    hasVideo: true,
    latencyMs: 42,
    latencyLevel: 'good',
    watching: true
  }
  const state = {
    robots: [{ robotId: 'robot-1', status: 'online', cameras: [camera] }],
    cameras: { [camera.key]: camera }
  }
  const commit = (type, payload) => {
    if (type === 'setCameras') state.cameras = payload
    if (type === 'updateRobot') state.robots = [payload]
  }
  const dispatch = () => Promise.resolve()

  module.actions.syncRobotEvent({ state, commit, dispatch }, {
    event: 'robot.state',
    data: {
      robotId: 'robot-1',
      status: 'fault',
      statusChangedAt: '2026-09-12T12:00:01Z',
      cameras: [{ deviceId: 'camera-1', cameraId: 'camera-1', status: 'online' }]
    }
  })

  assert.equal(state.robots[0].status, 'fault')
  assert.equal(state.cameras[camera.key].room, room)
  assert.equal(state.cameras[camera.key].hasVideo, true)
  assert.equal(state.cameras[camera.key].status, 'STREAMING')
  assert.equal(state.cameras[camera.key].latencyMs, 42)
})

test('原宫格中的固定摄像头只在真实恢复可播放时重建一次会话', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const key = 'fixed-1-fixed-1-fixed-1'
  const camera = { key, robotId: 'fixed-1', deviceId: 'fixed-1', cameraId: 'fixed-1' }
  const robot = {
    robotId: 'fixed-1',
    sourceType: 'FIXED_CAMERA',
    status: 'offline',
    enabled: true,
    configReady: true,
    playable: false,
    cameras: [camera]
  }
  let starts = 0
  const context = attachMethods({
    ZQL_videosInfos: { slot_1: { ...camera, robotId: 'fixed-1' } },
    ZQL_playingSource: { slot_1: key },
    robots: [robot],
    cameras: { [key]: camera },
    fixedCameraPlayableStates: {},
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    $delete(target, field) { delete target[field] },
    async startCamera() {
      starts += 1
      context.cameras[key] = { ...camera, session: { status: 'STREAMING' } }
    },
    async stopCamera() {}
  }, methods, [
    'wallConsumerId', 'startCameraPayload', 'stopCameraPayload',
    'resolvePlaybackRobot', 'isCameraIntended', 'isSlotCameraIntended', 'startAssignedCamera'
  ])

  // 首次快照只建立基线，其他装备的深层状态变化也不得重试。
  await methods.syncVideoSlots.call(context)
  await methods.syncVideoSlots.call(context)
  assert.equal(starts, 0)

  robot.status = 'online'
  robot.playable = true
  await methods.syncVideoSlots.call(context)
  await methods.syncVideoSlots.call(context)

  assert.equal(starts, 1)
  assert.equal(context.ZQL_videosInfos.slot_1.robot.robotId, 'fixed-1')
})

test('视频启动失败后清理预占宫格', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const context = attachMethods({
    ZQL_playingSource: {},
    ZQL_videosInfos: {},
    checkedIds: [],
    lastCheckedIds: [],
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    async startCamera() { throw new Error('创建失败') }
  }, methods, [
    'wallConsumerId', 'startCameraPayload', 'syncSlotSelections',
    'assignSlotCamera', 'takeSlotCamera', 'isSlotCameraIntended', 'startAssignedCamera'
  ])

  const started = await methods.start.call(context, { robotId: 'robot-1' }, {
    index: 'slot_1',
    data: { key: 'camera-1' }
  })

  assert.equal(started, false)
  assert.equal(context.ZQL_playingSource.slot_1, null)
  assert.equal(context.ZQL_videosInfos.slot_1, null)
})

test('固定摄像头启动失败后保留播放意图等待源恢复', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const camera = { key: 'fixed-camera-1', robotId: 'fixed-camera-1' }
  const robot = { robotId: 'fixed-camera-1', sourceType: 'FIXED_CAMERA' }
  const context = attachMethods({
    ZQL_playingSource: {},
    ZQL_videosInfos: {},
    checkedIds: [],
    lastCheckedIds: [],
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    async startCamera() { throw new Error('固定摄像头启动失败') }
  }, methods, [
    'wallConsumerId', 'startCameraPayload', 'syncSlotSelections',
    'assignSlotCamera', 'takeSlotCamera', 'isSlotCameraIntended', 'startAssignedCamera'
  ])

  const started = await methods.start.call(context, robot, {
    index: 'slot_1',
    data: camera
  })

  assert.equal(started, false)
  assert.equal(context.ZQL_playingSource.slot_1, camera.key)
  assert.equal(context.ZQL_videosInfos.slot_1.key, camera.key)
  assert.equal(context.ZQL_videosInfos.slot_1.sourceStartFailed, true)
})

test('缩小宫格先收缩播放意图且连续切换不会恢复已移除视频', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const items = Array.from({ length: 9 }, (_, index) => ({
    key: `camera-${index + 1}`,
    robotId: `robot-${index + 1}`
  }))
  let releaseStops
  const stopGate = new Promise(resolve => { releaseStops = resolve })
  const context = attachMethods({
    ZQL_videosInfos: Object.fromEntries(items.map((item, index) => [`slot_${index + 1}`, item])),
    ZQL_playingSource: Object.fromEntries(items.map((item, index) => [`slot_${index + 1}`, item.key])),
    cameras: Object.fromEntries(items.map(item => [item.key, item])),
    robots: [],
    isFixedCameraRobot: methods.isFixedCameraRobot,
    checkedIds: items.map(item => item.key),
    lastCheckedIds: items.map(item => item.key),
    slotDevices: new Array(9).fill(null),
    orderedPlayingVideoInfos: methods.orderedPlayingVideoInfos,
    currentVisibleCameras: methods.currentVisibleCameras,
    async stopCamera() { await stopGate },
    async $nextTick() {},
    rebindCameraTracks() {}
  }, methods, [
    'wallConsumerId', 'resolvePlaybackRobot', 'stopCameraPayload', 'syncSlotSelections'
  ])

  const toSix = methods.applySplitVideoChannels.call(context, items, 6)
  assert.equal(context.orderedPlayingVideoInfos().length, 6)

  const toFour = methods.applySplitVideoChannels.call(
    context,
    context.orderedPlayingVideoInfos(),
    4
  )
  assert.equal(context.orderedPlayingVideoInfos().length, 4)

  releaseStops()
  await Promise.all([toSix, toFour])
  assert.equal(context.orderedPlayingVideoInfos().length, 4)
})

test('固定摄像头人工关闭先撤销槽位意图且状态刷新不得重新拉起', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const key = 'fixed-1-fixed-1-fixed-1'
  const camera = { key, robotId: 'fixed-1', deviceId: 'fixed-1' }
  const robot = {
    robotId: 'fixed-1',
    sourceType: 'FIXED_CAMERA',
    status: 'online',
    enabled: true,
    configReady: true,
    playable: true,
    cameras: [camera]
  }
  let releaseStop
  const stopGate = new Promise(resolve => { releaseStop = resolve })
  let starts = 0
  let stopPayload
  const context = attachMethods({
    _uid: 101,
    prefixId: 'test-video-div',
    splitType: 1,
    robots: [robot],
    cameras: { [key]: camera },
    ZQL_videosInfos: { slot_1: { robot, ...camera } },
    ZQL_playingSource: { slot_1: key },
    checkedIds: [key],
    lastCheckedIds: [key],
    fixedCameraPlayableStates: {},
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    $delete(target, field) { delete target[field] },
    async startCamera() { starts += 1 },
    async stopCamera(payload) {
      stopPayload = payload
      await stopGate
    }
  }, methods, [
    'wallConsumerId', 'resolvePlaybackRobot', 'startCameraPayload',
    'stopCameraPayload', 'syncSlotSelections', 'takeSlotCamera',
    'isCameraIntended', 'stopTakenCamera', 'closeSlotCamera'
  ])

  const closing = methods.handleRemoveVideo.call(context, 'slot_1')
  assert.equal(context.ZQL_playingSource.slot_1, null)
  assert.equal(context.ZQL_videosInfos.slot_1, null)
  assert.equal(context.checkedIds.length, 0)

  await methods.syncVideoSlots.call(context)
  assert.equal(starts, 0)
  assert.equal(stopPayload.consumerId, 'patrol-monitor-wall:test-video-div:101')
  assert.equal(stopPayload.prefixId, 'test-video-div')

  releaseStop()
  await closing
})

test('快速切换槽位时迟到的旧停止不得覆盖最后一次播放意图', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const robot = { robotId: 'robot-1', status: 'online' }
  const cameras = {
    a: { key: 'a', robotId: 'robot-1' },
    b: { key: 'b', robotId: 'robot-1' },
    c: { key: 'c', robotId: 'robot-1' }
  }
  let releaseA
  const stopA = new Promise(resolve => { releaseA = resolve })
  const starts = []
  const context = attachMethods({
    _uid: 102,
    prefixId: 'test-video-div',
    robots: [robot],
    cameras,
    ZQL_videosInfos: { slot_1: { robot, ...cameras.a } },
    ZQL_playingSource: { slot_1: 'a' },
    checkedIds: ['a'],
    lastCheckedIds: ['a'],
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    async stopCamera(camera) {
      if (camera.key === 'a') await stopA
    },
    async startCamera({ camera }) { starts.push(camera.key) }
  }, methods, [
    'wallConsumerId', 'resolvePlaybackRobot', 'startCameraPayload',
    'stopCameraPayload', 'syncSlotSelections', 'assignSlotCamera',
    'takeSlotCamera', 'isSlotCameraIntended', 'stopTakenCamera',
    'closeSlotCamera', 'startAssignedCamera', 'replaceSlotCamera'
  ])

  const selectB = context.replaceSlotCamera('slot_1', robot, cameras.b)
  const selectC = context.replaceSlotCamera('slot_1', robot, cameras.c)
  releaseA()
  await Promise.all([selectB, selectC])

  assert.equal(context.ZQL_playingSource.slot_1, 'c')
  assert.equal(context.ZQL_videosInfos.slot_1.key, 'c')
  assert.deepEqual(starts, ['c'])
})

test('替换槽位时旧视频停止失败不得阻断最新视频启动', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const robot = { robotId: 'robot-1', status: 'online' }
  const oldCamera = { key: 'old', robotId: 'robot-1' }
  const nextCamera = { key: 'next', robotId: 'robot-1' }
  const starts = []
  const context = attachMethods({
    prefixId: 'test-video-div',
    robots: [robot],
    cameras: { old: oldCamera, next: nextCamera },
    ZQL_videosInfos: { slot_1: { robot, ...oldCamera } },
    ZQL_playingSource: { slot_1: 'old' },
    checkedIds: ['old'],
    lastCheckedIds: ['old'],
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    async stopCamera() { throw new Error('停止旧视频失败') },
    async startCamera({ camera }) { starts.push(camera.key) }
  }, methods, [
    'wallConsumerId', 'resolvePlaybackRobot', 'startCameraPayload',
    'stopCameraPayload', 'syncSlotSelections', 'assignSlotCamera',
    'takeSlotCamera', 'isSlotCameraIntended', 'stopTakenCamera',
    'startAssignedCamera', 'replaceSlotCamera'
  ])

  const started = await context.replaceSlotCamera('slot_1', robot, nextCamera)

  assert.equal(started, true)
  assert.equal(context.ZQL_playingSource.slot_1, 'next')
  assert.deepEqual(starts, ['next'])
})

test('机器人与固定摄像头统一使用当前视频墙消费者身份', () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const context = attachMethods({
    _uid: 102,
    prefixId: 'test-video-div',
    robots: [],
    isFixedCameraRobot: methods.isFixedCameraRobot
  }, methods, ['wallConsumerId', 'resolvePlaybackRobot'])
  const robot = { robotId: 'robot-1', status: 'online' }
  const robotCamera = { key: 'robot-camera' }
  const fixed = { robotId: 'fixed-1', sourceType: 'FIXED_CAMERA' }
  const fixedCamera = { key: 'fixed-camera' }

  const robotStartPayload = methods.startCameraPayload.call(context, robot, robotCamera)
  assert.equal(robotStartPayload.robot, robot)
  assert.equal(robotStartPayload.camera, robotCamera)
  assert.equal(robotStartPayload.throwOnError, true)
  assert.equal(robotStartPayload.consumerId, 'patrol-monitor-wall:test-video-div:102')
  assert.equal(robotStartPayload.prefixId, 'test-video-div')
  const robotStopPayload = methods.stopCameraPayload.call(context, robotCamera, { robot })
  assert.equal(robotStopPayload.key, 'robot-camera')
  assert.equal(robotStopPayload.consumerId, 'patrol-monitor-wall:test-video-div:102')
  assert.equal(robotStopPayload.prefixId, 'test-video-div')
  const fixedStartPayload = methods.startCameraPayload.call(context, fixed, fixedCamera)
  assert.equal(fixedStartPayload.robot, fixed)
  assert.equal(fixedStartPayload.camera, fixedCamera)
  assert.equal(fixedStartPayload.throwOnError, true)
  assert.equal(fixedStartPayload.consumerId, 'patrol-monitor-wall:test-video-div:102')
  assert.equal(fixedStartPayload.prefixId, 'test-video-div')
})

test('视频墙槽位同步同时发布摄像头和装备选中状态', () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const selections = []
  const context = attachMethods({
    ZQL_playingSource: {
      slot_1: 'camera-visible',
      slot_2: 'camera-thermal',
      slot_3: null
    },
    ZQL_videosInfos: {
      slot_1: { key: 'camera-visible', robotId: 1001 },
      slot_2: { key: 'camera-thermal', robot: { robotId: '1001' } },
      slot_3: null
    },
    checkedIds: [],
    lastCheckedIds: [],
    $emit(event, payload) { selections.push({ event, payload }) }
  }, methods, ['orderedPlayingVideoInfos', 'getPlayingRobotIds'])

  methods.syncSlotSelections.call(context)

  assert.deepEqual(Array.from(context.checkedIds), ['camera-visible', 'camera-thermal'])
  assert.deepEqual(JSON.parse(JSON.stringify(selections)), [{
    event: 'selection-change',
    payload: {
      cameraKeys: ['camera-visible', 'camera-thermal'],
      robotIds: ['1001']
    }
  }])
})

test('同一业务视频墙的不同组件实例使用不同消费者身份', () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const first = { prefixId: 'test-video-div', _uid: 201 }
  const second = { prefixId: 'test-video-div', _uid: 202 }

  assert.equal(methods.wallConsumerId.call(first), 'patrol-monitor-wall:test-video-div:201')
  assert.equal(methods.wallConsumerId.call(second), 'patrol-monitor-wall:test-video-div:202')
  assert.notEqual(methods.wallConsumerId.call(first), methods.wallConsumerId.call(second))
})

test('一级和控制中心列表选中状态只接收当前视频墙槽位结果', () => {
  const firstIndex = read('views/bi/patrol/monitor/first/Index.vue')
  const firstTree = read('views/bi/patrol/monitor/first/TaskListTree.vue')
  const secondIndex = read('views/bi/patrol/monitor/second/Index.vue')
  const secondTree = read('views/bi/patrol/monitor/second/EquipmentListTree.vue')

  assert.match(firstIndex, /:checked-robot-ids="playingRobotIds"/)
  assert.match(firstIndex, /@selection-change="handleVideoSelectionChange"/)
  assert.doesNotMatch(firstTree, /getActiveCameras/)
  assert.match(secondIndex, /:checked-camera-ids="playingCameraIds"/)
  assert.match(secondIndex, /@selection-change="handleVideoSelectionChange"/)
  assert.doesNotMatch(secondTree, /getActiveCameras/)
})

test('页面销毁释放当前视频墙全部消费者', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const fixedRobot = { robotId: 'fixed-1', sourceType: 'FIXED_CAMERA' }
  const mobileRobot = { robotId: 'robot-1', status: 'online' }
  const fixedCamera = { key: 'fixed-camera', robotId: 'fixed-1' }
  const robotCamera = { key: 'robot-camera', robotId: 'robot-1' }
  const stops = []
  const context = attachMethods({
    _uid: 103,
    prefixId: 'test-video-div',
    robots: [fixedRobot, mobileRobot],
    cameras: { 'fixed-camera': fixedCamera, 'robot-camera': robotCamera },
    ZQL_videosInfos: {
      slot_1: { robot: fixedRobot, ...fixedCamera },
      slot_2: { robot: mobileRobot, ...robotCamera }
    },
    ZQL_playingSource: { slot_1: 'fixed-camera', slot_2: 'robot-camera' },
    checkedIds: ['fixed-camera', 'robot-camera'],
    lastCheckedIds: ['fixed-camera', 'robot-camera'],
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    async stopCamera(payload) { stops.push(payload) }
  }, methods, [
    'wallConsumerId', 'resolvePlaybackRobot', 'stopCameraPayload',
    'syncSlotSelections', 'takeSlotCamera', 'stopTakenCamera'
  ])

  await methods.releaseAllSlots.call(context)

  assert.equal(context.ZQL_playingSource.slot_1, null)
  assert.equal(context.ZQL_playingSource.slot_2, null)
  assert.equal(context.checkedIds.length, 0)
  assert.equal(stops.length, 2)
  assert.equal(stops[0].key, 'fixed-camera')
  assert.equal(stops[0].consumerId, 'patrol-monitor-wall:test-video-div:103')
  assert.equal(stops[1].key, 'robot-camera')
  assert.equal(stops[1].consumerId, 'patrol-monitor-wall:test-video-div:103')
})

test('视频续期请求使用短超时且不携带界面反馈策略', async () => {
  const requests = []
  const api = loadMediaApi(options => {
    requests.push(options)
    return Promise.resolve({ sessionId: 'session-1' })
  })

  await api.heartbeatVideoSession('session-1')
  await api.heartbeatIntercom('session-1')
  await api.getActiveLiveRecording('session-1')

  requests.forEach(options => {
    assert.equal(options.timeout, 4000)
    assert.equal(Object.prototype.hasOwnProperty.call(options, 'skipErrorMessage'), false)
    assert.equal(Object.prototype.hasOwnProperty.call(options, 'errorMode'), false)
  })
})

test('视频展示状态明确区分视频源和播放端方向', () => {
  const resolve = videoDisplayState.resolveVideoDisplayState

  assert.equal(resolve({ hasVideo: true, status: 'FAILED' }).key, 'playing')
  assert.equal(resolve({ session: { status: 'REQUESTING_CLIENT' } }).text, '正在启动视频源')
  assert.equal(resolve({ session: { status: 'INTERRUPTED' } }).action, 'restart-source')
  assert.equal(resolve({ session: { status: 'FAILED' } }).action, 'restart-source')
  assert.equal(resolve({ session: { status: 'TIMEOUT' } }).action, 'restart-source')
  assert.equal(resolve({ session: { status: 'FAILED' }, restarting: true }).key, 'source-restarting')
  assert.equal(resolve({ session: { status: 'STREAMING' }, connecting: true }).text, '正在连接播放服务')
  assert.equal(resolve({ session: { status: 'STREAMING' }, viewerReconnecting: true }).text, '播放连接恢复中')
  assert.equal(resolve({ session: { status: 'STREAMING' } }).action, 'refresh-playback')
  assert.equal(resolve({ session: { status: 'STREAMING' } }, { loading: true }).key, 'viewer-failed')
  assert.equal(resolve({ status: 'offline' }).text, '设备离线')
  assert.equal(resolve({ status: 'offline', session: { status: 'STREAMING' } }).text, '设备离线')
  assert.equal(resolve({ session: { status: 'CLOSED' } }).action, 'refresh-playback')
})

test('固定摄像头展示配置、网关、RTSP 和推流具体根因且不泄露原始错误', () => {
  const resolve = videoDisplayState.resolveVideoDisplayState
  const fixed = { sourceType: 'FIXED_CAMERA', enabled: true, configReady: true }

  assert.equal(resolve({}, {}, { ...fixed, enabled: false }).text, '固定摄像头已停用')
  assert.equal(resolve({}, {}, { ...fixed, configReady: false }).text, '固定摄像头配置不完整')
  assert.equal(resolve({}, {}, {
    ...fixed,
    gatewayHealth: { status: 'OFFLINE', reasonCode: 'HEARTBEAT_TIMEOUT' }
  }).text, '固定摄像头网关离线')
  assert.equal(resolve({}, {}, {
    ...fixed,
    gatewayHealth: { status: 'ONLINE' },
    streamHealth: { status: 'UNAVAILABLE', reasonCode: 'RTSP_PROBE_FAILED' }
  }).text, '摄像头码流不可达')
  assert.equal(resolve({
    session: { status: 'REQUESTING_CLIENT' }
  }, {}, {
    ...fixed,
    gatewayHealth: { status: 'ONLINE' },
    streamHealth: { status: 'UNAVAILABLE', reasonCode: 'RTSP_PROBE_FAILED' }
  }).text, '正在启动视频源')
  assert.equal(resolve({
    session: {
      status: 'FAILED',
      lastErrorCode: 'PUBLISH_FAILED',
      lastErrorMessage: 'rtsp://user:password@example/internal'
    }
  }, {}, fixed).text, '视频推流启动失败')
  assert.equal(resolve({
    session: { status: 'INTERRUPTED', lastErrorCode: 'PUBLISH_PROCESS_EXITED' }
  }, {}, fixed).text, '视频推流进程异常退出')
  assert.equal(resolve({
    session: { status: 'FAILED', errorCode: 'GATEWAY_COMMAND_QUEUE_FULL' }
  }, {}, fixed).text, '视频网关繁忙，请稍后重试')
  assert.equal(resolve({}, {}, {
    ...fixed,
    gatewayHealth: { status: 'UNKNOWN', reasonCode: 'STATUS_MISSING' },
    streamHealth: { status: 'UNKNOWN', reasonCode: 'STATUS_MISSING' }
  }).text, '摄像头健康状态待确认')
  assert.equal(resolve({}, {}, {
    ...fixed,
    protocolType: 'RTMP',
    gatewayHealth: { status: 'UNKNOWN', reasonCode: 'NOT_APPLICABLE' },
    streamHealth: { status: 'AVAILABLE' }
  }).key, 'not-playing')
  assert.equal(resolve({}, { sourceStartFailed: true }, {
    ...fixed,
    gatewayHealth: { status: 'ONLINE' },
    streamHealth: { status: 'AVAILABLE' }
  }).text, '视频源启动失败')
})

test('机器人视频文案不受固定摄像头原因码映射影响', () => {
  const state = videoDisplayState.resolveVideoDisplayState({
    session: { status: 'FAILED', lastErrorCode: 'RTSP_PROBE_FAILED' }
  }, {}, { sourceType: 'EQUIPMENT' })

  assert.equal(state.text, '视频源启动失败')
  assert.equal(state.action, 'restart-source')
})

test('固定摄像头增量状态保留网关和码流健康原因', () => {
  const module = loadWebsocketRobot()
  const state = {
    robots: [{
      robotId: 'fixed-1',
      sourceType: 'FIXED_CAMERA',
      gatewayHealth: { status: 'ONLINE' },
      streamHealth: { status: 'AVAILABLE' },
      cameras: []
    }]
  }
  const dispatched = []
  const commit = (type, robot) => {
    if (type === 'updateRobot') state.robots[0] = robot
  }
  const dispatch = (...args) => dispatched.push(args)

  module.actions.patchFixedCameraStatuses({ commit, state, dispatch }, [{
    sourceId: 'fixed-1',
    status: 'offline',
    playable: true,
    enabled: true,
    configReady: true,
    gatewayHealth: { status: 'ONLINE', reasonCode: null },
    streamHealth: { status: 'UNAVAILABLE', reasonCode: 'RTSP_PROBE_FAILED' }
  }])

  assert.equal(state.robots[0].gatewayHealth.status, 'ONLINE')
  assert.equal(state.robots[0].streamHealth.status, 'UNAVAILABLE')
  assert.equal(state.robots[0].streamHealth.reasonCode, 'RTSP_PROBE_FAILED')
  assert.equal(dispatched[0][1].robotInfo.streamHealth.reasonCode, 'RTSP_PROBE_FAILED')
})

test('视频工具栏按故障方向复用同一恢复操作位', () => {
  const component = componentDefinition('views/bi/components/VideoTool.vue')
  const methods = component.methods
  const calls = []
  const context = {
    slotKey: 'slot_1',
    recoveryAction: 'restart-source',
    $emit(event, value) { calls.push([event, value]) }
  }

  methods.handleRecoveryAction.call(context)
  context.recoveryAction = 'refresh-playback'
  methods.handleRecoveryAction.call(context)

  assert.deepEqual(calls, [
    ['restartVideoSource', 'slot_1'],
    ['refreshVideo', 'slot_1']
  ])
  assert.equal(component.computed.showRecoveryAction.call({ recoveryAction: 'none', videoStatus: 'stopped' }), false)
  assert.equal(component.computed.showRecoveryAction.call({ recoveryAction: null, videoStatus: 'stopped' }), true)
  assert.equal(component.computed.recoveryActionTitle.call({ recoveryAction: 'restart-source' }), '重新启动视频源')
})

test('视频源恢复只调用现有 Publisher 重启动作', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/VideoBox.vue')
  const camera = { key: 'camera-1', session: { sessionId: 'session-1', status: 'FAILED' } }
  const calls = []
  const context = {
    cameraInfo: camera,
    async restartCamera(value) { calls.push(value) }
  }

  await methods.handleRestartVideoSource.call(context)

  assert.deepEqual(calls, [camera])
})

test('失败或超时会话可复用原 session 重新启动视频源', async () => {
  const restarted = []
  const module = loadWebsocketRobot({
    restartVideoSession: async sessionId => {
      restarted.push(sessionId)
      return { sessionId, status: 'REQUESTING_CLIENT', viewerCount: 1 }
    }
  })
  const state = {
    cameras: {},
    activeCameras: {},
    stoppedSessionIds: new Set()
  }
  const context = cameraActionContext(module.actions, state)

  for (const status of ['FAILED', 'TIMEOUT']) {
    const camera = {
      key: `camera-${status}`,
      watching: true,
      stopped: false,
      stopping: false,
      restarting: false,
      session: { sessionId: `session-${status}`, status }
    }
    state.cameras[camera.key] = camera
    await module.actions.performRestartCamera(context, camera)
    assert.equal(state.cameras[camera.key].session.status, 'REQUESTING_CLIENT')
    assert.equal(state.cameras[camera.key].restarting, false)
  }

  assert.deepEqual(restarted, ['session-FAILED', 'session-TIMEOUT'])
})

test('启动中会话不接受源重启且不会误停录像', async () => {
  let recordingStops = 0
  let restarts = 0
  const module = loadWebsocketRobot({
    restartVideoSession: async () => { restarts += 1 }
  })
  const camera = {
    key: 'camera-1',
    recordingActive: true,
    stopped: false,
    stopping: false,
    restarting: false,
    session: { sessionId: 'session-1', status: 'REQUESTING_CLIENT' }
  }
  const state = {
    cameras: { [camera.key]: camera },
    activeCameras: {},
    stoppedSessionIds: new Set()
  }
  const context = cameraActionContext(module.actions, state)
  const dispatch = context.dispatch
  context.dispatch = (type, payload) => {
    if (type === 'stopCameraRecording') {
      recordingStops += 1
      return Promise.resolve()
    }
    return dispatch(type, payload)
  }

  await module.actions.performRestartCamera(context, camera)

  assert.equal(recordingStops, 0)
  assert.equal(restarts, 0)
})

test('视频心跳补齐漏收的失败状态且不操作媒体连接', async () => {
  const module = loadWebsocketRobot({
    heartbeatVideoSession: async () => ({
      sessionId: 'session-1',
      status: 'FAILED',
      viewerCount: 1,
      lastErrorCode: 'VIDEO_START_FAILED',
      lastErrorMessage: '视频源启动失败'
    })
  })
  const camera = {
    key: 'camera-1',
    watching: true,
    stopped: false,
    stopping: false,
    recordingSyncedAt: Date.now(),
    viewerCount: 1,
    status: 'REQUESTING_CLIENT',
    session: { sessionId: 'session-1', status: 'REQUESTING_CLIENT' }
  }
  module.state.cameras = { [camera.key]: camera }
  module.state.activeIncomingCall = null
  module.state.heartbeatPending = false
  const commit = (type, payload) => {
    if (type === 'setCamera') module.state.cameras[payload.key] = payload
  }

  await module.actions.heartbeatViewers({ state: module.state, commit })

  assert.equal(module.state.cameras[camera.key].status, 'FAILED')
  assert.equal(module.state.cameras[camera.key].session.lastErrorCode, 'VIDEO_START_FAILED')
  assert.equal(module.state.heartbeatPending, false)
})

test('视频心跳确认旧会话已关闭时按原观看意图新建会话', async () => {
  const closed = new Error('Request failed with status code 409')
  closed.response = { status: 409, data: { message: '视频会话已关闭' } }
  const module = loadWebsocketRobot({
    heartbeatVideoSession: async () => { throw closed }
  })
  const camera = {
    key: 'camera-1',
    robotId: 'robot-1',
    watching: true,
    stopped: false,
    stopping: false,
    intercomActive: false,
    recordingSyncedAt: Date.now(),
    attachTargets: { slot_1: 'monitor-' },
    session: { sessionId: 'closed-session', status: 'STREAMING' }
  }
  const robot = { robotId: 'robot-1' }
  module.state.cameras = { [camera.key]: camera }
  module.state.activeCameras = { [camera.key]: { robot, camera } }
  module.state.activeIncomingCall = null
  module.state.heartbeatPending = false
  const calls = []
  const commit = (type, payload) => {
    if (type === 'setCamera') module.state.cameras[payload.key] = payload
  }
  const dispatch = async (type, payload) => {
    calls.push([type, payload])
  }

  await module.actions.heartbeatViewers({ state: module.state, commit, dispatch })

  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], 'startCamera')
  assert.equal(calls[0][1].robot, robot)
  assert.equal(calls[0][1].camera.session.sessionId, 'closed-session')
  assert.equal(calls[0][1].consumerId, 'slot_1')
  assert.equal(calls[0][1].prefixId, 'monitor-')
  assert.equal(module.state.cameras[camera.key].viewerReconnecting, true)
  assert.equal(module.state.heartbeatPending, false)
})

test('视频心跳从已连接 Room 补回漏收的视频轨道', async () => {
  const module = loadWebsocketRobot({
    heartbeatVideoSession: async () => ({
      sessionId: 'session-1',
      status: 'STREAMING',
      viewerCount: 1
    })
  })
  const track = { kind: 'video', sid: 'TR_video', attach() {} }
  const publication = { track }
  const room = {
    state: 'connected',
    remoteParticipants: new Map([['publisher', {
      trackPublications: new Map([['TR_video', publication]])
    }]])
  }
  const camera = {
    key: 'camera-1',
    watching: true,
    stopped: false,
    stopping: false,
    status: 'STREAMING',
    hasVideo: false,
    remoteVideoTrack: null,
    room,
    session: { sessionId: 'session-1', status: 'STREAMING' }
  }
  module.state.cameras = { [camera.key]: camera }
  module.state.activeIncomingCall = null
  module.state.heartbeatPending = false
  const context = cameraActionContext(module.actions, module.state)

  await module.actions.heartbeatViewers(context)

  assert.equal(module.state.cameras[camera.key].hasVideo, true)
  assert.equal(module.state.cameras[camera.key].remoteVideoTrack, track)
  assert.equal(module.state.cameras[camera.key].viewerReconnecting, false)
})

test('多画面心跳按唯一会话并发执行', () => {
  const source = read('store/modules/websocket-robot.js')
  const heartbeat = source.slice(source.indexOf('async heartbeatViewers'), source.indexOf('// 启动摄像头'))

  assert.match(heartbeat, /new Map\(\)/)
  assert.match(heartbeat, /Promise\.allSettled\(requests\)/)
  assert.match(heartbeat, /Promise\.allSettled\(jobs\)/)
  assert.match(heartbeat, /camera\.session = nextSession/)
  assert.match(heartbeat, /camera\.status = nextSession\.status/)
  assert.doesNotMatch(heartbeat, /for \(const camera of allCameras\(\)\)/)
})

test('固定摄像头等待真实视频轨道且关闭操作可取消等待', () => {
  const source = read('store/modules/websocket-robot.js')

  assert.match(source, /FIXED_CAMERA_TRACK_WAIT_MS = 15000/)
  assert.match(source, /waitForVideo: fixedCamera/)
  assert.match(source, /await waitForVideoTrack\(/)
  assert.match(source, /固定摄像头视频等待已取消/)
  assert.match(source, /stopOperations\.has\(camera\.key\)/)
  assert.match(source, /targets\.length > 0\) return false/)
  assert.match(source, /stopVideoSession\(createdSessionId/)
})

test('同路多画面时局部 stop 不进 stopOperations，起流 attachTargets 以 store 为准', () => {
  const source = read('store/modules/websocket-robot.js')
  const stopCamera = source.slice(source.indexOf('stopCamera({ commit, state, dispatch }, data)'), source.indexOf('// 停止摄像头。传入 consumerId'))
  const startCamera = source.slice(source.indexOf('async performStartCamera'), source.indexOf('stopCamera({ commit, state, dispatch }, data)'))

  assert.match(stopCamera, /if \(remainingTargets\.length > 0\) return dispatch\('performStopCamera', data\)/)
  assert.doesNotMatch(stopCamera, /remainingTargets\.length > 0 && !starting/)
  assert.match(stopCamera, /remainingTargets\.length > 0\)[\s\S]*cancelViewerReconnect\(key\)/)
  assert.match(source, /const prefixStillOwned = Object\.values\(nextTargets\)\.includes\(attachPrefix\)/)
  assert.match(source, /if \(!prefixStillOwned\) detachCameraMedia\(camera, attachPrefix\)/)
  assert.match(startCamera, /latestTargets/)
  assert.match(startCamera, /尽早写入 attachTargets/)
  assert.match(startCamera, /仍有其它画面时：保留会话/)
})

test('地图弹框固定摄像头不抢占全局 prefixId', () => {
  const source = read('views/bi/gis/globalMap/popup/Robot1.vue')
  const startFn = source.slice(source.indexOf('async startFixedCameraVideo'), source.indexOf('async stopFixedCameraVideo'))
  assert.match(startFn, /prefixId: this\.prefixId/)
  assert.match(startFn, /不改全局 prefixId/)
  assert.doesNotMatch(startFn, /setPrefixId\(this\.prefixId\)/)
})

test('浏览器 Track 或 Room 异常只恢复 viewer，不重启共享 Publisher', () => {
  const source = read('store/modules/websocket-robot.js')
  const connectAction = source.slice(
    source.indexOf('async performConnectLiveKit'),
    source.indexOf('// 只恢复当前浏览器')
  )

  assert.match(connectAction, /RoomEvent\.Reconnected/)
  assert.match(connectAction, /reconnectViewerAfterCurrentConnect\(dispatch, state, current\.key, sessionId\)/)
  assert.match(connectAction, /DUPLICATE_IDENTITY/)
  assert.match(connectAction, /viewer reconnected/)
  assert.match(connectAction, /isSameLiveKitTrack\(current\.remoteVideoTrack, track\)/)
  assert.match(connectAction, /restoreVideoTrack\(current, room, state, track\)/)
  assert.match(connectAction, /RoomEvent\.TrackUnsubscribed[\s\S]*!liveKitRoomReusable\(current\)[\s\S]*beginViewerRecovery\(commit, dispatch, state, current, sessionId\)/)
  assert.match(source, /scheduleViewerReconnect\(dispatch, state, key, sessionId/)
  assert.match(source, /viewerReconnectDelay\(attempt\)/)
  assert.match(source, /cancelViewerReconnect\(key\)/)
  assert.doesNotMatch(connectAction, /dispatch\('restartCamera'/)
})

test('旧 Track 迟到退出不会清空已接入的新 Track', () => {
  const oldTrack = { sid: 'TR_old' }
  const newTrack = { sid: 'TR_new' }

  assert.equal(trackRecovery.isSameLiveKitTrack(newTrack, oldTrack), false)
  assert.equal(trackRecovery.isSameLiveKitTrack(newTrack, { sid: 'TR_new' }), true)
  assert.equal(trackRecovery.isSameLiveKitTrack(newTrack, newTrack), true)
})

test('Viewer 重连按指数退避并封顶三十秒', () => {
  assert.equal(trackRecovery.viewerReconnectDelay(0), 1000)
  assert.equal(trackRecovery.viewerReconnectDelay(1), 2000)
  assert.equal(trackRecovery.viewerReconnectDelay(4), 16000)
  assert.equal(trackRecovery.viewerReconnectDelay(20), 30000)
})

test('viewer 换证连续失败后持续退避重试直至恢复', async () => {
  const timers = []
  const module = loadWebsocketRobot({}, {
    setTimeout(callback, delay) {
      timers.push({ callback, delay })
      return timers.length
    },
    clearTimeout() {},
    console: { ...console, error() {} }
  })
  const key = 'camera-retry'
  const sessionId = 'session-retry'
  const state = {
    cameras: {
      [key]: {
        key,
        watching: true,
        stopped: false,
        stopping: false,
        session: { sessionId, status: 'STREAMING' },
        room: null
      }
    }
  }
  let attempts = 0
  const dispatch = async (type, payload) => {
    assert.equal(type, 'connectLiveKit')
    assert.equal(payload.refreshToken, true)
    assert.equal(payload.throwOnError, true)
    attempts += 1
    if (attempts <= 3) throw new Error('viewer token endpoint unavailable')
    state.cameras[key].room = { state: 'connected' }
    state.cameras[key].hasVideo = true
  }

  module.__testHooks.scheduleViewerReconnect(dispatch, state, key, sessionId)
  for (let index = 0; index < 4; index++) {
    const timer = timers[index]
    assert.ok(timer)
    await timer.callback()
  }

  assert.equal(attempts, 4)
  assert.deepEqual(timers.map(timer => timer.delay), [0, 2000, 4000, 8000])
})

test('Room 已连接但发布 Track 暂时缺失时保留 viewer 等待自动重订阅', () => {
  const timers = []
  const module = loadWebsocketRobot({}, {
    setTimeout(callback, delay) {
      timers.push({ callback, delay })
      return timers.length
    },
    clearTimeout() {},
    console: { ...console, error() {} }
  })
  const key = 'camera-missing-track'
  const sessionId = 'session-missing-track'
  const state = {
    cameras: {
      [key]: {
        key,
        watching: true,
        stopped: false,
        stopping: false,
        hasVideo: false,
        session: { sessionId, status: 'ROOM_READY' },
        room: { state: 'connected' }
      }
    }
  }
  const calls = []
  const dispatch = (type, payload) => calls.push({ type, payload })

  module.__testHooks.scheduleViewerReconnect(dispatch, state, key, sessionId, 2000)
  assert.equal(calls.length, 0)
  assert.equal(timers.length, 0)
})

test('人工刷新只调用 viewer 恢复，不 stop/start 会话', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const camera = { key: 'camera-1', session: { sessionId: 'session-1' } }
  const calls = []
  const context = {
    ZQL_videosInfos: { slot_1: { ...camera } },
    cameras: { 'camera-1': camera },
    $set(target, field, value) { target[field] = value },
    async recoverCameraPlayback(value) { calls.push(['recover', value.key]) },
    rebindCameraTracks(values) { calls.push(['rebind', values[0].key]) },
    async startCamera() { throw new Error('不应调用 startCamera') },
    async stopCamera() { throw new Error('不应调用 stopCamera') }
  }

  await methods.refreshVideo.call(context, 'slot_1')

  assert.deepEqual(calls, [['recover', 'camera-1'], ['rebind', 'camera-1']])
})

test('play 被浏览器拒绝时只重新 attach 已有 Track', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const camera = { key: 'camera-1', remoteVideoTrack: { sid: 'track-1' } }
  const calls = []
  const video = {
    dataset: { userPaused: '1' },
    play: () => Promise.reject(new Error('NotAllowedError'))
  }
  const context = {
    cameras: { 'camera-1': camera },
    rebindCameraTracks(values) { calls.push(values[0].remoteVideoTrack.sid) },
    recoverCameraPlayback() { throw new Error('已有 Track 时不应重连 Room') }
  }

  methods.resumeVideo.call(context, video, camera)
  await new Promise(resolve => setImmediate(resolve))

  assert.deepEqual(calls, ['track-1', 'track-1'])
  assert.equal(video.dataset.userPaused, undefined)
})

test('摄像头操作使用在途表，新 session 不复用旧 Room', () => {
  const source = read('store/modules/websocket-robot.js')

  assert.match(source, /const startOperations = new Map\(\)/)
  assert.match(source, /const stopOperations = new Map\(\)/)
  assert.match(source, /const connectOperations = new Map\(\)/)
  assert.match(source, /const restartOperations = new Map\(\)/)
  assert.match(source, /stopping\.then\(\(\) => dispatch\('startCamera', payload\)\)/)
  assert.match(source, /if \(starting\) await starting\.catch\(\(\) => null\)/)
  assert.match(source, /stored\.session\.sessionId === session\.sessionId/)
  assert.match(source, /stored\.session\.roomName === session\.roomName/)
  assert.doesNotMatch(source, /setTimeout\(\(\) => \{\s*camera\.restarting = false/)
})

test('start 返回新 session 时必须重连 Room，不能沿用旧 Room', async () => {
  const oldRoom = { state: 'connected' }
  const newRoom = { state: 'connected' }
  const module = loadWebsocketRobot({
    createVideoSession: async () => ({
      sessionId: 'session-new',
      roomName: 'room-new',
      status: 'STREAMING',
      viewerCount: 1
    })
  })
  const camera = {
    key: 'camera-1',
    deviceId: 'camera-1',
    quality: 'sub',
    watching: true,
    stopped: false,
    room: oldRoom,
    session: { sessionId: 'session-old', roomName: 'room-old' },
    attachTargets: {}
  }
  const state = {
    cameras: { [camera.key]: camera },
    activeCameras: {},
    stoppedSessionIds: new Set(),
    prefixId: ''
  }
  const context = cameraActionContext(module.actions, state)
  let connects = 0
  context.dispatch = async (type, payload) => {
    if (type === 'connectLiveKit') {
      connects += 1
      payload.camera.room = newRoom
      context.commit('setCamera', { ...payload.camera })
      return
    }
    return module.actions[type](context, payload)
  }

  await module.actions.performStartCamera(context, {
    robot: { robotId: 'robot-1', status: 'online' },
    camera
  })

  assert.equal(connects, 1)
  assert.equal(state.cameras[camera.key].session.sessionId, 'session-new')
  assert.equal(state.cameras[camera.key].session.roomName, 'room-new')
  assert.equal(state.cameras[camera.key].room, newRoom)
})

test('启动响应晚到且期间 stop/start 时，最终只保留第二次启动的新会话', async () => {
  let releaseFirstStart
  const firstStart = new Promise(resolve => { releaseFirstStart = resolve })
  const sessions = [
    firstStart,
    Promise.resolve({ sessionId: 'session-2', roomName: 'room-2', status: 'STREAMING', viewerCount: 1 })
  ]
  const stoppedSessions = []
  let createCalls = 0
  const module = loadWebsocketRobot({
    createVideoSession: async () => sessions[createCalls++],
    stopVideoSession: async sessionId => {
      stoppedSessions.push(sessionId)
      return { status: 'CLOSED', viewerCount: 0 }
    }
  })
  const camera = {
    key: 'camera-1',
    robotId: 'robot-1',
    deviceId: 'camera-1',
    quality: 'sub',
    watching: false,
    stopped: true,
    attachTargets: {}
  }
  const state = {
    cameras: { [camera.key]: camera },
    activeCameras: {},
    stoppedSessionIds: new Set(),
    prefixId: ''
  }
  const context = cameraActionContext(module.actions, state)
  const originalDispatch = context.dispatch
  context.dispatch = async (type, payload) => {
    if (type === 'connectLiveKit') {
      payload.camera.room = { state: 'connected', disconnect: async () => {} }
      context.commit('setCamera', { ...payload.camera })
      return
    }
    return originalDispatch(type, payload)
  }
  const payload = { robot: { robotId: 'robot-1', status: 'online' }, camera }

  const starting = module.actions.startCamera(context, payload)
  await Promise.resolve()
  const stopping = module.actions.stopCamera(context, { key: camera.key })
  const startingAgain = module.actions.startCamera(context, payload)
  releaseFirstStart({ sessionId: 'session-1', roomName: 'room-1', status: 'STREAMING', viewerCount: 1 })
  await Promise.all([starting, stopping, startingAgain])

  assert.equal(createCalls, 2)
  assert.deepEqual(stoppedSessions, ['session-1'])
  assert.equal(state.cameras[camera.key].session.sessionId, 'session-2')
  assert.equal(state.cameras[camera.key].session.roomName, 'room-2')
  assert.equal(state.cameras[camera.key].watching, true)
  assert.equal(state.cameras[camera.key].stopped, false)
})

test('固定摄像头启动中关闭会立即撤销消费者且迟到启动不得重新选中', async () => {
  let releaseStart
  const pendingSession = new Promise(resolve => { releaseStart = resolve })
  const module = loadWebsocketRobot({
    createVideoSession: async () => pendingSession
  })
  const camera = {
    key: 'fixed-camera-1',
    robotId: 'fixed-1',
    deviceId: 'fixed-1',
    sourceType: 'FIXED_CAMERA',
    attachTargets: {}
  }
  const robot = {
    robotId: 'fixed-1',
    sourceType: 'FIXED_CAMERA',
    status: 'online',
    enabled: true,
    configReady: true
  }
  const state = {
    cameras: { [camera.key]: camera },
    activeCameras: {},
    stoppedSessionIds: new Set(),
    prefixId: ''
  }
  const context = cameraActionContext(module.actions, state)
  const originalDispatch = context.dispatch
  context.dispatch = async (type, payload) => {
    if (type === 'connectLiveKit') {
      payload.camera.room = { state: 'connected', disconnect: async () => {} }
      return
    }
    return originalDispatch(type, payload)
  }
  const consumerId = 'patrol-monitor-wall:test-video-div'
  const start = module.actions.startCamera(context, {
    robot,
    camera,
    consumerId,
    prefixId: 'test-video-div'
  })
  await Promise.resolve()

  const stop = module.actions.stopCamera(context, {
    ...camera,
    consumerId,
    prefixId: 'test-video-div'
  })
  assert.equal(state.cameras[camera.key].attachTargets[consumerId], undefined)

  releaseStart({
    sessionId: 'fixed-session-1',
    roomName: 'fixed-room-1',
    status: 'ROOM_READY',
    viewerCount: 1
  })
  await Promise.all([start, stop])

  assert.equal(state.activeCameras[camera.key], undefined)
  assert.equal(state.cameras[camera.key].watching, false)
  assert.equal(state.cameras[camera.key].session, null)
})
