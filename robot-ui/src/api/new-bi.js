import request from '@/utils/request'
const pre = '/api'

// 巡逻巡查
// 全景地图
export function getPatrolPanoramaOverview() {
  return request({
    url: pre + '/bigscreen/panorama/overview',
    method: 'get'
  })
}

// 数据统计
export function getPatrolStatisticsOverview(params) {
  return request({
    url: pre + '/bigscreen/statistics/overview',
    method: 'get',
    params
  })
}

export function exportPatrolStatisticsReport(data) {
  return request({
    url: pre + '/bigscreen/statistics/reports/export',
    method: 'post',
    data,
    responseType: 'blob',
    timeout: 300000
  })
}
