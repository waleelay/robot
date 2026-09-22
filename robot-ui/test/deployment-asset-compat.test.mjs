import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { test } from 'node:test'

const repositoryRoot = new URL('../../', import.meta.url)
const installScript = new URL('../../deploy/docker/install-robot-ui-dist.sh', import.meta.url)
const nginxConfig = readFileSync(new URL('../../deploy/docker/config/nginx/nginx.conf', import.meta.url), 'utf8')

test('Robot UI 发布保留上一版懒加载资源并最后切换入口', () => {
  const root = mkdtempSync(join(tmpdir(), 'robot-ui-publish-'))
  const source = join(root, 'source')
  const target = join(root, 'target')
  try {
    mkdirSync(join(source, 'static/js'), { recursive: true })
    mkdirSync(join(source, 'static/css'), { recursive: true })
    mkdirSync(join(target, 'static/js'), { recursive: true })
    mkdirSync(join(target, 'static/css'), { recursive: true })
    writeFileSync(join(source, 'index.html'), 'new-entry')
    writeFileSync(join(source, 'static/js/app.new.js'), 'new-js')
    writeFileSync(join(source, 'static/css/chunk.new.css'), 'new-css')
    writeFileSync(join(target, 'index.html'), 'old-entry')
    writeFileSync(join(target, 'static/js/app.old.js'), 'old-js')
    writeFileSync(join(target, 'static/css/chunk.old.css'), 'old-css')

    const result = spawnSync('sh', [installScript.pathname, source, target, 'overwrite', '7'], {
      cwd: repositoryRoot,
      encoding: 'utf8'
    })
    assert.equal(result.status, 0, result.stderr)
    assert.equal(readFileSync(join(target, 'index.html'), 'utf8'), 'new-entry')
    assert.equal(readFileSync(join(target, 'static/js/app.new.js'), 'utf8'), 'new-js')
    assert.equal(readFileSync(join(target, 'static/js/app.old.js'), 'utf8'), 'old-js')
    assert.equal(readFileSync(join(target, 'static/css/chunk.old.css'), 'utf8'), 'old-css')
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('Nginx 对入口和哈希静态资源使用不同缓存策略', () => {
  assert.match(nginxConfig, /location = \/index\.html \{[\s\S]*no-cache, no-store, must-revalidate/)
  assert.match(nginxConfig, /location \^~ \/static\/ \{[\s\S]*max-age=604800, immutable/)
})
