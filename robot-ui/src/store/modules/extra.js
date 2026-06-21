import Cookies from 'js-cookie'

const extra = {
  namespaced: true,
  state: {
    // 二级页面切换
    routeDetails: {}
  },
  getters: {
    getRouteDetails: (state) => state.routeDetails,
  },
  mutations: {
    SET_ROUTE_DETAILS: (state, data) => {
      state.routeDetails = data
    },
  },
  actions: {
    setRouteDetails({ commit, state, dispatch }, data) {
      console.log('setRouteDetails', data); 
      Cookies.set('routeDetails', JSON.stringify(data))
      commit('SET_ROUTE_DETAILS', data)
    }
  }
}

export default extra
