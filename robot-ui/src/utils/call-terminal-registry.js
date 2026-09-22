/**
 * 记录已经由当前页面明确收口的来电。
 *
 * WebSocket 可能重连、重复投递或乱序投递。拒接、超时、结束之后到达的 accepted
 * 事件只能触发服务端挂断，不能重新占用浏览器麦克风。登记项仅保留一个有限窗口，
 * 避免长期运行的大屏持续累积 callId。
 */
export class CallTerminalRegistry {
  constructor(retentionMs = 60000, timerApi = globalThis) {
    this.retentionMs = retentionMs
    this.timerApi = timerApi
    this.entries = new Map()
  }

  mark(callId, reason = 'terminal') {
    if (!callId) return false
    this.delete(callId)
    const timer = this.timerApi.setTimeout(() => {
      this.entries.delete(callId)
    }, this.retentionMs)
    // Node 回归测试中的终态宽限定时器不应阻止测试进程退出；浏览器定时器无 unref。
    if (timer && typeof timer.unref === 'function') timer.unref()
    this.entries.set(callId, { reason, timer })
    return true
  }

  markAll(callIds, reason = 'terminal') {
    let marked = 0
    for (const callId of callIds || []) {
      if (this.mark(callId, reason)) marked += 1
    }
    return marked
  }

  has(callId) {
    return Boolean(callId && this.entries.has(callId))
  }

  reason(callId) {
    return this.entries.get(callId)?.reason || ''
  }

  delete(callId) {
    const current = this.entries.get(callId)
    if (!current) return false
    this.timerApi.clearTimeout(current.timer)
    this.entries.delete(callId)
    return true
  }

  clear() {
    this.entries.forEach(entry => this.timerApi.clearTimeout(entry.timer))
    this.entries.clear()
  }
}
