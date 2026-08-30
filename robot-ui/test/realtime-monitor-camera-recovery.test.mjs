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
