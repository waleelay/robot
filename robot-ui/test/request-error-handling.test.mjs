import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function compile(relativePath, moduleRequire) {
  const source = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
  const compiled = require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  vm.runInNewContext(compiled, {
    exports,
    process: { env: {} },
    console,
    require: moduleRequire || (() => ({}))
  })
  return exports
}

function loadRequestError() {
  return compile('../src/utils/request-error.js')
}

function loadRequestInterceptors() {
  let responseHandler
  let errorHandler
  const actionNotifications = []
  const service = Object.assign(() => {}, {
    interceptors: {
      request: { use() {} },
      response: { use(success, failure) { responseHandler = success; errorHandler = failure } }
    },
    post() {}
  })
  const requestError = loadRequestError()
  const exports = compile('../src/utils/request.js', name => {
    if (name === 'axios') return { __esModule: true, default: { create: () => service } }
    if (name === 'element-ui') return { Loading: { service() {} } }
    if (name === '@/utils/errorCode') return { __esModule: true, default: {} }
    if (name === '@/utils/ruoyi') return { tansParams: () => '', blobValidate: () => true }
    if (name === '@/plugins/cache') return { __esModule: true, default: { session: {} } }
    if (name === 'file-saver') return { saveAs() {} }
    if (name === '@/utils/media-client-id') return { mediaClientId: 'test-client' }
    if (name === '@/auth') return { bearerToken: async () => '', login() {} }
    if (name === '@/utils/integration-log') return { integrationLog() {}, newRequestId: () => 'request-1' }
    if (name === '@/utils/request-error') return requestError
    if (name === '@/utils/error-feedback') return {
      notifyActionError: (...args) => actionNotifications.push(args)
    }
    return {}
  })
  return { responseHandler, errorHandler, actionNotifications, requestError, exports }
}

function loadErrorFeedback() {
  const messages = []
  const requestError = loadRequestError()
  const exports = compile('../src/utils/error-feedback.js', name => {
    if (name === 'element-ui') return { Message: value => messages.push(value) }
    if (name === '@/utils/request-error') return requestError
    return {}
  })
  return { messages, exports }
}

test('请求层只标准化业务错误，不决定界面反馈', async () => {
  const { responseHandler, actionNotifications } = loadRequestInterceptors()
  const response = {
    data: { code: 409, message: '设备当前正在执行任务' },
    config: { url: '/api/bigscreen/business/external/temporary-navigations' },
    request: {},
    status: 200
  }

  await assert.rejects(responseHandler(response), error => {
    return error.message === '设备当前正在执行任务' && error.isBusinessError === true
  })
  assert.equal(actionNotifications.length, 0)
})

test('HTTP 409 优先使用后端中文消息，不暴露 Axios 原始错误', async () => {
  const { errorHandler, actionNotifications, requestError } = loadRequestInterceptors()
  const error = {
    message: 'Request failed with status code 409',
    config: {},
    response: { status: 409, data: { code: 'INVALID_STATE', message: '设备当前正在执行任务' } }
  }

  await assert.rejects(errorHandler(error), rejected => rejected === error)
  assert.equal(requestError.requestErrorMessage(error), '设备当前正在执行任务')
  assert.equal(actionNotifications.length, 0)
})

test('HTTP 409 缺少业务消息时使用可理解的状态冲突提示', () => {
  const { requestError } = loadRequestInterceptors()
  const error = {
    message: 'Request failed with status code 409',
    response: { status: 409, data: { code: 'CONFLICT' } }
  }
  assert.equal(requestError.requestErrorMessage(error), '当前操作与资源状态冲突，请刷新后重试')
})

test('响应体业务码 409 也使用状态冲突提示', () => {
  const requestError = loadRequestError()
  const error = {
    message: '系统未知错误',
    businessCode: 409,
    isBusinessError: true,
    response: { status: 200, data: { code: 409 } }
  }
  assert.equal(requestError.requestErrorMessage(error), '当前操作与资源状态冲突，请刷新后重试')
  assert.equal(requestError.requestErrorLevel(error), 'warning')
})

test('字符串 JSON 中的后端消息可以被统一提取', () => {
  const requestError = loadRequestError()
  const error = {
    message: 'Request failed with status code 403',
    response: { status: 403, data: JSON.stringify({ message: '当前用户没有该机器人控制权限' }) }
  }
  assert.equal(requestError.requestErrorMessage(error), '当前用户没有该机器人控制权限')
})

