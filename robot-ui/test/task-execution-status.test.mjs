import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const source = readFileSync(new URL('../src/views/bi/patrol/business/execution-status.js', import.meta.url), 'utf8')
const status = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const taskEquipmentSource = readFileSync(new URL('../src/views/bi/patrol/business/task-equipment.js', import.meta.url), 'utf8')
const taskEquipment = await import(`data:text/javascript;base64,${Buffer.from(taskEquipmentSource).toString('base64')}`)
const taskPlanStateSource = readFileSync(new URL('../src/views/bi/patrol/business/task-plan-state.js', import.meta.url), 'utf8')
const taskPlanState = await import(`data:text/javascript;base64,${Buffer.from(taskPlanStateSource).toString('base64')}`)

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
