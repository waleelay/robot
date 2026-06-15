import request from '@/utils/request'
const pre = '/dashboard2'

// 首页设备概览
export function getDeviceOverview() {
  return request({
    url: pre + '/Entity/searchDeviceOverview',
    method: 'post'
  })
}
// 场景注册
export function getSceneRegister(data) {
  return request({
    url: pre + '/Entity/searchSceneRegister',
    method: 'post',
    data
  })
}
// 首页告警
export function getWarningInfo(data) {
  return request({
    url: pre + '/Error/searchErrorStatistic',
    method: 'post',
    data
  })
}
// 调度任务执行情况
export function getDdrwzxqkInfo(data) {
  return request({
    url: pre + '/monitor/job/selectTaskStatistic',
    method: 'post',
    data
  })
}
// 巡逻巡查 调度任务执行情况
export function getXlxcDdrwzxqkInfo(data) {
  return request({
    url: pre + '/Send/getRecordByTaskType',
    method: 'post',
    data
  })
}
// 巡逻巡查 告警
export function getXlxcWarningInfo(data) {
  return request({
    url: pre + '/Error/selectErrorInfoByPatrol',
    method: 'post',
    data
  })
}
export function executeDdrw(data) {
  return request({
    url: '/monitor/job/run',
    method: 'post',
    data
  })
}


// 巡逻巡查概况
export function getXlxcOverview(data) {
  return request({
    url: pre + '/Entity/getPatrolOverview',
    method: 'post',
    data
  })
}
// 巡逻巡查调度任务
export function getXlxcDdrw(data) {
  return request({
    url: '',
    method: 'post',
    data
  })
}


// 麦克风控制，静音/调节音量
export function controlVoice(command, client_ip) {
  return request({
    url: '/control-vol/clientAudio/volume',
    method: 'get',
    params: {
      action: command,
      client_ip
    }
  })
}
// 麦克风开启/关闭
export function controlVoiceStatus(action, client_ip) {
  return request({
    url: '/control-vol/clientAudio/micControl',
    method: 'get',
    params: {
      action,
      client_ip
    }
  })
}
// 退出充电桩、一键返航
export function controlDevice(command, deviceId) {
  return request({
    url: '/charger/control',
    method: 'get',
    params: {
      command, deviceId
    }
  })
}
// 让机器狗发语音 text audioNum: 2
export function playVoice(params) {
  return request({
    url: '/audio/serverAudio/sendText',
    // url: 'http://192.168.124.204:10000/serverAudio/sendText',
    method: 'get',
    params: params
  })
}

// 车辆监管
// 车辆概况
export function getCarOverview(data) {
  return request({
    url: '/rsp/vehicle/statistic',
    method: 'get',
    params: data
  })
}
// 修改车辆信息
export function updateCar(data) {
  return request({
    url: '/rsp/vehicle',
    method: 'put',
    data
  })
}
// 新增车辆信息
export function addCar(data) {
  return request({
    url: '/rsp/vehicle',
    method: 'post',
    data
  })
}
// 车辆详细信息
export function getCarInfoById(id) {
  return request({
    url: '/rsp/vehicle/' + id,
    method: 'get'
  })
}
// 删除车辆详细信息
export function deleteCar(ids) {
  return request({
    url: '/rsp/vehicle/' + ids,
    method: 'delete'
  })
}
// 导出车辆信息列表
export function exportCarInfo(data) {
  return request({
    url: '/rsp/vehicle/export',
    method: 'post'
  })
}
// 查询车辆信息列表
export function getCarList(params) {
  return request({
    url: '/rsp/vehicle/list',
    method: 'get',
    params
  })
}
// 执行任务
export function executeTask(data) {
  return request({
    url: '/rsp/vehicle/task/batchStart',
    method: 'post',
    data
  })
}
// 批量执行-立即执行按钮
export function executeTaskByStep(data) {
  return request({
    url: '/rsp/vehicle/task/resume',
    method: 'patch',
    data
  })
}
// 开始身份核验
export function verifyVehicle(params) {
  return request({
    url: '/rsp/vehicle/detect',
    method: 'get',
    params
  })
}
// 盒子地址
export function getBoxHostList() {
  return request({
    url: '/system/dict/data/type/box_host',
    method: 'get'
  })
}