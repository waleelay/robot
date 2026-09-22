import axios from 'axios'
import { Loading } from 'element-ui'
import errorCode from '@/utils/errorCode'
import { tansParams, blobValidate } from "@/utils/ruoyi";
import cache from '@/plugins/cache'
import { saveAs } from 'file-saver'
import { mediaClientId } from '@/utils/media-client-id'
import { bearerToken, login } from '@/auth'
import { integrationLog, newRequestId } from '@/utils/integration-log'
import { notifyActionError } from '@/utils/error-feedback'
import { errorPayloadMessage } from '@/utils/request-error'

let downloadLoadingInstance;
// 是否显示重新登录
export let isRelogin = { show: false };

export { requestErrorMessage } from '@/utils/request-error'

// axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: process.env.VUE_APP_BASE_API,
  // 超时
  timeout: 300000
})

function isManagementRequest(url) {
  const value = String(url || '')
  return value.includes('/management/') || value.includes('/api/manage/')
}

// request拦截器
service.interceptors.request.use(async config => {
  config.headers = config.headers || {}
  if (!config.headers['X-Request-Id']) config.headers['X-Request-Id'] = newRequestId()
  config.integrationStartedAt = Date.now()
  if (isManagementRequest(config.url)) {
    integrationLog('请求', {
      protocol: 'http',
      direction: '出站',
      outcome: '已发起',
      requestId: config.headers['X-Request-Id'],
      method: String(config.method || '').toUpperCase(),
      url: config.url
    })
  }
  if (!config.headers['X-Client-Id']) {
    config.headers['X-Client-Id'] = mediaClientId
  }
  // console.log('----------------------------------------------------------', config.url)
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  if (!isToken) {
    const token = await bearerToken()
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
  }
  if (config.url.includes('/box/box')) {
    config.baseURL = '';
    // config.url = config.url.replace('box/box', 'box')
  }
  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params);
    url = url.slice(0, -1);
    config.params = {};
    config.url = url;
  }
  if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const requestSize = Object.keys(JSON.stringify(requestObj)).length; // 请求数据大小
    const limitSize = 5 * 1024 * 1024; // 限制存放数据5M
    if (requestSize >= limitSize) {
      console.warn(`[${config.url}]: ` + '请求数据大小超出允许的5M限制，无法进行防重复提交验证。')
      return config;
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url;                  // 请求地址
      const s_data = sessionObj.data;                // 请求数据
      const s_time = sessionObj.time;                // 请求时间
      const interval = 1000;                         // 间隔时间(ms)，小于此时间视为重复提交
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        console.log(requestObj, s_url, s_data, s_time)
        const message = '数据正在处理，请勿重复提交';
        console.warn(`[${s_url}]: ` + message)
        // return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, error => {
    console.log(error)
    return Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(res => {
    if (isManagementRequest(res.config?.url)) {
      integrationLog('响应', {
        protocol: 'http',
        direction: '出站',
        outcome: '完成',
        requestId: res.config?.headers?.['X-Request-Id'],
        method: String(res.config?.method || '').toUpperCase(),
        url: res.config?.url,
        statusCode: res.status,
        businessCode: res.data?.code,
        durationMs: Date.now() - (res.config?.integrationStartedAt || Date.now())
      })
    }
    // 未设置状态码则默认成功状态
    // const code = res.data.code || 200;
    const code = res.data.code === '0' || res.data.code === 0 ? 200 : res.data.code || 200;
    // 获取错误信息

    const msg = errorPayloadMessage(res.data) || errorCode[code] || errorCode['default']
    // 二进制数据则直接返回
    if (res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer') {
      return res.data
    }
    // 控制权接口以普通响应体返回 CONTROL_LOCKED 等业务结果，由调用方展示准确提示。
    if (res.config && res.config.acceptBusinessResponse) {
      return res.data
    }
    if (code === 401) {
      login()
      return Promise.reject(new Error('无效的会话，或者会话已过期，请重新登录。'))
    } else if (code !== 200) {
      const error = new Error(msg)
      error.businessCode = code
      error.isBusinessError = true
      error.config = res.config
      error.response = { status: res.status, data: res.data }
      return Promise.reject(error)
    } else {
      return res.data
    }
  },
  error => {
    if (isManagementRequest(error.config?.url)) {
      integrationLog('响应', {
        protocol: 'http',
        direction: '出站',
        outcome: '失败',
        requestId: error.config?.headers?.['X-Request-Id'],
        method: String(error.config?.method || '').toUpperCase(),
        url: error.config?.url,
        statusCode: error.response?.status,
        businessCode: error.response?.data?.code,
        durationMs: Date.now() - (error.config?.integrationStartedAt || Date.now())
      }, 'error')
    }
    console.error('HTTP 请求失败', error)
    if (error.response && error.response.status === 401) {
      login()
      return Promise.reject(error)
    }
    return Promise.reject(error)
  }
)

// 通用下载方法
export function download(url, params, filename, config) {
  downloadLoadingInstance = Loading.service({ text: "正在下载数据，请稍候", spinner: "el-icon-loading", background: "rgba(0, 0, 0, 0.7)", })
  return service.post(url, params, {
    transformRequest: [(params) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  }).then(async (data) => {
    const isBlob = blobValidate(data);
    if (isBlob) {
      const blob = new Blob([data])
      saveAs(blob, filename)
    } else {
      const resText = await data.text();
      const rspObj = JSON.parse(resText);
      const error = new Error(errorPayloadMessage(rspObj) || errorCode[rspObj.code] || errorCode['default'])
      error.businessCode = rspObj.code
      error.isBusinessError = true
      notifyActionError(error, '下载文件失败，请稍后重试')
    }
    downloadLoadingInstance.close();
  }).catch((r) => {
    console.error(r)
    notifyActionError(r, '下载文件失败，请稍后重试')
    downloadLoadingInstance.close();
  })
}
export default service
