import assert from 'node:assert/strict'
import { createServer } from 'vite'

class StorageMock {
  constructor(entries = {}) {
    this.values = new Map(Object.entries(entries))
  }

  getItem(key) {
    return this.values.has(key) ? this.values.get(key) : null
  }

  setItem(key, value) {
    this.values.set(key, String(value))
  }

  removeItem(key) {
    this.values.delete(key)
  }
}

globalThis.localStorage = new StorageMock({
  opc_user_token: 'cross-tab-token',
  opc_user_profile: JSON.stringify({ username: 'ACha_' }),
})
globalThis.sessionStorage = new StorageMock()

const vite = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
})

try {
  const auth = await vite.ssrLoadModule('/src/api/auth.js')
  const { default: request } = await vite.ssrLoadModule('/src/api/request.js')

  assert.equal(
    auth.isUserAuthenticated(),
    true,
    'a new tab must restore the shared user login state',
  )
  assert.equal(auth.getUserProfile()?.username, 'ACha_')

  request.defaults.adapter = async (config) => ({
    data: { code: 200, data: config.headers.Authorization || '' },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })
  const authorization = await request.get('/auth/me')
  assert.equal(
    authorization,
    'Bearer cross-tab-token',
    'API requests from a new tab must include the shared user token',
  )

  globalThis.localStorage = new StorageMock()
  globalThis.sessionStorage = new StorageMock({
    opc_user_token: 'legacy-tab-token',
    opc_user_profile: JSON.stringify({ username: 'LegacyUser' }),
  })
  assert.equal(auth.getUserToken(), 'legacy-tab-token')
  assert.equal(localStorage.getItem('opc_user_token'), 'legacy-tab-token')
  assert.equal(sessionStorage.getItem('opc_user_token'), null)
  assert.equal(auth.getUserProfile()?.username, 'LegacyUser')
  assert.equal(sessionStorage.getItem('opc_user_profile'), null)

  console.log('cross-tab user session: ok')
} finally {
  await vite.close()
}
