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
test('远程控制按钮只按管理端允许动作及活动实例显示', () => {
  const context = { isNavMode: false, showResume: true, hasManagementPermission: () => true,
    taskPlan: { executionStatus: 'PAUSED', activeWorkflowInstanceId: 'active1', availableLifecycleActions: ['RESUME'] } }
  assert.equal(buttons.computed.canResumeExecution.call(context), true)
  assert.equal(buttons.computed.canTerminateExecution.call(context), false)
  context.taskPlan.activeWorkflowInstanceId = null
  assert.equal(buttons.computed.canResumeExecution.call(context), false)
})
