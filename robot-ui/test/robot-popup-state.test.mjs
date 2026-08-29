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

test('无运行态版本的详情不改速度，明确未知模式可以清空旧模式', () => {
  const previous = { speed: 2, controlMode: '手动模式', runtimeUpdatedAt: early }
  const detail = mergeRobotBaseInfo(previous, { model: 'M2', mountedDeviceCount: 0 })
  assert.equal(detail.speed, 2)
  assert.equal(detail.mountedDeviceCount, 0)
  assert.equal(mergeRobotBaseInfo(detail, { controlMode: null, runtimeUpdatedAt: late }, true).controlMode, null)
})

// 执行组件的实际请求方法，验证取消、切换及去重，不依赖真实服务器。
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
    if (name === '@/api/new-bi') return { getPatrolPanoramaDeviceDetail: (id, signal) => new Promise((resolve, reject) => pending.push({ id, signal, resolve, reject })) }
    if (name.includes('prefer-live-robot-fields')) return helpers
    if (name.includes('execution-status')) return { executionStatusLabel: value => value }
    if (name === '@/store/modules/websocket-extra-data') return { getRobotStatus: () => ({}) }
    return {}
  }
})
const methods = exports.default.methods
const component = exports.default
function context(robotId = 'A') {
  const ctx = { ...component.data(), ...methods, visible: true, selectedRobotId: robotId, selectedRobot: {},
    $store: { dispatch: () => assert.fail('装备详情不能回填 Overview/全局装备档案') } }
  Object.defineProperty(ctx, 'currenRobot', { get: () => component.computed.currenRobot.call(ctx) })
  Object.defineProperty(ctx, 'deviceDetailTarget', { get: () => component.computed.deviceDetailTarget.call(ctx) })
  return ctx
}
function updateShared(ctx, data) {
  if (data.robotId === ctx.selectedRobotId) ctx.selectedRobot = mergeRobotBaseInfo(ctx.selectedRobot, data, true)
}

test('快速切换只写入当前弹窗，同装备在途请求去重，关闭取消并清空', async () => {
  const ctx = context()
  const index = pending.length
  const first = ctx.loadSelectedDeviceDetail()
  await ctx.loadSelectedDeviceDetail()
  assert.equal(pending.length, index + 1)
  assert.equal(ctx.deviceDetailLoading, true)
  ctx.selectedRobotId = 'B'
  const second = ctx.loadSelectedDeviceDetail()
  assert.equal(pending[index].signal.aborted, true)
  pending[index].resolve({ robotId: 'A', model: 'A', mountedDeviceCount: 8 })
  await first
  assert.equal(ctx.deviceDetail, null)
  assert.equal(ctx.deviceDetailLoading, true)
  pending[index + 1].resolve({ robotId: 'B', model: 'B', mountedDeviceCount: 0, speed: 99 })
  await second
  assert.equal(ctx.currenRobot.robotId, 'B')
  assert.equal(ctx.currenRobot.mountedDeviceCount, 0)
  assert.equal(ctx.currenRobot.speed, 99)
  assert.equal(ctx.deviceDetailLoading, false)
  await ctx.loadSelectedDeviceDetail()
  assert.equal(pending.length, index + 2, '成功后重复加载不应重查档案')
  ctx.selectedRobotId = 'C'
  const third = ctx.loadSelectedDeviceDetail()
  ctx.visible = false
  await ctx.loadSelectedDeviceDetail()
  assert.equal(pending[index + 2].signal.aborted, true)
  pending[index + 2].resolve({ robotId: 'C' })
  await third
  assert.equal(ctx.deviceDetail, null)
  assert.equal(ctx.deviceDetailRobotId, null)
})

test('详情失败显示未知而非 Overview/假零值，点击重试可恢复', async () => {
  const ctx = context('C')
  ctx.robotBaseInfo = { C: { model: '旧型号', battery: 80 } }
  let index = pending.length
  const first = ctx.loadSelectedDeviceDetail()
  pending[index].reject(new Error('测试下游暂不可用'))
  await first
  assert.equal(ctx.deviceDetail, null)
  assert.equal(ctx.currenRobot.model, undefined)
  assert.equal(ctx.currenRobot.battery, undefined)
  assert.equal(ctx.deviceDetailError, true)
  assert.equal(ctx.deviceDetailController, null)
  index = pending.length
  const second = ctx.retryDeviceDetail()
  pending[index].resolve({ robotId: 'C', mountedDeviceCount: null })
  await second
  assert.equal(ctx.currenRobot.mountedDeviceCount, null)
  assert.equal(ctx.deviceDetailError, false)
})

