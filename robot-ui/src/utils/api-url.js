const configuredApiPrefix = (process.env.VUE_APP_BASE_API || '').replace(/\/$/, '')

export const BIGSCREEN_API_PREFIX = '/api/bigscreen'
export const BIGSCREEN_CONTROL_API_PREFIX = BIGSCREEN_API_PREFIX + '/control'
export const BIGSCREEN_BUSINESS_API_PREFIX = BIGSCREEN_API_PREFIX + '/business'
export const BIGSCREEN_PANORAMA_API_PREFIX = BIGSCREEN_API_PREFIX + '/panorama'
export const BIGSCREEN_STATISTICS_API_PREFIX = BIGSCREEN_API_PREFIX + '/statistics'

export function withApiPrefix(url) {
  if (!url || /^(?:[a-z]+:)?\/\//i.test(url) || /^(?:blob|data):/i.test(url)) return url
  if (!configuredApiPrefix || url === configuredApiPrefix || url.startsWith(`${configuredApiPrefix}/`)) return url
  return `${configuredApiPrefix}${url.startsWith('/') ? '' : '/'}${url}`
}

export function withBigscreenApiPrefix(url) {
  if (!url || /^(?:[a-z]+:)?\/\//i.test(url) || /^(?:blob|data):/i.test(url)) return url
  const normalized = url.replace(/^\/api\/control(?=\/|$)/, BIGSCREEN_CONTROL_API_PREFIX)
  return withApiPrefix(normalized)
}
