import request from '@/utils/request'

// 查询地图信息列表
export function listMapInfo(query) {
  return request({
    url: '/rsp/map-info/list',
    method: 'get',
    params: query
  })
}

// 查询地图信息详细
export function getMapInfo(id) {
  return request({
    url: '/rsp/map-info/' + id,
    method: 'get'
  })
}

// 新增地图信息
export function addMapInfo(data) {
  return request({
    url: '/rsp/map-info',
    method: 'post',
    data: data
  })
}

// 修改地图信息
export function updateMapInfo(data) {
  return request({
    url: '/rsp/map-info',
    method: 'put',
    data: data
  })
}

// 发布/撤销地图信息
export function changeMapStatus(data) {
  return request({
    url: `/rsp/map-info/changeStatus/${data.ids}?status=${data.status}`,
    method: 'put'
  })
}

// 删除地图信息
export function delMapInfo(id) {
  return request({
    url: '/rsp/map-info/' + id,
    method: 'delete'
  })
}

//批量导入
export function importPoint(data) {
  return request({
    url: '/rsp/map-info/importPoint',
    method:'post',
    data:data
  })
}
