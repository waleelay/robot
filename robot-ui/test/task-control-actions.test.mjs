import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const read = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
const helper = async path => import('data:text/javascript;base64,' + Buffer.from(read(path)).toString('base64'))
const executionStatus = await helper('views/bi/patrol/business/execution-status.js')
const planState = await helper('views/bi/patrol/business/task-plan-state.js')
const calls = []
const taskApi = {
  pauseTaskRecord: async id => { calls.push(['pause', id]); return { accepted: true } },
  resumeTaskRecord: async id => { calls.push(['resume', id]); return { accepted: true } },
  terminateTaskRecord: async id => { calls.push(['terminate', id]); return { accepted: true } }
}
function component(path) {
  const source = read(path).split('<script>')[1].split('</script>')[0]
  const exports = {}
  vm.runInNewContext(require('@babel/core').transformSync(source, {
    babelrc: false, configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code, {
    exports,
    require: name => {
      if (name === 'vuex') return { mapGetters: () => ({}) }
      if (name.endsWith('execution-status')) return executionStatus
      if (name.endsWith('task-plan-state')) return planState
      if (name.endsWith('api/new-bi')) return taskApi
      if (name.endsWith('api/media')) return { takeoverControl: async () => ({}) }
      if (name === '@/utils/bigscreen-access') return {
        hasManagementPermission: () => true,
        TASK_PERMISSIONS: { EXECUTION_PAUSE: 'pause', EXECUTION_RESUME: 'resume', EXECUTION_TERMINATE: 'terminate' }
      }
      return {}
    }
  })
  return exports.default
}
const warning = component('views/bi/patrol/monitor/second/components/ControlModeWarning.vue')
const buttons = component('views/bi/patrol/monitor/second/components/ControlModeActions.vue')
function javascriptModule(path) {
  const exports = {}
  vm.runInNewContext(require('@babel/core').transformSync(read(path), {
    babelrc: false, configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code, {
    exports,
    require: name => {
      if (name === 'vuex') return { mapActions: () => ({}), mapState: () => ({}) }
      if (name.endsWith('execution-status')) return executionStatus
      if (name.endsWith('api/media')) return {}
      return {}
    }
  })
  return exports.default
}
const controlMixin = javascriptModule('views/bi/patrol/monitor/second/components/ptz-control-mixin.js')
function warningContext(plan) {
  const state = { websocketExtraData: {
    robotBaseInfo: { robot1: { runningTask: { taskId: 'plan1', executionStatus: 'RUNNING' } } },
    taskData: { plan1: plan }
  } }
  return {
    ...warning.methods, $store: { state }, robotId: 'robot1',
    bigscreenPermissions: [], bigscreenAuthorizationBypassed: true,
    $message: { success() {} }
  }
}
test('远程控制从完整任务计划读取动作和活动实例，历史实例不能发命令', async () => {
  calls.length = 0
  const plan = {
    taskId: 'plan1', executionStatus: 'RUNNING',
    activeWorkflowInstanceId: 'active1', lastWorkflowInstanceId: 'old1',
    availableLifecycleActions: ['PAUSE', 'TERMINATE']
  }
  const ctx = warningContext(plan)
  assert.equal(ctx.getRelatedTask(), plan)
  assert.equal(ctx.canRunTaskAction('pause'), true)
  assert.equal(ctx.canRunTaskAction('resume'), false)
  await ctx.runTaskAction('pause')
  assert.deepEqual(calls, [['pause', 'active1']])
  plan.activeWorkflowInstanceId = null
  assert.equal(ctx.canRunTaskAction('terminate'), false)
  await assert.rejects(ctx.runTaskAction('terminate'), /缺少执行记录标识/)
  assert.deepEqual(calls, [['pause', 'active1']])
})
test('远程控制状态只按任务是否执行中展示，立即接管仅在执行中出现', () => {
  const context = { taskPlan: { executionStatus: 'RUNNING' } }
  assert.equal(buttons.computed.isRunningTask.call(context), true)
  context.taskPlan.executionStatus = 'PAUSED'
  assert.equal(buttons.computed.isRunningTask.call(context), false)
  context.taskPlan = null
  assert.equal(buttons.computed.isRunningTask.call(context), false)
  const template = read('views/bi/patrol/monitor/second/components/ControlModeActions.vue').split('<script>')[0]
  assert.match(template, /v-if="isRunningTask"/)
  assert.match(template, /@click="\$emit\('takeover'\)"/)
  assert.doesNotMatch(template, /\$emit\('(resume|terminate)'\)/)
})

test('暂停确认后等待 PAUSED 再接管，终止只终止任务', async () => {
  const order = []
  const context = {
    ...warning.methods,
    action: 'takeover',
    showTaskSelection: true,
    selectedTaskAction: 'pause',
    runTaskAction: async action => {
      order.push(action)
      return { task: { taskId: 'plan1' } }
    },
    waitForTaskStatus: async (taskId, status) => order.push(`wait:${taskId}:${status}`),
    executeTakeover: async () => order.push('takeover')
  }
  await context.executeAction()
  assert.deepEqual(order, ['pause', 'wait:plan1:PAUSED', 'takeover'])

  order.length = 0
  context.selectedTaskAction = 'terminate'
  await context.executeAction()
  assert.deepEqual(order, ['terminate'])
})

test('本体方向控制在任务中要求接管，非任务导航模式自动切手动', () => {
  const calls = []
  const context = {
    ...controlMixin.methods,
    isRunningTask: true,
    isManualMode: false,
    openControlAction: action => calls.push(action),
    requestManualMode: () => calls.push('manual'),
    controlTimers: {}
  }
  context.startFrameControl('base-forward')
  assert.deepEqual(calls, ['takeover'])

  calls.length = 0
  context.isRunningTask = false
  context.startFrameControl('base-forward')
  assert.deepEqual(calls, ['manual'])
})

test('非任务导航模式下方向控件保持可点击', () => {
  ;[
    'views/bi/patrol/monitor/second/components/SelfRobotCarControl.vue',
    'views/bi/patrol/monitor/second/components/SelfRobotDogControl.vue',
    'views/bi/gis/globalMap/popup/ControlPart.vue'
  ].forEach(path => {
    const template = read(path).split('<script>')[0]
    assert.match(template, /'is-disabled': isRunningTask/)
  })
  const car = read('views/bi/gis/globalMap/popup/RobotCarControlPart.vue')
  assert.match(car, /isBodyControlDisabled\(\)\s*\{\s*return this\.isRunningTask/)
})

test('控制会话只在租约有效时复用', () => {
  const active = controlMixin.methods.isControlSessionActive
  assert.equal(active({ status: 'ACTIVE', leaseExpireAt: new Date(Date.now() + 10000).toISOString() }), true)
  assert.equal(active({ status: 'ACTIVE', leaseExpireAt: new Date(Date.now() - 1).toISOString() }), false)
  assert.equal(active({ status: 'ACTIVE' }), false)
})

test('控制权申请期间松键不会发送迟到的移动帧', async () => {
  let resolveFrame
  const sent = []
  const context = {
    ...controlMixin.methods,
    isRunningTask: false,
    isManualMode: true,
    controlPressed: {},
    controlTimers: {},
    wsConnected: true,
    mediaSocket: { send: frame => sent.push(frame) },
    canStartFrameControl: () => true,
    controlFrame: () => new Promise(resolve => { resolveFrame = resolve }),
    sendBaseStop: () => {},
    touchControlSession: () => {},
    $set: (object, key, value) => { object[key] = value },
    $delete: (object, key) => { delete object[key] },
    $message: { error() {} }
  }
  const starting = context.startFrameControl('base-forward')
  context.stopFrameControl('base-forward')
  resolveFrame({ controlSessionId: 'tc1' })
  await starting
  assert.deepEqual(sent, [])
  assert.equal(context.controlTimers['base-forward'], undefined)
})

test('任务进入 RUNNING 时释放本体控制权', () => {
  const reasons = []
  controlMixin.watch.isRunningTask.call({
    releaseBaseControlSession: reason => reasons.push(reason)
  }, true)
  assert.deepEqual(reasons, ['task_started'])
})

test('立即接管使用页面权威状态和最新实时序号', () => {
  const context = {
    ...warning.methods,
    robotId: 'robot1',
    $store: { state: {
      websocketRobot: { robots: [{ robotId: 'robot1', status: 'offline', stateSeq: 12 }] },
      websocketExtraData: { robotBaseInfo: {
        robot1: { robotId: 'robot1', status: 'online', controlMode: '导航模式', stateSeq: 11 }
      } }
    } }
  }
  const robot = context.getRobot()
  assert.equal(robot.status, 'online')
  assert.equal(robot.controlMode, '导航模式')
  assert.equal(robot.stateSeq, 12)
})

test('地图远程控制传入当前任务计划', () => {
  ;[
    'views/bi/gis/globalMap/popup/RobotControlPart.vue',
    'views/bi/gis/globalMap/popup/RobotCarControlPart.vue'
  ].forEach(path => assert.match(read(path), /:task-plan="activeTask"/))
})
