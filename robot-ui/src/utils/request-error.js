const HTTP_ERROR_MESSAGES = {
  400: '请求参数有误，请检查后重试',
  403: '当前操作没有权限',
  404: '请求的资源不存在或已失效',
  405: '当前操作暂不受支持，请联系管理员',
  408: '系统接口请求超时，请稍后重试',
  409: '当前操作与资源状态冲突，请刷新后重试',
  410: '请求的资源已失效，请刷新后重试',
  412: '页面数据已发生变化，请刷新后重试',
  413: '上传内容过大，请调整后重试',
  415: '上传内容格式不受支持',
  422: '请求内容无法处理，请检查后重试',
  423: '当前资源正在被其他操作占用，请稍后重试',
  429: '操作过于频繁，请稍后重试',
  500: '服务处理异常，请稍后重试',
  502: '下游服务暂不可用，请稍后重试',
  503: '服务暂不可用，请稍后重试',
  504: '下游服务响应超时，请稍后重试'
}

function parseErrorPayload(data) {
  let value = data
  for (let index = 0; index < 2 && typeof value === 'string'; index += 1) {
    const text = value.trim()
    if (!text) return null
    try {
      value = JSON.parse(text)
    } catch (_) {
      return text
    }
  }
  return value
}

export function errorPayloadMessage(data) {
  const payload = parseErrorPayload(data)
  if (typeof payload === 'string') return payload
  if (!payload || typeof payload !== 'object') return ''
  const direct = payload.message || payload.msg || payload.detail || payload.error_description
  if (typeof direct === 'string' && direct.trim()) return direct.trim()
  if (payload.error && typeof payload.error === 'object') return errorPayloadMessage(payload.error)
  if (payload.data && typeof payload.data === 'object') return errorPayloadMessage(payload.data)
  if (typeof payload.error === 'string' && payload.error.trim()) return payload.error.trim()
  return ''
}

export function isCanceledRequest(error) {
  return error == null ||
    error === 'cancel' ||
    error === 'close' ||
    error.code === 'ERR_CANCELED' ||
    error.name === 'CanceledError' ||
    error.__CANCEL__ === true
}

export function requestErrorMessage(error, fallbackMessage = '请求失败，请稍后重试') {
  const response = error && error.response
  const httpStatus = Number(response && response.status)
  const businessStatus = Number(error && error.businessCode)
  const status = httpStatus >= 400 ? httpStatus : businessStatus
  const backendMessage = errorPayloadMessage(response && response.data)
  if (backendMessage) return backendMessage
  if (isCanceledRequest(error)) return ''
  const rawMessage = String((error && error.message) || '')
  if (rawMessage === 'Network Error') return '网络连接异常，请检查网络或服务状态'
  if (rawMessage.toLowerCase().includes('timeout')) return HTTP_ERROR_MESSAGES[408]
  if (status && HTTP_ERROR_MESSAGES[status]) return HTTP_ERROR_MESSAGES[status]
  if (status >= 500) return '服务暂不可用，请稍后重试'
  if (status >= 400) return `请求失败（${status}），请稍后重试`
  if (rawMessage && !rawMessage.includes('Request failed with status code')) return rawMessage
  return fallbackMessage
}

export function requestErrorLevel(error) {
  const httpStatus = Number(error && error.response && error.response.status)
  const businessStatus = Number(error && error.businessCode)
  const status = httpStatus >= 400 ? httpStatus : businessStatus
  return status >= 400 && status < 500 ? 'warning' : 'error'
}
