import request from '@/utils/request'

//查询点名列表
export function getRollcallList(query) {
  console.log(query)
  return request({
    url:'/Error/searchAllTaskRecord',
    method: 'post',
    data: query
  })
}

//查询详细信息
export function searchResultInfo(query) {
  return request({
    url:'/Error/selectResultInfo',
    method: 'post',
    data: query
  })
}
