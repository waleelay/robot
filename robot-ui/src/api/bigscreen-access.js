import request from '@/utils/request'

// 查询当前登录用户在 EIOP 中生效的角色和权限码。
export function getCurrentBigscreenAccess() {
  return request({
    url: '/api/bigscreen/access-control/me',
    method: 'get',
    skipErrorMessage: true
  })
}
