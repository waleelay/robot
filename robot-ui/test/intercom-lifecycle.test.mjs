import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const helperSource = readFileSync(new URL('../src/utils/livekit-local-media.js', import.meta.url), 'utf8')
const registrySource = readFileSync(new URL('../src/utils/intercom-operation-registry.js', import.meta.url), 'utf8')
const coordinatorSource = readFileSync(new URL('../src/utils/media-call-coordinator.js', import.meta.url), 'utf8')
const terminalRegistrySource = readFileSync(new URL('../src/utils/call-terminal-registry.js', import.meta.url), 'utf8')
const websocketSource = readFileSync(new URL('../src/store/modules/websocket-robot.js', import.meta.url), 'utf8')
const fieldCallSource = readFileSync(new URL('../src/store/modules/fieldCall.js', import.meta.url), 'utf8')
const mapPopupSource = readFileSync(new URL('../src/views/bi/gis/globalMap/popup/common.js', import.meta.url), 'utf8')
const debugAppSource = readFileSync(new URL('../../frontend/src/App.vue', import.meta.url), 'utf8')
const debugHelperSource = readFileSync(new URL('../../frontend/src/livekit-local-media.js', import.meta.url), 'utf8')
const mediaApiSource = readFileSync(new URL('../src/api/media.js', import.meta.url), 'utf8')

const helperModule = await import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)
const registryModule = await import(`data:text/javascript;base64,${Buffer.from(registrySource).toString('base64')}`)
const coordinatorModule = await import(`data:text/javascript;base64,${Buffer.from(coordinatorSource).toString('base64')}`)
const terminalRegistryModule = await import(`data:text/javascript;base64,${Buffer.from(terminalRegistrySource).toString('base64')}`)
const require = createRequire(import.meta.url)

test('对讲启动注册表保证单键唯一并等待取消操作真正结束', async() => {
  const registry = new registryModule.IntercomOperationRegistry()
  const operation = registry.begin('camera-1')
  assert.ok(operation)
  assert.equal(registry.begin('camera-1'), null)

  let cancelFinished = false
  const cancelling = registry.cancel('camera-1', 'route-leave').then(result => {
    cancelFinished = true
    return result
  })
  await Promise.resolve()
  assert.equal(operation.cancelled, true)
  assert.equal(operation.cancelReason, 'route-leave')
  assert.equal(operation.controller.signal.aborted, true)
  assert.equal(cancelFinished, false)

  registry.finish('camera-1', operation)
  assert.equal(await cancelling, true)
  assert.equal(cancelFinished, true)
  assert.equal(registry.has('camera-1'), false)
})

test('挂断会取消发布并停止浏览器麦克风轨道', async() => {
  let unpublished = 0
  let stopped = 0
  const track = {
    stop() { stopped += 1 },
    mediaStreamTrack: { readyState: 'ended', stop() { stopped += 1 } }
  }
  const participant = {
    getTrackPublication: source => source === 'microphone' ? { source, track } : null,
    trackPublications: new Map(),
    async unpublishTrack(value, stopOnUnpublish) {
      assert.equal(value, track)
      assert.equal(stopOnUnpublish, false)
      unpublished += 1
    }
  }

  await helperModule.releaseLocalMicrophone({ localParticipant: participant })

  assert.equal(unpublished, 1)
  assert.equal(stopped, 1)
})

test('取消发布失败时仍强制停止底层采集', async() => {
  let stopped = 0
  const track = {
    stop() { stopped += 1 },
    mediaStreamTrack: { readyState: 'ended', stop() {} }
  }
  const participant = {
    getTrackPublication: () => ({ source: 'microphone', track }),
    trackPublications: new Map(),
    async unpublishTrack() { throw new Error('disconnected') }
  }

  const originalWarn = console.warn
  console.warn = () => {}
  try {
    await helperModule.releaseLocalMicrophone({ localParticipant: participant })
  } finally {
    console.warn = originalWarn
  }

  assert.equal(stopped, 1)
})

