import request from '@/utils/request'

// 查询执行记录列表
export function listTaskRecord(query) {
  return request({
    url: '/rsp/task-record/list',
    method: 'get',
    params: query
  })
}

// 查询执行记录详细
export function getTaskRecord(id) {
  return request({
    url: '/rsp/task-record/' + id,
    method: 'get'
  })
}

// 新增执行记录
export function addTaskRecord(data) {
  return request({
    url: '/rsp/task-record',
    method: 'post',
    data: data
  })
}

// 修改执行记录
export function updateTaskRecord(data) {
  return request({
    url: '/rsp/task-record',
    method: 'put',
    data: data
  })
}

// 删除执行记录
export function delTaskRecord(id) {
  return request({
    url: '/rsp/task-record/' + id,
    method: 'delete'
  })
}
