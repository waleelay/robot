import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const source = readFileSync(new URL('../src/store/modules/media-websocket-reconnect.js', import.meta.url), 'utf8')
const policy = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

test('4001 立即换证，其他断线按封顶 30 秒的抖动退避', () => {
  assert.equal(policy.mediaReconnectDelay(4001, 4, 1), 0)
  assert.equal(policy.mediaReconnectDelay(4003, 0, 0), 2000)
  assert.equal(policy.mediaReconnectDelay(4003, 1, 1), 4500)
  assert.equal(policy.mediaReconnectDelay(4003, 9, 1), 30000)
})

test('连续第五次 4003 才展示局部故障状态，其他关闭码不会误报', () => {
  assert.equal(policy.isSustainedAuthorizationFailure(4003, 1), false)
  assert.equal(policy.isSustainedAuthorizationFailure(4003, 4), false)
  assert.equal(policy.isSustainedAuthorizationFailure(4003, 5), true)
  assert.equal(policy.isSustainedAuthorizationFailure(1006, 5), false)
  assert.equal(policy.isSustainedAuthorizationFailure(4001, 5), false)
})
