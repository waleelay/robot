import request from '@/utils/request'

// 登录方法
export function login(username, password, code, uuid) {
  const data = {
    username,
    password,
    code,
    uuid
  }
  return request({
    url: '/login',
    headers: {
      isToken: false,
      repeatSubmit: false
    },
    method: 'post',
    // data: data
  })
}

// 注册方法
export function register(data) {
  return request({
    url: '/register',
    headers: {
      isToken: false
    },
    method: 'post',
    data: data
  })
}

// 获取用户详细信息
export function getInfo() {
  return request({
    url: '/getInfo',
    method: 'get'
  })
}

// 退出方法
export function logout() {
  return request({
    url: '/logout',
    method: 'post'
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}
//以下都是测试狗的接口用的
//获取点的列表
export function getPointList() {
  return request({
    url: '/Send/searchPointId',
    method: 'get',
    params: {
      MapID: 0,
    }
  })
}
//获取狗的基础状态
export function getDogState() {
  return request({
    url: '/Send/sendDogStateRequest',
    method: 'get'
  })
}


//取消导航任务
export function cancelTask() {
  return request({
    url: '/Send/cancelNavigationTask',
    method: 'get'
  })
}
//查询导航任务状态
export function searchTask() {
  return request({
    url: '/Send/searchNavigationState',
    method: 'get'
  })
}
//运动控制
export function motionControl(motion, value, endpoint) {
  return request({
    url: '/Send/SportsControl',
    method: 'get',
    params: {
      commond: motion,// 指令
      itemsValue: value,
      endpoint
    }
  })
}
//获取电池信息
export function getPower() {
  return request({
    url: '/Send/searchBattery',
    method: 'get'
  })
}
// 打点的接口
export function addPoint(PosX,PosY,PosZ,AngleYaw) {
  // const data = {
  //   Value:1,
  //   MapID:0,
  //   PosX:PosX,
  //   PosY:PosY,
  //   PosZ:PosZ,
  //   AngleYaw:AngleYaw,
  //   PointInfo:0,
  //   Gait:0,
  //   Speed:0,
  //   Manner:0,
  //   ObsMode:0,
  //   NavMode:0,
  //   Terrain:0,
  //   Posture:0,
  // }
return request({
  url: '/Send/addPoint',
  method: 'get',
  // data:data
  params: {
    PosX:PosX,
    PosY:PosY,
    PosZ:PosZ,
    AngleYaw:AngleYaw,
  }
})
}

//下发导航任务
export function issueTaskbyId(id) {
  return request({
    url: '/Send/sendNavigationTaskById',
    method: 'get',
    params: {
      pointId:id
    }
  })
}


