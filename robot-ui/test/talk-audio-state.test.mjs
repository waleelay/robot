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
  assert.match(talkSource, /audioReportRevision: device\?\.runtimeRevision \|\| 0/)
  assert.match(talkSource, /volume: status\.volumePercent === undefined/)
  assert.doesNotMatch(talkSource, /this\.audioState\?\.\[key\]/)
  assert.doesNotMatch(talkSource, /setAudioState/)
  assert.doesNotMatch(talkSource, /persistDeviceStateCache/)
})

test('设备新状态到达后统一校准音量和静音状态', () => {
  assert.match(talkSource, /audioStatusVersion\(\)/)
  assert.match(talkSource, /this\.reconcileAudioStatus\(\)/)
  assert.match(talkSource, /this\.requestedMuted = null/)
})

test('相同音量的重复上报仍使用 Control 快照版本触发校准', () => {
  const firstVersion = audioStateModule.audioStatusVersion('robot-1', 'speaker_main', {
    volume: 60, muted: false, audioReportRevision: 1
  })
  const secondVersion = audioStateModule.audioStatusVersion('robot-1', 'speaker_main', {
    volume: 60, muted: false, audioReportRevision: 2
  })
  assert.notEqual(firstVersion, secondVersion)
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
  assert.match(talkSource, /:disabled="!talkAvailable \|\| displayMuted"/)
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

test('音频状态只按平台 ID 合并且不再复制音量缓存', () => {
  assert.doesNotMatch(websocketStoreSource, /function incomingProfileDevice|function audioDriverDeviceId/)
  assert.doesNotMatch(websocketStoreSource, /mergeReportedAudioState/)
  assert.match(websocketStoreSource, /status: reported\.status/)
})

test('普通扬声器缺失状态显示未同步，离线时键盘与方法入口均不能发布', async () => {
  const vm = await import('node:vm')
  const methodsSource = talkSource.slice(talkSource.indexOf('    normalizeAudioVolume(volume) {'), talkSource.indexOf('    async sendDeviceCommand(device, action, params, source) {'))
  const methods = vm.runInNewContext('({' + methodsSource + '})')
  const sent = []
  const context = { ...methods, talkAvailable: false, displayMuted: false, displayVolume: 60,
    sendDeviceCommand: async (...args) => { sent.push(args); return true } }
  assert.equal(await context.toggleAudioMute({}), false)
  assert.equal(await context.requestAudioVolume({}, 35, 'test'), false)
  assert.equal(sent.length, 0)
  // 在线但没有查询反馈的设备仍允许用户设置目标值，不增加状态查询前置条件。
  context.talkAvailable = true
  assert.equal(await context.requestAudioVolume({}, 35, 'test'), true)
  assert.equal(sent.length, 1)
  assert.match(talkSource, /hasAudioStatus\(audioDevice\) \? displayVolume : '未同步'/)
  assert.match(talkSource, /:disabled="!talkAvailable \|\| displayMuted"/)
})


test('扬声器状态只读取规范音量字段，零音量保持有效且旧 volume 不伪装已同步', async () => {
  const vm = await import('node:vm')
  const start = talkSource.indexOf('    audioStatus(device) {')
  const block = talkSource.slice(start, talkSource.indexOf('    audioVolume(device)', start))
  // 直接执行组件方法，验证规范字段的真实读取行为。
  const methods = vm.runInNewContext('({' + block + '})')
  assert.equal(methods.audioStatus({ status: { volumePercent: 0, muted: true } }).volume, 0)
  assert.equal(methods.hasAudioStatus({ status: { volumePercent: 0, muted: true } }), true)
  assert.equal(methods.hasAudioStatus({ status: { volume: 70, muted: false } }), false)
})
