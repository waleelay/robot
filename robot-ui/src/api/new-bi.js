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