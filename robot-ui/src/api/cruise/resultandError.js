import request from '@/utils/request'

//查询巡航列表
export function getCruiseList(query) {
  return request({
    url:'/Send/getRecord',
    method: 'post',
    data: query
  })
}

//大屏处展示巡查报告列表
export function recordInfo(query) {
  return request({
    url: '/Send/getAllTaskInfo',
    method:'get',
    params: query
  })
}


//查询异常列表
export function getCruiseErrorList(query) {
  return request({
    url:'/Error/searchErrorInfo',
    method:'post',
    data: query
  })
}
//设置异常是否已处理
export function setErrorisDeal(query) {
  return request({
    url: '/Error/UpdateIsDeal',
    method:'get',
    params: query
  })
}

//大屏显示告警信息
export function warnInfo(query) {
  return request({
    url: '/Error/selectAllErrorInfo',
    method:'get',
    params: query
  })
}
