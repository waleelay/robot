import request from '@/utils/request'
//获取地图列表
export function getMapIdOptions() {
  return request({
    url: '/Entity/searchMapInfo',
    method: 'get'
  })
}
//获取点位列表
export function listPoint(queryParams) {
  return request({
    url: '/Point/searchPointInfo',
    method: 'post',
    data: queryParams
  })
}

//删除
export function delPoint(PointIds) {
  return request({
    url: '/Point/deletePointInfo/' + PointIds,
    method: 'delete'
  })
}
//修改
export function updataPoint(data) {
  return request({
    url: '/Point/updatePointInfo',
    method: 'post',
    data: data
  })
}

//新建
export function addPoint(data) {
  return request({
    url: '/Point/addPoint',
    method:'post',
    data:data
  })
}
//根据地图id查询dogid
export function searDogId(MapID) {
  return request({
    url:'/Entity/searchDogIdByMapID',
    method: 'get',
    params: {
      MapID
    }
  })
}
//批量导入
export function importPoint(data) {
  return request({
    url: '/Point/importPoint',
    method:'post',
    data:data
  })
}
