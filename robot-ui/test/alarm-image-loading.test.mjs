import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadFileCache(createFileObjectUrl) {
  const source = readFileSync(new URL('../src/utils/file-object-url-cache.js', import.meta.url), 'utf8')
  const compiled = require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  vm.runInNewContext(compiled, {
    exports,
    Promise,
    Map,
    Date,
    require: name => {
      if (name === '@/api/media') {
        return {
          createFileObjectUrl,
          getFilePlayUrl: async () => ({}),
          revokeFileObjectUrl: () => {}
        }
      }
      return {}
    }
  })
  return exports
}

test('告警图片文件请求最多四路并发，相同 fileId 复用同一请求', async () => {
  let active = 0
  let maxActive = 0
  const releases = []
  const cache = loadFileCache(id => new Promise(resolve => {
    active += 1
    maxActive = Math.max(maxActive, active)
    releases.push(() => {
      active -= 1
      resolve(`blob:${id}`)
    })
  }))

  const first = cache.getCachedFileObjectUrl('same')
  const duplicate = cache.getCachedFileObjectUrl('same')
  const requests = [first, duplicate]
  for (let i = 0; i < 8; i += 1) requests.push(cache.getCachedFileObjectUrl(`file-${i}`))

  await new Promise(resolve => setImmediate(resolve))
  assert.equal(releases.length, 4)
  assert.equal(maxActive, 4)

  while (releases.length) {
    releases.shift()()
    await new Promise(resolve => setImmediate(resolve))
  }
  const urls = await Promise.all(requests)
  assert.equal(urls[0], 'blob:same')
  assert.equal(urls[1], 'blob:same')
  assert.equal(maxActive, 4)
})

test('退出清理缓存后不再启动排队图片，迟到结果不写回缓存', async () => {
  const releases = []
  let started = 0
  const cache = loadFileCache(id => new Promise(resolve => {
    started += 1
    releases.push(() => resolve(`blob:${id}`))
  }))
  const requests = Array.from({ length: 8 }, (_, index) =>
    cache.getCachedFileObjectUrl(`logout-${index}`).catch(error => error.message)
  )

  await new Promise(resolve => setImmediate(resolve))
  assert.equal(started, 4)
  cache.clearFileObjectUrlCache()
  releases.splice(0).forEach(release => release())
  const results = await Promise.all(requests)

  assert.equal(started, 4)
  assert.equal(results.every(result => result === '文件缓存已清空'), true)
})

test('全景告警列表只在图片进入可视区后加载，不再批量预取', () => {
  const component = readFileSync(new URL('../src/components/AlarmSnapshotImage.vue', import.meta.url), 'utf8')
  const left = readFileSync(new URL('../src/views/bi/patrol/panorama/Left.vue', import.meta.url), 'utf8')
  const batch = readFileSync(new URL('../src/views/bi/patrol/panorama/warning/WarningBatch.vue', import.meta.url), 'utf8')
  const helper = readFileSync(new URL('../src/utils/alarm-snapshot.js', import.meta.url), 'utf8')

  assert.match(component, /IntersectionObserver/)
  assert.match(component, /rootMargin: '100px 0px'/)
  assert.match(left, /<AlarmSnapshotImage :item="item">/)
  assert.match(batch, /<AlarmSnapshotImage :item="item">/)
  assert.doesNotMatch(helper, /loadAlarmListObjectUrls/)
})
