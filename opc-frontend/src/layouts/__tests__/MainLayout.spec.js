import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'assistant',
    fullPath: '/assistant',
  }),
}))

vi.mock('@/api/auth', () => ({
  isUserAuthenticated: () => true,
}))

import MainLayout from '@/layouts/MainLayout.vue'
import layoutSource from '@/layouts/MainLayout.vue?raw'
import assistantSource from '@/views/AssistantView.vue?raw'
import conversationSource from '@/components/assistant/AssistantConversation.vue?raw'
import composerSource from '@/components/assistant/AssistantComposer.vue?raw'

describe('MainLayout Assistant route shell', () => {
  it('does not render the ordinary public page heading around the Assistant workspace', async () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: { template: '<div data-test="assistant-view" class="assistant-workspace" />' },
        },
      },
    })

    expect(wrapper.find('.public-page-heading').exists()).toBe(false)
    expect(wrapper.find('.archive-sidebar').exists()).toBe(true)
    expect(wrapper.find('.mobile-menu-button').exists()).toBe(true)
    expect(wrapper.classes()).toContain('assistant-route-shell')
    expect(wrapper.find('.assistant-content-shell').exists()).toBe(true)
    expect(wrapper.find('.content-shell').exists()).toBe(false)
    expect(wrapper.find('[data-test="assistant-view"]').exists()).toBe(true)
    expect(layoutSource).toMatch(/height:\s*100vh;\s*height:\s*100dvh/)
    expect(layoutSource).toMatch(/\.assistant-content-shell[\s\S]*overflow:\s*hidden/)
    expect(assistantSource).toMatch(/\.research-desk\{[^}]*display:flex;flex-direction:column[^}]*height:100%[^}]*min-height:0[^}]*overflow:hidden/)
    expect(assistantSource).toMatch(/\.desk-header\{[^}]*flex:0 0 auto/)
    expect(conversationSource).toMatch(/\.conversation\{[^}]*min-height:0[^}]*flex:1 1 auto[^}]*overflow-y:auto/)
    expect(composerSource).toMatch(/\.composer\{[^}]*flex:0 0 auto/)
    expect(assistantSource).toMatch(/@media\(max-width:840px\)\{\.desk-header\{padding-left:72px\}\}/)

    await wrapper.get('.sidebar-toggle').trigger('click')
    expect(wrapper.classes()).toContain('sidebar-collapsed')
    expect(wrapper.find('.assistant-workspace').exists()).toBe(true)
  })
})
