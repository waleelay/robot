import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const source = readFileSync(new URL('../src/views/bi/js/utils/prefer-live-robot-fields.js', import.meta.url), 'utf8')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const { mergeRobotBaseInfo, overlayLiveRobotRuntimeFields, formatRobotSpeed, normalizeRobotControlMode } = helpers
const early = '2026-08-28T07:00:00.123456001Z'
const late = '2026-08-28T07:00:00.123456002Z'

test('同毫秒内新运行态生效，重复及乱序事件不回退', () => {
  const previous = { runtimeUpdatedAt: early, battery: 20, speed: 1, controlMode: '导航模式' }
  const next = mergeRobotBaseInfo(previous, { runtimeUpdatedAt: late, battery: 0, speed: 0, controlMode: '手动模式' }, true)
  assert.equal(next.speed, 0)
  assert.equal(next.battery, 0)
  assert.equal(next.controlMode, '手动模式')
  assert.deepEqual(mergeRobotBaseInfo(next, previous, true), next)
  assert.deepEqual(mergeRobotBaseInfo(next, { runtimeUpdatedAt: late, speed: 9 }, true), next)
})

test('静态字段可由新快照刷新，实时空类型不能覆盖档案', () => {
  const previous = { name: '装备一', type: '机器狗', model: 'M1', mountedDeviceCount: 2, runtimeUpdatedAt: late, speed: 0 }
  const patch = mergeRobotBaseInfo(previous, { type: '-', name: 'robot-1', speed: 9, runtimeUpdatedAt: early }, true)
  assert.equal(patch.type, '机器狗')
  assert.equal(patch.name, '装备一')
  const snapshot = mergeRobotBaseInfo(patch, { model: 'M2', mountedDeviceCount: null, runtimeUpdatedAt: early, speed: 9 })
  assert.equal(snapshot.model, 'M2')
  assert.equal(snapshot.mountedDeviceCount, null)
  assert.equal(snapshot.speed, 0)
})

test('新快照可纠正旧实时值，未知不冒充零值或默认模式', () => {
  const snapshot = { speed: null, controlMode: null, runtimeUpdatedAt: late, status: 'offline', statusChangedAt: late }
  const result = overlayLiveRobotRuntimeFields(snapshot, { speed: 5, controlMode: '导航模式', runtimeUpdatedAt: early, status: 'online', statusChangedAt: early })
  assert.equal(result.speed, null)
  assert.equal(result.status, 'offline')
  assert.equal(formatRobotSpeed(result), '-')
  assert.equal(formatRobotSpeed({ status: 'online', speed: 0 }), '0.00m/s')
  assert.equal(formatRobotSpeed({ speed: '坏数据' }), '-')
  assert.equal(normalizeRobotControlMode(null), null)
  assert.equal(normalizeRobotControlMode('UNKNOWN'), null)
  assert.equal(normalizeRobotControlMode('常规模式'), '手动模式')
})

const pending = []
const popupSource = readFileSync(new URL('../src/views/bi/gis/globalMap/popup/Robot1.vue', import.meta.url), 'utf8').split('<script>')[1].split('</script>')[0]
const compiled = require('@babel/core').transformSync(popupSource, {
  babelrc: false, configFile: false, plugins: ['@babel/plugin-transform-modules-commonjs']
}).code
const exports = {}
vm.runInNewContext(compiled, {
  exports, AbortController, console,
  require: name => {
    if (name === 'vuex') return { mapActions: () => ({}), mapState: () => ({}) }
    if (name === '@/api/new-bi') {
      return { getPatrolPanoramaMountedDeviceCount: (id, signal) => new Promise((resolve, reject) => pending.push({ id, signal, resolve, reject })) }
    }
    if (name.includes('prefer-live-robot-fields')) return helpers
    if (name.includes('execution-status')) return { executionStatusLabel: value => value }
    return {}
  }
})
const methods = exports.default.methods
const component = exports.default

function context(robotId = 'A', robot = {}) {
  const ctx = {
    ...component.data(),
    ...methods,
    visible: true,
    selectedRobotId: robotId,
    selectedRobot: { robotId, name: `装备${robotId}`, model: 'M1', battery: 80, ...robot },
    robotBaseInfo: { [robotId]: { robotId, name: `装备${robotId}`, model: 'M1', battery: 80, ...robot } }
  }
  Object.defineProperty(ctx, 'currenRobot', { get: () => component.computed.currenRobot.call(ctx) })
  Object.defineProperty(ctx, 'isFixedCamera', { get: () => component.computed.isFixedCamera.call(ctx) })
  Object.defineProperty(ctx, 'mountedDeviceCountTarget', { get: () => component.computed.mountedDeviceCountTarget.call(ctx) })
  Object.defineProperty(ctx, 'mountedDeviceCountText', { get: () => component.computed.mountedDeviceCountText.call(ctx) })
  return ctx
}

