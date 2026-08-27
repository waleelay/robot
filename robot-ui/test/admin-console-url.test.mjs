import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { resolveAdminConsoleUrl } = require('../src/utils/adminConsoleUrl.js')

assert.equal(
  resolveAdminConsoleUrl({ runtimeUrl: 'https://example.test:5443/' }),
  'https://example.test:5443'
)
assert.equal(
  resolveAdminConsoleUrl({ envUrl: 'http://localhost:5173/' }),
  'http://localhost:5173'
)
assert.equal(
  resolveAdminConsoleUrl({ runtimeUrl: 'https://override.test:5443', envUrl: 'http://localhost:5173/' }),
  'https://override.test:5443'
)
assert.equal(
  resolveAdminConsoleUrl({ hostname: 'localhost', protocol: 'http:' }),
  'http://localhost:5173'
)
assert.equal(
  resolveAdminConsoleUrl({ hostname: '127.0.0.1', protocol: 'http:' }),
  'http://localhost:5173'
)
assert.equal(
  resolveAdminConsoleUrl({ hostname: 'console.example.test', protocol: 'https:' }),
  'https://console.example.test:5443'
)
assert.equal(resolveAdminConsoleUrl({ runtimeUrl: '   ', hostname: 'localhost' }), 'http://localhost:5173')

console.log('admin console url tests passed')
