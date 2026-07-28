import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'policy-list',
    fullPath: '/policies',
  }),
}))

vi.mock('@/api/auth', () => ({
  isUserAuthenticated: () => true,
}))

import MainLayout from '@/layouts/MainLayout.vue'

describe('MainLayout public archive shell', () => {
  it('keeps the public archive navigation without Assistant-only branches', async () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: { template: '<div data-test="public-view" />' },
        },
      },
    })

    expect(wrapper.find('.public-page-heading').exists()).toBe(true)
    expect(wrapper.find('.archive-sidebar').exists()).toBe(true)
    expect(wrapper.find('.content-shell').exists()).toBe(true)
    expect(wrapper.find('.assistant-content-shell').exists()).toBe(false)
    expect(wrapper.classes()).not.toContain('assistant-route-shell')
    expect(wrapper.find('[data-test="public-view"]').exists()).toBe(true)

    await wrapper.get('.sidebar-toggle').trigger('click')
    expect(wrapper.classes()).toContain('sidebar-collapsed')
  })
})