test('弹窗主体立即取 Overview，只异步补充上装设备计数', async () => {
  const ctx = context()
  const index = pending.length
  const request = ctx.loadMountedDeviceCount()
  assert.equal(ctx.currenRobot.name, '装备A')
  assert.equal(ctx.currenRobot.model, 'M1')
  assert.equal(ctx.currenRobot.battery, 80)
  assert.equal(ctx.mountedDeviceCountText, '加载中…')
  pending[index].resolve({ robotId: 'A', mountedDeviceCount: 0 })
  await request
  assert.equal(ctx.currenRobot.name, '装备A')
  assert.equal(ctx.currenRobot.mountedDeviceCount, undefined)
  assert.equal(ctx.mountedDeviceCountText, '0个')
})

test('Overview 已有计数时不发补充请求', async () => {
  const ctx = context('A', { mountedDeviceCount: 3 })
  const count = pending.length
  await ctx.loadMountedDeviceCount()
  assert.equal(pending.length, count)
  assert.equal(ctx.currenRobot.mountedDeviceCount, 3)
})

test('固定摄像头不查上装设备，位置直接使用 Overview', async () => {
  const ctx = context('camera-1', {
    sourceType: 'FIXED_CAMERA',
    typeCode: 'FIXED_CAMERA',
    location: { address: '园区东门' }
  })
  const count = pending.length
  await ctx.loadMountedDeviceCount()
  assert.equal(pending.length, count)
  assert.equal(component.computed.isFixedCamera.call(ctx), true)
  assert.equal(component.computed.fixedCameraLocation.call(ctx), '园区东门')
})

test('快速切换取消旧请求，同装备在途请求去重，关闭后清空补充值', async () => {
  const ctx = context()
  const index = pending.length
  const first = ctx.loadMountedDeviceCount()
  await ctx.loadMountedDeviceCount()
  assert.equal(pending.length, index + 1)
  ctx.selectedRobotId = 'B'
  ctx.selectedRobot = { robotId: 'B', name: '装备B', mountedDeviceCount: null }
  ctx.robotBaseInfo.B = { robotId: 'B', name: '装备B', mountedDeviceCount: null }
  const second = ctx.loadMountedDeviceCount()
  assert.equal(pending[index].signal.aborted, true)
  pending[index].resolve({ robotId: 'A', mountedDeviceCount: 8 })
  await first
  assert.equal(ctx.mountedDeviceCountSupplement, null)
  pending[index + 1].resolve({ robotId: 'B', mountedDeviceCount: 2 })
  await second
  assert.equal(ctx.currenRobot.name, '装备B')
  assert.equal(ctx.currenRobot.mountedDeviceCount, null)
  assert.equal(ctx.mountedDeviceCountText, '2个')
  ctx.visible = false
  await ctx.loadMountedDeviceCount()
  assert.equal(ctx.mountedDeviceCountSupplement, null)
})

test('计数查询失败不遮挡主体，重新打开时再次查询', async () => {
  const ctx = context('C')
  const index = pending.length
  const first = ctx.loadMountedDeviceCount()
  pending[index].reject(new Error('测试下游暂不可用'))
  await first
  assert.equal(ctx.currenRobot.name, '装备C')
  assert.equal(ctx.mountedDeviceCountText, '-')
  assert.equal(methods.retryMountedDeviceCount, undefined)
  ctx.visible = false
  await ctx.loadMountedDeviceCount()
  ctx.visible = true
  const second = ctx.loadMountedDeviceCount()
  pending[index + 1].resolve({ robotId: 'C', mountedDeviceCount: 4 })
  await second
  assert.equal(ctx.currenRobot.mountedDeviceCount, undefined)
  assert.equal(ctx.mountedDeviceCountText, '4个')
})

test('错误装备响应视为失败，销毁会取消在途请求', async () => {
  const ctx = context()
  let index = pending.length
  const wrong = ctx.loadMountedDeviceCount()
  pending[index].resolve({ robotId: 'B', mountedDeviceCount: 9 })
  await wrong
  assert.equal(ctx.mountedDeviceCountSupplement, null)

  ctx.visible = false
  await ctx.loadMountedDeviceCount()
  ctx.visible = true
  index = pending.length
  const active = ctx.loadMountedDeviceCount()
  ctx.clearAttachRetry = () => {}
  ctx.stopFixedCameraVideo = () => {}
  component.beforeDestroy.call(ctx)
  assert.equal(pending[index].signal.aborted, true)
  pending[index].resolve({ robotId: 'A', mountedDeviceCount: 1 })
  await active
  assert.equal(ctx.mountedDeviceCountSupplement, null)
})
