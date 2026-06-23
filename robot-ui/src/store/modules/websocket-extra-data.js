import { set } from "nprogress";
import Vue from "vue";

const state = {
  // 设备对象：设备详情，包含坐标位置，task基本信息
  deviceObj: {},
  // 任务详情
  taskData: {}, // { taskId: { ...taskInfo } }
  // 告警数据
  alarmsData: {}, // { high: {}, medium: {}, low: {} }
  // 设备类型统计
  deviceTypeStats: [], // [{  type: '', count:0, name: '' }]
  // 设备状态统计
  deviceStats: {}, // { fault: '-', offline: '-', total: '-', online: '-' }
   // 实时定位
  robotLocation: {}, // { robotId: { lat, lng, altitude, address, updatedAt } }
  // 设备基本信息
  robotBaseInfo: {}, // { robotId: { ...robotInfo } }
  // 装备列表
  robotList: [],
  robotAlarmObj: {} // { robotId: { ...alarmInfo } }
}

const mutations = {
  SET_DEVICE_OBJ(state, value) {
    state.deviceObj = value;
  },
  SET_TASK_INFO(state, value) {
    state.taskData = Object.assign({}, state.taskData, { [value.taskId]: {...state.taskData[value.taskId] || {}, ...value} });
  },
  SET_ALARMS_DATA(state, value) {
    if (value.high && value.medium && value.low) {
      state.alarmsData = value
      return;
    }
    value.level = value.level.toLowerCase();
    if (state.alarmsData?.[value.level]) {
      const { count, items } = state.alarmsData[value.level];
      const index = items.findIndex(item => item.alarmId === value.alarmId);
      if (index !== -1) {
        // items[index] = value;
        items.splice(index, 1);
      } else {
        items.push(value);
      }
      state.alarmsData = Object.assign({}, state.alarmsData, {[value.level]: {
        ...state.alarmsData[value.level],
        count: index !== -1 ? count : (count + 1),
        items
      }});
    } else {
      state.alarmsData = Object.assign({}, state.alarmsData, { [value.level]: {
        level: value.level,
        levelName: value.levelName,
        count: 1,
        items: [value]
      }});
    }
  },
  SET_ROBOT_ALARM_INFO(state, { robotId, alarmInfo, close }) {
    if (close) {
      Vue.delete(state.robotAlarmObj, robotId);      
    } else {
      state.robotAlarmObj = { ...state.robotAlarmObj, [robotId]: alarmInfo };
    }
  },
  SET_DEVICE_TYPES_STATS(state, value) {
    state.deviceTypeStats = value;
  },
  SET_DEVICE_STATS(state, value) {
    state.deviceStats = value;
  },
  SET_ROBOT_LOCATION(state, data) {
    state.robotLocation = { ...state.robotLocation, [data.robotId]: data.location };
  },
  SET_ROBOT_BASE_INFO(state, { robotId, robotInfo }) {
    state.robotBaseInfo = { ...state.robotBaseInfo, [robotId]: robotInfo };
  },
  SET_ROBOT_LIST(state, value) {
    state.robotList = value;
  }
}

