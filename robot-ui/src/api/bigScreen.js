import request from '@/utils/request'

//获取柱形图数据
export function getBarDate(query) {
  return request({
    url: '/Send/CallResultSegmentation',
    method: 'get',
    params: query
  })
}
//获取扇形图的数据
export function getPieDate(query) {
  return request({
    url: '/Send/CallResultTotal',
    method: 'get',
    params: query
  })
}
//获取异常数据(error)
export function ErrorStatistic(query) {
  return request({
    url: '/Send/ErrorStatistic',
    method: 'get',
    params: query
  })
}
//获取狗的导航状态(map)
export function getRecentTaskInfo(dogId) {
  return request({
    url: '/Send/getRecentTaskInfo',
    method: 'get',
    params: { dogId }
  })
}
//获取巡航柱状图
export function getCruiseBarDate(query) {
  return request({
    url: '/Send/ErrorResultSegmentation',
    method: 'get',
    params: query
  })
}
// 获取巡航饼状图
export function getCruisePieDate(query) {
  return request({
    url: '/Send/ErrorResultTotal',
    method: 'get',
    params: query
  })
}
