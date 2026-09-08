// 参考导航地图的绿色路线语义，保留同图多设备所需的有限色阶差异。
export const TRAJECTORY_COLORS = ['#00C853', '#00B578', '#21A675', '#52B788', '#2E8B57', '#7CB342', '#009688']
export const STOPPED_TRAJECTORY_COLOR = '#7F948B'

function colorStartIndex(robotId) {
  const value = String(robotId || '')
  let hash = 0
  for (let i = 0; i < value.length; i++) hash = ((hash << 5) - hash + value.charCodeAt(i)) | 0
  return (hash >>> 0) % TRAJECTORY_COLORS.length
}

/**
 * 同图前 N 条轨迹优先使用不同颜色；输入集合不变时，刷新后分配结果保持一致。
 */
export function buildTrajectoryVisuals(robotIds, selectedRobotIds = []) {
  const ids = [...new Set((robotIds || []).map(String))].sort()
  const selected = new Set((selectedRobotIds || []).map(String))
  const hasFocusedTrajectory = ids.some(robotId => selected.has(robotId))
  const usedColorIndexes = new Set()

  return ids.reduce((result, robotId) => {
    let colorIndex = colorStartIndex(robotId)
    if (usedColorIndexes.size < TRAJECTORY_COLORS.length) {
      while (usedColorIndexes.has(colorIndex)) {
        colorIndex = (colorIndex + 1) % TRAJECTORY_COLORS.length
      }
      usedColorIndexes.add(colorIndex)
    }
    const focused = hasFocusedTrajectory && selected.has(robotId)
    result[robotId] = {
      color: TRAJECTORY_COLORS[colorIndex],
      focused,
      muted: hasFocusedTrajectory && !focused
    }
    return result
  }, {})
}

/** 历史轨迹在底层，当前选中轨迹最后绘制在最上层。 */
export function compareTrajectoryLayers(a, b) {
  if (a.stopped !== b.stopped) return a.stopped ? -1 : 1
  if (a.focused !== b.focused) return a.focused ? 1 : -1
  return String(a.robotId).localeCompare(String(b.robotId))
}
