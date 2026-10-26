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

  it('keeps the data-versioned dashboard in the public product shell', () => {
    const publicShell = routes.find((route) => route.path === '/')
    const analytics = publicShell.children.find((route) => route.name === 'analytics-dashboard')

    expect(analytics).toMatchObject({ path: 'analytics' })
    expect(analytics.meta?.requiresUser).not.toBe(true)
  })

  it('allows anonymous visitors to open read-only analytics', async () => {
    await router.push('/analytics')

    expect(router.currentRoute.value.path).toBe('/analytics')
    expect(router.currentRoute.value.name).toBe('analytics-dashboard')
  })
})
