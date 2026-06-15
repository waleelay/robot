import request from '@/utils/request'
const pre = ''

// 获取摄像头列表
export function getDeviceList(url) {
  return request({
    url,
    method: 'get'
  })
}
// 监听摄像头
export function subscribeLiveBySourceId(url) {
  return request({
    url,
    method: 'get'
  })
}
// 获取一些服务连接端口 srs_server srs_http_api srs_http_server websocket
export function getTunnels(url) {
  return request({
    url,
    method: 'get'
  })
}
export function getSysArgs(url) {
  return request({
    url,
    method: 'get'
  })
}
export function getDetectStream(url) {
  return request({
    url,
    method: 'get'
  })
}
export function getDetectVideo(url) {
  return request({
    url,
    method: 'get'
  })
}
export function getToken(url) {
  return request({
    url,
    method: 'get'
  })
}