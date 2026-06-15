/*
 * @Author: dengxumei
 * @Date: 2025-09-09 17:00:51
 * @LastEditors: dengxumei
 * @LastEditTime: 2025-09-12 17:05:02
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\api\menu.js
 * @Version: 
 */
import request from '@/utils/request'

// 获取路由
export const getRouters = () => {
  return request({
    url: '/getRouters',
    method: 'get'
  })
}
//控制获得狗的基础信息
export function getBasicMessage(mapInfoId) {
  return request({
    url: '/Send/openGetState',
    method: 'get',
    params: { mapInfoId }
  })
}
// // 控制不获得狗的基础信息
// export function closeBasicMessage() {
//   return request({
//     url: '/Send/closeGetState',
//     method: 'get',
//   })
// }
//云台巡视一圈
