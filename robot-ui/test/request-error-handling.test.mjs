import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadResponseInterceptor() {
  let responseHandler
  const notifications = []
  const service = Object.assign(() => {}, {
    interceptors: {
      request: { use() {} },
      response: { use(success) { responseHandler = success } }
    },
    post() {}
  })
  const source = readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')
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
    require: name => {
      if (name === 'axios') return { __esModule: true, default: { create: () => service } }
      if (name === 'element-ui') return {
        Notification: { error: value => notifications.push(value) },
        Message: {},
        Loading: { service() {} }
      }
      if (name === '@/utils/errorCode') return { __esModule: true, default: {} }
      if (name === '@/utils/ruoyi') return { tansParams: () => '', blobValidate: () => true }
      if (name === '@/plugins/cache') return { __esModule: true, default: { session: {} } }
      if (name === 'file-saver') return { saveAs() {} }
      if (name === '@/utils/media-client-id') return { mediaClientId: 'test-client' }
      if (name === '@/auth') return { bearerToken: async () => '', login() {} }
      if (name === '@/utils/integration-log') return { integrationLog() {}, newRequestId: () => 'request-1' }
      return {}
    }
  })
  return { responseHandler, notifications, exports }
}

function businessErrorResponse(skipErrorMessage) {
  return {
    data: { code: 409, message: '设备当前正在执行任务' },
    config: { url: '/api/bigscreen/business/external/temporary-navigations', skipErrorMessage },
    request: {},
    status: 200
  }
}

test('skipErrorMessage 由调用方单次展示管理端业务错误', async () => {
  const { responseHandler, notifications, exports } = loadResponseInterceptor()

  await assert.rejects(
    responseHandler(businessErrorResponse(true)),
    error => error.message === '设备当前正在执行任务' && !exports.isRequestErrorNotified(error)
  )
  assert.equal(notifications.length, 0)

  await assert.rejects(
    responseHandler(businessErrorResponse(false)),
    error => error.message === '设备当前正在执行任务' && exports.isRequestErrorNotified(error)
  )
  assert.equal(notifications.length, 1)
})