test('六项装备字段及标题均取详情，实时静态字段和 Overview 不参与填充', async () => {
  const ctx = context()
  ctx.robotBaseInfo = { A: { name: '旧名', type: '旧类型', battery: 1, model: '旧型号', speed: 8, controlMode: '手动模式', mountedDeviceCount: 99 } }
  const index = pending.length
  const request = ctx.loadSelectedDeviceDetail()
  updateShared(ctx, { robotId: 'A', name: '实时旧名', type: '旧类型', model: '旧型号', mountedDeviceCount: 9 })
  assert.equal(ctx.currenRobot.name, undefined)
  const detail = { robotId: 'A', name: '详情名称', type: '机器狗', battery: 0, model: 'M1', speed: 0, controlMode: '导航模式', mountedDeviceCount: 0, runtimeUpdatedAt: late }
  pending[index].resolve(detail)
  await request
  for (const key of Object.keys(detail)) assert.equal(ctx.currenRobot[key], detail[key])
  ctx.selectedRobot = { robotId: 'A', model: '再次刷新 Overview', speed: 99, runtimeUpdatedAt: early }
  assert.equal(ctx.currenRobot.model, 'M1')
  assert.equal(ctx.currenRobot.speed, 0)
})

test('加载期间实时更新不被慢详情覆盖，后续乱序/重复事件不回退', async () => {
  const ctx = context()
  const index = pending.length
  const request = ctx.loadSelectedDeviceDetail()
  const event = { event: 'robot.state', data: { robotId: 'A', speed: 2, battery: 0, controlMode: null, runtimeUpdatedAt: late, status: 'offline', statusChangedAt: late, stateSeq: 8 } }
  updateShared(ctx, event.data)
  pending[index].resolve({ robotId: 'A', name: '详情', speed: 1, battery: 20, controlMode: '手动模式', runtimeUpdatedAt: early, status: 'online', statusChangedAt: early, stateSeq: 7 })
  await request
  assert.equal(ctx.currenRobot.speed, 2)
  assert.equal(ctx.currenRobot.battery, 0)
  assert.equal(ctx.currenRobot.controlMode, null)
  assert.equal(ctx.currenRobot.status, 'offline')
  assert.equal(ctx.currenRobot.stateSeq, 8)
  updateShared(ctx, { ...event.data, speed: 9, runtimeUpdatedAt: early, status: 'online', statusChangedAt: early })
  updateShared(ctx, { ...event.data, speed: 10 })
  assert.equal(ctx.currenRobot.speed, 2)
  assert.equal(ctx.currenRobot.status, 'offline')
})

test('较新详情优于旧共享状态，其他装备不覆盖弹窗', async () => {
  const ctx = context()
  const index = pending.length
  const request = ctx.loadSelectedDeviceDetail()
  pending[index].resolve({ robotId: 'A', speed: 3, runtimeUpdatedAt: late, status: 'offline', statusChangedAt: late })
  await request
  updateShared(ctx, { robotId: 'A', speed: 9, runtimeUpdatedAt: early, status: 'online', statusChangedAt: early })
  updateShared(ctx, { robotId: 'B', speed: 9 })
  assert.equal(ctx.currenRobot.speed, 3)
  assert.equal(ctx.currenRobot.status, 'offline')
  updateShared(ctx, { robotId: 'A', speed: 4, runtimeUpdatedAt: '2026-08-28T07:00:01Z' })
  assert.equal(ctx.currenRobot.speed, 4)
  assert.equal(ctx.currenRobot.status, 'offline')
})

