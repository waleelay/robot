/*
 * @Author: dengxumei
 * @Date: 2025-11-14 14:40:25
 * @LastEditors: dengxumei
 * @LastEditTime: 2025-11-19 17:19:07
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\api\rsp\path.js
 * @Version: 
 */
import request from '@/utils/request'

// 查询路径规划列表
export function listPath(query) {
  return request({
    url: '/rsp/path/list',
    method: 'get',
    params: query
  })
}
export function listAllPath(query) {
  return request({
    url: '/rsp/path/listAll' + query,
    method: 'get'
  })
}

// 查询路径规划详细
export function getPath(pathId) {
  return request({
    url: '/rsp/path/' + pathId,
    method: 'get'
  })
}

// 新增路径规划
export function addPath(data) {
  return request({
    url: '/rsp/path',
    method: 'post',
    data: data
  })
}

// 修改路径规划
export function updatePath(data) {
  return request({
    url: '/rsp/path',
    method: 'put',
    data: data
  })
}
// 更新路径规划
export function updatePathStatus(data) {
  return request({
    url: '/rsp/path/pathState',
    method: 'put',
    data: data
  })
}

// 删除路径规划
export function delPath(pathId) {
  return request({
    url: '/rsp/path/' + pathId,
    method: 'delete'
  })
}
