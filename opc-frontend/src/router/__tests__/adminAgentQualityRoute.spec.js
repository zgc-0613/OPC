import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({ admin: false, user: false }))

vi.mock('@/api/auth', () => ({
  isAdminAuthenticated: () => auth.admin,
  isUserAuthenticated: () => auth.user,
}))

vi.mock('@/api/visit', () => ({ recordVisit: vi.fn(() => Promise.resolve()) }))
vi.mock('@/views/HomeView.vue', () => ({ default: { template: '<div />' } }))

import router, { routes } from '@/router'

describe('admin agent-quality route', () => {
  beforeEach(async () => {
    auth.admin = false
    auth.user = false
    await router.replace('/login')
  })

  it('is nested under the protected admin shell and preserves its requested location on redirect', async () => {
    const adminShell = routes.find((route) => route.path === '/admin')
    const qualityRoute = adminShell.children.find((route) => route.name === 'admin-agent-quality')

    expect(qualityRoute.path).toBe('agent-quality')
    expect(router.resolve('/admin/agent-quality').meta.requiresAdmin).toBe(true)

    await router.push('/admin/agent-quality')

    expect(router.currentRoute.value.path).toBe('/admin/login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/agent-quality')

    auth.admin = true
    await router.push('/admin/agent-quality')

    expect(router.currentRoute.value.path).toBe('/admin/agent-quality')
  })
})
