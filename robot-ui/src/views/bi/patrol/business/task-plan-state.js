// 生命周期操作只使用活动实例，不能回退到历史实例。
export function hasPlanAction(plan, action) {
  return Boolean(plan?.activeWorkflowInstanceId && plan.availableLifecycleActions?.includes(action))
}
