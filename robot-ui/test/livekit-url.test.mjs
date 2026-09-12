import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { resolveLiveKitUrl } = require('../src/utils/livekitUrl.js')

assert.equal(
  resolveLiveKitUrl('ws://211.137.109.150:7880', {
    protocol: 'https:',
    host: '211.137.109.150:4443'
  }),
  'wss://211.137.109.150:4443/livekit'
)
assert.equal(
  resolveLiveKitUrl('ws://211.137.109.150:7880', {
    protocol: 'http:',
    host: 'localhost:8080'
  }),
  'ws://211.137.109.150:7880'
)

console.log('livekit url tests passed')