test('交互入口显式提示错误，同一错误对象不会被重复展示', () => {
  const { messages, exports } = loadErrorFeedback()
  const error = {
    message: 'Request failed with status code 409',
    response: { status: 409, data: { message: '摄像头已被其他用户占用' } }
  }

  assert.equal(exports.notifyActionError(error, '视频启动失败'), true)
  assert.equal(exports.notifyActionError(error, '视频启动失败'), false)
  assert.equal(messages.length, 1)
  assert.equal(messages[0].message, '摄像头已被其他用户占用')
  assert.equal(messages[0].type, 'warning')
})

test('错误没有可用原因时使用具体操作的兜底文案', () => {
  const { messages, exports } = loadErrorFeedback()
  assert.equal(exports.notifyActionError(new Error(), '视频启动失败'), true)
  assert.equal(messages[0].message, '视频启动失败')
})

test('取消操作不提示，服务异常使用错误级别', () => {
  const { messages, exports } = loadErrorFeedback()
  assert.equal(exports.notifyActionError('cancel'), false)
  assert.equal(exports.notifyActionError('close'), false)
  assert.equal(exports.notifyActionError({
    message: 'Request failed with status code 503',
    response: { status: 503, data: {} }
  }), true)
  assert.equal(messages.length, 1)
  assert.equal(messages[0].message, '服务暂不可用，请稍后重试')
  assert.equal(messages[0].type, 'error')
})

test('API 层不携带展示策略，用户操作入口负责反馈', () => {
  const requestSource = readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')
  const panoramaSource = readFileSync(new URL('../src/api/new-bi.js', import.meta.url), 'utf8')
  const mediaSource = readFileSync(new URL('../src/api/media.js', import.meta.url), 'utf8')
  const storeSource = readFileSync(new URL('../src/store/modules/websocket-robot.js', import.meta.url), 'utf8')
  const modalVideoSource = readFileSync(new URL('../src/views/bi/components/modal/common.js', import.meta.url), 'utf8')
  const mapPopupSource = readFileSync(new URL('../src/views/bi/gis/globalMap/popup/common.js', import.meta.url), 'utf8')

  assert.doesNotMatch(requestSource, /import\s*\{[^}]*\bMessage\b[^}]*\}\s*from 'element-ui'/)
  assert.doesNotMatch(panoramaSource, /errorMode|errorKey|notifyActionError/)
  assert.doesNotMatch(mediaSource, /errorMode|errorKey|notifyActionError/)
  assert.match(storeSource, /if \(userInitiated\) notifyActionError\(error, '视频启动失败'\)/)
  assert.match(modalVideoSource, /userInitiated: true/)
  assert.match(mapPopupSource, /userInitiated: true/)
})

test('列表和详情加载失败使用页面内状态，不触发操作弹窗', () => {
  const planList = readFileSync(new URL('../src/views/bi/patrol/business/plan/Index.vue', import.meta.url), 'utf8')
  const recordList = readFileSync(new URL('../src/views/bi/patrol/business/record/Index.vue', import.meta.url), 'utf8')
  const recordDetail = readFileSync(new URL('../src/views/bi/patrol/business/record/RecordDetail.vue', import.meta.url), 'utf8')

  assert.match(planList, /if \(!silent\) this\.loadError = requestErrorMessage\(error\)/)
  assert.match(recordList, /this\.loadError = requestErrorMessage\(error\)/)
  assert.match(recordDetail, /this\.loadError = requestErrorMessage\(error\)/)
  assert.doesNotMatch(recordList, /notifyActionError/)
})

test('首屏自动加载静默记录，用户触发的视频和地图操作保留明确提示', () => {
  const bigscreen = readFileSync(new URL('../src/views/bi/Bi.vue', import.meta.url), 'utf8')
  const taskRobot = readFileSync(new URL('../src/views/bi/components/modal/TaskRobotView.vue', import.meta.url), 'utf8')
  const globalMap = readFileSync(new URL('../src/views/bi/gis/globalMap/GlobalGisMap.vue', import.meta.url), 'utf8')
  const taskList = readFileSync(new URL('../src/views/bi/patrol/monitor/first/TaskListTree.vue', import.meta.url), 'utf8')

  assert.match(bigscreen, /console\.error\('大屏首屏数据加载失败', error\)/)
  assert.doesNotMatch(bigscreen, /Message\.error\('大屏数据暂不可用/)
  assert.match(taskRobot, /this\.\$message\.warning\('固定摄像头列表暂不可用，请稍后重试'\)/)
  assert.match(globalMap, /this\.\$message\.warning\('未找到相关装备'\)/)
  assert.match(globalMap, /this\.\$message\.warning\('任务视频关闭超时，请稍后重试'\)/)
  assert.match(taskList, /this\.\$message\.warning\('当前宫格已满，请先关闭已有画面'\)/)
})
