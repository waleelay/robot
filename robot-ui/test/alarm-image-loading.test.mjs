import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadFileCache(getFileInlineUrl, DateImpl = Date) {
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
    Date: DateImpl,
    require: name => {
      if (name === '@/api/media') {
        return {
          getFileInlineUrl,
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
      resolve({ url: `blob:${id}`, expiresAt: new Date(Date.now() + 3600000).toISOString() })
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
    releases.push(() => resolve({ url: `blob:${id}`, expiresAt: new Date(Date.now() + 3600000).toISOString() }))
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

test('图片预签名地址在到期前复用，剩余不足一分钟时重新签发', async () => {
  let now = Date.now()
  class TestDate extends Date {
    static now() { return now }
  }
  let requests = 0
  const cache = loadFileCache(async id => {
    requests += 1
    return {
      url: `https://files.example/${id}?version=${requests}`,
      expiresAt: new Date(now + 120000).toISOString()
    }
  }, TestDate)

  assert.equal(await cache.getCachedFileObjectUrl('image-1'), 'https://files.example/image-1?version=1')
  assert.equal(await cache.getCachedFileObjectUrl('image-1'), 'https://files.example/image-1?version=1')
  now += 61000
  assert.equal(await cache.getCachedFileObjectUrl('image-1'), 'https://files.example/image-1?version=2')
  assert.equal(requests, 2)
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
