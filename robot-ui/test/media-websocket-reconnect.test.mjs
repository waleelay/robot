import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const source = readFileSync(new URL('../src/store/modules/media-websocket-reconnect.js', import.meta.url), 'utf8')
const policy = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const websocketSource = readFileSync(new URL('../src/store/modules/websocket-robot.js', import.meta.url), 'utf8')

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

test('会话配额关闭不重连，网络和鉴权故障继续按原策略恢复', () => {
  assert.equal(policy.shouldReconnectMedia(4008), false)
  assert.equal(policy.shouldReconnectMedia(1006), true)
  assert.equal(policy.shouldReconnectMedia(4001), true)
  assert.equal(policy.shouldReconnectMedia(4003), true)
})

test('连接内授权不可用只切换页面状态，恢复后校准 Overview', () => {
  const start = websocketSource.indexOf("event.event === 'bigscreen.authorization.state'")
  const end = websocketSource.indexOf("event.event === 'bigscreen.authorization.changed'", start)
  const handler = websocketSource.slice(start, end)

  assert.ok(start >= 0 && end > start)
  assert.match(handler, /commit\('setAuthorizationUnavailable', unavailable\)/)
  assert.match(handler, /if \(!unavailable\)/)
  assert.match(handler, /refreshAuthorizedOverview\(dispatch, \{ failClosed: true/)
  assert.doesNotMatch(handler, /socket\.close/)
})
