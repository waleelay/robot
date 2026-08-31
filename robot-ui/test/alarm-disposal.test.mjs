import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadAlarmApi(request) {
  const source = readFileSync(new URL('../src/api/media.js', import.meta.url), 'utf8')
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
      if (name === '@/utils/media-client-id') return { mediaClientId: 'test-client' }
      if (name === '@/utils/api-url') {
        return { withApiPrefix: value => value, withBigscreenApiPrefix: value => value }
      }
      return {}
    }
  })
  return exports
}

test('告警处置按工作流处置标识选择接口并显式传 null', async () => {
  const requests = []
  const api = loadAlarmApi(config => {
    requests.push(JSON.parse(JSON.stringify(config)))
    return Promise.resolve({ success: true })
  })

  await api.executeAlarm({ alarmId: 'ordinary-1', disposalStatus: 'FALSE_ALARM' })
  await api.executeAlarm({
    alarmId: 'workflow-1',
    workflowActionable: true,
    disposalStatus: 'IMMEDIATE_DISPOSAL'
  })

  assert.deepEqual(requests, [
    {
      url: '/api/bigscreen/panorama/alarms/ordinary-1/handled',
      method: 'post',
      data: { disposalStatus: 'FALSE_ALARM', handleResult: null }
    },
    {
      url: '/api/bigscreen/panorama/alarms/workflow-1/handle-and-continue',
      method: 'post',
      data: { disposalStatus: 'IMMEDIATE_DISPOSAL', handleResult: null }
    }
  ])
})

test('两个告警弹窗的稍后处置均只关闭弹窗', () => {
  const files = [
    '../src/views/bi/patrol/panorama/warning/WarnInfo.vue',
    '../src/views/bi/patrol/panorama/warning/WarningBatch.vue'
  ]
  files.forEach(file => {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    assert.match(source, /if \(type === 1\) \{\s*this\.close\(\)\s*return\s*\}/)
  })
})

test('处置成功后两个告警弹窗均从全局列表移除当前告警', () => {
  const files = [
    '../src/views/bi/patrol/panorama/warning/WarnInfo.vue',
    '../src/views/bi/patrol/panorama/warning/WarningBatch.vue'
  ]
  files.forEach(file => {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    assert.match(source, /this\.removeAlarm\(alarm\.alarmId\)/)
  })

  const store = readFileSync(new URL('../src/store/modules/websocket-extra-data.js', import.meta.url), 'utf8')
  assert.match(store, /removeAlarm\(\{ commit \}, alarmId\) \{\s*commit\('REMOVE_ALARM_DATA', alarmId\)\s*\}/)
})
