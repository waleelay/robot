import request from '@/utils/request'
//获取机器人列表
export function getDogList() {
  return request({
    url: '/Entity/searchAll',
    method: 'get',
  })
}
//获取地图列表
export function getMapList(data) {
  return request({
    url: '/Entity/searchMapInfoBy',
    method: 'post',
    data:data,
  })
}
//新增地图
export function addMap(data) {
  return request({
    url: '/Entity/addMapInfo',
    method:'post',
    data:data
  })
}
//删除地图
export function delMap(MapIds) {
  return request({
    url:'/Entity/deleteMapInfo/'+MapIds,
    method:'delete'
  })
}

//修改
export function updateMap(data) {
  return request({
    url: '/Entity/updateMapInfo',
    method: 'post',
    data: data
  })
}
//根据MapID查询信息
export function getMapInfoById(MapID) {
  return request({
    url: '/Entity/',
    method: 'post',
    data: {
      MapID: MapID
    }
  })
}