test('取消发布永久挂起时也立即停止底层采集', async() => {
  let stopped = 0
  const track = {
    stop() { stopped += 1 },
    mediaStreamTrack: { readyState: 'ended', stop() {} }
  }
  const release = helperModule.releaseLocalMicrophone({
    localParticipant: {
      getTrackPublication: () => ({ source: 'microphone', track }),
      trackPublications: new Map(),
      unpublishTrack: () => new Promise(() => {})
    }
  })
  await Promise.race([
    release,
    new Promise((_, reject) => setTimeout(() => reject(new Error('release timeout')), 100))
  ])
  assert.equal(stopped, 1)
})

test('麦克风启用被取消时立即返回且迟到成功的轨道仍会被停止', async() => {
  let resolveEnable
  let publication = null
  let stopped = 0
  const track = {
    stop() { stopped += 1 },
    mediaStreamTrack: { readyState: 'ended', stop() {} }
  }
  const room = {
    localParticipant: {
      getTrackPublication: () => publication,
      trackPublications: new Map(),
      unpublishTrack: async() => {},
      setMicrophoneEnabled: () => new Promise(resolve => {
        resolveEnable = () => {
          publication = { source: 'microphone', track }
          resolve()
        }
      })
    }
  }
  const operation = { controller: new AbortController() }
  const enabling = helperModule.enableLocalMicrophone(room, operation)
  operation.controller.abort()

  await assert.rejects(Promise.race([
    enabling,
    new Promise((_, reject) => setTimeout(() => reject(new Error('cancel timeout')), 100))
  ]), /对讲启动已取消/)
  assert.equal(stopped, 0)

  resolveEnable()
  await Promise.resolve()
  await Promise.resolve()
  assert.equal(stopped, 1)
})

test('Room 连接被取消时立即返回且迟到成功后再次断开', async() => {
  let resolveConnect
  let disconnects = 0
  const room = {
    connect: () => new Promise(resolve => { resolveConnect = resolve }),
    async disconnect() { disconnects += 1 }
  }
  const operation = { controller: new AbortController() }
  const connecting = helperModule.connectRoomWithCancellation(room, 'wss://livekit', 'token', operation)
  await Promise.resolve()
  operation.controller.abort()
  await assert.rejects(Promise.race([
    connecting,
    new Promise((_, reject) => setTimeout(() => reject(new Error('cancel timeout')), 100))
  ]), /对讲启动已取消/)
  assert.ok(disconnects >= 1)

  resolveConnect()
  for (let index = 0; index < 10 && disconnects < 2; index += 1) {
    await new Promise(resolve => setImmediate(resolve))
  }
  assert.ok(disconnects >= 2)
})

test('Room disconnect 永久挂起时在截止时间后继续本地收口', async() => {
  const originalWarn = console.warn
  console.warn = () => {}
  try {
    const disconnected = await Promise.race([
      helperModule.disconnectRoomSafely({ disconnect: () => new Promise(() => {}) }, {
        timeoutMs: 5,
        context: '测试 Room'
      }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('disconnect timeout')), 100))
    ])
    assert.equal(disconnected, false)
  } finally {
    console.warn = originalWarn
  }
})

test('调试前端的 Room 断开同样有超时边界且页面生命周期统一复用', () => {
  assert.match(debugHelperSource, /export const ROOM_DISCONNECT_TIMEOUT_MS = 3000/)
  assert.match(debugHelperSource, /export async function disconnectRoomSafely/)
  assert.match(debugHelperSource, /Promise\.race/)
  assert.match(debugAppSource, /disconnectRoomSafely\(camera\.room, \{ context: '退出调试页面时的 LiveKit Room' \}\)/)
  assert.match(debugAppSource, /disconnectRoomSafely\(camera\.room, \{ context: '对讲启动回滚时的 LiveKit Room' \}\)/)
  assert.match(debugAppSource, /disconnectRoomSafely\(camera\.room, \{ context: '对讲挂断时的 LiveKit Room' \}\)/)
})

