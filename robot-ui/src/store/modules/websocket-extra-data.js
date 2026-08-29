import Vue from "vue";
import { mergeRobotBaseInfo } from '../../views/bi/js/utils/prefer-live-robot-fields';
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
import {
  collectTaskEquipmentIds,
  getTaskById,
  listTasksForRobot
} from "../../views/bi/patrol/business/task-equipment";
import {
  getPatrolPanoramaMapResources,
  getPatrolPanoramaMapTaskRoutes,
  getPatrolPanoramaOverview,
  getPatrolPanoramaTaskDetail,
  getPatrolPanoramaTaskFixedCameras
} from '../../api/new-bi'

let overviewRefreshPromise = null
const mapResourcePromises = new Map()
const taskFixedCameraPromises = new Map()

const state = {
  // 设备对象：按需加载的设备详情
  deviceObj: {},
  // 任务详情
  taskData: {}, // { taskId: { ...taskInfo } }
  // 实时监控任务卡按需展开的固定摄像头，键为 taskId
  taskFixedCameraData: {},
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
  // 聚合数据质量；任务降级时页面不得把空集合解释为真实的 0 条任务
  dataQuality: {},
  // 告警统计
  alarmSummary: {}, // { totalToday: 50, handled: 18, unhandled: 0, handleRate: 100, handleRateText: "100%" }
  alarmRevision: 0,
   // 实时定位
  robotLocation: {}, // { robotId: { lat, lng, altitude, address, updatedAt } }
  // 设备基本信息；task 由 taskData 反查；cameras 只存 websocketRobot（overview/robot.state）
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
  // 快照替换/失权/退出后，旧地图请求不得回写新页面。
  overviewRevision: 0,
  // 具备 GPS 经纬度的设备列表（来自 panorama overview.gpsDevices）
  defaultGpsDevices: [],
  // overview（setAll）是否已完成默认地图判断；未就绪前不挂载 GIS，避免抢在 SLAM 数据前闪一下
  overviewReady: false,
  // overview 默认地图是否为 SLAM（用于无预览气泡：仅默认 SLAM 时提示）
  defaultMapIsSlam: false,
  // 首屏总览加载失败时保留明确错误态，不能长期显示“正在获取地图数据”。
  overviewLoadError: false,
  // SLAM 无预览气泡是否已在全局展示过（跨路由只提示一次）
  slamEmptyTipShown: false,
  // gis 中心视图，默认坐标点 [lat, lng]，来自 map-config.js（gisConfig.area.key）
  gisMapCenterPoint: GIS_MAP_CENTER_POINT
}