test('固定摄像头展示信息也按需加载，位置不回落全局缓存', async () => {
  const ctx = context('camera-1')
  ctx.selectedRobot = { typeCode: 'FIXED_CAMERA' }
  ctx.robotLocation = { 'camera-1': { address: '旧地址' } }
  const index = pending.length
  const request = ctx.loadSelectedDeviceDetail()
  assert.equal(component.computed.isFixedCamera.call(ctx), true)
  assert.equal(component.computed.fixedCameraLocation.call(ctx), '-')
  pending[index].resolve({ robotId: 'camera-1', name: '相机', typeCode: 'FIXED_CAMERA', location: { address: '详情地址' } })
  await request
  assert.equal(component.computed.fixedCameraLocation.call(ctx), '详情地址')
  ctx.selectedRobot = { typeCode: 'FIXED_CAMERA', location: { address: '共享状态新地址' }, mountedDeviceCount: null }
  assert.equal(component.computed.fixedCameraLocation.call(ctx), '详情地址')
})

test('错误装备响应视为失败，不静默显示成功', async () => {
  const ctx = context()
  const index = pending.length
  const request = ctx.loadSelectedDeviceDetail()
  pending[index].resolve({ robotId: 'B', model: '串值' })
  await request
  assert.equal(ctx.deviceDetail, null)
  assert.equal(ctx.deviceDetailError, true)
})

