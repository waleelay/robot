/**
 * 从 public/js/map-config.js 的全局 gisConfig 读取当前区域配置
 * 切换区域：修改 gisConfig.area.key（如 nanchong / qihang）
 */
export function getGisAreaConfig() {
  const config = typeof window !== 'undefined' ? window.gisConfig : null
  const key = config?.area?.key
  if (key && config?.[key]) return config[key]
  return {}
}

// 区域多边形点 [lat, lng][]
export const POLYGON_POINTS = getGisAreaConfig().polygonPoints || []

// 路径点 { lat, lng, desc? }[]
export const WAY_POINTS = getGisAreaConfig().wayPoints || []

// GIS 中心点 [lat, lng]
export const GIS_MAP_CENTER_POINT = getGisAreaConfig().center || []

/**
 * GIS 缩放范围：map-config 中 zoom: [min, max]
 * - minZoom：Leaflet 下限，指挥中心默认显示级别
 * - maxZoom：Leaflet 上限，巡逻巡查（全景/监控）默认显示级别
 */
export function getGisZoomRange() {
  const zoom = getGisAreaConfig().zoom
  const fallback = { minZoom: 12, maxZoom: 18 }
  if (!Array.isArray(zoom) || zoom.length < 2) return fallback
  const a = Number(zoom[0])
  const b = Number(zoom[1])
  if (!Number.isFinite(a) || !Number.isFinite(b)) return fallback
  return {
    minZoom: Math.min(a, b),
    maxZoom: Math.max(a, b)
  }
}

export const GIS_ZOOM_RANGE = getGisZoomRange()

/** 最大缩放层级时的地图旋转角（度），来自 map-config.rotate */
export function getGisMapRotate() {
  const rotate = Number(getGisAreaConfig().rotate)
  return Number.isFinite(rotate) ? rotate : 0
}

export const GIS_MAP_ROTATE = getGisMapRotate()

// 当前区域 key（用于 tdt 瓦片路径等）
export const GIS_AREA_KEY = getGisAreaConfig().key
  || (typeof window !== 'undefined' ? window.gisConfig?.area?.key : '')
  || ''

/** 天地图离线瓦片地址：/tdt/{areaKey}/{z}/{x}/{y}.png */
export function getGisTileUrl() {
  const base = process.env.VUE_APP_BASE_ORIGIN
    || (typeof location !== 'undefined' ? location.origin : '')
    || ''
  return `${base}/tdt/${GIS_AREA_KEY}/{z}/{x}/{y}.png`
}

// slam地图点位，暂时固定，后续需对接接口
export const SLAM_POINTS = {
  // 红塔
  '2081299659660746754': [
    {
        "id": "2081301134763909122",
        "mapId": "2081299659660746754",
        "pointCode": "2",
        "pointName": "点位2",
        "pointType": "NORMAL",
        "coordinateX": 3.801476,
        "coordinateY": -4.488939,
        "coordinateZ": 0.191687,
        "remark": null
    },
    {
        "id": "2081301441677910018",
        "mapId": "2081299659660746754",
        "pointCode": "3",
        "pointName": "点位3",
        "pointType": "NORMAL",
        "coordinateX": -2.093077,
        "coordinateY": -16.976892,
        "coordinateZ": -0.143329,
        "remark": null
    },
    {
        "id": "2081301658338877441",
        "mapId": "2081299659660746754",
        "pointCode": "4",
        "pointName": "点位4",
        "pointType": "NORMAL",
        "coordinateX": -41.961193,
        "coordinateY": -49.230068,
        "coordinateZ": -2.061812,
        "remark": null
    },
    {
        "id": "2081301825846796290",
        "mapId": "2081299659660746754",
        "pointCode": "5",
        "pointName": "点位5",
        "pointType": "NORMAL",
        "coordinateX": -57.158157,
        "coordinateY": -33.540661,
        "coordinateZ": -2.945984,
        "remark": null
    },
    {
        "id": "2081302263568556034",
        "mapId": "2081299659660746754",
        "pointCode": "1",
        "pointName": "点位1",
        "pointType": "NORMAL",
        "coordinateX": -0.066766,
        "coordinateY": 0.027624,
        "coordinateZ": 0.106916,
        "remark": null
    }
  ],
  // 联通
  '2077775285125144578': [
    {
        "id": "2077776201211469825",
        "mapId": "2077775285125144578",
        "pointCode": "point-1",
        "pointName": "point-1",
        "pointType": "NORMAL",
        "coordinateX": -2.874684,
        "coordinateY": -0.391763,
        "coordinateZ": null,
        "remark": null
    },
    {
        "id": "2077776298003423234",
        "mapId": "2077775285125144578",
        "pointCode": "point-2",
        "pointName": "point-2",
        "pointType": "NORMAL",
        "coordinateX": -9.842506,
        "coordinateY": -0.33602,
        "coordinateZ": null,
        "remark": null
    },
    {
        "id": "2077776460083912705",
        "mapId": "2077775285125144578",
        "pointCode": "point-3",
        "pointName": "point-3",
        "pointType": "NORMAL",
        "coordinateX": -10.670098,
        "coordinateY": 5.522158,
        "coordinateZ": null,
        "remark": null
    },
    {
        "id": "2077776541868646401",
        "mapId": "2077775285125144578",
        "pointCode": "point-4",
        "pointName": "point-4",
        "pointType": "NORMAL",
        "coordinateX": -10.91751,
        "coordinateY": -0.278282,
        "coordinateZ": null,
        "remark": null
    },
    {
        "id": "2077776632289452034",
        "mapId": "2077775285125144578",
        "pointCode": "point-5",
        "pointName": "point-5",
        "pointType": "NORMAL",
        "coordinateX": -3.165264,
        "coordinateY": 0.436464,
        "coordinateZ": null,
        "remark": null
    }
  ]
}

