import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const source = readFileSync(new URL('../src/views/bi/patrol/business/execution-status.js', import.meta.url), 'utf8')
const statusUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const status = await import(statusUrl)
const taskEquipmentSource = readFileSync(new URL('../src/views/bi/patrol/business/task-equipment.js', import.meta.url), 'utf8')
const taskEquipment = await import(`data:text/javascript;base64,${Buffer.from(taskEquipmentSource).toString('base64')}`)
const taskPlanStateSource = readFileSync(new URL('../src/views/bi/patrol/business/task-plan-state.js', import.meta.url), 'utf8')
const taskPlanState = await import(`data:text/javascript;base64,${Buffer.from(taskPlanStateSource).toString('base64')}`)
const temporaryNavigationSource = readFileSync(
  new URL('../src/views/bi/gis/globalMap/slam/temporary-navigation.js', import.meta.url), 'utf8'
).replace('../../../patrol/business/execution-status.js', statusUrl)
const temporaryNavigation = await import(
  `data:text/javascript;base64,${Buffer.from(temporaryNavigationSource).toString('base64')}`
)

test('任务状态优先使用 management executionStatus', () => {
  const task = { executionStatus: 'PAUSED', status: 'running', statusName: '执行中' }
  assert.equal(status.taskExecutionStatus(task), 'PAUSED')
  assert.equal(status.executionStatusLabel(status.taskExecutionStatus(task)), '已暂停')
  assert.equal(status.taskStatusColorClass(status.taskExecutionStatus(task)), 'gray')
})

test('任务计划不回退工作流 status，只认运行和暂停为活跃状态', () => {
  assert.equal(status.taskExecutionStatus({ status: 'paused' }), undefined)
  for (const value of ['RUNNING', 'PAUSED']) {
    assert.equal(status.isActiveTaskStatus(value), true)
  }
  for (const value of ['WAITING', 'PREPARING', 'PAUSING', 'RESUMING', 'TERMINATING']) {
    assert.equal(status.isActiveTaskStatus(value), false)
  }
  assert.equal(status.isActiveTaskStatus('COMPLETED'), false)
})

test('任务计划操作分别使用待执行状态和管理端动作集合', () => {
  assert.equal(taskPlanState.canStartPlan({ executionStatus: 'WAITING' }), true)
  assert.equal(taskPlanState.canStartPlan({ executionStatus: 'RUNNING' }), false)
  assert.equal(taskPlanState.canStartPlan({ status: 'waiting' }), false)
  const running = { executionStatus: 'RUNNING', activeWorkflowInstanceId: 'run-1', availableLifecycleActions: ['PAUSE'] }
  assert.equal(taskPlanState.canStartPlan(running), false)
  assert.equal(taskPlanState.hasPlanAction(running, 'PAUSE'), true)
  assert.equal(taskPlanState.hasPlanAction({ ...running, activeWorkflowInstanceId: null }, 'PAUSE'), false)
})

test('装备任务筛选按 executionStatus 判断', () => {
  const tasks = {
    1: { taskId: 1, executionStatus: 'RUNNING', status: 'running', equipmentList: ['robot-1'] },
    2: { taskId: 2, executionStatus: 'WAITING', status: 'running', equipmentList: ['robot-1'] }
  }
  const result = taskEquipment.listTasksForRobot(tasks, 'robot-1', {
    activeOnly: true,
    isActive: task => status.isDeviceAssociatedTaskStatus(status.taskExecutionStatus(task))
  })
  assert.deepEqual(result.map(task => task.taskId), [1])
})

test('运行态临时任务复用普通任务结构但不进入任务列表', () => {
  const task = temporaryNavigation.buildTemporaryNavigationTask({ data: {
    workflowPlanId: 88,
    workflowInstanceId: 9001,
    deviceTaskInstanceId: 9101,
    workflowName: '临时导航',
    deviceName: '一号机器人',
    executionStatus: 'PREPARING',
    startedAt: '2026-09-21T10:00:00'
  } }, {
    robotId: 'robot-001',
    mapId: 1,
    targetPoint: { x: 1.2, y: 2.3, yaw: 0.4 }
  })

  assert.equal(task.taskId, 88)
  assert.equal(task.executionStatus, 'RUNNING')
  assert.equal(task.activeWorkflowInstanceStatus, 'PREPARING')
  assert.equal(temporaryNavigation.isActiveTemporaryNavigationTask(task), true)
  assert.equal(taskEquipment.isTaskListVisible(task), false)
  assert.equal(taskEquipment.isTaskListVisible({ ...task, runtimeOnly: false }), true)
})

test('临时导航响应缺少任一任务标识时拒绝接管', () => {
  assert.throws(() => temporaryNavigation.buildTemporaryNavigationTask({ data: {
    workflowPlanId: 88,
    workflowInstanceId: 9001
  } }, { robotId: 'robot-001', targetPoint: {} }), /缺少任务实例标识/)
})
