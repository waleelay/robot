import request from '@/utils/request'

//获取路径列表(MapID,LineName)
export function getRouteList(data) {
  return request({
    url: '/Point/searchAllLinePoint',
    method: 'post',
    data: data
  })
}

//添加路径
export function addRoute(data) {
  return request({
    url: '/Point/SetPath',
    method: 'post',
    data: data
  })
}

//修改
export function updateRoute(data) {
  return request({
    url: '/Point/updateLine',
    method: 'post',
    data: data
  })
}

//删除路径
export function delRoute(routeIds) {
  return request({
    url:'/Point/deleteLine/'+routeIds,
    method:'delete'
  })
}
//查询路径上已配好的点
export function getLinebyId(LineId) {
  return request({
    url: '/Point/getAimPoint',
    method: 'get',
    params: LineId
  })
}

//根据地图id查询该地图上所有的点
export function getMapPointbyId(MapID) {
  return request({
    url: '/Point/searchMapPoint',
    method: 'get',
    params: {
      MapID
    }
  })
}

//查询动作
export function searchAllAction() {
  return request({
    url: '/Point/searchPointAction',
    method: 'get'
  })
}


//根据LineId获取这个路径上所有的信息
export function getRouteInfoById(LineId) {
  return request({
    url: '/Point/searchLinePoint',
    method: 'post',
    data: {
      LineId: LineId
    }
  })
}




