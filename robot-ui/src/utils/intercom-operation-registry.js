/**
 * 管理对讲启动请求的唯一性和取消时序。
 * 页面清理必须等待对应操作完成，避免迟到响应在销毁后重新开启麦克风。
 */
export class IntercomOperationRegistry {
  constructor(controllerFactory = () => new AbortController()) {
    this.operations = new Map()
    this.controllerFactory = controllerFactory
  }

  begin(key) {
    if (!key || this.operations.has(key)) return null
    let resolveDone
    const operation = {
      cancelled: false,
      finished: false,
      controller: this.controllerFactory(),
      done: new Promise(resolve => { resolveDone = resolve }),
      resolveDone
    }
    this.operations.set(key, operation)
    return operation
  }

  get(key) {
    return this.operations.get(key)
  }

  has(key) {
    return this.operations.has(key)
  }

  keys() {
    return this.operations.keys()
  }

  async cancel(key, reason = 'lifecycle') {
    const operation = this.operations.get(key)
    if (!operation) return false
    operation.cancelled = true
    operation.cancelReason = operation.cancelReason || reason
    try { operation.controller.abort() } catch (_) {}
    await operation.done
    return true
  }

  finish(key, operation) {
    if (!operation || operation.finished) return
    operation.finished = true
    if (this.operations.get(key) === operation) this.operations.delete(key)
    operation.resolveDone()
  }
}
