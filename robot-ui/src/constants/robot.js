export const ROBOT_TYPE_INFO = {
  'ROBOT_DOG': { width: 38, height: 28, bigImg: 'dog.png', img: 'robot_dog', icon: 'robot-dog' },
  机器狗: { width: 38, height: 28, bigImg: 'dog.png', img: 'robot_dog', icon: 'robot-dog' },
  'HUMANOID_ROBOT': { width: 24, height: 39, img: 'robot1', icon: 'robot' },
  人形机器人: { width: 24, height: 39, img: 'robot1', icon: 'robot' },
  'WHEELED_ROBOT': { width: 30, height: 28, bigImg: 'car.png', img: 'robot_car', icon: 'robot-car' },
  轮式机器人: { width: 30, height: 28, bigImg: 'car.png', img: 'robot_car', icon: 'robot-car' },
  'FIXED_CAMERA': { width: 44, height: 62, img: 'robot-camera-normal', icon: 'robot-camera' },
  固定摄像头: { width: 44, height: 62, img: 'robot-camera-normal', icon: 'robot-camera' },
  // 备用
  四足机器狗: { width: 38, height: 28, bigImg: 'dog.png', img: 'robot_dog', icon: 'robot-dog' },
  四足机器人: { width: 38, height: 28, bigImg: 'dog.png', img: 'robot_dog', icon: 'robot-dog' },
  default: { width: 24, height: 39, img: 'robot1', icon: 'robot' },
  UAV: { width: 43, height: 20, img: 'robot_uav', icon: 'robot-uav' },
}

export const ROBOT_DOG_TYPE_MARKERS = ['ROBOT_DOG', '机器狗', '四足机器狗', '四足机器人']
export const FIXED_CAMERA_TYPE_MARKERS = ['FIXED_CAMERA', '固定摄像头']

export function isRobotDog(robot = {}) {
  const markers = [robot.typeCode, robot.type, robot.equipmentType]
  return markers.some(value => ROBOT_DOG_TYPE_MARKERS.includes(value))
}

export function isFixedCamera(robot = {}) {
  const markers = [robot.typeCode, robot.type, robot.equipmentType, robot.sourceType]
  return markers.some(value => FIXED_CAMERA_TYPE_MARKERS.includes(value))
}