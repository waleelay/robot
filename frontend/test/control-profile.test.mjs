import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { test } from 'node:test'

const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
  .split('<script>')[1].split('</script>')[0]
  .replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*$/gm, '')
  .replace('export default', 'globalThis.component =')

// 执行实际组件方法，仅替换网络和持久化边界，不连接设备或发布命令。
function createContext(getControlProfile = async () => ({ devices: [] })) {
  const sandbox = { getControlProfile, IntercomOperationRegistry: class {} }
  vm.runInNewContext(source, sandbox)
  const context = {
    selectedRobotId: 'robot-a', robots: [], controlProfiles: {}, controlProfileLoading: {},
    audioState: {}, launcherSafety: {}, warningLightState: {}, ptzAutoRotateState: {},
    vehicleLightState: { front: { mode: 'OFF' }, rear: { mode: 'OFF' } },
    vehicleLightModeOptions: [], vehicleLightStateReady: false,
    $set: (object, key, value) => { object[key] = value }, log() {}
  }
  Object.assign(context, sandbox.component.methods)
  context.persistDeviceStateCache = () => {}
  return context
}

function profile(context, robotId, devices) {
  context.controlProfiles[robotId] = { devices }
}

function plain(value) { return JSON.parse(JSON.stringify(value)) }

function stateEvent(robotId, devices) {
  return { event: 'robot.state', data: { robotId, status: 'online', devices, cameras: [] } }
}

test('没有画像或画像为空时，不从推送生成控制设备', () => {
  const context = createContext()
  context.selectedRobot = { devices: [{ deviceId: 'base', deviceType: 'WHEELED_BASE' }] }
  assert.equal(context.controlDevices().length, 0)
  profile(context, 'robot-a', [])
  assert.equal(context.controlDevices().length, 0)
})

test('冲突推送只更新运行态，保留画像身份、类型、动作和控制参数', () => {
  const context = createContext()
  const definition = {
    deviceId: 'ptz_main', deviceType: 'DUAL_LIGHT_PTZ', scope: 'PAYLOAD',
    displayName: '双光云台', actions: ['auto_rotate'], controlProfile: { maxSpeed: 20 },
    status: { autoRotateEnabled: false, oldField: 1 }
  }
  profile(context, 'robot-a', [definition])
  context.syncRobotEvent(stateEvent('robot-a', [{
    deviceId: 'ptz_main', deviceType: 'PTZ', scope: 'ROBOT', displayName: '客户端名称',
    actions: ['unsupported'], controlProfile: { maxSpeed: 999 },
    onlineStatus: 'online', status: { autoRotateEnabled: true }
  }]))
  const result = context.controlDevices()[0]
  for (const key of ['deviceId', 'deviceType', 'scope', 'displayName', 'actions', 'controlProfile']) {
    assert.deepEqual(plain(result[key]), plain(definition[key]))
  }
  assert.deepEqual(plain(result.status), { autoRotateEnabled: true })
  assert.equal(result.onlineStatus, 'online')
  assert.equal(context.ptzAutoRotateState['robot-a:ptz_main'], true)
  assert.equal(definition.status.autoRotateEnabled, false)
})

test('驱动别名、同类型和未登记设备均不能关联平台扬声器', () => {
  const context = createContext()
  profile(context, 'robot-a', [{ deviceId: 'speaker_main', deviceType: 'SPEAKER', status: { volume: 20 } }])
  const devices = [
    { deviceId: 'driver-speaker', deviceType: 'SPEAKER', status: { driverDeviceId: 'speaker_main', volume: 99 } },
    { deviceId: 'other-speaker', deviceType: 'SPEAKER', driverDeviceId: 'speaker_main', status: { volume: 88 } }
  ]
  context.syncRobotEvent(stateEvent('robot-a', devices))
  assert.equal(context.controlDevices().length, 1)
  assert.equal(context.controlDevices()[0].status.volume, 20)
  assert.deepEqual(context.audioState, {})
  assert.equal(context.profileDeviceType('robot-a', devices[0]), null)
})

