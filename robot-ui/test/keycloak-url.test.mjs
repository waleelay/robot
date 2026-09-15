import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { resolveKeycloakUrl } = require('../src/utils/keycloakUrl.js')

assert.equal(
  resolveKeycloakUrl({ runtimeUrl: 'https://iam.example.test:18443/' }),
  'https://iam.example.test:18443'
)
assert.equal(
  resolveKeycloakUrl({
    runtimeUrl: 'https://runtime-iam.example.test:18443'
  }),
  'https://runtime-iam.example.test:18443'
)
assert.equal(
  resolveKeycloakUrl({ runtimeUrl: '   ', origin: 'https://192.168.1.20:4443' }),
  'https://192.168.1.20:18443'
)
assert.equal(
  resolveKeycloakUrl({ origin: 'https://192.168.1.20:4443' }),
  'https://192.168.1.20:18443'
)
assert.equal(
  resolveKeycloakUrl({ origin: 'https://screen.example.test:4443' }),
  'https://screen.example.test:18443'
)
assert.equal(
  resolveKeycloakUrl({ origin: 'http://192.168.1.20' }),
  'https://192.168.1.20:18443'
)
assert.equal(
  resolveKeycloakUrl({ origin: 'https://[2001:db8::1]:4443' }),
  'https://[2001:db8::1]:18443'
)

console.log('keycloak url tests passed')
