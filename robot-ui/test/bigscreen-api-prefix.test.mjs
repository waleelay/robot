import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadNewBiApi(request) {
  const source = readFileSync(new URL('../src/api/new-bi.js', import.meta.url), 'utf8')
  const compiled = require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  vm.runInNewContext(compiled, {
    exports,
    require: name => {
      if (name === '@/utils/request') return request
      if (name === '@/utils/api-url') return {
        BIGSCREEN_API_PREFIX: '/api/bigscreen',
        BIGSCREEN_BUSINESS_API_PREFIX: '/api/bigscreen/business',
        BIGSCREEN_PANORAMA_API_PREFIX: '/api/bigscreen/panorama',
        BIGSCREEN_STATISTICS_API_PREFIX: '/api/bigscreen/statistics'
      }
      return {}
    }
  })
  return exports
}

test('大屏导航接口统一由共享前缀进入 BFF business 白名单', async () => {
  const requests = []
  const api = loadNewBiApi(config => {
    requests.push(config)
    return Promise.resolve({})
  })

  await api.getServicePointOptions('sx-songling-001')
  await api.createServicePointNavigation({ serialNumber: 'sx-songling-001' })
  await api.addTaskByPoint({ serialNumber: 'sx-songling-001', x: 1, y: 2, yaw: 0 })

  assert.deepEqual(requests.map(item => item.url), [
    '/api/bigscreen/business/external/devices/sx-songling-001/service-point-options',
    '/api/bigscreen/business/external/service-point-navigations',
    '/api/bigscreen/business/external/temporary-navigations'
  ])
})
