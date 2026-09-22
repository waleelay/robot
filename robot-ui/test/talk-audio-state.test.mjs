import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const audioStateSource = readFileSync(
  new URL('../src/utils/audio-device-state.js', import.meta.url),
  'utf8'
)
const audioStateModule = await import(`data:text/javascript;base64,${Buffer.from(audioStateSource).toString('base64')}`)

const talkSource = readFileSync(
  new URL('../src/views/bi/patrol/monitor/second/components/Talk.vue', import.meta.url),
  'utf8'
)
const controlMixinSource = readFileSync(
  new URL('../src/views/bi/patrol/monitor/second/components/ptz-control-mixin.js', import.meta.url),
  'utf8'
)
const websocketStoreSource = readFileSync(
  new URL('../src/store/modules/websocket-robot.js', import.meta.url),
  'utf8'
)

test('普通扬声器使用原生滑块，避免大屏缩放下组件坐标换算失真', () => {
  assert.match(talkSource, /:value="displayVolume"/)
  assert.match(talkSource, /type="range"/)
  assert.match(talkSource, /@input="updateAudioVolume\(\$event\.target\.value\)"/)
  assert.match(talkSource, /@change="setAudioVolume\(\$event\.target\.value\)"/)
  assert.doesNotMatch(talkSource, /<el-slider/)
})

test('机器人没有摄像头时语音组件不会读取空数组首项', () => {
  assert.match(talkSource, /this\.selectedRobot\?\.cameras\?\.\[0\]\?\.key/)
})

test('拖动期间显示本地交互值，交互结束后以设备上报状态为准', () => {
  assert.match(talkSource, /this\.localVolume === null/)
  assert.match(talkSource, /this\.volumeInteracting = true/)
  assert.match(talkSource, /if \(!this\.volumeInteracting\)/)
  assert.match(talkSource, /this\.localVolume = muted && volume === 0/)
  assert.match(talkSource, /const reported = key \? \(this\.audioState\?\.\[key\] \|\| \{\}\) : \{\}/)
  assert.match(talkSource, /volume: reportedVolume === undefined/)
  assert.doesNotMatch(talkSource, /setAudioState/)
  assert.doesNotMatch(talkSource, /persistDeviceStateCache/)
})

test('设备新状态到达后统一校准音量和静音状态', () => {
  assert.match(talkSource, /audioStatusVersion\(\)/)
  assert.match(talkSource, /this\.reconcileAudioStatus\(\)/)
  assert.match(talkSource, /this\.requestedMuted = null/)
})

test('边缘端无时间戳且重复上报相同值时仍生成新的最终状态版本', () => {
  const first = audioStateModule.mergeReportedAudioState({}, { volume: 60, muted: false })
  const firstVersion = audioStateModule.audioStatusVersion('robot-1', 'speaker_main', first)
  const second = audioStateModule.mergeReportedAudioState(first, { volume: 60, muted: false })
  const secondVersion = audioStateModule.audioStatusVersion('robot-1', 'speaker_main', second)

  assert.equal(first.audioStatusUpdatedAt, undefined)
  assert.equal(first.audioReportRevision, 1)
  assert.equal(second.audioReportRevision, 2)
  assert.notEqual(firstVersion, secondVersion)
})

test('初始化快照保留已有实时值但仍确认收到一次新状态包', () => {
  const current = { volume: 77, muted: false, audioReportRevision: 4 }
  const next = audioStateModule.mergeReportedAudioState(current, { volume: 60, muted: true }, {
    preserveExisting: true
  })
  assert.deepEqual(next, { volume: 77, muted: false, audioReportRevision: 5 })
})

test('加减键和滑块复用同一音量命令流程并立即联动显示', () => {
  assert.match(talkSource, /<button type="button" class="btn-volume" aria-label="音量减"/)
  assert.match(talkSource, /<button type="button" class="btn-volume" aria-label="音量加"/)
  assert.match(talkSource, /this\.displayVolume \+ delta/)
  assert.match(talkSource, /this\.requestAudioVolume\(device, nextVolume/)
  assert.match(talkSource, /this\.localVolume = nextVolume/)
})

test('静音时保留最后一次非静音音量，取消静音后不再显示零音量', () => {
  assert.match(talkSource, /if \(!muted\) this\.lastUnmutedVolume = volume/)
  assert.match(talkSource, /if \(muted\) this\.lastUnmutedVolume = this\.displayVolume/)
  assert.match(talkSource, /:disabled="displayMuted"/)
})

test('语音对讲音量选择普通扬声器且不把 INTERCOM 当作音量设备', () => {
  const start = controlMixinSource.indexOf('audioDevice()')
  const end = controlMixinSource.indexOf('// 警示灯', start)
  const audioDeviceBlock = controlMixinSource.slice(start, end)

  assert.ok(start >= 0 && end > start)
  assert.match(audioDeviceBlock, /device\.deviceType === 'SPEAKER'/)
  assert.match(audioDeviceBlock, /'CLIENT_AUDIO', 'VOLUME_CONTROL'/)
  assert.doesNotMatch(audioDeviceBlock, /INTERCOM/)
})

test('逻辑扬声器状态按 driverDeviceId 合并到物理控制画像且保留物理 deviceId', () => {
  assert.match(websocketStoreSource, /function incomingProfileDevice/)
  assert.match(websocketStoreSource, /audioDriverDeviceId\(device\) === profileDevice\.deviceId/)
  assert.match(websocketStoreSource, /deviceId: device\.deviceId/)
  assert.match(websocketStoreSource, /status: Object\.assign/)
})

test('普通扬声器同步物理音量缓存别名但 INTERCOM 不参与映射', () => {
  assert.match(websocketStoreSource, /const ORDINARY_AUDIO_DEVICE_TYPES = \['SPEAKER', 'CLIENT_AUDIO', 'VOLUME_CONTROL'\]/)
  assert.match(websocketStoreSource, /const deviceIds = \[\.\.\.new Set\(\[device\.deviceId, physicalDeviceId\]/)
  assert.match(websocketStoreSource, /mergeReportedAudioState\(state\.audioState\[key\], status, options\)/)
  assert.doesNotMatch(websocketStoreSource, /ORDINARY_AUDIO_DEVICE_TYPES = \[[^\]]*INTERCOM/)
})