const mutations = {
  RESET_OVERVIEW_RESOURCE_STATE(state) {
    state.overviewRevision++;
    mapResourcePromises.clear();
    state.slamMapList = [];
    state.slamOfRobot = {};
    state.deviceObj = {};
    state.taskData = {};
    state.taskFixedCameraData = {};
    state.alarmsData = {};
    state.robotLocation = {};
    state.robotBaseInfo = {};
    state.robotList = [];
    state.robotAlarmObj = {};
    state.taskPathPoints = {};
    state.dataQuality = {};
    state.defaultGpsDevices = [];
    state.overviewReady = false;
    state.overviewLoadError = false;
  },
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
    applyDerivedRobotTasks(state, [
      ...collectTaskEquipmentIds(prev),
      ...collectTaskEquipmentIds(merged),
      ...robotsHoldingTask(state.robotBaseInfo, value.taskId),
      ...Object.keys(state.robotBaseInfo || {})
    ])
  },
  SET_TASK_FIXED_CAMERAS(state, { taskId, items }) {
    if (taskId === undefined || taskId === null || taskId === '') return
    state.taskFixedCameraData = {
      ...state.taskFixedCameraData,
      [taskId]: Array.isArray(items) ? items : []
    }
  },
  REMOVE_TASK_INFO(state, taskId) {
    if (taskId === undefined || taskId === null) return
    const prev = getTaskById(state.taskData, taskId)
    if (!prev) return
    const nextTasks = { ...state.taskData }
    Object.keys(nextTasks).forEach(key => {
      if (String(key) === String(taskId)) delete nextTasks[key]
    })
    state.taskData = nextTasks
    const nextPaths = { ...state.taskPathPoints }
    Object.keys(nextPaths).forEach(key => {
      if (String(key) === String(taskId)) delete nextPaths[key]
    })
    state.taskPathPoints = nextPaths
    applyDerivedRobotTasks(state, [
      ...collectTaskEquipmentIds(prev),
      ...robotsHoldingTask(state.robotBaseInfo, taskId),
      ...Object.keys(state.robotBaseInfo || {})
    ])
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
  REMOVE_ALARM_DATA(state, alarmId) {
    if (alarmId === undefined || alarmId === null) return
    const next = { ...state.alarmsData }
    ;['high', 'medium', 'low'].forEach(key => {
      const group = next[key] || { items: [] }
      next[key] = {
        ...group,
        items: (group.items || []).filter(item => String(item.alarmId) !== String(alarmId))
      }
    })
    state.alarmsData = next
    const nextRobotAlarms = { ...state.robotAlarmObj }
    Object.keys(nextRobotAlarms).forEach(robotId => {
      if (String(nextRobotAlarms[robotId]?.alarmId) === String(alarmId)) {
        delete nextRobotAlarms[robotId]
      }
    })
    state.robotAlarmObj = nextRobotAlarms
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
  SET_DATA_QUALITY(state, value) {
    state.dataQuality = value || {};
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
  SET_ROBOT_BASE_INFO(state, { robotId, robotInfo, fromRealtime }) {
    const incoming = { ...(robotInfo || {}) }
    if (fromRealtime && (incoming.type === undefined || incoming.type === null || incoming.type === '')) {
      delete incoming.type
    }
    if (fromRealtime && (incoming.typeCode === undefined || incoming.typeCode === null || incoming.typeCode === '')) {
      delete incoming.typeCode
    }
    delete incoming.task
    delete incoming.runningTask
    delete incoming.runningTaskId
    delete incoming.customStatusName
    delete incoming.statusClass
    delete incoming.cameras
    const prev = state.robotBaseInfo?.[robotId] || {}
    const merged = mergeRobotBaseInfo(prev, incoming, !!fromRealtime)
    merged.robotId = incoming.robotId ?? robotId
    delete merged.cameras
    const task = toRobotTaskSummaries(state.taskData, merged.robotId)
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
  SET_OVERVIEW_LOAD_ERROR(state, value) {
    state.overviewLoadError = !!value;
  },
  SET_DEFAULT_MAP_IS_SLAM(state, value) {
    state.defaultMapIsSlam = !!value;
  },
  SET_SLAM_EMPTY_TIP_SHOWN(state, value) {
    state.slamEmptyTipShown = !!value;
  },
}

const actions = {
  resetOverviewResourceState({ commit }) {
    commit('RESET_OVERVIEW_RESOURCE_STATE')
    commit('SET_GLOBAL_MAP_ID', '')
    overviewRefreshPromise = null
  },
  refreshOverviewResources({ state, commit, dispatch }, { failClosed = true } = {}) {
    // 只有 BFF 已明确通知权限集合变化时才先清空；普通重连或健康变化不得中断正在观看的视频。
    if (failClosed) {
      commit('RESET_OVERVIEW_RESOURCE_STATE')
      dispatch('websocketRobot/loadRobots', [], { root: true })
    } else if (overviewRefreshPromise) {
      return overviewRefreshPromise
    }
    const revision = state.overviewRevision
    const pending = loadOverviewWithRetry()
      .then(data => {
        if (revision === state.overviewRevision) return dispatch('applyOverview', data)
      })
      .catch(error => {
        if (revision !== state.overviewRevision) return
        commit('SET_OVERVIEW_LOAD_ERROR', true)
        throw error
      })
      .finally(() => {
        if (overviewRefreshPromise === pending) overviewRefreshPromise = null
      })
    overviewRefreshPromise = pending
    return pending
  },
  async applyOverview({ state, dispatch }, overview) {
    const revision = state.overviewRevision
    const mapId = resolveOverviewMapId(overview, state.globalMapId)
    const [mapResourcesResult, taskRoutesResult] = await requestMapResources(mapId, revision)
    if (revision !== state.overviewRevision) return
    const mapResources = fulfilledValue(mapResourcesResult)
    const taskRoutes = fulfilledValue(taskRoutesResult)
    // 等待资源期间允许用户切图；提交时以最新选择为准，必要时补齐新图资源。
    await dispatch('setAll', mergeOverviewMapResources(overview, mapResources, taskRoutes))
    if (String(state.globalMapId) !== String(mapId)) {
      await dispatch('loadMapResources', state.globalMapId)
    }
  },
  async loadMapResources({ state, commit }, mapId) {
    if (mapId === undefined || mapId === null || mapId === '' || mapId === 'gis') return
    const key = String(mapId)
    if (!state.overviewReady || !state.slamMapList.some(item => String(item.id) === key)) return
    const revision = state.overviewRevision
    const [mapResourcesResult, taskRoutesResult] = await requestMapResources(mapId, revision)
    if (revision !== state.overviewRevision || String(state.globalMapId) !== key
      || !state.slamMapList.some(item => String(item.id) === key)) return
    const mapResources = fulfilledValue(mapResourcesResult)
    const taskRoutes = fulfilledValue(taskRoutesResult)
    const maps = (state.slamMapList || []).map(item => String(item?.id) === key
      ? mergeMapResources(item, mapResources)
      : item)
    commit('SET_SLAM_MAP_LIST', maps)
    ;(taskRoutes?.items || []).forEach(item => {
      const previous = getTaskById(state.taskData, item.taskId) || {}
      commit('SET_TASK_INFO', { ...previous, ...item })
      commit('SET_TASK_PATH_POINTS', { taskId: item.taskId, data: { mapId: item.mapId, pathPoints: item.pathPoints || [] } })
    })
    commit('SET_SLAM_OF_ROBOT', buildSlamOfRobot(maps, state.robotList || [], Object.values(state.taskData || {})))
  },
  /**
   * 完整任务数据只在用户打开任务视频时请求，避免首屏和切图预取回放、设备任务等高成本数据。
   * 详情请求失败时由调用方继续使用首屏摘要，不能影响已经可用的视频入口。
   */
  async loadTaskDetail({ state, commit }, taskId) {
    if (taskId === undefined || taskId === null || taskId === '') return null
    const response = await getPatrolPanoramaTaskDetail(taskId)
    const payload = response?.data && response?.task === undefined ? response.data : response
    const task = payload?.task
    if (!task) return null
    const previous = getTaskById(state.taskData, taskId) || {}
    const merged = { ...previous, ...task }
    commit('SET_TASK_INFO', merged)
    if (merged.mapId !== undefined && merged.mapId !== null) {
      commit('SET_TASK_PATH_POINTS', {
        taskId: merged.taskId ?? taskId,
        data: { mapId: merged.mapId, pathPoints: merged.pathPoints || [] }
      })
    }
    return merged
  },
  /** 实时监控任务卡展开时读取固定摄像头候选源；不预取，也不创建视频会话。 */
  async loadTaskFixedCameras({ state, commit }, taskId) {
    if (taskId === undefined || taskId === null || taskId === '') return []
    const key = String(taskId)
    const existing = state.taskFixedCameraData?.[taskId] || state.taskFixedCameraData?.[key]
    if (Array.isArray(existing)) return existing
    let pending = taskFixedCameraPromises.get(key)
    if (!pending) {
      pending = getPatrolPanoramaTaskFixedCameras(taskId)
        .then(response => {
          const payload = response?.data && response?.items === undefined ? response.data : response
          const items = Array.isArray(payload?.items) ? payload.items : []
          commit('SET_TASK_FIXED_CAMERAS', { taskId, items })
          return items
        })
        .finally(() => taskFixedCameraPromises.delete(key))
      taskFixedCameraPromises.set(key, pending)
    }
    return pending
  },
  setAll({commit, state, dispatch}, data) {
    commit('RESET_OVERVIEW_RESOURCE_STATE')
    const taskQuality = data?.dataQuality?.tasks || { complete: true, degraded: false, reasonCodes: [] }
    commit('SET_DATA_QUALITY', data?.dataQuality || {})
    // 任务查询局部降级时保留已成功加载的数据；页面通过“--”或空态表达未知值，
    // 不弹出瞬时提示干扰正在查看总览或实时视频的用户。
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

    // 调用 websocketRobot 模块的 loadRobots
    dispatch('websocketRobot/loadRobots', devices, { root: true })
    commit('SET_ALARMS_DATA', data?.alarms || {});
    commit('SET_DEVICE_TYPES_STATS', data?.deviceTypeStats || []);
    commit('SET_DEVICE_STATS', data?.deviceStats || {
      fault: '-',
      offline: '-',
      total: '-',
      online: '-'
    });
    commit('SET_PATROL_OVERVIEW', data?.patrolOverview || { durationToday: '-', durationUnit: '小时', mileageToday: '-', mileageUnit: 'KM' });
    commit('SET_TASK_OVERVIEW', taskQuality.degraded
      ? { totalToday: '-', completedRate: '-', completedRateText: '-%', running: '-', pending: '-' }
      : data?.taskOverview || { totalToday: '-', completedRate: '-', completedRateText: '-%', running: '-', pending: '-' });
    commit('SET_ALARM_SUMMARY', data?.alarms?.summary || { totalToday: '-', handled: '-', unhandled: '-', handleRate: '-', handleRateText: '-%' });
    tasks.map((item, index) => {
      commit('SET_TASK_INFO', { ...item, timestamp: new Date().getTime() + tasks.length - index });
      commit('SET_TASK_PATH_POINTS', { taskId: item.taskId, data: { mapId: item.mapId, pathPoints: item.pathPoints || [] } });
    })
    commit('SET_ROBOT_LIST', devices);
    devices.map(item => {
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
    const defaultGpsDevices = overviewGpsDevices({ ...data, devices });
    commit('SET_DEFAULT_GPS_DEVICES', defaultGpsDevices);
    commit('SET_SLAM_MAP_LIST', slamMapList);
    commit('SET_SLAM_OF_ROBOT', buildSlamOfRobot(slamMapList, devices, tasks));
    const mapId = resolveOverviewMapId({ ...data, devices }, state.globalMapId);
    commit('SET_GLOBAL_MAP_ID', mapId);
    commit('SET_DEFAULT_MAP_IS_SLAM', !defaultGpsDevices.length && slamMapList.length > 0);
    commit('SET_OVERVIEW_READY', true);
    commit('SET_OVERVIEW_LOAD_ERROR', false);
  },
  markOverviewLoadFailed({ commit }) {
    commit('SET_OVERVIEW_LOAD_ERROR', true)
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
  syncRobot({ commit, dispatch, rootState }, event) {
    // | `panorama.device.status.changed`   | 设备在线、离线、故障、电量变化                   |
    // | ---------------------------------- | ------------------------------------------------ |
    // | `panorama.device.location.changed` | 地图位置、速度、朝向变化                         |
    // | `panorama.task.changed`            | 任务创建、更新、删除、状态变化、设备任务关联变化 |
    // | `panorama.alarm.changed`           | 告警创建、更新、处置状态变化                     |
    // | `panorama.stats.changed`           |                                                  |
    if (!event) return
    if (event.event === 'panorama.device.status.changed') {
      const robotId= event.data.robotId;
      const robot = rootState.websocketRobot.robots?.length
      ? rootState.websocketRobot.robots.find(item => item.robotId === robotId) || {}
      : {};
      commit('SET_ROBOT_BASE_INFO', {
        robotId,
        robotInfo: { ...robot, ...state.robotBaseInfo[robotId], ...event.data },
        fromRealtime: true
      });
      dispatch('websocketRobot/patchRobotRealtime', event.data, { root: true })
      // commit('SET_ROBOT_BASE_INFO', { robotId: 'test111', robotInfo: { ...state.robotBaseInfo['test111'], status: 'online' } });
    } else if (event.event === 'panorama.device.location.changed') {
      commit('SET_ROBOT_LOCATION', { robotId: event.data.robotId, location: event.data.location });
    } else if (event.event === 'panorama.task.changed') {
      if (event.data?.changeType === 'REMOVE') {
        commit('REMOVE_TASK_INFO', event.data.taskId)
        return
      }
      const task = event.data?.task || (event.data?.taskId != null ? event.data : null)
      if (task) commit('SET_TASK_INFO', task)
    } else if (event.event === 'panorama.alarm.changed') {
      if (event.data?.changeType === 'REMOVE') {
        commit('REMOVE_ALARM_DATA', event.data.alarmId)
        if (event.data.summary) commit('SET_ALARM_SUMMARY', event.data.summary)
        commit('BUMP_ALARM_REVISION')
        return
      }
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
  setRobotBaseInfo({ commit }, { robotId, robotInfo, fromRealtime }) {
    commit('SET_ROBOT_BASE_INFO', { robotId, robotInfo, fromRealtime });
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
  setGlobalMapId({ commit, dispatch }, value) {
    commit('SET_GLOBAL_MAP_ID', value);
    return dispatch('loadMapResources', value)
  },
  setSlamEmptyTipShown({ commit }, value) {
    commit('SET_SLAM_EMPTY_TIP_SHOWN', value);
  },
}

/**
 * 并发登录或下游短暂繁忙时，只在当前页保留一次带抖动的退避重试。
 * 普通刷新通过 overviewRefreshPromise 合并；权限变化必须废弃旧快照并重新查询。
 */
async function loadOverviewWithRetry() {
  try {
    return await getPatrolPanoramaOverview()
  } catch (error) {
    const retryDelayMs = 500 + Math.floor(Math.random() * 300)
    await new Promise(resolve => window.setTimeout(resolve, retryDelayMs))
    return getPatrolPanoramaOverview()
  }
}

function resolveOverviewMapId(overview, preferredId) {
  const maps = Array.isArray(overview?.map) ? overview.map : []
  if (preferredId === 'gis') return 'gis'
  const selected = maps.find(item => String(item.id) === String(preferredId))
  if (selected) return selected.id
  return overviewGpsDevices(overview).length ? 'gis' : (maps[0]?.id ?? 'gis')
}

function overviewGpsDevices(overview) {
  if (Array.isArray(overview?.gpsDevices) && overview.gpsDevices.length) return overview.gpsDevices
  const devices = Array.isArray(overview?.devices) ? overview.devices : []
  return devices.filter(item => {
    const lat = item?.location?.lat
    const lng = item?.location?.lng
    return lat != null && lat !== '' && lng != null && lng !== ''
      && Number.isFinite(Number(lat)) && Number.isFinite(Number(lng))
  })
}

function requestMapResources(mapId, revision) {
  if (mapId === 'gis') return Promise.resolve([])
  const key = `${revision}:${mapId}`
  let pending = mapResourcePromises.get(key)
  if (!pending) {
    pending = Promise.allSettled([
      getPatrolPanoramaMapResources(mapId),
      getPatrolPanoramaMapTaskRoutes(mapId)
    ]).finally(() => mapResourcePromises.delete(key))
    mapResourcePromises.set(key, pending)
  }
  return pending
}

function mergeOverviewMapResources(overview, mapResources, taskRoutes) {
  const mapId = mapResources?.mapId
  const maps = (overview?.map || []).map(item => String(item?.id) === String(mapId)
    ? mergeMapResources(item, mapResources)
    : item)
  const routesByTaskId = new Map((taskRoutes?.items || []).map(item => [String(item.taskId), item]))
  const tasks = (overview?.tasks || []).map(task => {
    const route = routesByTaskId.get(String(task.taskId))
    return route ? { ...task, mapId: route.mapId, pathPoints: route.pathPoints || [] } : task
  })
  return { ...overview, map: maps, tasks }
}

/**
 * 地图渲染资源只补充当前地图的重数据；设备详情由 devices/{deviceId} 按需提供。
 * 每个字段独立合并，保证 task-routes 临时不可用时不影响点位和设备图标，反之亦然。
 */
function mergeMapResources(map, mapResources) {
  if (!mapResources) return map
  const result = { ...map }
  if (Array.isArray(mapResources.points)) result.points = mapResources.points
  if (Array.isArray(mapResources.fixedCamares)) result.fixedCamares = mapResources.fixedCamares
  if (Array.isArray(mapResources.deviceIds)) result.deviceIds = mapResources.deviceIds
  return result
}

function fulfilledValue(result) {
  return result?.status === 'fulfilled' ? result.value : null
}

function buildSlamOfRobot(maps, robots, tasks) {
  const result = {}
  const robotMapIds = {}
  const resourceMapIds = new Set()
  const resourceDeviceMapIds = {}

  maps.forEach(mapInfo => {
    if (mapInfo?.id === undefined || mapInfo?.id === null) return
    const mapId = String(mapInfo.id)
    result[mapId] = { mapInfo, robots: [] }
    // deviceIds 是地图渲染资源返回的归属快照。已加载资源的地图以它为准，
    // 避免同一装备因旧定位或任务残留被绘制到错误地图。
    if (Array.isArray(mapInfo.deviceIds)) {
      resourceMapIds.add(mapId)
      mapInfo.deviceIds.forEach(robotId => {
        if (robotId !== undefined && robotId !== null && robotId !== '') {
          resourceDeviceMapIds[String(robotId)] = mapInfo.id
        }
      })
    }
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
    const explicitMapId = resourceDeviceMapIds[String(robot?.robotId)]
    // 已加载渲染资源的地图不得再用旧定位补图标；未加载资源的地图维持原有兜底，
    // 避免首次总览尚未按需读取时清空其他地图的既有展示。
    if (directMapId !== undefined && directMapId !== null
      && resourceMapIds.has(String(directMapId)) && explicitMapId === undefined) return
    const mapId = explicitMapId ?? directMapId ?? robotMapIds[String(robot?.robotId)]
    if (mapId === undefined || mapId === null) return
    const key = String(mapId)
    if (!result[key]) result[key] = { mapInfo: null, robots: [] }
    if (!result[key].robots.some(item => item.robotId === robot.robotId)) {
      result[key].robots.push(robot)
    }
  })

  return result
}

function toRobotTaskSummary(task) {
  return {
    taskId: task.taskId,
    workflowInstanceId: task.workflowInstanceId,
    name: task.name,
    status: task.status,
    statusName: task.statusName,
    timeRange: task.timeRange,
    mapId: task.mapId
  }
}

function toRobotTaskSummaries(taskData, robotId) {
  return listTasksForRobot(taskData, robotId, {
    activeOnly: true,
    isActive: isDeviceAssociatedTaskStatus
  }).map(toRobotTaskSummary)
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
    const task = toRobotTaskSummaries(state.taskData, robot.robotId ?? key)
    const withTask = { ...robot, task }
    next[key] = { ...withTask, ...getRobotStatus(withTask, state.taskData) }
  })
  state.robotBaseInfo = next
}

function getRobotStatus(robot, taskData) {
  const { status, robotId } = robot || {}
  const taskList = listTasksForRobot(taskData, robotId, {
    activeOnly: true,
    isActive: isDeviceAssociatedTaskStatus
  }).map(item => getTaskById(taskData, item.taskId) || item)
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
  getRobotStatus
}
