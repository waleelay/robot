import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadMediaApi(request) {
  const source = readFileSync(new URL('../src/api/media.js', import.meta.url), 'utf8')
  const compiled = require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  vm.runInNewContext(compiled, {
    exports,
    process: { env: {} },
    require: name => {
      if (name === '@/utils/request') return request
      if (name === '@/utils/media-client-id') return { mediaClientId: 'test-client' }
      if (name === '@/utils/api-url') return { withApiPrefix: value => value, withBigscreenApiPrefix: value => value }
      return {}
    }
  })
  return exports
}

test('手动图片和录像查询统一携带服务端来源条件', async () => {
  const requests = []
  const api = loadMediaApi(options => {
    requests.push(options)
    return Promise.resolve({ items: [] })
  })

  await api.getManualMediaFiles('IMAGE', { status: 'READY', page: 0 })
  await api.getManualMediaFiles('VIDEO', { status: 'READY', robotId: 'robot-1' })

  assert.deepEqual(JSON.parse(JSON.stringify(requests.map(item => item.params))), [
    { status: 'READY', page: 0, fileType: 'IMAGE', source: 'WEB_SNAPSHOT' },
    { status: 'READY', robotId: 'robot-1', fileType: 'VIDEO', source: 'LIVEKIT_EGRESS' }
  ])
})

test('多媒体列表和详情不再按 extensionId 或不存在的 sourceType 补丁过滤', () => {
  const files = [
    '../src/views/bi/components/Snapshot.vue',
    '../src/views/bi/components/recording.js',
    '../src/views/bi/patrol/monitor/second/components/MultimediaRecord.vue',
    '../src/views/bi/patrol/monitor/second/components/MultimediaDetail.vue'
  ].map(path => readFileSync(new URL(path, import.meta.url), 'utf8'))

  files.forEach(source => assert.match(source, /getManualMediaFiles/))
  assert.doesNotMatch(files[0], /item\.sourceType/)
  assert.doesNotMatch(files[1], /item\.extensionId/)
})

test('多媒体详情按二十条分页，并仅加载可视区媒体资源', () => {
  const detail = readFileSync(new URL(
    '../src/views/bi/patrol/monitor/second/components/MultimediaDetail.vue',
    import.meta.url
  ), 'utf8')

  assert.match(detail, /listSize: 20/)
  assert.match(detail, /@scroll="handleListScroll"/)
  assert.match(detail, /new IntersectionObserver/)
  assert.match(detail, /if \(!this\.visibleMediaIds\.has\(recording\.fileId\)\) return/)
  assert.doesNotMatch(detail, /size: 50/)
  assert.doesNotMatch(detail, /hydrateImageItems/)
  assert.doesNotMatch(detail, /bindListThumbs/)
})
