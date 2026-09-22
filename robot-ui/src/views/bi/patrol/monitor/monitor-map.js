function normalizedId(value) {
  if (value === undefined || value === null || String(value).trim() === '') return null
  return String(value).trim()
}

function isFixedMapDevice(robot) {
  return String(robot?.sourceType || robot?.typeCode || robot?.equipmentType || '').toUpperCase() === 'FIXED_CAMERA'
}

function findMapById(maps, value) {
  const id = normalizedId(value)
  if (id == null) return null
  return maps.find(item => normalizedId(item?.id) === id) || null
}

function findMapByEdgeId(maps, value) {
  const edgeMapId = normalizedId(value)
  if (edgeMapId == null) return null
  const matches = maps.filter(item => normalizedId(item?.edgeMapId) === edgeMapId)
  return matches.length === 1 ? matches[0] : null
}

/**
 * 将中心地图 id 或边缘地图 id 统一转换为前端地图列表中的中心地图 id。
 * 边缘地图 id 只有唯一匹配时才采用，避免同名边缘地图时展示错误底图。
 */
export function resolveSlamMapReference(maps, value, allowEdgeMapId = true) {
  const list = Array.isArray(maps) ? maps : []
  const matched = findMapById(list, value)
    || (allowEdgeMapId ? findMapByEdgeId(list, value) : null)
  return matched?.id ?? null
}

/**
 * 实时监控设备地图解析顺序：设备显式地图、有效实时定位、运行任务、地图设备归属。
 * 任务路线只作为兜底，路线查询降级不能再阻断设备底图展示。
 */
export function resolveMonitorRobotSlamMapId({
  robotId,
  robotBaseInfo,
  robotLocation,
  slamMapList,
  slamOfRobot,
  taskRoutesByMap
}) {
  const targetId = normalizedId(robotId)
  if (targetId == null) return null

  const maps = Array.isArray(slamMapList) ? slamMapList : []
  const robot = robotBaseInfo?.[robotId] || robotBaseInfo?.[targetId] || {}
  const explicitMapId = resolveSlamMapReference(maps, robot.mapId)
  if (explicitMapId != null) return explicitMapId

  const location = robotLocation?.[targetId] ?? robot.location
  if (location) {
    const locationMapId = isFixedMapDevice(robot)
      ? resolveSlamMapReference(maps, location.mapId, false)
      : (location.localized === true ? resolveSlamMapReference(maps, location.mapId) : null)
    if (locationMapId != null) return locationMapId
  }

  const taskId = robot.runningTaskId
  if (taskId !== undefined && taskId !== null && taskId !== '') {
    // 路线只能证明任务与地图的关系；Management deviceId 与 robotId 无权威映射。
    // 仅当任务唯一归属一张地图时回退，跨地图任务继续交给地图装备归属判定。
    const routeMaps = maps.filter(map => taskRoutesByMap?.[String(map?.id)]?.[String(taskId)])
    if (routeMaps.length === 1 && routeMaps[0]?.id != null) return routeMaps[0].id
  }

  for (const [mapId, group] of Object.entries(slamOfRobot || {})) {
    if (!group?.robots?.some(item => normalizedId(item?.robotId) === targetId)) continue
    const resolvedGroupMapId = resolveSlamMapReference(maps, mapId, false)
    if (resolvedGroupMapId != null) return resolvedGroupMapId
  }
  return null
}
