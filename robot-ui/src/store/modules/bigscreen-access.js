import { getCurrentBigscreenAccess } from '@/api/bigscreen-access'
import { isBigscreenPermissionEnabled } from '@/utils/bigscreen-access'

let pendingRequest = null

const bigscreenAccess = {
  namespaced: true,

  state: {
    loaded: false,
    permissions: [],
    roles: [],
    user: null
  },

  mutations: {
    SET_ACCESS(state, access) {
      state.loaded = true
      state.permissions = access.permissions
      state.roles = access.roles
      state.user = access.user
    },
    RESET_ACCESS(state) {
      state.loaded = false
      state.permissions = []
      state.roles = []
      state.user = null
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
          const data = response && response.data ? response.data : response
          const permissions = Array.isArray(data?.permissions) ? data.permissions : []
          const roles = Array.isArray(data?.roles) ? data.roles : []
          const roleCodes = roles.map(role => role?.roleCode).filter(Boolean)
          const access = {
            permissions,
            roles,
            user: data || null
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

export default bigscreenAccess
