/** 从全局 taskData 按 taskId 取值（兼容 number / string 键） */
export function getTaskById(taskData, taskId) {
  if (taskId === undefined || taskId === null || taskId === '') return null
  return taskData?.[taskId] || taskData?.[String(taskId)] || null
}

/** 任务关联的装备 ID：equipmentList / devices / robots，以及载荷上的 robotId */
export function collectTaskEquipmentIds(task) {
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

export function isTaskAssignedToRobot(task, robotId) {
  if (!task || robotId === undefined || robotId === null || robotId === '') return false
  return collectTaskEquipmentIds(task).includes(String(robotId))
}

/** 全局 taskData 中属于该装备的任务（完整对象，随 taskData 更新） */
export function listTasksForRobot(taskData, robotId, { activeOnly = false, isActive } = {}) {
  if (robotId === undefined || robotId === null || robotId === '') return []
  const result = []
  const seen = new Set()
  Object.values(taskData || {}).forEach(task => {
    if (!task || !isTaskAssignedToRobot(task, robotId)) return
    if (activeOnly && typeof isActive === 'function' && !isActive(task.status)) return
    const key = String(task.taskId ?? task.id ?? '')
    if (key && seen.has(key)) return
    if (key) seen.add(key)
    result.push(task)
  })
  return result
}
