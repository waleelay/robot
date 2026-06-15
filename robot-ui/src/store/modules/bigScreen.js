const state = {
  arrivedNum: null,//已到人员
  unarrivedNum: null,//未到人员

  importArrivedNum: null,//重点人员已到人数
  importUnArrivedNum: null,//重点人员未到人数

  needTime: null,//需要的时间
  processNum: null,//进度条时间
  taskPointNum: null,//任务点数

  unarrivedUsers: [],//未到人员列表
  signResultDatas: [],//人员事件列表

  cruiseTaskPointNum: null,
  cruiseProssesNum: null,
  cruiseNeedTime: null,

  severity: null,
  normal: null,
}

const mutations = {
  SET_ARRIVEDNUM(state,value) {
    state.arrivedNum = value;
  },
  SET_UNARRIVEDNUM(state,value) {
    state.unarrivedNum = value;
  },
  SET_IMPORTARRIVEDNUM(state,value) {
    state.importArrivedNum = value;
  },
  SET_IMPORTUNARRIVEDNUM(state,value) {
    state.importUnArrivedNum = value;
  },
  SET_NEEDTIME(state,value) {
    state.needTime = value;
  },
  SET_PROCESSNUM(state,value) {
    state.processNum = value;
  },
  SET_TASKPOINTNUM(state,value) {
    state.taskPointNum = value;
  },
  SET_UNARRIVEDUSERS(state,value) {
    // console.log(value)
    state.unarrivedUsers = value;
  },
  SET_SIGNRESULTDATAS(state,value) {
    state.signResultDatas = value;
  },

  SET_CRUISETASKPOINTNUM(state,value) {
    state.cruiseTaskPointNum = value
  },
  SET_CRUISEPROSSESNUM(state,value) {
    state.cruiseProssesNum = value
  },
  SET_CRUISENEEDTIME(state,value) {
    state.cruiseNeedTime = value
  },

  SET_SEVERITY(state,value) {
    state.severity = value
  },
  SET_NORMAL(state,value) {
    state.normal = value
  },
}

const actions = {
  //为了保证页面刷新后数据不消失，需要存储到localStorage
  initializeStore({ commit }) {
    const storedData = localStorage.getItem('bigScreenData');
    if (storedData) {
      const payload = JSON.parse(storedData);
      commit('SET_ARRIVEDNUM', payload.arrivedNum);
      commit('SET_UNARRIVEDNUM', payload.unarrivedNum);
      commit('SET_IMPORTARRIVEDNUM', payload.importArrivedNum);
      commit('SET_IMPORTUNARRIVEDNUM',payload.importUnArrivedNum);
      commit('SET_NEEDTIME', payload.needTime);
      commit('SET_PROCESSNUM', payload.processNum);
      commit('SET_TASKPOINTNUM', payload.taskPointNum);
      commit('SET_UNARRIVEDUSERS', payload.unarrivedUsers);
      commit('SET_SIGNRESULTDATAS', getResultList(payload.signResultDatas));

      commit('SET_CRUISETASKPOINTNUM',payload.cruiseTaskPointNum)
      commit('SET_CRUISEPROSSESNUM',payload.cruiseProssesNum)
      commit('SET_CRUISENEEDTIME',payload.cruiseNeedTime)

      commit('SET_SEVERITY',payload.severity)
      commit('SET_NORMAL',payload.normal)
    }
  },
  updateVariables({ commit,state }, payload) {
    // console.log(payload)
    commit('SET_ARRIVEDNUM', payload.arrivedNum);
    commit('SET_UNARRIVEDNUM',payload.unarrivedNum);
    commit('SET_IMPORTARRIVEDNUM', payload.importArrivedNum);
    commit('SET_IMPORTUNARRIVEDNUM',payload.importUnArrivedNum);
    commit('SET_NEEDTIME', payload.needTime);
    commit('SET_PROCESSNUM',payload.processNum);
    commit('SET_TASKPOINTNUM',payload.taskPointNum);
    commit('SET_UNARRIVEDUSERS', payload.unarrivedUsers);
    // commit('SET_SIGNRESULTDATAS', payload.signResultDatas);
    const formattedData = getResultList(payload.signResultDatas);
    commit('SET_SIGNRESULTDATAS',formattedData);

    const updatedData = {
      ...state, // 包含所有状态
      ...payload,
    };
    localStorage.setItem('bigScreenData', JSON.stringify(updatedData));
  },
  updateCruiseDate({ commit,state }, payload) {
    commit('SET_CRUISETASKPOINTNUM', payload.taskPointNum);
    commit('SET_CRUISEPROSSESNUM', payload.processNum);
    commit('SET_CRUISENEEDTIME', payload.needTime);
    const updatedData = {
      ...state, // 包含所有状态
      cruiseTaskPointNum: payload.taskPointNum,
      cruiseProssesNum: payload.processNum,
      cruiseNeedTime: payload.needTime,
    };
    localStorage.setItem('bigScreenData', JSON.stringify(updatedData));
  },
  updateStatistics({ commit,state }, payload) {
    commit('SET_SEVERITY',payload.severity);
    commit('SET_NORMAL',payload.normal)
    const updatedData = {
      ...state, // 包含所有状态
      severity: payload.severity,
      normal: payload.normal,
    };
    localStorage.setItem('bigScreenData', JSON.stringify(updatedData));
  }

};

// 数据变形方法
function getResultList(data) {
  let formattedData = data.map(item => {
    let date = new Date(item.resultDate);
    let formattedDate = `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
    return [
      formattedDate,
      item.pointName,
      item.dogName,
      item.userName,
      item.result
    ];
  });
  // console.log(formattedData);
  return formattedData;
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
