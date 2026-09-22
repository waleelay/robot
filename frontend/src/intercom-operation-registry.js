/** 调试前端与生产大屏保持一致的对讲启动取消语义。 */
export class IntercomOperationRegistry {
  constructor() {
    this.operations = new Map()
  }

  begin(key) {
    if (!key || this.operations.has(key)) return null
    let resolveDone
    const operation = {
      cancelled: false,
      finished: false,
      controller: new AbortController(),
      done: new Promise(resolve => { resolveDone = resolve }),
      resolveDone
    }
    this.operations.set(key, operation)
    return operation
  }

  get(key) { return this.operations.get(key) }
  has(key) { return this.operations.has(key) }
  keys() { return this.operations.keys() }

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
