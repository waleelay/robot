const defaultKeycloakPort = '18443'

function stripTrailingSlash(value) {
  return `${value || ''}`.trim().replace(/\/+$/, '')
}

/**
 * 优先使用运行时配置；未配置时复用大屏地址栏主机并切换到 IAM HTTPS 端口。
 */
function resolveKeycloakUrl({ runtimeUrl = '', origin } = {}) {
  const configured = stripTrailingSlash(runtimeUrl)
  if (configured) {
    return configured
  }

  const pageOrigin = origin || (typeof window !== 'undefined' ? window.location.origin : '')
  if (!pageOrigin) {
    throw new Error('缺少大屏页面地址，无法推导 Keycloak 地址')
  }

  const keycloakUrl = new URL(pageOrigin)
  keycloakUrl.protocol = 'https:'
  keycloakUrl.port = defaultKeycloakPort
  return keycloakUrl.origin
}

module.exports = {
  resolveKeycloakUrl
}
