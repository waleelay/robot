const AUTHORIZATION_FAILURE_NOTICE_ATTEMPTS = 5

// 4001 先立即刷新 Token；4003 和网络异常采用带抖动的指数退避，避免权限服务故障时重连风暴。
export function mediaReconnectDelay(closeCode, attempts, randomValue = Math.random()) {
  if (closeCode === 4001) return 0
  const exponent = Math.min(Math.max(Number(attempts) || 0, 0), 4)
  const baseDelay = Math.min(30000, 2000 * Math.pow(2, exponent))
  const jitter = Math.floor(Math.max(0, Math.min(randomValue, 1)) * 500)
  return Math.min(30000, baseDelay + jitter)
}

export function isSustainedAuthorizationFailure(closeCode, attempts) {
  return closeCode === 4003 && attempts >= AUTHORIZATION_FAILURE_NOTICE_ATTEMPTS
}

// 服务端已明确拒绝超额会话时停止自动重连，避免无效连接持续冲击配额入口。
export function shouldReconnectMedia(closeCode) {
  return closeCode !== 4008
}
