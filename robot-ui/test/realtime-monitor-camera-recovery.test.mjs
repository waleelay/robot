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

function componentMethods(path) {
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
  return exports.default.methods
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
      if (name === '@/utils/api-url') return { withApiPrefix: value => value, withBigscreenApiPrefix: value => value }
      return {}
    }
  })
  return exports
}

function loadWebsocketRobot(apiOverrides = {}) {
  const compiled = require('@babel/core').transformSync(read('store/modules/websocket-robot.js'), {
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
      if (name === '@/auth') return { bearerToken: () => '' }
      if (name.includes('prefer-live-robot-fields')) return robotStateHelpers
      if (name.includes('pick-default-camera')) return cameraHelpers
      if (name.includes('livekit-user-pause')) return { attachTrackRespectingUserPause: () => true }
      if (name.includes('media-websocket-reconnect')) {
        return {
          mediaReconnectDelay: () => 0,
          isSustainedAuthorizationFailure: () => false,
          shouldReconnectMedia: () => false
        }
      }
      return {}
    }
  })
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

test('原宫格中的固定摄像头恢复在线后只重建一次会话', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const key = 'fixed-1-fixed-1-fixed-1'
  const camera = { key, robotId: 'fixed-1', deviceId: 'fixed-1', cameraId: 'fixed-1' }
  const robot = {
    robotId: 'fixed-1',
    sourceType: 'FIXED_CAMERA',
    status: 'online',
    enabled: true,
    configReady: true,
    playable: true,
    cameras: [camera]
  }
  let starts = 0
  const context = {
    ZQL_videosInfos: { slot_1: { ...camera, robotId: 'fixed-1' } },
    ZQL_playingSource: { slot_1: key },
    robots: [robot],
    cameras: { [key]: camera },
    recoveringFixedCameras: {},
    isFixedCameraRobot: methods.isFixedCameraRobot,
    $set(target, field, value) { target[field] = value },
    $delete(target, field) { delete target[field] },
    async startCamera() {
      starts += 1
      context.cameras[key] = { ...camera, session: { status: 'STREAMING' } }
    }
  }

  await methods.syncVideoSlots.call(context)
  await methods.syncVideoSlots.call(context)

  assert.equal(starts, 1)
  assert.equal(context.ZQL_videosInfos.slot_1.robot.robotId, 'fixed-1')
})

test('视频启动失败后清理预占宫格', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const context = {
    ZQL_playingSource: {},
    ZQL_videosInfos: {},
    $set(target, field, value) { target[field] = value },
    clearSlot: methods.clearSlot,
    async startCamera() { throw new Error('创建失败') }
  }

  const started = await methods.start.call(context, { robotId: 'robot-1' }, {
    index: 'slot_1',
    data: { key: 'camera-1' }
  })

  assert.equal(started, false)
  assert.equal(context.ZQL_playingSource.slot_1, null)
  assert.equal(context.ZQL_videosInfos.slot_1, null)
})

test('缩小宫格先收缩播放意图且连续切换不会恢复已移除视频', async () => {
  const methods = componentMethods('views/bi/patrol/monitor/first/LeftVideo.vue')
  const items = Array.from({ length: 9 }, (_, index) => ({
    key: `camera-${index + 1}`,
    robotId: `robot-${index + 1}`
  }))
  let releaseStops
  const stopGate = new Promise(resolve => { releaseStops = resolve })
  const context = {
    ZQL_videosInfos: Object.fromEntries(items.map((item, index) => [`slot_${index + 1}`, item])),
    ZQL_playingSource: Object.fromEntries(items.map((item, index) => [`slot_${index + 1}`, item.key])),
    cameras: Object.fromEntries(items.map(item => [item.key, item])),
    robots: [],
    checkedIds: items.map(item => item.key),
    lastCheckedIds: items.map(item => item.key),
    slotDevices: new Array(9).fill(null),
    orderedPlayingVideoInfos: methods.orderedPlayingVideoInfos,
    currentVisibleCameras: methods.currentVisibleCameras,
    async stopCamera() { await stopGate },
    async $nextTick() {},
    rebindCameraTracks() {}
  }

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

test('视频续期请求使用短超时且关闭全局错误提示', async () => {
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
    assert.equal(options.skipErrorMessage, true)
  })
})

test('多画面心跳按唯一会话并发执行', () => {
  const source = read('store/modules/websocket-robot.js')
  const heartbeat = source.slice(source.indexOf('async heartbeatViewers'), source.indexOf('// 启动摄像头'))

  assert.match(heartbeat, /new Map\(\)/)
  assert.match(heartbeat, /Promise\.allSettled\(requests\)/)
  assert.match(heartbeat, /Promise\.allSettled\(jobs\)/)
  assert.doesNotMatch(heartbeat, /for \(const camera of allCameras\(\)\)/)
})

test('固定摄像头等待真实视频轨道，超时后由启动流程清理会话', () => {
  const source = read('store/modules/websocket-robot.js')

  assert.match(source, /FIXED_CAMERA_TRACK_WAIT_MS = 15000/)
  assert.match(source, /waitForVideo: fixedCamera/)
  assert.match(source, /await waitForVideoTrack\(room/)
  assert.match(source, /stopVideoSession\(createdSessionId/)
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
  assert.doesNotMatch(connectAction, /dispatch\('restartCamera'/)
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
