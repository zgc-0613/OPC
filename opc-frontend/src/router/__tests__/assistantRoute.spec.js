import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({ user: false, admin: false }))

vi.mock('@/api/auth', () => ({
  isUserAuthenticated: () => auth.user,
  isAdminAuthenticated: () => auth.admin,
}))

vi.mock('@/api/visit', () => ({ recordVisit: vi.fn(() => Promise.resolve()) }))
vi.mock('@/views/HomeView.vue', () => ({ default: { template: '<div />' } }))

import router, { routes } from '@/router'

describe('Assistant top-level route', () => {
  beforeEach(async () => {
    auth.user = false
    await router.replace('/login')
  })

  it('is not nested under MainLayout and lazy-loads its own workspace layout', () => {
    const publicShell = routes.find((route) => route.path === '/')
    const assistant = routes.find((route) => route.path === '/assistant')

    expect(publicShell.children.some((route) => route.name === 'assistant')).toBe(false)
    expect(assistant.children[0].name).toBe('assistant')
    expect(typeof assistant.component).toBe('function')
    expect(typeof assistant.children[0].component).toBe('function')
    expect(assistant.meta.requiresUser).toBe(true)
  })

  it('preserves the complete Assistant redirect and controlled login reason', async () => {
    await router.push('/assistant')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query).toEqual({
      redirect: '/assistant',
      reason: 'ai-login-required',
    })
  })
})
