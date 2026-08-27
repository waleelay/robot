function stripTrailingSlash(value) {
  return `${value || ''}`.replace(/\/+$/, '') || '/'
}

function isBlankUrl(value) {
  const normalized = `${value || ''}`.trim()
  return !normalized || normalized === '/'
}

/**
 * Resolve the EIOP Admin UI origin. Explicit runtime/env values win;
 * otherwise localhost uses the Vite dev server, and deployed hosts use :5443.
 */
function resolveAdminConsoleUrl({
  runtimeUrl = '',
  envUrl = '',
  hostname,
  protocol
} = {}) {
  const configured = `${runtimeUrl || envUrl || ''}`.trim()
  if (!isBlankUrl(configured)) {
    return stripTrailingSlash(configured)
  }
  const host = hostname
    || (typeof window !== 'undefined' ? window.location.hostname : 'localhost')
  const proto = protocol
    || (typeof window !== 'undefined' ? window.location.protocol : 'http:')
  if (host === 'localhost' || host === '127.0.0.1') {
    return 'http://localhost:5173'
  }
  return `${proto}//${host}:5443`
}

module.exports = {
  resolveAdminConsoleUrl
}
