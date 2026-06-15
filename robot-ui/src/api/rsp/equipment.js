import request from '@/utils/request'

// 查询无人装备信息列表
export function listMotion(query) {
  return request({
    url: '/rsp/motion/list',
    method: 'get',
    params: query
  })
}
export function listAllMotion(query) {
  return request({
    url: '/rsp/motion/listAll',
    method: 'get',
    params: query
  })
}

// 查询无人装备信息详细
export function getMotion(id) {
  return request({
    url: '/rsp/motion/' + id,
    method: 'get'
  })
}

// 新增无人装备信息
export function addMotion(data) {
  return request({
    url: '/rsp/motion',
    method: 'post',
    data: data
  })
}

// 修改无人装备信息
export function updateMotion(data) {
  return request({
    url: '/rsp/motion',
    method: 'put',
    data: data
  })
}
// 发布/撤销装备
export function changeMotionStatus(data) {
  return request({
    url: `/rsp/motion/changeState/${data.ids}?state=${data.state}`,
    method: 'put'
  })
}

// 删除无人装备信息
export function delMotion(id) {
  return request({
    url: '/rsp/motion/' + id,
    method: 'delete'
  })
}
// 上装设备根据类型查型号
export function getMountedModelByType(type) {
  return request({
    url: '/system/dict/data/type/' + type,
    method: 'get'
  })
}
// 装备根据厂商查型号
export function getEquipmentModelByType(type) {
  return request({
    url: '/system/dict/data/type/' + type,
    method: 'get'
  })
}