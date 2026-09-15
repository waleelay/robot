import request from '@/utils/request'
import { BIGSCREEN_API_PREFIX } from '@/utils/api-url'

// 查询当前登录用户在 EIOP 中生效的角色和权限码。
export function getCurrentBigscreenAccess() {
  return request({
    url: BIGSCREEN_API_PREFIX + '/access-control/me',
    method: 'get',
    skipErrorMessage: true
  })
}
