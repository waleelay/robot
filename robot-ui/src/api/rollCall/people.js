import request from '@/utils/request'

//查询犯人列表(导出全部人员)
export function getUserList(query) {
  return request({
    url:'/User/searchAllUserInfo',
    method: 'get',
    params: query
  })
}
//删除犯人
export function delUser(userId) {
  return request({
    url: '/User/deleteUserInfo/' + userId,
    method: 'delete'
  })
}
// 查询
export function getExportUserList(query) {
  return request({
    url: '/User/searchUserInfo',
    method: 'post',
    data: query
  })
}
// 修改用户
export function updateUser(data) {
  return request({
    url: '/User/updateUserInfo',
    method: 'put',
    data: data
  })
}
// 新增用户
//用的这个方法
export function addUser(UserInfo) {
  return request({
    url: '/User/addUserInfo',
    method: 'post',
    data: UserInfo
  })
}

