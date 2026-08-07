const apiPrefix = (process.env.VUE_APP_BASE_API || '').replace(/\/$/, '')

export function withApiPrefix(url) {
  if (!url || /^(?:[a-z]+:)?\/\//i.test(url) || /^(?:blob|data):/i.test(url)) return url
  if (!apiPrefix || url === apiPrefix || url.startsWith(`${apiPrefix}/`)) return url
  return `${apiPrefix}${url.startsWith('/') ? '' : '/'}${url}`
}

export function withBigscreenApiPrefix(url) {
  if (!url || /^(?:[a-z]+:)?\/\//i.test(url) || /^(?:blob|data):/i.test(url)) return url
  const normalized = url.replace(/^\/api\/control(?=\/|$)/, '/api/bigscreen/control')
  return withApiPrefix(normalized)
}
