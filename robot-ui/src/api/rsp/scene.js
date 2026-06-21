import request from '@/utils/request'

// 查询场景注册列表
export function listScene(query) {
  return request({
    url: '/rsp/scene/list',
    method: 'get',
    params: query
  })
}

// 查询场景注册详细
export function getScene(sceneId) {
  return request({
    url: '/rsp/scene/' + sceneId,
    method: 'get'
  })
}

// 新增场景注册
export function addScene(data) {
  return request({
    url: '/rsp/scene',
    method: 'post',
    data: data
  })
}

// 修改场景注册
export function updateScene(data) {
  return request({
    url: '/rsp/scene',
    method: 'put',
    data: data
  })
}
// 批量更新场景注册
export function updateSceneBatch(data) {
  return request({
    url: '/rsp/scene/batch',
    method: 'put',
    data: data
  })
}

// 删除场景注册
export function delScene(sceneId) {
  return request({
    url: '/rsp/scene/' + sceneId,
    method: 'delete'
  })
}
export function getSceneToken() {
  return request({
    url: 'rsp/scene/token',
    method: 'get'
  })
}


