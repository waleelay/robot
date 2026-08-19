/** 是否本体相机 */
export function isBodyCamera(camera) {
  if (!camera) return false
  const type = String(camera.groupType || '').trim().toLowerCase()
  const name = String(camera.groupTypeName || '').trim()
  return type === 'body' || name === '本体'
}

/** 装备相机列表：优先 robot.cameras，否则从全局索引按 robotId 取 */
export function listRobotCameras(robot, camerasIndex) {
  const fromRobot = Array.isArray(robot && robot.cameras) ? robot.cameras.filter(Boolean) : []
  if (fromRobot.length) return fromRobot
  const robotId = robot && robot.robotId
  if (robotId === undefined || robotId === null || robotId === '') return []
  return Object.values(camerasIndex || {}).filter(item => String(item && item.robotId) === String(robotId))
}

/** 默认视频数据源：优先本体相机，没有则取装备第一个相机 */
export function pickDefaultCamera(robot, camerasIndex) {
  const cameras = listRobotCameras(robot, camerasIndex)
  if (!cameras.length) return null
  return cameras.find(isBodyCamera) || cameras[0]
}
