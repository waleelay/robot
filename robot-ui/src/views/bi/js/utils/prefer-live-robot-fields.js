/** overview/快照与实时装备合并：仅用 live 覆盖运行态字段，不整体替换快照 */

export function overlayLiveRobotRuntimeFields(snapshot, live) {
  if (!snapshot) return live || null
  if (!live) return snapshot
  return Object.assign({}, snapshot, {
    status: live.status,
    cameras: (live.cameras && live.cameras.length) ? live.cameras : snapshot.cameras,
    battery: live.battery !== undefined && live.battery !== null ? live.battery : snapshot.battery,
    controlMode: live.controlMode || snapshot.controlMode,
    controlModeName: live.controlModeName || snapshot.controlModeName,
    devices: live.devices || snapshot.devices,
    fault: live.fault !== undefined ? live.fault : snapshot.fault,
    speed: live.speed !== undefined && live.speed !== null ? live.speed : snapshot.speed
  })
}
