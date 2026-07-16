import { set } from "nprogress";
import { active } from "sortablejs";
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
  // 巡航统计
  patrolOverview: {}, // { durationToday: 32.6, durationUnit: "小时", mileageToday: 262.6, mileageUnit: "KM" }
  // 任务统计
  taskOverview: {}, //{ totalToday: 50, completedRate: 100, completedRateText: "100%", running: 48, pending: 2 }
  // 告警统计
  alarmSummary: {}, // { totalToday: 50, handled: 18, unhandled: 0, handleRate: 100, handleRateText: "100%" }
   // 实时定位
  robotLocation: {}, // { robotId: { lat, lng, altitude, address, updatedAt } }
  // 设备基本信息
  robotBaseInfo: {}, // { robotId: { ...robotInfo } }
  // 装备列表
  robotList: [],
  robotAlarmObj: {}, // { robotId: { ...alarmInfo } }
  slamMapData: [],
  taskPathPoints: {}, // { taskId: [pathPoints] } taskId: 任务id，pathId: 路径id，mapId: 地图id，pathPoints: 任务路径点
  mapSearchValue: '',
  slamMapList: [],
  slamOfRobot: {},
  showRobotIds: []
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
        // items.splice(index, 1);
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
  SET_ALARM_SUMMARY(state, value) {
    state.alarmSummary = value;
  },
  SET_TASK_OVERVIEW(state, value) {
    state.taskOverview = value;
  },
  SET_PATROL_OVERVIEW(state, value) {
    state.patrolOverview = value;
  },
  SET_ROBOT_LOCATION(state, data) {
    // setTimeout(() => {
    //   console.log('执行==========');
      state.robotLocation = { ...state.robotLocation, [data.robotId]: data.location };
    // }, 20000);
  },
  SET_ROBOT_BASE_INFO(state, { robotId, robotInfo }) {
    state.robotBaseInfo = { ...state.robotBaseInfo, [robotId]: { ...state.robotBaseInfo?.[robotId] || {}, ...robotInfo, ...getRobotStatus({ ...state.robotBaseInfo?.[robotId], ...robotInfo }, state.taskData) } };
  },
  SET_ROBOT_LIST(state, value) {
    state.robotList = value;
  },
  SET_SLAM_MAP_DATA(state, value) {
    state.slamMapData = value;
  },
  SET_TASK_PATH_POINTS(state, { taskId, data }) {
    state.taskPathPoints = { ...state.taskPathPoints, [taskId]: data };    
  },
  SET_MAP_SEARCH_VALUE(state, value) {
    state.mapSearchValue = value ? `${value}_timestamp_${new Date().getTime()}` : '';
  },
  SET_SLAM_MAP_LIST(state, value) {
    state.slamMapList = value;
  },
  SET_SLAM_OF_ROBOT(state, value) {
    state.slamOfRobot = value;
  },
  SET_SHOW_ROBOT_IDS(state, value) {
    state.showRobotIds = value;
  },
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
    commit('SET_PATROL_OVERVIEW', data?.patrolOverview || { durationToday: '-', durationUnit: '小时', mileageToday: '-', mileageUnit: 'KM' });
    commit('SET_TASK_OVERVIEW', data?.taskOverview || { totalToday: '-', completedRate: '-', completedRateText: '-%', running: '-', pending: '-' });
    commit('SET_ALARM_SUMMARY', data?.alarms?.summary || { totalToday: '-', handled: '-', unhandled: '-', handleRate: '-', handleRateText: '-%' });
    [...(data?.tasks || [])].concat([
    //   {
    //     "statusName": "暂停中",
    //     "endTime": "2026-06-12 22:00:00",
    //     "startTime": "2026-06-12 20:00:00",
    //     "taskId": "task-011",
    //     "name": "B区-夜间巡逻",
    //     "timeRange": "18:00-19:00",
    //     "equipmentList": [
    //         {
    //             "status": "online",
    //             "name": "R1轮式机器人",
    //             "type": "WHEELED_ROBOT",
    //             "robotId": "robot-001"
    //         }
    //     ],
    //     "currentLocation": "B区主干道",
    //     "status": "paused"
    // },
    //   {
    //     "statusName": "暂停中",
    //     "endTime": "2026-06-12 22:00:00",
    //     "startTime": "2026-06-12 20:00:00",
    //     "taskId": "task-012",
    //     "name": "B区-仓库复核",
    //     "timeRange": "09:00-10:00",
    //     "equipmentList": [
    //         {
    //             "status": "online",
    //             "name": "R1轮式机器人",
    //             "type": "WHEELED_ROBOT",
    //             "robotId": "robot-001"
    //         }
    //     ],
    //     "currentLocation": "B区主干道",
    //     "status": "paused"
    // },
    ]).map(item => {
      commit('SET_TASK_INFO', item);
      commit('SET_TASK_PATH_POINTS', { taskId: item.taskId, data: { mapId: item.mapId, pathPoints: item.pathPoints || [] } });
    })
    commit('SET_ROBOT_LIST', data?.devices || []);
    data?.devices?.map(item => {
      state.robotBaseInfo[item.robotId] = Object.assign({}, item);
      commit('SET_ROBOT_BASE_INFO', { robotId: item.robotId, robotInfo: { ...item } });
      commit('SET_ROBOT_LOCATION', { robotId: item.robotId, location: item.location });
      
    })
    data?.alarms?.high?.items.map((item, index) => {
      commit('SET_ROBOT_ALARM_INFO', { robotId: item.robotId, alarmInfo: item });
    })
    const slamMapList = data?.map || [];
    commit('SET_SLAM_MAP_LIST', slamMapList);
    commit('SET_SLAM_OF_ROBOT', buildSlamOfRobot(slamMapList, data?.devices || [], data?.tasks || []));
  },
  setSlamMapData({ commit }, value) {
    commit('SET_SLAM_MAP_DATA', value);
  },
  setTaskPathPoints({ commit }, { taskId, data }) {    
    commit('SET_TASK_PATH_POINTS', { taskId, data });
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
  syncRobot({ commit }, event) {
    // | `panorama.device.status.changed`   | 设备在线、离线、故障、电量变化                   |
    // | ---------------------------------- | ------------------------------------------------ |
    // | `panorama.device.location.changed` | 地图位置、速度、朝向变化                         |
    // | `panorama.task.changed`            | 任务创建、更新、删除、状态变化、设备任务关联变化 |
    // | `panorama.alarm.changed`           | 告警创建、更新、处置状态变化                     |
    // | `panorama.stats.changed`           |                                                  |
    if (!event) return
    if (event.event === 'panorama.device.status.changed') {
      // console.log(123, event.data.robotId, event.data.status);
      
      commit('SET_ROBOT_BASE_INFO', { robotId: event.data.robotId, robotInfo: { ...state.robotBaseInfo[event.data.robotId], ...event.data } });
      // commit('SET_ROBOT_BASE_INFO', { robotId: 'test111', robotInfo: { ...state.robotBaseInfo['test111'], status: 'online' } });
    } else if (event.event === 'panorama.device.location.changed') {      
      commit('SET_ROBOT_LOCATION', { robotId: event.data.robotId, location: event.data.location });
    } else if (event.event === 'panorama.task.changed') {
      commit('SET_TASK_INFO', event.data.task);
    } else if (event.event === 'panorama.alarm.changed') {
      // commit('SET_ALARMS_DATA', event.data.alarm);
      // if (event.data.alarm.level.toLowerCase() === 'high') {
      //   commit('SET_ROBOT_ALARM_INFO', { robotId: event.data.alarm.robotId, alarmInfo: event.data.alarm });
      // }
    } else if (event.event === 'panorama.stats.changed') {
      commit('SET_DEVICE_TYPES_STATS', event.data.deviceTypeStats || []);
      commit('SET_DEVICE_STATS', event.data.deviceStats || {});
      commit('SET_ALARM_SUMMARY', event.data.alarmSummary || {});
      commit('SET_TASK_OVERVIEW', event.data.taskOverview || {});
      commit('SET_PATROL_OVERVIEW', event.data.patrolOverview || {});
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
  },
  setMapSearchValue({ commit }, value) {
    commit('SET_MAP_SEARCH_VALUE', value);
  },
  setSlamMapList({ commit }, value) {
    commit('SET_SLAM_MAP_LIST', value);
  },
  setShowRobotIds({ commit }, value) {
    commit('SET_SHOW_ROBOT_IDS', value);
  },
}

function buildSlamOfRobot(maps, robots, tasks) {
  const result = {}
  const robotMapIds = {}
  const taskMapIds = {}

  maps.forEach(mapInfo => {
    if (mapInfo?.id === undefined || mapInfo?.id === null) return
    result[String(mapInfo.id)] = { mapInfo, robots: [] }
  })

  tasks.forEach(task => {
    const mapId = task?.mapId
    if (mapId === undefined || mapId === null) return
    if (task?.taskId !== undefined && task?.taskId !== null) {
      taskMapIds[String(task.taskId)] = mapId
    }
    const equipmentList = task?.equipmentList || task?.devices || task?.robots || []
    equipmentList.forEach(robot => {
      const robotId = robot?.robotId || robot?.id || robot
      if (robotId === undefined || robotId === null) return
      robotMapIds[String(robotId)] = mapId
    })
  })

  robots.forEach(robot => {
    const directMapId = robot?.mapId ?? robot?.location?.mapId
    const taskMapId = (Array.isArray(robot?.task) ? robot.task : [robot?.task])
      .filter(Boolean)
      .map(task => task?.mapId ?? taskMapIds[String(task?.taskId ?? task?.id)])
      .find(mapId => mapId !== undefined && mapId !== null)
    const mapId = directMapId ?? taskMapId ?? robotMapIds[String(robot?.robotId)]
    if (mapId === undefined || mapId === null) return
    const key = String(mapId)
    if (!result[key]) result[key] = { mapInfo: null, robots: [] }
    if (!result[key].robots.some(item => item.robotId === robot.robotId)) {
      result[key].robots.push(robot)
    }
  })

  return result
}

function getRobotStatus(robot, taskData) {
  const { status, task = [] } = robot || {}
  const runningTask = task?.map(item => taskData?.[item.taskId] || item)?.find(item => item.status === 'running') || null
  const customStatusName = status === 'online' ? runningTask ? '任务中' : '空闲中' : status === 'offline' ? '离线' : '故障'
  const statusClass = status === 'online' ? runningTask ? 'green' : 'blue' : status === 'offline' ? 'gray' : 'orange'
  return { customStatusName, statusClass, runningTaskId: runningTask?.taskId, runningTask }
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
  getShowRobotIds: state => state.showRobotIds,
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
