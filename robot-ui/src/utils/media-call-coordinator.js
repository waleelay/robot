/**
 * 浏览器内非多合一语音通话的唯一占用协调器。
 *
 * 普通机器人对讲和现场视频呼叫虽然来自不同 Vuex 模块，但都会占用同一个
 * 浏览器麦克风。租约从 STARTING 阶段开始持有，直到本地媒体完全释放；这样
 * 可以避免两个模块在 HTTP/LiveKit 异步连接窗口内同时打开麦克风。
 */
export class MediaCallCoordinator {
  constructor() {
    this.current = null
    this.sequence = 0
  }

  acquire(key, type, ownerId = '') {
    if (!key || this.current) return null
    const lease = {
      id: ++this.sequence,
      key,
      type,
      ownerId,
      phase: 'STARTING'
    }
    this.current = lease
    return lease
  }

  activate(lease) {
    if (!this.owns(lease)) return false
    lease.phase = 'ACTIVE'
    return true
  }

  owns(lease) {
    return Boolean(lease && this.current && this.current.id === lease.id)
  }

  ownedBy(lease, ownerId) {
    return this.owns(lease) && Boolean(ownerId) && lease.ownerId === ownerId
  }

  release(lease) {
    if (!this.owns(lease)) return false
    this.current = null
    return true
  }

  holder() {
    return this.current
  }
}

export const mediaCallCoordinator = new MediaCallCoordinator()