test('来电终态登记有界保留并阻止迟到 accepted 重新激活', () => {
  const timers = new Map()
  let sequence = 0
  const timerApi = {
    setTimeout(handler) {
      const id = ++sequence
      timers.set(id, handler)
      return id
    },
    clearTimeout(id) { timers.delete(id) }
  }
  const registry = new terminalRegistryModule.CallTerminalRegistry(60000, timerApi)
  assert.equal(registry.mark('call-1', 'local-reject'), true)
  assert.equal(registry.has('call-1'), true)
  assert.equal(registry.reason('call-1'), 'local-reject')
  timers.values().next().value()
  assert.equal(registry.has('call-1'), false)
})

test('普通来电与现场来电都以统一终态登记拦截乱序 accepted', () => {
  assert.match(websocketSource, /incomingCallTerminals\.mark\(callId, 'local-reject'\)/)
  assert.match(websocketSource, /if \(incomingCallTerminals\.has\(callId\)\)/)
  assert.match(fieldCallSource, /fieldCallTerminals\.mark\(callId, 'local-reject'\)/)
  assert.match(fieldCallSource, /if \(fieldCallTerminals\.has\(callId\)\)/)
})

test('普通对讲和现场呼叫共享唯一浏览器通话租约', () => {
  const coordinator = new coordinatorModule.MediaCallCoordinator()
  const regular = coordinator.acquire('robot:camera-1', 'robot-intercom')
  assert.ok(regular)
  assert.equal(coordinator.acquire('field:call-1', 'field-call'), null)
  assert.equal(coordinator.activate(regular), true)
  assert.equal(coordinator.holder().phase, 'ACTIVE')
  assert.equal(coordinator.release({ id: -1 }), false)
  assert.equal(coordinator.release(regular), true)
  assert.ok(coordinator.acquire('field:call-1', 'field-call'))
})

test('轨道 stop 失败时仍停止底层 MediaStreamTrack', async() => {
  let mediaStreamStopped = 0
  const track = {
    stop() { throw new Error('track stop failed') },
    mediaStreamTrack: {
      readyState: 'live',
      stop() { mediaStreamStopped += 1 }
    }
  }
  const participant = {
    getTrackPublication: () => ({ source: 'microphone', track }),
    trackPublications: new Map(),
    async unpublishTrack() {}
  }
  const originalWarn = console.warn
  console.warn = () => {}
  try {
    await helperModule.releaseLocalMicrophone({ localParticipant: participant })
  } finally {
    console.warn = originalWarn
  }
  assert.equal(mediaStreamStopped, 1)
})

test('远端音频清理失败不阻断元素移除和 Room 断开', async() => {
  let removed = 0
  let disconnected = 0
  const originalWarn = console.warn
  console.warn = () => {}
  try {
    await helperModule.releaseIntercomClientMedia({
      room: {
        localParticipant: {
          getTrackPublication: () => null,
          trackPublications: new Map()
        },
        async disconnect() { disconnected += 1 }
      },
      remoteAudioTrack: { detach() { throw new Error('detach failed') } },
      remoteAudioElement: { remove() { removed += 1 } }
    }, { disconnectRoom: true })
  } finally {
    console.warn = originalWarn
  }
  assert.equal(removed, 1)
  assert.equal(disconnected, 1)
})

test('Room disconnect 同步抛错时清理函数仍正常返回', async() => {
  const originalWarn = console.warn
  console.warn = () => {}
  try {
    await helperModule.releaseIntercomClientMedia({
      room: {
        localParticipant: {
          getTrackPublication: () => null,
          trackPublications: new Map()
        },
        disconnect() { throw new Error('sync disconnect failed') }
      }
    }, { disconnectRoom: true })
  } finally {
    console.warn = originalWarn
  }
})

test('已有视频 Room 回滚对讲时可保留远端音频', async() => {
  let detached = 0
  let removed = 0
  await helperModule.releaseIntercomClientMedia({
    room: { localParticipant: { getTrackPublication: () => null, trackPublications: new Map() } },
    remoteAudioTrack: { detach() { detached += 1 } },
    remoteAudioElement: { remove() { removed += 1 } }
  }, { preserveRemoteAudio: true })
  assert.equal(detached, 0)
  assert.equal(removed, 0)
})

