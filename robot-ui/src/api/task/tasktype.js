import request from '@/utils/request'

//获取动作列表()
export function getActionList(data) {
  return request({
    url: '',
    method: 'post',
    data: data
  })
}
//添加动作
export function addAction(data) {
  return request({
    url: '',
    method: 'post',
    data: data
  })
}

//删除动作
export function delAction(ActionIds) {
  return request({
    url:''+ActionIds,
    method:'delete'
  })
}

//修改动作
export function updateAction(data) {
  return request({
    url: '',
    method: 'post',
    data: data
  })
}
