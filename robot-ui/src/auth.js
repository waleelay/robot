import Keycloak from 'keycloak-js/lib/keycloak.js'
import { clearFileObjectUrlCache } from '@/utils/file-object-url-cache'

let keycloak = null
let initPromise = null
let loginStarted = false
const minTokenValiditySeconds = 30
const oidcCallbackParams = ['code', 'state', 'session_state', 'iss']

function runtimeConfig() {
  return window.__BIGSCREEN_AUTH_CONFIG__ || {}
}

function runtimeValue(name, fallback = '') {
  const value = runtimeConfig()[name]
  if (value === undefined || value === null || `${value}`.trim() === '') {
    return fallback
  }
  return `${value}`.trim()
}

function authDisabled() {
  return runtimeValue('disabled', process.env.VUE_APP_KEYCLOAK_DISABLED || 'false') === 'true'
}

function keycloakConfig() {
  const url = runtimeValue('keycloakUrl', process.env.VUE_APP_KEYCLOAK_URL || '')
  if (!url) {
    throw new Error('缺少大屏 Keycloak 地址配置')
  }
  return {
    url: url.replace(/\/$/, ''),
    realm: runtimeValue('keycloakRealm', process.env.VUE_APP_KEYCLOAK_REALM || 'iam-auth'),
    clientId: runtimeValue('keycloakClientId', process.env.VUE_APP_KEYCLOAK_CLIENT_ID || 'bigscreen-web')
  }
}

export async function initAuth() {
  if (authDisabled()) return true
  if (initPromise) return initPromise

  keycloak = new Keycloak(keycloakConfig())
  initPromise = keycloak.init({
    onLoad: 'login-required',
    flow: 'standard',
    responseMode: 'query',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    locale: runtimeValue('keycloakLocale', process.env.VUE_APP_KEYCLOAK_LOCALE || 'zh-CN')
  }).then(async authenticated => {
    if (!authenticated) {
      await login()
      return false
    }
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(0).catch(() => login())
    }
    clearOidcCallbackParams()
    // Keycloak 适配器在部分浏览器中会在 init resolve 后继续回写当前回调 URL；
    // 下一轮事件循环和短延迟各复查一次，只移除一次性协议参数，不触发登录或路由跳转。
    window.setTimeout(clearOidcCallbackParams, 0)
    window.setTimeout(clearOidcCallbackParams, 500)
    window.setTimeout(clearOidcCallbackParams, 2000)
    window.setTimeout(clearOidcCallbackParams, 5000)
    return true
  })
  return initPromise
}

/**
 * 授权码交换成功后清理地址栏中的一次性 OIDC 参数。
 * 只删除协议回调参数，保留业务查询参数、当前 History 状态和 hash 路由。
 */
export function clearOidcCallbackParams() {
  const current = new URL(window.location.href)
  const clearedQuery = clearOidcParams(current.searchParams)
  const clearedHash = clearOidcParamsFromHash(current)
  if (!clearedQuery && !clearedHash) return false
  const cleanUrl = `${current.pathname}${current.search}${current.hash}`
  window.history.replaceState(window.history.state, document.title, cleanUrl)
  return true
}

function clearOidcParams(params) {
  const hasCallbackParam = oidcCallbackParams.some(name => params.has(name))
  if (!hasCallbackParam) return false
  oidcCallbackParams.forEach(name => params.delete(name))
  return true
}

function clearOidcParamsFromHash(current) {
  const queryIndex = current.hash.indexOf('?')
  if (queryIndex < 0) return false
  const hashRoute = current.hash.slice(0, queryIndex)
  const params = new URLSearchParams(current.hash.slice(queryIndex + 1))
  if (!clearOidcParams(params)) return false
  const query = params.toString()
  current.hash = query ? `${hashRoute}?${query}` : hashRoute
  return true
}

export async function bearerToken() {
  if (authDisabled()) {
    return process.env.VUE_APP_DEV_BEARER_TOKEN || ''
  }
  await initAuth()
  if (!keycloak || !keycloak.authenticated || !keycloak.token) {
    await login()
    return ''
  }
  try {
    await keycloak.updateToken(minTokenValiditySeconds)
  } catch (error) {
    await login()
    return ''
  }
  return keycloak.token || ''
}

export function currentToken() {
  if (authDisabled()) {
    return process.env.VUE_APP_DEV_BEARER_TOKEN || ''
  }
  return keycloak && keycloak.authenticated ? keycloak.token || '' : ''
}

export async function tokenClaims() {
  const token = await bearerToken()
  if (!token) return {}
  const payload = token.split('.')[1]
  if (!payload) return {}
  const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), '=')
  const bytes = Uint8Array.from(atob(padded), char => char.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes))
}

export function isAuthenticated() {
  return authDisabled() || Boolean(keycloak && keycloak.authenticated)
}

export async function login(redirectPath) {
  if (authDisabled() || !keycloak || loginStarted) return
  loginStarted = true
  try {
    await keycloak.login({
      redirectUri: redirectUri(redirectPath),
      locale: runtimeValue('keycloakLocale', process.env.VUE_APP_KEYCLOAK_LOCALE || 'zh-CN')
    })
  } catch (error) {
    loginStarted = false
    throw error
  }
}

export async function logout() {
  if (authDisabled() || !keycloak) return
  clearFileObjectUrlCache()
  await keycloak.logout({ redirectUri: `${window.location.origin}/` })
}

export async function switchAccount(redirectPath) {
  if (authDisabled() || !keycloak) return
  clearFileObjectUrlCache()
  // 先结束 SSO，再由 login-required 打开空白登录页；仅 prompt=login 会锁定当前用户名。
  await keycloak.logout({ redirectUri: redirectUri(redirectPath) })
}

function redirectUri(redirectPath) {
  if (redirectPath && redirectPath.startsWith('/')) {
    return `${window.location.origin}${redirectPath}`
  }
  return `${window.location.origin}${window.location.pathname}`
}