test('主动断开 Room 会留下意图标记以避免按异常断线重复收口', async() => {
  const room = {
    localParticipant: { getTrackPublication: () => null, trackPublications: new Map() },
    async disconnect() {}
  }
  await helperModule.releaseIntercomClientMedia({ room }, { disconnectRoom: true })
  assert.equal(helperModule.isIntentionalRoomDisconnect(room), true)
})

test('查询麦克风发布异常时释放函数仍正常返回', async() => {
  const originalWarn = console.warn
  console.warn = () => {}
  try {
    await helperModule.releaseLocalMicrophone({
      localParticipant: {
        getTrackPublication() { throw new Error('query failed') },
        trackPublications: { forEach() { throw new Error('iterate failed') } }
      }
    })
  } finally {
    console.warn = originalWarn
  }
})

test('主动挂断、对端结束和现场呼叫统一释放本地麦克风', () => {
  const releaseCalls = websocketSource.match(/await releaseLocalMicrophone\(camera\.room\)/g) || []
  assert.ok(releaseCalls.length >= 2)
  assert.match(websocketSource, /await releaseIntercomClientMedia\(camera, \{ disconnectRoom: true \}\)/)
  assert.match(fieldCallSource, /await releaseLocalMicrophone\(session\.room\)/)
  const cleanupStart = fieldCallSource.indexOf('async cleanupFieldSession')
  const cleanupEnd = fieldCallSource.indexOf('async toggleFieldMic', cleanupStart)
  assert.doesNotMatch(fieldCallSource.slice(cleanupStart, cleanupEnd), /setMicrophoneEnabled\(false\)/)
})

test('地图弹窗销毁和全局退出会收口仍在进行的通话', () => {
  assert.match(mapPopupSource, /await this\.stopIntercomLifecycle\(current\)/)
  assert.match(websocketSource, /await dispatch\('stopIntercomLifecycle', current \|\| key\)/)
})

test('启动失败回滚服务端占用、本地媒体和原视频状态', () => {
  const start = websocketSource.indexOf('async startIntercom')
  const end = websocketSource.indexOf('async applyIntercomResponse', start)
  const block = websocketSource.slice(start, end)
  assert.match(block, /preserveRemoteAudio: !disconnectRoom/)
  assert.match(block, /await stopIntercom\(rollbackSessionId\)/)
  assert.match(block, /camera\.room = previous\.room \|\| null/)
  assert.match(block, /camera\.session = previous\.session \|\| null/)
  assert.match(block, /commit\('setCamera', camera\)/)
})

test('被叫接听失败复用统一清理并回滚服务端占用', () => {
  const start = websocketSource.indexOf('async activateIncomingIntercom')
  const end = websocketSource.indexOf('async clearActiveIncomingCall', start)
  const block = websocketSource.slice(start, end)
  assert.match(block, /await releaseIntercomClientMedia\(camera, \{ disconnectRoom: true \}\)/)
  assert.match(block, /camera\.remoteAudioElement = null/)
  assert.match(block, /await stopIntercom\(intercom\.sessionId\)/)
  assert.doesNotMatch(block, /Promise\.resolve\(camera\.room\.disconnect\(\)\)/)
})

test('对讲请求进行中忽略重复点击', () => {
  const start = websocketSource.indexOf('async toggleIntercom')
  const end = websocketSource.indexOf('async startIntercom', start)
  const block = websocketSource.slice(start, end)
  assert.match(block, /if \(current\.intercomBusy\) return/)
})

test('在途启动支持取消且迟到响应不得继续开启麦克风', () => {
  const cancelStart = websocketSource.indexOf('async cancelPendingIntercomStart')
  const cancelEnd = websocketSource.indexOf('async stopIntercomLifecycle', cancelStart)
  const cancelBlock = websocketSource.slice(cancelStart, cancelEnd)
  assert.match(cancelBlock, /intercomStartOperations\.cancel\(key\)/)

  const start = websocketSource.indexOf('async startIntercom')
  const end = websocketSource.indexOf('async applyIntercomResponse', start)
  const block = websocketSource.slice(start, end)
  const cancelledChecks = block.match(/if \(operation\.cancelled\)/g) || []
  assert.equal(cancelledChecks.length, 2)
  assert.match(block, /intercomStartOperations\.finish\(camera\.key, operation\)/)
  assert.match(block, /signal: operation\.controller\.signal/)
  assert.match(block, /operation\.cancelReason !== 'media-unavailable'/)
  assert.match(mediaApiSource, /startCameraIntercom\(data, \{ signal \} = \{\}\)/)
  assert.match(mediaApiSource, /timeout: 15000/)
})