/** 联通展厅 SLAM 地图 id */
export const LIANTONG_SLAM_MAP_ID = '2077775285125144578'

/**
 * 联通展厅装备/任务路径模拟开关（联调用）
 * 机器狗：point-1，执行中任务路径 1→2→3
 * 固定摄像头：point-2，执行中任务路径 1→4→5
 */
export const ENABLE_LIANTONG_SLAM_MOCK = false

/** 生成联通展厅 SLAM 模拟装备与任务（路径点 1/2/3） */
export function getLiantongSlamMock() {
  const mapId = LIANTONG_SLAM_MAP_ID
  const pathPoints = (SLAM_POINTS[mapId] || []).slice(0, 3)
  const robotId = 'mock-liantong-slam-robot'
  const taskId = 'mock-liantong-task-path-123'
  const coordinateX = -2.874684
  const coordinateY = -0.391763
  const device = {
    robotId,
    name: '联通展厅机器狗',
    type: '四足机器狗',
    typeCode: 'ROBOT_DOG',
    model: 'MOCK-DOG',
    status: 'online',
    battery: 88,
    speed: 0.4,
    controlMode: '导航模式',
    alarmLevel: 'none',
    mountedDeviceCount: 0,
    mapId,
    location: {
      mapId,
      x: coordinateX,
      y: coordinateY,
      coordinateX,
      coordinateY,
      yaw: 0
    },
    task: [{ taskId, mapId }]
  }
  const task = {
    taskId,
    mapId,
    name: '联通展厅路径点1-2-3',
    status: 'running',
    statusName: '执行中',
    timeRange: '全天',
    pathPoints,
    equipmentList: [{
      robotId,
      name: device.name,
      type: device.type,
      typeCode: device.typeCode,
      status: 'online'
    }]
  }
  return { mapId, robotId, taskId, device, task, pathPoints }
}

/** 生成联通展厅固定摄像头模拟装备（挂在同一张 SLAM 地图 point-2，执行中任务路径 1→4→5） */
export function getLiantongFixedCameraMock() {
  const mapId = LIANTONG_SLAM_MAP_ID
  const robotId = 'mock-liantong-fixed-camera'
  const taskId = 'mock-liantong-camera-task-path-145'
  const allPoints = SLAM_POINTS[mapId] || []
  // 点位顺序：1、4、5（对应数组下标 0、3、4）
  const pathPoints = [allPoints[0], allPoints[3], allPoints[4]].filter(Boolean)
  const coordinateX = -9.842506
  const coordinateY = -0.33602
  const device = {
    robotId,
    name: '联通展厅固定摄像头',
    type: '固定摄像头',
    typeCode: 'FIXED_CAMERA',
    sourceType: 'FIXED_CAMERA',
    model: 'MOCK-FIXED-CAMERA',
    status: 'online',
    battery: null,
    speed: 0,
    controlMode: 'IDLE',
    alarmLevel: 'none',
    mountedDeviceCount: 1,
    mapId,
    location: {
      mapId,
      x: coordinateX,
      y: coordinateY,
      coordinateX,
      coordinateY,
      yaw: 0,
      lat: 30.74812517003875,
      lng: 106.03462176616857
    },
    cameras: [
      {
        cameraId: 'fixed-camera-01',
        deviceId: 'fixed-camera-01',
        name: '联通展厅固定监控',
        groupType: 'body',
        quality: 'sub',
        sourceType: 'FIXED_CAMERA'
      }
    ],
    task: [{ taskId, mapId }]
  }
  const task = {
    taskId,
    mapId,
    name: '联通展厅固定摄像头巡检',
    status: 'running',
    statusName: '执行中',
    timeRange: '全天',
    pathPoints,
    equipmentList: [{
      robotId,
      name: device.name,
      type: device.type,
      typeCode: device.typeCode,
      status: 'online'
    }]
  }
  return { mapId, robotId, taskId, device, task, pathPoints }
}
