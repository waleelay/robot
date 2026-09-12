// 生命周期操作只使用活动实例，不能回退到历史实例。
export function hasPlanAction(plan, action) {
  return Boolean(plan?.activeWorkflowInstanceId && plan.availableLifecycleActions?.includes(action))
}

// 立即执行只适用于管理端明确返回的待执行计划。
export function canStartPlan(plan) {
  return !plan?.activeWorkflowInstanceId
    && plan?.executionStatus === 'WAITING'
}
