import request from '@/utils/request'


//获取列表(MapID,UserName,PointName)
export function getppList(data) {
  return request({
    url: '/PointAndUser/searchAllRelation',
    method: 'post',
    data: data
  })
}

export function getTotal(data) {
  return request({
    url: '/PointAndUser/getTotal',
    method: 'post',
    data: data
  })
}

//修改
export function setPeopleforPoint(data) {
  return request({
    url: '/PointAndUser/updatePointUser',
    method: 'post',
    data: data
  })
}



export function getUsefulUserList(data) {
  return request({
    url:'/PointAndUser/searchPointUsefulUser',
    method: 'post',
    data: data
  })
}
export function getExportPointUserList(query) {
  return request({
    url: '/PointAndUser/exportPointAndUser',
    method: 'post',
    data: query
  })
}