test('多个同类型设备按平台 ID 分别同步，推送可以省略类型', () => {
  const context = createContext()
  profile(context, 'robot-a', [
    { deviceId: 'speaker_a', deviceType: 'SPEAKER' },
    { deviceId: 'speaker_b', deviceType: 'SPEAKER' }
  ])
  context.syncRobotEvent(stateEvent('robot-a', [
    { deviceId: 'speaker_b', status: { volumePercent: 30, muted: true } },
    { deviceId: 'speaker_a', deviceType: 'PTZ', status: { volume: 70, muted: false } }
  ]))
  assert.deepEqual(plain(context.audioState), {
    'robot-a:speaker_a': { volume: 70, muted: false },
    'robot-a:speaker_b': { volume: 30, muted: true }
  })
})

test('发射器、警示灯和车灯均按画像类型同步状态', () => {
  const context = createContext()
  profile(context, 'robot-a', [
    { deviceId: 'launcher', deviceType: 'LAUNCHER' },
    { deviceId: 'warning', deviceType: 'WARNING_LIGHT' },
    { deviceId: 'lights', deviceType: 'VEHICLE_LIGHT' }
  ])
  context.syncRobotEvent(stateEvent('robot-a', [
    { deviceId: 'launcher', deviceType: 'PTZ', status: { safetySwitchEnabled: false } },
    { deviceId: 'warning', status: { powerOn: true } },
    { deviceId: 'lights', status: { front: { mode: 'ON' }, rear: { mode: 'OFF' } } }
  ]))
  assert.equal(context.launcherSafety.launcher, false)
  assert.equal(context.warningLightState.warning, true)
  assert.equal(context.vehicleLightState.front.mode, 'ON')
  assert.equal(context.vehicleLightState.rear.mode, 'OFF')
})

test('兼容读取同 ID 的 runtimeStatus，但不合并过期状态字段', () => {
  const context = createContext()
  profile(context, 'robot-a', [{ deviceId: 'ptz_main', deviceType: 'DUAL_LIGHT_PTZ', runtimeStatus: { obsolete: true } }])
  context.mergeControlProfileDevices('robot-a', [{ deviceId: 'ptz_main', runtimeStatus: { autoRotateEnabled: false } }])
  const device = context.controlDevices()[0]
  assert.deepEqual(plain(device.status), { autoRotateEnabled: false })
  assert.equal(device.runtimeStatus, undefined)
})

test('加载画像时不重放列表中的旧状态覆盖 HTTP 最新状态', async () => {
  const context = createContext(async () => ({ devices: [
    { deviceId: 'ptz_main', deviceType: 'DUAL_LIGHT_PTZ', status: { autoRotateEnabled: false } }
  ] }))
  context.robots = [{ robotId: 'robot-a', devices: [
    { deviceId: 'ptz_main', deviceType: 'PTZ', status: { autoRotateEnabled: true } }
  ] }]
  await context.loadControlProfile('robot-a')
  assert.equal(context.controlDevices()[0].status.autoRotateEnabled, false)
  assert.equal(context.ptzAutoRotateState['robot-a:ptz_main'], false)
})

test('画像请求期间切换机器人，迟到响应不能写入新机器人的音频缓存', async () => {
  let resolve
  const context = createContext(() => new Promise(done => { resolve = done }))
  context.robots = [{ robotId: 'robot-a', devices: [] }, { robotId: 'robot-b', devices: [] }]
  profile(context, 'robot-b', [{ deviceId: 'speaker_main', deviceType: 'SPEAKER' }])
  context.audioState['robot-b:speaker_main'] = { volume: 10, muted: false }
  const pending = context.loadControlProfile('robot-a')
  context.selectedRobotId = 'robot-b'
  resolve({ devices: [{ deviceId: 'speaker_main', deviceType: 'SPEAKER', status: { volume: 80, muted: true } }] })
  await pending
  assert.deepEqual(plain(context.audioState['robot-b:speaker_main']), { volume: 10, muted: false })
  assert.deepEqual(plain(context.audioState['robot-a:speaker_main']), { volume: 80, muted: true })
  assert.equal(context.controlProfileLoading['robot-a'], false)
})