test('真实共享状态链路：三项数据与上下线响应式更新，重连刷新不重查或清空档案', async () => {
  const Vue = require('vue')
  const Vuex = require('vuex')
  Vue.use(Vuex)
  // 执行生产 Vuex 模块的状态合并/Overview 刷新，而非在弹窗另造事件订阅。
  const storeExports = {}
  const storeSource = readFileSync(new URL('../src/store/modules/websocket-robot.js', import.meta.url), 'utf8')
  vm.runInNewContext(require('@babel/core').transformSync(storeSource, {
    babelrc: false, configFile: false, plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code, { exports: storeExports, console, require: name => {
    if (name === 'vue') return Vue
    if (name.includes('prefer-live-robot-fields')) return helpers
    return {}
  } })
  const store = new Vuex.Store({ modules: {
    websocketRobot: storeExports.default,
    websocketExtraData: { namespaced: true, actions: { setRobotBaseInfo() {} } }
  } })
  store.commit('websocketRobot/setRobots', [{ robotId: 'A', cameras: [] }])
  store.commit('websocketRobot/setSelectedRobotId', 'A')
  const index = pending.length
  const popup = new Vue({ ...component, mixins: [], store,
    data: () => ({ ...component.data(), visible: true, taskData: {}, taskPathPoints: {}, globalMapId: 'gis' }),
    methods: { ...methods, clearAttachRetry() {}, stopFixedCameraVideo() {} }
  })
  assert.equal(pending.length, index + 1, '首次已打开的弹窗自动查询一次')
  const request = popup.loadSelectedDeviceDetail()
  const snapshot = { robotId: 'A', name: '装备A', type: '机器狗', model: 'M1', mountedDeviceCount: 3,
    battery: 30, speed: 1, controlMode: '手动模式', runtimeUpdatedAt: early, status: 'online', statusChangedAt: early }
  pending[index].resolve(snapshot)
  await request
  assert.equal(popup.currenRobot.mountedDeviceCount, 3)
  assert.equal(popup.showControl, true)
  await store.dispatch('websocketRobot/syncRobotEvent', { event: 'robot.state', data: {
    robotId: 'A', battery: 20, speed: 2, controlMode: '导航模式', runtimeUpdatedAt: late,
    status: 'offline', statusChangedAt: late, cameras: []
  } })
  assert.equal(popup.currenRobot.battery, 20)
  assert.equal(popup.currenRobot.speed, 2)
  assert.equal(popup.currenRobot.controlMode, '导航模式')
  assert.equal(popup.showControl, false)
  assert.equal(formatRobotSpeed(popup.currenRobot), '-')
  await store.dispatch('websocketRobot/syncRobotEvent', { event: 'robot.state', data: {
    ...snapshot, battery: 99, cameras: []
  } })
  assert.equal(popup.currenRobot.battery, 20, '旧运行态不得回退电量')
  assert.equal(popup.showControl, false, '旧上线事件不得恢复控制入口')
  const saved = popup.deviceDetail
  const now = '2026-08-28T07:00:01Z'
  store.commit('websocketRobot/setWsConnected', false)
  store.commit('websocketRobot/setWsConnected', true)
  // 对应 WebSocket 重连后既有 Overview 刷新得到组件未知/档案变化。
  await store.dispatch('websocketRobot/loadRobots', [{ ...snapshot, model: null, mountedDeviceCount: null,
    battery: 0, speed: 0, controlMode: '手动模式', runtimeUpdatedAt: now, statusChangedAt: now }])
  await Vue.nextTick()
  await popup.loadSelectedDeviceDetail()
  assert.equal(pending.length, index + 1)
  assert.equal(popup.deviceDetail, saved)
  assert.equal(popup.currenRobot.model, 'M1')
  assert.equal(popup.currenRobot.mountedDeviceCount, 3)
  assert.equal(popup.currenRobot.battery, 0)
  assert.equal(popup.currenRobot.speed, 0)
  assert.equal(popup.currenRobot.controlMode, '手动模式')
  assert.equal(popup.showControl, true)
  // 授权列表移除目标后，复用现有选择清空链路，不保留失权档案。
  await store.dispatch('websocketRobot/loadRobots', [])
  await Vue.nextTick()
  assert.equal(popup.deviceDetail, null)
  assert.equal(popup.currenRobot.model, undefined)
  popup.$destroy()
})

test('单一查询目标监听：隐藏不请求，打开/切换触发，关闭取消，重开重新查询', async () => {
  const Vue = require('vue')
  const index = pending.length
  const popup = new Vue({ ...component, mixins: [],
    data: () => ({ ...component.data(), visible: false, chosenId: 'A', taskData: {}, taskPathPoints: {} }),
    computed: { ...component.computed, selectedRobotId() { return this.chosenId }, selectedRobot() { return {} } },
    methods: { ...methods, clearAttachRetry() {}, stopFixedCameraVideo() {} }
  })
  assert.equal(pending.length, index)
  popup.chosenId = 'B'
  await Vue.nextTick()
  assert.equal(pending.length, index, '隐藏期间更换选择不请求')
  popup.visible = true
  await Vue.nextTick()
  assert.equal(pending[index].id, 'B')
  popup.chosenId = 'A'
  await Vue.nextTick()
  assert.equal(pending[index].signal.aborted, true)
  assert.equal(pending[index + 1].id, 'A')
  pending[index].resolve({ robotId: 'B', mountedDeviceCount: 9 })
  pending[index + 1].resolve({ robotId: 'A', mountedDeviceCount: 3 })
  await Vue.nextTick()
  assert.equal(popup.currenRobot.mountedDeviceCount, 3)
  popup.visible = false
  await Vue.nextTick()
  assert.equal(popup.deviceDetail, null)
  popup.visible = true
  await Vue.nextTick()
  assert.equal(pending[index + 2].id, 'A')
  popup.visible = false
  await Vue.nextTick()
  assert.equal(pending[index + 2].signal.aborted, true)
  pending[index + 2].resolve({ robotId: 'A', mountedDeviceCount: 5 })
  await Vue.nextTick()
  assert.equal(popup.deviceDetail, null)
  popup.$destroy()
})

test('销毁取消在途请求；关闭再打开重新查询档案，包括合法零值', async () => {
  const ctx = context()
  const index = pending.length
  const old = ctx.loadSelectedDeviceDetail()
  ctx.clearAttachRetry = () => {}
  ctx.stopFixedCameraVideo = () => {}
  component.beforeDestroy.call(ctx)
  assert.equal(pending[index].signal.aborted, true)
  pending[index].resolve({ robotId: 'A', mountedDeviceCount: 9 })
  await old
  assert.equal(ctx.deviceDetail, null)
  const first = ctx.loadSelectedDeviceDetail()
  pending[index + 1].resolve({ robotId: 'A', mountedDeviceCount: 3 })
  await first
  assert.equal(ctx.currenRobot.mountedDeviceCount, 3)
  ctx.visible = false
  await ctx.loadSelectedDeviceDetail()
  assert.equal(ctx.deviceDetail, null)
  ctx.visible = true
  const reopened = ctx.loadSelectedDeviceDetail()
  pending[index + 2].resolve({ robotId: 'A', mountedDeviceCount: 0 })
  await reopened
  assert.equal(ctx.currenRobot.mountedDeviceCount, 0)
})
