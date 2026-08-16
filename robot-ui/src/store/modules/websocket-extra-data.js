import { set } from "nprogress";
import { active } from "sortablejs";
import Vue from "vue";
import {
  SLAM_POINTS,
  ENABLE_LIANTONG_SLAM_MOCK,
  GIS_MAP_CENTER_POINT,
  getLiantongSlamMock,
  getLiantongFixedCameraMock,
  getLiantongWheeledRobotMock,
  getLiantongPendingTaskMock
} from "../../views/bi/js/constants/gisMapPoints";
import {
  isDeviceAssociatedTaskStatus,
  isPausedTaskStatus,
  isRunningTaskStatus
} from "../../views/bi/patrol/business/execution-status";

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
  alarmRevision: 0,
   // 实时定位
  robotLocation: {}, // { robotId: { lat, lng, altitude, address, updatedAt } }
  // 设备基本信息；task / runningTask 由 taskData.equipmentList 反查，不取 overview.devices.task
  robotBaseInfo: {}, // { robotId: { ...robotInfo } }
  // 装备列表
  robotList: [],
  robotAlarmObj: {}, // { robotId: { ...alarmInfo } }
  slamMapData: [],
  taskPathPoints: {}, // { taskId: [pathPoints] } taskId: 任务id，pathId: 路径id，mapId: 地图id，pathPoints: 任务路径点
  mapSearchValue: '',
  slamMapList: [],
  slamOfRobot: {},
  showRobotIds: [],
  // 当前全局地图标识：GIS 为 'gis'，SLAM 为对应地图 id
  globalMapId: '',
  // 具备 GPS 经纬度的设备列表（来自 panorama overview.gpsDevices）
  defaultGpsDevices: [],
  // overview（setAll）是否已完成默认地图判断；未就绪前不挂载 GIS，避免抢在 SLAM 数据前闪一下
  overviewReady: false,
  // overview 默认地图是否为 SLAM（用于无预览气泡：仅默认 SLAM 时提示）
  defaultMapIsSlam: false,
  // SLAM 无预览气泡是否已在全局展示过（跨路由只提示一次）
  slamEmptyTipShown: false,
  // gis 中心视图，默认坐标点 [lat, lng]，来自 map-config.js（gisConfig.area.key）
  gisMapCenterPoint: GIS_MAP_CENTER_POINT
}

const mutations = {
  SET_DEVICE_OBJ(state, value) {
    state.deviceObj = value;
  },
  SET_TASK_INFO(state, value) {
    if (!value || value.taskId === undefined || value.taskId === null) return
    const prev = getTaskById(state.taskData, value.taskId) || {}
    const obj = { ...value };
    if (!getTaskById(state.taskData, value.taskId) && !value.timestamp) {
      obj.timestamp = new Date().getTime() + 10;
    }
    const merged = { ...prev, ...obj }
    state.taskData = Object.assign({}, state.taskData, { [value.taskId]: merged });
    const affectedIds = [
      ...collectTaskEquipmentIds(prev),
      ...collectTaskEquipmentIds(merged),
      ...robotsHoldingTask(state.robotBaseInfo, value.taskId)
    ]
    applyDerivedRobotTasks(state, affectedIds)
  },
  SET_ALARMS_DATA(state, value) {
    if (!value) return;
    if (value.high && value.medium && value.low) {
      state.alarmsData = value
      return;
    }
    const level = String(value.level || '').toLowerCase();
    if (!['high', 'medium', 'low'].includes(level) || value.alarmId == null) return;
    const next = { ...state.alarmsData };
    ['high', 'medium', 'low'].forEach(key => {
      const group = next[key] || { items: [] };
      next[key] = {
        ...group,
        items: (group.items || []).filter(item => String(item.alarmId) !== String(value.alarmId))
      };
    });
    next[level] = {
      ...next[level],
      items: [...(next[level].items || []), { ...value, level: value.level.toUpperCase() }]
    };
    state.alarmsData = next;
  },
  BUMP_ALARM_REVISION(state) {
    state.alarmRevision += 1;
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
    const incoming = { ...(robotInfo || {}) }
    delete incoming.task
    delete incoming.runningTask
    delete incoming.runningTaskId
    delete incoming.customStatusName
    delete incoming.statusClass
    const merged = {
      ...state.robotBaseInfo?.[robotId] || {},
      ...incoming,
      robotId: incoming.robotId ?? robotId
    }
    const task = tasksForRobot(state.taskData, merged.robotId)
    const withTask = { ...merged, task }
    state.robotBaseInfo = {
      ...state.robotBaseInfo,
      [robotId]: { ...withTask, ...getRobotStatus(withTask, state.taskData) }
    }
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
    state.showRobotIds = Array.isArray(value) ? value : (value != null && value !== '' ? [value] : []);
  },
  SET_GLOBAL_MAP_ID(state, value) {
    // GIS 为 'gis'；SLAM 为地图 id；空值归一为 ''
    state.globalMapId = value == null ? '' : value;
  },
  SET_DEFAULT_GPS_DEVICES(state, value) {
    state.defaultGpsDevices = Array.isArray(value) ? value : [];
  },
  SET_OVERVIEW_READY(state, value) {
    state.overviewReady = !!value;
  },
  SET_DEFAULT_MAP_IS_SLAM(state, value) {
    state.defaultMapIsSlam = !!value;
  },
  SET_SLAM_EMPTY_TIP_SHOWN(state, value) {
    state.slamEmptyTipShown = !!value;
  },
}