test('跨摄像头启动互斥覆盖 busy 和 STARTING 状态', () => {
  assert.match(websocketSource, /camera\.intercomBusy \|\|[\s\S]*camera\.intercomActive/)
  assert.match(websocketSource, /item\.key !== camera\.key && intercomInProgress\(item\)/)
  assert.match(websocketSource, /mediaCallCoordinator\.acquire/)
  assert.match(fieldCallSource, /mediaCallCoordinator\.acquire/)
})

test('普通对讲把取消信号传入 Room 连接，挂断确认失败仍执行本地关闭', () => {
  assert.match(websocketSource, /connectRoomWithCancellation\(room, livekitUrl, token, operation\)/)
  const start = websocketSource.indexOf('async hangupIntercom')
  const end = websocketSource.indexOf('connectLiveKit({ state, dispatch }', start)
  const block = websocketSource.slice(start, end)
  assert.match(block, /finally \{[\s\S]*await releaseIntercomClientMedia/)
  assert.match(block, /releaseRegularIntercomLease\(camera\.key\)/)
  assert.match(block, /return true/)
})

test('最终断线、设备离线和权限移除均释放麦克风', () => {
  const disconnected = websocketSource.indexOf('room.on(RoomEvent.Disconnected')
  const disconnectedEnd = websocketSource.indexOf('camera.room = room', disconnected)
  assert.match(websocketSource.slice(disconnected, disconnectedEnd), /await releaseLocalMicrophone\(room\)/)
  assert.match(websocketSource, /releaseIntercomClientMedia\(old, \{ disconnectRoom: true \}\)/)
  assert.match(websocketSource, /await dispatch\('stopIntercomLifecycle', key\)/)
})

test('被叫接听启动中收到结束事件会取消媒体激活', () => {
  assert.match(websocketSource, /pendingIncomingIntercomKeys\.set\(call\.callId, camera\.key\)/)
  assert.match(websocketSource, /intercomStartOperations\.cancel\(pendingKey, 'call-ended'\)/)
  assert.match(websocketSource, /applyIntercomResponse', \{ camera, response: intercom, operation \}/)
  assert.match(websocketSource, /intercomStartOperations\.finish\(camera\.key, operation\)/)
})

test('页面退出与注销清理边界分离', () => {
  const pageStart = websocketSource.indexOf('async stopPageMediaSessions')
  const pageEnd = websocketSource.indexOf('// 切换激活摄像头', pageStart)
  const pageBlock = websocketSource.slice(pageStart, pageEnd)
  assert.match(pageBlock, /\.\.\.intercomStartOperations\.keys\(\)/)
  assert.match(pageBlock, /await dispatch\('stopIntercomLifecycle', current\)/)
  assert.match(pageBlock, /state\.activeCameras\[key\] && current\.watching/)
  assert.doesNotMatch(pageBlock, /current\.recordingActive/)
  assert.match(websocketSource, /Object\.values\(state\.cameras \|\| \{\}\)\.map\(camera => camera\?\.room\)/)
})

test('现场呼叫退出会清理活动通话之外的所有残留 session', () => {
  const start = fieldCallSource.indexOf('async disconnectFieldCall')
  const end = fieldCallSource.indexOf('syncFieldCallEvent', start)
  const block = fieldCallSource.slice(start, end)
  assert.match(block, /await dispatch\('hangupFieldCall'\)/)
  assert.match(block, /Object\.keys\(state\.sessions\)/)
})

test('现场呼叫连接中结束会取消等待，且迟到连接不会再打开麦克风', async() => {
  let resolveConnect
  let microphoneEnables = 0
  let disconnects = 0
  let latestRoom = null
  class FakeRoom {
    constructor() {
      latestRoom = this
      this.handlers = new Map()
      this.remoteParticipants = new Map()
      this.localParticipant = {
        getTrackPublication: () => null,
        trackPublications: new Map(),
        setMicrophoneEnabled: async enabled => { if (enabled) microphoneEnables += 1 }
      }
    }
    on(event, handler) { this.handlers.set(event, handler) }
    connect() { return new Promise(resolve => { resolveConnect = resolve }) }
    async disconnect() { disconnects += 1 }
  }
  const compiled = require('@babel/core').transformSync(fieldCallSource, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  const sent = []
  const originalError = console.error
  console.error = () => {}
  try {
    vm.runInNewContext(compiled, {
      exports,
      console,
      Date,
      WebSocket: { OPEN: 1 },
      document: { body: { appendChild() {} } },
      require: name => {
        if (name === 'livekit-client') {
          return {
            Room: FakeRoom,
            RoomEvent: { TrackSubscribed: 'subscribed', TrackUnsubscribed: 'unsubscribed' },
            Track: { Kind: { Video: 'video', Audio: 'audio' } }
          }
        }
        if (name === 'element-ui') return { Message: { warning() {}, error() {} } }
        if (name.includes('livekitUrl')) return { resolveLiveKitUrl: value => value }
        if (name.includes('livekit-local-media')) return helperModule
        if (name.includes('intercom-operation-registry')) return registryModule
        if (name.includes('media-call-coordinator')) return coordinatorModule
        if (name.includes('call-terminal-registry')) return terminalRegistryModule
        return {}
      }
    })
    const module = exports.default
    const state = {
      ...module.state,
      incomingCalls: [],
      activeIncomingCall: null,
      sessions: {}
    }
    const rootState = {
      websocketRobot: {
        mediaSocket: { readyState: 1, send: value => sent.push(JSON.parse(value)) }
      }
    }
    const commit = (type, payload) => module.mutations[type](state, payload)
    const context = { state, rootState, commit }
    context.dispatch = (type, payload) => module.actions[type](context, payload)

    const activating = context.dispatch('activateFieldCall', {
      call: { callId: 'call-race' },
      session: { roomName: 'room-race', livekitUrl: 'wss://livekit', token: 'token' }
    })
    for (let index = 0; index < 10 && !latestRoom; index += 1) await Promise.resolve()
    assert.ok(latestRoom)

    const cleanup = context.dispatch('cleanupFieldSession', 'call-race')
    await Promise.race([
      cleanup,
      new Promise((_, reject) => setTimeout(() => reject(new Error('field cleanup timeout')), 100))
    ])
    await activating
    assert.equal(microphoneEnables, 0)
    assert.deepEqual(Object.keys(state.sessions), [])
    assert.ok(disconnects >= 1)

    resolveConnect()
    await Promise.resolve()
    await Promise.resolve()
    assert.equal(microphoneEnables, 0)
    assert.ok(disconnects >= 2)
    assert.ok(sent.some(message => message.type === 'video.field.call.hangup'))
  } finally {
    console.error = originalError
  }
})

test('调试前端也使用停止采集语义', () => {
  assert.match(debugAppSource, /await releaseLocalMicrophone\(camera\.room\)/)
  assert.doesNotMatch(debugAppSource, /hangupIntercom\(camera\)[\s\S]*?setMicrophoneEnabled\(false\)/)
})

test('调试前端具备启动取消、跨设备互斥和离线回滚保护', () => {
  assert.match(debugAppSource, /intercomStartOperations\.has\(camera\.key\)/)
  assert.match(debugAppSource, /item\.key !== camera\.key && this\.intercomInProgress\(item\)/)
  assert.match(debugAppSource, /signal: operation\.controller\.signal/)
  assert.match(debugAppSource, /operation\.cancelReason !== 'media-unavailable'/)
  assert.match(debugAppSource, /intercomStartOperations\.cancel\(old\.key, 'media-unavailable'\)/)
})
