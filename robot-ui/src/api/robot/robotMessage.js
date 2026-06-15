import request from '@/utils/request'
//获取机器列表
export function getRobotList(data) {
  return request({
    url: '/Entity/searchDogInfoByDogName',
    method: 'post',
    data: data
  })
}
//新增地图
export function addRobot(data) {
  return request({
    url: '/Entity/addDog',
    method:'post',
    data:data
  })
}
//删除地图
export function delRobot(robotIds) {
  return request({
    url:'/Entity/deleteDog/'+robotIds,
    method:'delete'
  })

}

//修改
export function updataRobot(data) {
  return request({
    url: '/Entity/updateDog',
    method: 'post',
    data: data
  })
}

//根据dogid查找mapid
export function searchMapInfoByDogId(dogId) {
  console.log(dogId)
  return request({
    url: '/Entity/searchMapInfoByDogId',
    method: 'get',
    params: {dogId},
  })
}


