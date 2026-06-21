import request from '@/utils/request'

// 查询告警信息列表
export function listAlarmRecord(query) {
  return request({
    url: '/rsp/alarm-record/list',
    method: 'get',
    params: query
  })
}

// 查询告警信息详细
export function getAlarmRecord(id) {
  return request({
    url: '/rsp/alarm-record/' + id,
    method: 'get'
  })
}

// 新增告警信息
export function addAlarmRecord(data) {
  return request({
    url: '/rsp/alarm-record',
    method: 'post',
    data: data
  })
}

// 修改告警信息
export function updateAlarmRecord(data) {
  return request({
    url: '/rsp/alarm-record',
    method: 'put',
    data: data
  })
}

// 删除告警信息
export function delAlarmRecord(id) {
  return request({
    url: '/rsp/alarm-record/' + id,
    method: 'delete'
  })
}