const actions = {
  setAll({commit, state, dispatch}, data) {
    // 调用 websocketRobot 模块的 loadRobots
    dispatch('websocketRobot/loadRobots', data?.devices || [], { root: true })
    commit('SET_ALARMS_DATA', data?.alarms || {});
    commit('SET_DEVICE_TYPES_STATS', data?.deviceTypeStats || []);
    commit('SET_DEVICE_STATS', data?.deviceStats || {
      fault: '-',
      offline: '-',
      total: '-',
      online: '-'
    });
    data?.tasks?.map(item => {
      commit('SET_TASK_INFO', item);
    })
    commit('SET_ROBOT_LIST', data?.devices || []);
    data?.devices?.map(item => {
      state.robotBaseInfo[item.robotId] = Object.assign({}, item);
      state.robotLocation[item.robotId] = Object.assign({}, item.location);
      
    })
    data?.alarms?.high?.items.map((item, index) => {
      commit('SET_ROBOT_ALARM_INFO', { robotId: item.robotId, alarmInfo: item });
    })
  },
  // 设置设备对象
  setDeviceObj({ commit }, value) {
    commit('SET_DEVICE_OBJ', value);
  },
  // 设置任务列表
  setTaskList({ commit }, value) {
    commit('SET_TASK_INFO', value);
  },
  // 设置告警列表
  setAlarmsData({ commit }, value) {
    commit('SET_ALARMS_DATA', value);
  },
  updateHighAlarmsData({ commit }, value) {
    const index = state.alarmsData?.[value.level]?.items?.findIndex(item => item.alarmId === value.alarmId);
    if (index > 0) {
      state.alarmsData = Object.assign({}, state.alarmsData, {[value.level]: {
        ...state.alarmsData[value.level],
        items: value.items
      }});

    }
  },
  // 设置设备类型统计
  setDeviceTypesStats({ commit }, value) {
    commit('SET_DEVICE_TYPES_STATS', value);
  },
  // 设置设备状态统计
  setDeviceStats({ commit }, value) {
    commit('SET_DEVICE_STATS', value);
  },
  // 设置实时定位
  setRobotLocation({ commit }, { robotId, location }) {
    commit('SET_ROBOT_LOCATION', { robotId, location });
  },
  syncRobot({ commit }, data) {
    // | `panorama.device.status.changed`   | 设备在线、离线、故障、电量变化                   |
    // | ---------------------------------- | ------------------------------------------------ |
    // | `panorama.device.location.changed` | 地图位置、速度、朝向变化                         |
    // | `panorama.task.changed`            | 任务创建、更新、删除、状态变化、设备任务关联变化 |
    // | `panorama.alarm.changed`           | 告警创建、更新、处置状态变化                     |
    // | `panorama.stats.changed`           |                                                  |
    if (!event) return
    if (event.event === 'panorama.device.status.changed') {
      commit('SET_ROBOT_BASE_INFO', { robotId: event.data.robotId, robotInfo: { ...state.robotBaseInfo[event.data.robotId], ...event.data } });
    } else if (event.event === 'panorama.device.location.changed') {
      commit('SET_ROBOT_LOCATION', { robotId: event.data.robotId, location: event.data.location });
    } else if (event.event === 'panorama.task.changed') {
      commit('SET_TASK_INFO', event.data.task);
    } else if (event.event === 'panorama.alarm.changed') {
      commit('SET_ALARMS_DATA', event.data);
      if (event.data.level === 'HIGH') {
        commit('SET_ROBOT_ALARM_INFO', { robotId: event.data.robotId, alarmInfo: event.data });
      }
    } else if (event.event === 'panorama.stats.changed') {
      commit('SET_DEVICE_TYPES_STATS', event.data.deviceTypeStats || []);
      commit('SET_DEVICE_STATS', event.data.deviceStats);
      // alarmStats: { high: 0, medium: 0, low: 0 }
    }
  },
  setRobotBaseInfo({ commit }, { robotId, robotInfo }) {
    commit('SET_ROBOT_BASE_INFO', { robotId, robotInfo });
  },
  setRobotAlarmInfo({ commit }, { robotId, alarmInfo, close }) {
    if (close) {
      commit('SET_ALARMS_DATA', alarmInfo);
    }
    commit('SET_ROBOT_ALARM_INFO', { robotId, alarmInfo, close });
  }
}

const getters = {
  // 获取设备对象
  getDeviceObj: state => state.deviceObj,
  // 获取任务列表
  getTaskData: state => state.taskData,
  // 获取告警列表
  getalarmsData: state => state.alarmsData,
  // 获取设备类型统计
  getDeviceTypesStats: state => state.deviceTypeStats,
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