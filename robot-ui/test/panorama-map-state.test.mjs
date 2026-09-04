import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const Vue = require('vue')
const Vuex = require('vuex')
Vue.use(Vuex)
const A = '2092133240826003457'
const B = '2093243815606890497'
const read = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
const helpers = {}
for (const path of [
  'views/bi/js/utils/prefer-live-robot-fields',
  'views/bi/patrol/business/task-equipment',
  'views/bi/patrol/business/execution-status'
]) {
  helpers[path.split('/').at(-1)] = await import('data:text/javascript;base64,' + Buffer.from(read(path + '.js')).toString('base64'))
}

// 执行真实 Store 和组件脚本，仅替换网络、静态资源及无关子组件。
function compile(path, api = {}) {
  let source = read(path)
  if (path.endsWith('.vue')) source = source.split('<script>')[1].split('</script>')[0]
  const exports = {}
  vm.runInNewContext(require('@babel/core').transformSync(source, {
    babelrc: false, configFile: false, plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code, {
    exports, console, clearInterval, setTimeout, clearTimeout,
    document: { addEventListener() {}, removeEventListener() {} },
    window: { setTimeout: resolve => setTimeout(resolve, 0) },
    require: name => {
      if (name === 'vue') return Vue
      if (name === 'vuex') return Vuex
      if (name.endsWith('constants/robot.js')) return { isFixedCamera: () => false }
      if (name.endsWith('api/new-bi')) return api
      for (const [key, value] of Object.entries(helpers)) if (name.endsWith(key)) return value
      if (name.includes('gisMapPoints')) return {
        ENABLE_LIANTONG_SLAM_MOCK: false, isPointToolRequireCharge: () => false
      }
      if (name.endsWith('.json')) return { data: {} }
      return {}
    }
  })
  return exports.default
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const tick = () => new Promise(resolve => setImmediate(resolve))
const overview = (ids = [B, A], extra = {}) => ({
  map: ids.map(id => ({ id, name: id })), devices: [],
  tasks: ids.map(id => ({ taskId: 'task-' + id, mapId: id, equipmentList: [] })),
  alarms: { high: { items: [] } }, ...extra
})
const resources = mapId => ({ mapId, points: [{ id: 'point-' + mapId }], deviceIds: ['robot-' + mapId] })
const routes = mapId => ({ mapId, items: [{ taskId: 'task-' + mapId, mapId, pathPoints: [{ pointId: 'point-' + mapId }] }] })
function setup(overrides = {}) {
  const requests = []
  const watches = []
  const api = {
    getPatrolPanoramaOverview: async () => overview(),
    getPatrolPanoramaMapResources: async id => { requests.push(id); return resources(id) },
    getPatrolPanoramaMapTaskRoutes: async id => routes(id),
    ...overrides
  }
  const module = compile('store/modules/websocket-extra-data.js', api)
  const store = new Vuex.Store({ modules: {
    websocketExtraData: module,
    websocketRobot: { namespaced: true, actions: {
      loadRobots() {}, setSelectedRobotId() {},
      syncTrajectoryWatchTargets(_, targets) { watches.push(targets) }
    } }
  } })
  return {
    store, api, requests, watches, state: store.state.websocketExtraData,
    dispatch: (name, payload) => store.dispatch('websocketExtraData/' + name, payload),
    refresh: () => store.dispatch('websocketExtraData/refreshOverviewResources', { failClosed: false })
  }
}
const panorama = compile('views/bi/patrol/panorama/map/Index.vue')
const home = compile('views/bi/home/Index.vue')
const mapTool = compile('views/bi/patrol/panorama/map/MapTool.vue')
const trajectory = compile('views/bi/gis/globalMap/slam/session-traveled-path.js')
const trajectoryOverview = (extra = {}) => overview([B], {
  devices: [{ robotId: 'robot-' + B, mapId: B }],
  tasks: [{ taskId: 'task-' + B, mapId: B, workflowInstanceId: 9001, status: 'running',
    equipmentList: [{ robotId: 'robot-' + B }] }], ...extra
})
const trajectoryEvent = (action = 'RESET', timestamp = 1000) => ({
  event: 'robot.trajectory.changed', data: { robotId: 'robot-' + B, workflowInstanceId: 9001,
    action, points: [{ timestamp, x: 1, y: 2 }] }
})
function trajectoryView(ctx, showSmall = false) {
  return new Vue({ store: ctx.store, mixins: [trajectory],
    data: () => ({ showSmall, hasPreview: true }),
    computed: {
      map() { return ctx.state.slamMapList.find(item => String(item.id) === String(ctx.state.globalMapId)) },
      slamOfRobot() { return ctx.state.slamOfRobot },
      taskData() { return ctx.state.taskData },
      robotBaseInfo() { return ctx.state.robotBaseInfo }
    }
  })
}

test('重连 RESET 与 Overview 任意先后到达均保留基线并继续 APPEND', async () => {
  for (const resetFirst of [true, false]) {
    const ctx = setup({ getPatrolPanoramaOverview: async () => trajectoryOverview() })
    await ctx.refresh()
    const view = trajectoryView(ctx)
    await ctx.dispatch('syncRobot', trajectoryEvent())
    const delayed = deferred()
    ctx.api.getPatrolPanoramaOverview = () => delayed.promise
    const refresh = ctx.refresh()
    if (resetFirst) await ctx.dispatch('syncRobot', trajectoryEvent())
    const record = ctx.state.trajectoryByRobot['robot-' + B]
    delayed.resolve(trajectoryOverview())
    await refresh
    assert.equal(ctx.state.trajectoryByRobot['robot-' + B], record)
    if (!resetFirst) await ctx.dispatch('syncRobot', trajectoryEvent())
    await ctx.dispatch('syncRobot', trajectoryEvent('APPEND', 1001))
    assert.equal(ctx.state.trajectoryByRobot['robot-' + B].points.length, 2)
    assert.ok(ctx.watches.every(targets => targets.length === 1))
    ctx.api.getPatrolPanoramaOverview = async () => { throw new Error('暂不可用') }
    await assert.rejects(ctx.refresh(), /暂不可用/)
    assert.equal(ctx.state.trajectoryByRobot['robot-' + B].points.length, 2)
    view.$destroy()
  }
})

test('普通刷新保留已结束轨迹，失权、移图、换轮和退出仍清理', async () => {
  for (const change of ['device', 'map', 'workflow', 'logout', 'authorization']) {
    const ctx = setup({ getPatrolPanoramaOverview: async () => trajectoryOverview() })
    await ctx.refresh()
    const view = trajectoryView(ctx)
    await ctx.dispatch('syncRobot', trajectoryEvent())
    await ctx.dispatch('syncRobot', trajectoryEvent('STOPPED'))
    const stopped = ctx.state.trajectoryByRobot['robot-' + B]
    await ctx.refresh()
    assert.equal(ctx.state.trajectoryByRobot['robot-' + B], stopped)
    if (change === 'logout') {
      await ctx.dispatch('resetOverviewResourceState')
    } else {
      const data = change === 'device' ? trajectoryOverview({ devices: [] })
        : change === 'map' ? overview([A]) : trajectoryOverview()
      if (change === 'workflow') data.tasks[0].workflowInstanceId = 9002
      ctx.api.getPatrolPanoramaOverview = async () => data
      if (change === 'authorization') {
        await ctx.dispatch('refreshOverviewResources', { failClosed: true })
      } else await ctx.refresh()
    }
    await Vue.nextTick()
    assert.equal(ctx.state.trajectoryByRobot['robot-' + B], undefined, change)
    view.$destroy()
  }
})

test('任务摘要降级沿用已有订阅，明确 waiting 才冻结；小地图不拥有清理权限', async () => {
  const ctx = setup({ getPatrolPanoramaOverview: async () => trajectoryOverview() })
  await ctx.refresh()
  const view = trajectoryView(ctx)
  await ctx.dispatch('syncRobot', trajectoryEvent())
  const beforeSmall = ctx.watches.length
  const small = trajectoryView(ctx, true)
  small.$destroy()
  assert.equal(ctx.watches.length, beforeSmall)
  assert.ok(ctx.state.trajectoryByRobot['robot-' + B])
  ctx.api.getPatrolPanoramaOverview = async () => trajectoryOverview({
    tasks: [], dataQuality: { tasks: { degraded: true, complete: false } }
  })
  await ctx.refresh()
  await Vue.nextTick()
  assert.equal(view.trajectoryWatchTargets.length, 1)
  assert.equal(ctx.state.trajectoryByRobot['robot-' + B].stopped, false)
  ctx.api.getPatrolPanoramaOverview = async () => {
    const data = trajectoryOverview()
    data.tasks[0].status = 'waiting'
    return data
  }
  await ctx.refresh()
  await Vue.nextTick()
  assert.equal(view.trajectoryWatchTargets.length, 0)
  assert.equal(ctx.state.trajectoryByRobot['robot-' + B].stopped, true)
  view.$destroy()
  assert.equal(Object.keys(ctx.state.trajectoryByRobot).length, 0)
})

test('工作流告警只消费 BFF 快照，普通告警仍仅高风险弹窗', async () => {
  const ctx = setup()
  await ctx.dispatch('syncRobot', { event: 'panorama.alarm.changed', data: { alarm: {
    alarmId: 'workflow-warn', sourceType: 'TASK', level: 'medium', status: 'unhandled', robotId: 'robot-1'
  } } })
  assert.equal(ctx.state.workflowAlarms.length, 0)
  assert.equal(Object.keys(ctx.state.robotAlarmObj).length, 0)

  await ctx.dispatch('syncRobot', { event: 'panorama.workflow-alarms.changed', data: { items: [{
    alarmId: 'workflow-warn', workflowActionable: true, level: 'MEDIUM', status: 'unhandled'
  }] } })
  assert.equal(ctx.state.workflowAlarms[0].alarmId, 'workflow-warn')
  await ctx.dispatch('syncRobot', { event: 'panorama.workflow-alarms.changed', data: { items: [] } })
  assert.equal(ctx.state.workflowAlarms.length, 0)

  await ctx.dispatch('syncRobot', { event: 'panorama.alarm.changed', data: { alarm: {
    alarmId: 'ordinary-high', sourceType: 'COMPONENT', level: 'high', status: 'unhandled', robotId: 'robot-1'
  } } })
  assert.equal(ctx.state.robotAlarmObj['robot-1'].alarmId, 'ordinary-high')
})

test('任务轨迹按执行轮次重置、按毫秒去重并独立更新当前位姿', async () => {
  const ctx = setup()
  await ctx.dispatch('syncRobot', { event: 'robot.trajectory.changed', data: {
    robotId: 'robot-1', workflowInstanceId: 9001, action: 'RESET',
    points: [{ timestamp: 1000.001, x: 1, y: 2 }, { timestamp: 1000.0014, x: 9, y: 9 }]
  } })
  await ctx.dispatch('syncRobot', { event: 'robot.trajectory.changed', data: {
    robotId: 'robot-1', workflowInstanceId: 9001, action: 'APPEND', points: [],
    currentPose: { timestamp: 1001, x: 2, y: 3 }
  } })

  assert.equal(ctx.state.trajectoryByRobot['robot-1'].points.length, 1)
  assert.equal(ctx.state.trajectoryByRobot['robot-1'].currentPose.x, 2)

  await ctx.dispatch('syncRobot', { event: 'robot.trajectory.changed', data: {
    robotId: 'robot-1', workflowInstanceId: 9002, action: 'RESET',
    points: [{ timestamp: 2000, x: 4, y: 5 }]
  } })
  assert.equal(ctx.state.trajectoryByRobot['robot-1'].workflowInstanceId, 9002)
  assert.equal(ctx.state.trajectoryByRobot['robot-1'].points[0].x, 4)
})

test('首屏规则一致：GPS 默认 GIS，无 GPS 默认首张 SLAM，无地图回退 GIS', async () => {
  for (const [data, expected] of [
    [overview(), B],
    [overview([], {}), 'gis'],
    [overview([B, A], { gpsDevices: [{ robotId: 'gps' }] }), 'gis'],
    [overview([B, A], { devices: [{ robotId: 'gps', location: { lat: 0, lng: 0 } }] }), 'gis']
  ]) {
    const ctx = setup({ getPatrolPanoramaOverview: async () => data })
    await ctx.refresh()
    assert.equal(ctx.state.globalMapId, expected)
    assert.equal(ctx.state.overviewReady, true)
    assert.deepEqual(ctx.requests, expected === 'gis' ? [] : [B])
  }
})

test('续期刷新保留非首张地图：底图、工具栏、任务地图一致，切换才清理选择', async () => {
  const ctx = setup()
  await ctx.refresh()
  await ctx.dispatch('setGlobalMapId', A)
  const components = [panorama, home].map(component => new Vue({ ...component, store: ctx.store }))
  let resets = 0
  components.forEach(component => { component.clearMapSelectionUI = () => { resets++ } })
  ctx.requests.length = 0
  await ctx.refresh()
  await Vue.nextTick()
  for (const component of components) {
    assert.equal(component.currentSlamMapId, A)
    assert.equal(component.slamMapPayload.id, A)
    assert.equal(component.slamMapPayload.points[0].id, 'point-' + A)
    const tool = new Vue({ ...mapTool, store: ctx.store, propsData: {
      isSlam: component.isSlam, currentSlam: component.currentSlamMapId
    } })
    assert.equal(tool.currentSlamMapInfo.id, A)
    assert.equal(tool.pathOperable, true)
    assert.equal(tool.taskPathOperable, true)
    tool.$destroy()
  }
  assert.deepEqual(ctx.requests, [A], '不得请求第一张地图的资源')
  assert.equal(ctx.state.taskData['task-' + A].mapId, ctx.state.globalMapId)
  assert.equal(resets, 0, '同图刷新不能清理选择')
  await ctx.dispatch('setGlobalMapId', B)
  await Vue.nextTick()
  assert.equal(resets, 2)
  components.forEach(component => {
    assert.equal(component.slamMapPayload.id, B)
    component.$destroy()
  })
})

test('地图排序和 GPS 设备变化不覆盖选择，手动 GIS 也保持', async () => {
  const ctx = setup()
  await ctx.refresh()
  await ctx.dispatch('setGlobalMapId', A)
  ctx.api.getPatrolPanoramaOverview = async () => overview([B, A], { gpsDevices: [{ robotId: 'gps' }] })
  await ctx.refresh()
  assert.equal(ctx.state.globalMapId, A)
  await ctx.dispatch('setGlobalMapId', 'gis')
  ctx.api.getPatrolPanoramaOverview = async () => overview([A, B])
  ctx.requests.length = 0
  await ctx.refresh()
  assert.equal(ctx.state.globalMapId, 'gis')
  assert.deepEqual(ctx.requests, [])
})

test('选中地图移除后底图与数据一起回退，无地图时统一 GIS', async () => {
  const ctx = setup()
  await ctx.refresh()
  await ctx.dispatch('setGlobalMapId', A)
  const component = new Vue({ ...panorama, store: ctx.store })
  ctx.api.getPatrolPanoramaOverview = async () => overview([B])
  await ctx.refresh()
  await Vue.nextTick()
  assert.equal(component.slamMapPayload.id, B)
  assert.equal(ctx.state.globalMapId, B)
  assert.equal(ctx.state.slamOfRobot[A], undefined)
  ctx.api.getPatrolPanoramaOverview = async () => overview([])
  await ctx.refresh()
  await Vue.nextTick()
  assert.equal(component.currentSlamMapId, null)
  assert.equal(component.isSlam, false)
  assert.equal(ctx.state.globalMapId, 'gis')
  component.$destroy()
})

test('等待 Overview 期间切图，资源请求使用用户最新选择', async () => {
  const ctx = setup()
  await ctx.refresh()
  const delayed = deferred()
  ctx.api.getPatrolPanoramaOverview = () => delayed.promise
  const refresh = ctx.refresh()
  await ctx.dispatch('setGlobalMapId', A)
  ctx.requests.length = 0
  delayed.resolve(overview())
  await refresh
  assert.equal(ctx.state.globalMapId, A)
  assert.deepEqual(ctx.requests, [A])
})

test('刷新等待旧图资源期间切图，提交后补齐新图，迟到旧快照资源不写回', async () => {
  const ctx = setup()
  await ctx.refresh()
  const oldA = deferred()
  const oldB = deferred()
  let aCalls = 0
  ctx.api.getPatrolPanoramaMapResources = id => id === B ? oldB.promise
    : (++aCalls === 1 ? oldA.promise : Promise.resolve(resources(A)))
  const refresh = ctx.refresh()
  await tick()
  const change = ctx.dispatch('setGlobalMapId', A)
  oldB.resolve(resources(B))
  await refresh
  assert.equal(ctx.state.globalMapId, A)
  assert.equal(ctx.state.slamMapList.find(item => item.id === A).points[0].id, 'point-' + A)
  oldA.resolve({ mapId: A, points: [{ id: '过期点位' }] })
  await change
  assert.equal(ctx.state.slamMapList.find(item => item.id === A).points[0].id, 'point-' + A)
})

test('快速切图的旧响应不写任务；同一快照同一地图的在途请求合并', async () => {
  const ctx = setup()
  await ctx.refresh()
  const delayed = deferred()
  let calls = 0
  ctx.api.getPatrolPanoramaMapResources = id => { calls++; return delayed.promise }
  ctx.api.getPatrolPanoramaMapTaskRoutes = async id => ({
    mapId: id, items: [{ taskId: '不应插入', mapId: id, pathPoints: [] }]
  })
  const first = ctx.dispatch('setGlobalMapId', A)
  const second = ctx.dispatch('loadMapResources', A)
  assert.equal(calls, 1)
  await ctx.dispatch('setGlobalMapId', 'gis')
  delayed.resolve(resources(A))
  await Promise.all([first, second])
  assert.equal(ctx.state.taskData['不应插入'], undefined)
  assert.equal(ctx.state.globalMapId, 'gis')
})

test('资源局部失败不切图，普通 Overview 失败保留既有地图和数据', async () => {
  const ctx = setup()
  await ctx.refresh()
  await ctx.dispatch('setGlobalMapId', A)
  ctx.api.getPatrolPanoramaMapResources = async () => { throw new Error('资源暂不可用') }
  await ctx.refresh()
  assert.equal(ctx.state.globalMapId, A)
  assert.equal(ctx.state.taskPathPoints['task-' + A].pathPoints.length, 1)
  const previous = ctx.state.slamMapList
  ctx.api.getPatrolPanoramaOverview = async () => { throw new Error('总览暂不可用') }
  await assert.rejects(ctx.refresh(), /总览暂不可用/)
  assert.equal(ctx.state.globalMapId, A)
  assert.equal(ctx.state.slamMapList, previous)
})

test('退出清空选择，迟到 Overview/地图响应不能复活旧页面，新登录重新默认选择', async () => {
  const ctx = setup()
  await ctx.refresh()
  const delayedOverview = deferred()
  const delayedMap = deferred()
  ctx.api.getPatrolPanoramaOverview = () => delayedOverview.promise
  ctx.api.getPatrolPanoramaMapResources = () => delayedMap.promise
  const refresh = ctx.refresh()
  const change = ctx.dispatch('setGlobalMapId', A)
  await ctx.dispatch('resetOverviewResourceState')
  delayedOverview.resolve(overview())
  delayedMap.resolve(resources(A))
  await Promise.all([refresh, change])
  assert.equal(ctx.state.globalMapId, '')
  assert.equal(ctx.state.overviewReady, false)
  assert.equal(ctx.state.slamMapList.length, 0)
  assert.equal(Object.keys(ctx.state.taskData).length, 0)
  ctx.api.getPatrolPanoramaOverview = async () => overview()
  ctx.api.getPatrolPanoramaMapResources = async id => resources(id)
  await ctx.refresh()
  assert.equal(ctx.state.globalMapId, B)
})

test('权限刷新替代普通在途刷新，旧请求完成不恢复已移除地图', async () => {
  const ctx = setup()
  await ctx.refresh()
  await ctx.dispatch('setGlobalMapId', A)
  const old = deferred()
  ctx.api.getPatrolPanoramaOverview = () => old.promise
  const refresh = ctx.refresh()
  ctx.api.getPatrolPanoramaOverview = async () => overview([B])
  await ctx.dispatch('refreshOverviewResources', { failClosed: true })
  old.resolve(overview())
  await refresh
  assert.equal(ctx.state.globalMapId, B)
  assert.equal(ctx.state.slamMapList.length, 1)
})

test('工具栏只在用户操作时改地图，旧 props 变化不得回写 Store', async () => {
  const ctx = setup()
  await ctx.refresh()
  const tool = new Vue({ ...mapTool, store: ctx.store, propsData: { isSlam: true, currentSlam: A } })
  assert.equal(ctx.state.globalMapId, B)
  tool.$props.currentSlam = '不存在的旧地图'
  tool.$props.isSlam = false
  await Vue.nextTick()
  assert.equal(ctx.state.globalMapId, B)
  tool.selectSlamMap({ id: A })
  await tick()
  assert.equal(ctx.state.globalMapId, A)
  tool.selectMapType('gis')
  await tick()
  assert.equal(ctx.state.globalMapId, 'gis')
  tool.$destroy()
})
