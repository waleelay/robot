export const dogBasicInfo = {
  // 机器人运动状态
  motionState: 0,
  // 机器人在地图坐标系下的坐标
  posX: 0,
  posY: 0,
  posZ: 0,
  // 机器人在地图坐标系下的姿态角度
  Roll: 0,
  Pitch: 0,
  Yaw: 0,
  // 机器人的前向移动速度 m/s
  speed: 1,
  // 当次开机后的里程数 km
  curOdom: 1,
  // 累计里程数 km
  sumOdom: 1,
  // 地图图像像素在实际地图中的物理长度 m
  res: 0.1,
  // 地图图像左下角在地图坐标系下的 X 轴坐标 m
  x0: 10.0,
  // 地图图像左下角在地图坐标系下的 Y 轴坐标
  y0: 10.0,
  // 地图图像高度包含的像素数
  h: 1000,
  // 机器人电量
  electricity: 50,
  // 机器人定位状态
  location: 0,
  // 机器人步态
  gaitState: 0,
  // 机器人电机状态
  motorState: 0,
  // 机器人自主充电状态
  chargeState: 0,
  // 机器人控制模式
  controlMode: 0,
  // 机器人是否趴在充电桩上
  onDockState: 0,
}
  

// 机器人运动状态
export const motionStateObj = {
  0: '趴下',
  1: '正在起立',
  2: '初始站立',
  3: '力控站立',
  4: '踏步',
  5: '正在趴下',
  6: '软急停',
  7: '摔倒',
}
// 机器人定位状态
export const locationObj = {
  0: '定位正常',
  1: '定位丢失',
}
// 机器人步态
export const gaitStateObj = {
  0: '行走步态',
  1: '复杂路面越障步态',
  2: '斜坡/防滑步态',
  6: '感知楼梯步态',
  7: '累积帧楼梯步态',
  8: '累积帧45°楼梯步态'
}
// 机器人电机状态
export const motorStateObj = {
  0: '电机正常',
  1: '电机过温'
}
// 机器人自主充电状态
export const chargeStateObj = {
  0: '未在充电',
  1: '正在充电'
}
// 机器人控制模式
export const controlModeObj = {
  0: '手动模式',
  1: '自动模式'
}
// 机器人是否趴在充电桩上
export const onDockStateObj = {
  0: '不在桩上',
  1: '趴在桩上'
}
