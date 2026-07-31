import Keycloak from 'keycloak-js/lib/keycloak.js'

let keycloak = null
let initPromise = null
let loginStarted = false
const minTokenValiditySeconds = 30

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
    return true
  })
  return initPromise
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
  await keycloak.logout({ redirectUri: `${window.location.origin}/` })
}

function redirectUri(redirectPath) {
  if (redirectPath && redirectPath.startsWith('/')) {
    return `${window.location.origin}${redirectPath}`
  }
  return `${window.location.origin}${window.location.pathname}`
}