const actions = {
  setAll({commit, state, dispatch}, data) {
    // 联通展厅 SLAM：注入模拟装备与任务路径
    const devices = [...(data?.devices || [])]
    const tasks = [...(data?.tasks || [])]
    if (ENABLE_LIANTONG_SLAM_MOCK) {
      const mock = getLiantongSlamMock()
      if (!devices.some(item => String(item.robotId) === String(mock.robotId))) {
        devices.push(mock.device)
      }
      if (!tasks.some(item => String(item.taskId) === String(mock.taskId))) {
        tasks.push(mock.task)
      }
      const cameraMock = getLiantongFixedCameraMock()
      if (!devices.some(item => String(item.robotId) === String(cameraMock.robotId))) {
        devices.push(cameraMock.device)
      }
      if (cameraMock.task && !tasks.some(item => String(item.taskId) === String(cameraMock.taskId))) {
        tasks.push(cameraMock.task)
      }
      const wheeledMock = getLiantongWheeledRobotMock()
      if (!devices.some(item => String(item.robotId) === String(wheeledMock.robotId))) {
        devices.push(wheeledMock.device)
      }
      if (wheeledMock.task && !tasks.some(item => String(item.taskId) === String(wheeledMock.taskId))) {
        tasks.push(wheeledMock.task)
      }
      const pendingMock = getLiantongPendingTaskMock()
      if (pendingMock.task && !tasks.some(item => String(item.taskId) === String(pendingMock.taskId))) {
        tasks.push(pendingMock.task)
      }
    }

    // 装备 task 只从全局 taskData 反查，不使用 overview.devices.task
    const devicesWithoutTask = devices.map(item => {
      if (!item || item.task === undefined) return item
      const next = { ...item }
      delete next.task
      return next
    })

    // 调用 websocketRobot 模块的 loadRobots
    dispatch('websocketRobot/loadRobots', devicesWithoutTask, { root: true })
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
    tasks.map((item, index) => {
      commit('SET_TASK_INFO', { ...item, timestamp: new Date().getTime() + tasks.length - index });
      commit('SET_TASK_PATH_POINTS', { taskId: item.taskId, data: { mapId: item.mapId, pathPoints: item.pathPoints || [] } });
    })
    commit('SET_ROBOT_LIST', devicesWithoutTask);
    devicesWithoutTask.map(item => {
      commit('SET_ROBOT_BASE_INFO', { robotId: item.robotId, robotInfo: { ...item } });
      commit('SET_ROBOT_LOCATION', { robotId: item.robotId, location: item.location });
    })
    data?.alarms?.high?.items.map((item, index) => {
      commit('SET_ROBOT_ALARM_INFO', { robotId: item.robotId, alarmInfo: item });
    })
    // 当前地图点位数据模拟在map对象的points字段中
    const slamMapList = (data?.map || []).map(item => {
      item.points = item.points || (ENABLE_LIANTONG_SLAM_MOCK ? SLAM_POINTS?.[item.id] || [] : []);
      return item;
    });
    // 有 GPS 设备时默认 GIS；否则默认第一张 SLAM；两者都没有时才默认 GIS
    // 必须等 overview 数据到位后再写入，避免页面加载瞬间先挂 GIS 再被 SLAM 顶掉
    const defaultGpsDevices = (Array.isArray(data?.gpsDevices) && data.gpsDevices.length)
      ? data.gpsDevices
      : devices.filter(item => {
          const lat = item?.location?.lat
          const lng = item?.location?.lng
          return lat != null && lat !== '' && lng != null && lng !== ''
            && Number.isFinite(Number(lat)) && Number.isFinite(Number(lng))
        });
    commit('SET_DEFAULT_GPS_DEVICES', defaultGpsDevices);
    commit('SET_SLAM_MAP_LIST', slamMapList);
    commit('SET_SLAM_OF_ROBOT', buildSlamOfRobot(slamMapList, devicesWithoutTask, tasks));
    if (defaultGpsDevices.length) {
      commit('SET_GLOBAL_MAP_ID', 'gis');
      commit('SET_DEFAULT_MAP_IS_SLAM', false);
    } else if (slamMapList.length) {
      commit('SET_GLOBAL_MAP_ID', slamMapList[0]?.id);
      commit('SET_DEFAULT_MAP_IS_SLAM', true);
    } else {
      commit('SET_GLOBAL_MAP_ID', 'gis');
      commit('SET_DEFAULT_MAP_IS_SLAM', false);
    }
    commit('SET_OVERVIEW_READY', true);
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
  syncRobot({ commit, rootState }, event) {
    // | `panorama.device.status.changed`   | 设备在线、离线、故障、电量变化                   |
    // | ---------------------------------- | ------------------------------------------------ |
    // | `panorama.device.location.changed` | 地图位置、速度、朝向变化                         |
    // | `panorama.task.changed`            | 任务创建、更新、删除、状态变化、设备任务关联变化 |
    // | `panorama.alarm.changed`           | 告警创建、更新、处置状态变化                     |
    // | `panorama.stats.changed`           |                                                  |
    if (!event) return
    if (event.event === 'panorama.device.status.changed') {
      // console.log(123, event.data.robotId, event.data.status);
      const robotId= event.data.robotId;
      const robot = rootState.websocketRobot.robots?.length
      ? rootState.websocketRobot.robots.find(item => item.robotId === robotId) || {}
      : {};
      commit('SET_ROBOT_BASE_INFO', { robotId, robotInfo: { ...robot, ...state.robotBaseInfo[robotId], ...event.data }});
      // commit('SET_ROBOT_BASE_INFO', { robotId: 'test111', robotInfo: { ...state.robotBaseInfo['test111'], status: 'online' } });
    } else if (event.event === 'panorama.device.location.changed') {
      commit('SET_ROBOT_LOCATION', { robotId: event.data.robotId, location: event.data.location });
    } else if (event.event === 'panorama.task.changed') {
      const task = event.data?.task || (event.data?.taskId != null ? event.data : null)
      if (task) commit('SET_TASK_INFO', task)
    } else if (event.event === 'panorama.alarm.changed') {
      const alarm = event.data && event.data.alarm;
      if (!alarm) return;
      commit('SET_ALARMS_DATA', alarm);
      if (event.data.summary) {
        commit('SET_ALARM_SUMMARY', event.data.summary);
      }
      commit('BUMP_ALARM_REVISION');
      if (alarm.level && alarm.level.toLowerCase() === 'high' && alarm.status === 'unhandled') {
        commit('SET_ROBOT_ALARM_INFO', { robotId: alarm.robotId, alarmInfo: alarm });
      } else if (alarm.robotId) {
        commit('SET_ROBOT_ALARM_INFO', { robotId: alarm.robotId, alarmInfo: alarm, close: true });
      }
    } else if (event.event === 'panorama.stats.changed') {
      commit('SET_DEVICE_TYPES_STATS', event.data.deviceTypeStats || state.deviceTypeStats || []);
      commit('SET_DEVICE_STATS', event.data.deviceStats || state.deviceStats || {});
      commit('SET_ALARM_SUMMARY', event.data.alarmSummary || state.alarmSummary || {});
      commit('SET_TASK_OVERVIEW', event.data.taskOverview || state.taskOverview || {});
      commit('SET_PATROL_OVERVIEW', event.data.patrolOverview || state.patrolOverview || {});
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
  setGlobalMapId({ commit }, value) {
    commit('SET_GLOBAL_MAP_ID', value);
  },
  setSlamEmptyTipShown({ commit }, value) {
    commit('SET_SLAM_EMPTY_TIP_SHOWN', value);
  },
}

function buildSlamOfRobot(maps, robots, tasks) {
  const result = {}
  const robotMapIds = {}

  maps.forEach(mapInfo => {
    if (mapInfo?.id === undefined || mapInfo?.id === null) return
    result[String(mapInfo.id)] = { mapInfo, robots: [] }
  })

  tasks.forEach(task => {
    const mapId = task?.mapId
    if (mapId === undefined || mapId === null) return
    const equipmentList = task?.equipmentList || task?.devices || task?.robots || []
    equipmentList.forEach(robot => {
      const robotId = robot?.robotId || robot?.id || robot
      if (robotId === undefined || robotId === null) return
      robotMapIds[String(robotId)] = mapId
    })
  })

  robots.forEach(robot => {
    const directMapId = robot?.mapId ?? robot?.location?.mapId
    const mapId = directMapId ?? robotMapIds[String(robot?.robotId)]
    if (mapId === undefined || mapId === null) return
    const key = String(mapId)
    if (!result[key]) result[key] = { mapInfo: null, robots: [] }
    if (!result[key].robots.some(item => item.robotId === robot.robotId)) {
      result[key].robots.push(robot)
    }
  })

  return result
}

function getTaskById(taskData, taskId) {
  if (taskId === undefined || taskId === null || taskId === '') return null
  return taskData?.[taskId] || taskData?.[String(taskId)] || null
}

function collectTaskEquipmentIds(task) {
  const ids = []
  const list = task?.equipmentList || task?.devices || task?.robots || []
  list.forEach(item => {
    const id = item == null ? null : (typeof item === 'object' ? (item.robotId ?? item.id) : item)
    if (id !== undefined && id !== null && id !== '') ids.push(String(id))
  })
  if (task?.robotId !== undefined && task?.robotId !== null && task?.robotId !== '') {
    ids.push(String(task.robotId))
  }
  return [...new Set(ids)]
}

function toRobotTaskSummary(task) {
  return {
    taskId: task.taskId,
    workflowInstanceId: task.workflowInstanceId,
    name: task.name,
    status: task.status,
    timeRange: task.timeRange,
    mapId: task.mapId
  }
}

function tasksForRobot(taskData, robotId) {
  if (robotId === undefined || robotId === null || robotId === '') return []
  const target = String(robotId)
  const result = []
  Object.values(taskData || {}).forEach(task => {
    if (!task || !isDeviceAssociatedTaskStatus(task.status)) return
    if (!collectTaskEquipmentIds(task).includes(target)) return
    if (result.some(item => String(item.taskId) === String(task.taskId))) return
    result.push(toRobotTaskSummary(task))
  })
  return result
}

function findRobotKey(robotBaseInfo, robotId) {
  if (!robotBaseInfo || robotId === undefined || robotId === null || robotId === '') return null
  if (robotBaseInfo[robotId]) return robotId
  const target = String(robotId)
  if (robotBaseInfo[target]) return target
  return Object.keys(robotBaseInfo).find(key => String(key) === target) || null
}

function robotsHoldingTask(robotBaseInfo, taskId) {
  const target = String(taskId)
  return Object.keys(robotBaseInfo || {}).filter(key =>
    (robotBaseInfo[key]?.task || []).some(item => String(item?.taskId) === target)
  )
}

function applyDerivedRobotTasks(state, robotIds) {
  const seen = new Set()
  const keys = []
  ;(robotIds || []).forEach(id => {
    const key = findRobotKey(state.robotBaseInfo, id)
    if (key == null || seen.has(String(key))) return
    seen.add(String(key))
    keys.push(key)
  })
  if (!keys.length) return
  const next = { ...state.robotBaseInfo }
  keys.forEach(key => {
    const robot = next[key]
    if (!robot) return
    const task = tasksForRobot(state.taskData, robot.robotId ?? key)
    const withTask = { ...robot, task }
    next[key] = { ...withTask, ...getRobotStatus(withTask, state.taskData) }
  })
  state.robotBaseInfo = next
}

function getRobotStatus(robot, taskData) {
  const { status, robotId } = robot || {}
  const taskList = tasksForRobot(taskData, robotId)
    .map(item => getTaskById(taskData, item.taskId) || item)
  const runningTask = taskList.find(item => isRunningTaskStatus(item?.status))
    || taskList.find(item => isPausedTaskStatus(item?.status))
    || null
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
