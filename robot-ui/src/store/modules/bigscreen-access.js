import { getCurrentBigscreenAccess } from '@/api/bigscreen-access'
import { isBigscreenPermissionEnabled, normalizePermissionCodes } from '@/utils/bigscreen-access'

let pendingRequest = null

const bigscreenAccess = {
  namespaced: true,

  state: {
    loaded: false,
    permissions: [],
    roles: [],
    user: null,
    authorizationBypassed: false
  },

  mutations: {
    SET_ACCESS(state, access) {
      state.loaded = true
      state.permissions = access.permissions
      state.roles = access.roles
      state.user = access.user
      state.authorizationBypassed = Boolean(access.authorizationBypassed)
    },
    RESET_ACCESS(state) {
      state.loaded = false
      state.permissions = []
      state.roles = []
      state.user = null
      state.authorizationBypassed = false
    }
  },

  actions: {
    ensureLoaded({ state, dispatch }) {
      if (!isBigscreenPermissionEnabled()) return Promise.resolve(state)
      if (state.loaded) return Promise.resolve(state)
      return dispatch('load')
    },
    load({ commit }) {
      if (pendingRequest) return pendingRequest
      pendingRequest = getCurrentBigscreenAccess()
        .then(response => {
          const payload = unwrapAccessPayload(response)
          const permissions = normalizePermissionCodes(payload.permissions)
          const roles = Array.isArray(payload.roles) ? payload.roles : []
          const roleCodes = roles.map(role => (typeof role === 'string' ? role : role && role.roleCode)).filter(Boolean)
          const access = {
            permissions,
            roles,
            user: payload,
            authorizationBypassed: payload.authorizationBypassed === true
          }
          commit('SET_ACCESS', access)
          commit('user/SET_PERMISSIONS', permissions, { root: true })
          commit('user/SET_ROLES', roleCodes, { root: true })
          return access
        })
        .finally(() => {
          pendingRequest = null
        })
      return pendingRequest
    },
    reset({ commit }) {
      pendingRequest = null
      commit('RESET_ACCESS')
      commit('user/SET_PERMISSIONS', [], { root: true })
      commit('user/SET_ROLES', [], { root: true })
    }
  }
}

function unwrapAccessPayload(response) {
  if (!response || typeof response !== 'object') return {}
  if (isAccessPayload(response)) return response
  const nested = response.data
  if (nested && typeof nested === 'object') {
    if (isAccessPayload(nested)) return nested
    if (nested.data && typeof nested.data === 'object' && isAccessPayload(nested.data)) {
      return nested.data
    }
    return nested
  }
  return response
}

function isAccessPayload(value) {
  return Boolean(value)
    && (Array.isArray(value.permissions)
      || typeof value.authorizationBypassed === 'boolean'
      || value.userId
      || value.username)
}

export default bigscreenAccess
