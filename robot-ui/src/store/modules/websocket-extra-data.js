const state = {
  // 设备对象：设备详情，包含坐标位置，task基本信息
  deviceObj: {},
  // 任务列表
  taskList: [],
  // 告警列表
  alarmList: [],
  deviceTypeStats: [],
}

const mutations = {
  SET_DEVICEOBJ(state,value) {
    state.deviceObj = value;
  },
  SET_TASKLIST(state,value) {
    state.taskList = value;
  },
  SET_ALARMLIST(state,value) {
    state.alarmList = value;
  },
}

const actions = {
  // 设置设备对象
  setDeviceObj({ commit }, value) {
    commit('SET_DEVICEOBJ', value);
  },
  // 设置任务列表
  setTaskList({ commit }, value) {
    commit('SET_TASKLIST', value);
  },
  // 设置告警列表
  setAlarmList({ commit }, value) {
    commit('SET_ALARMLIST', value);
  },
}

const getters = {
  // 获取设备对象
  getDeviceObj: state => state.deviceObj,
  // 获取任务列表
  getTaskList: state => state.taskList,
  // 获取告警列表
  getAlarmList: state => state.alarmList,
}



export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters,
};

// 导出工具函数供外部使用
export {
}