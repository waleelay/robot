import request from '@/utils/request'

// 查询无人装备信息列表
export function listMotion(query) {
  return request({
    url: '/rsp/motion/list',
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

// 删除无人装备信息
export function delMotion(id) {
  return request({
    url: '/rsp/motion/' + id,
    method: 'delete'
  })
}
