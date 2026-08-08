import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({ user: false, admin: false }))

vi.mock('@/api/auth', () => ({
  isUserAuthenticated: () => auth.user,
  isAdminAuthenticated: () => auth.admin,
}))
vi.mock('@/api/visit', () => ({ recordVisit: vi.fn(() => Promise.resolve()) }))
vi.mock('@/views/HomeView.vue', () => ({ default: { template: '<div />' } }))

import router, { routes } from '@/router'

describe('Analytics dashboard route', () => {
  beforeEach(async () => {
    auth.user = false
    await router.replace('/login')
  })

  it('keeps the data-versioned dashboard in the authenticated product shell', () => {
    const publicShell = routes.find((route) => route.path === '/')
    const analytics = publicShell.children.find((route) => route.name === 'analytics-dashboard')

    expect(analytics).toMatchObject({ path: 'analytics', meta: { requiresUser: true } })
  })

  it('redirects anonymous visitors before requesting user-owned analytics', async () => {
    await router.push('/analytics')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query).toEqual({ redirect: '/analytics', reason: 'ai-login-required' })
  })
})
