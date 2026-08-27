import { resolveAdminConsoleUrl } from './adminConsoleUrl'

export const ADMIN_WINDOW_NAME = 'eiopAdmin'
const ADMIN_RETURN_KEY = 'eiopAdminReturnUrl'

function runtimeConfig() {
  return (typeof window !== 'undefined' && window.__BIGSCREEN_AUTH_CONFIG__) || {}
}

function readStoredAdminReturnUrl() {
  if (typeof window === 'undefined') {
    return ''
  }
  try {
    return `${window.sessionStorage.getItem(ADMIN_RETURN_KEY) || ''}`.trim()
  } catch (error) {
    return ''
  }
}

/** Persist Admin origin from launch query before Keycloak strips the URL. */
export function captureAdminReturnUrl() {
  if (typeof window === 'undefined') {
    return
  }
  try {
    const fromQuery = new URLSearchParams(window.location.search).get('eiopAdminUrl')
    if (!fromQuery) {
      return
    }
    const normalized = resolveAdminConsoleUrl({ runtimeUrl: fromQuery })
    if (normalized) {
      window.sessionStorage.setItem(ADMIN_RETURN_KEY, normalized)
    }
  } catch (error) {
    // Ignore malformed launch URLs; configured defaults still apply.
  }
}

export function getAdminConsoleUrl() {
  const stored = readStoredAdminReturnUrl()
  const runtime = runtimeConfig().adminConsoleUrl || stored || ''
  const envUrl = process.env.VUE_APP_ADMIN_CONSOLE_URL || ''
  return resolveAdminConsoleUrl({ runtimeUrl: runtime, envUrl })
}

/** Reuse or open the Admin tab; survives OAuth redirects that clear window.opener. */
export function openAdminConsole() {
  const adminUrl = getAdminConsoleUrl()
  if (!adminUrl) {
    return
  }

  const adminWindow = window.open(adminUrl, ADMIN_WINDOW_NAME)
  if (adminWindow) {
    try {
      adminWindow.focus()
    } catch (error) {
      // focus may fail on some cross-origin combinations; the window still opened.
    }
    return
  }

  try {
    if (window.opener && !window.opener.closed) {
      window.opener.focus()
      return
    }
  } catch (error) {
    // Fall through when cross-origin opener inspection is blocked.
  }

  window.open(adminUrl, '_blank')
}
