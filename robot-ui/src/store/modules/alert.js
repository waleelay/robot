// store/modules/alert.js
const state = {
  visible: false,
  message: '',
  type: 'error' // 默认类型为 error
};

const mutations = {
  SHOW_ALERT(state, { message, type = 'error' }) {
    state.visible = true;
    state.message = message;
    state.type = type;
  },
  HIDE_ALERT(state) {
    state.visible = false;
    state.message = '';
    state.type = 'error';
  }
};

const actions = {
  showAlert({ commit }, payload) {
    commit('SHOW_ALERT', payload);
  },
  hideAlert({ commit }) {
    commit('HIDE_ALERT');
  }
};

export default {
  namespaced: true,
  state,
  mutations,
  actions
};
