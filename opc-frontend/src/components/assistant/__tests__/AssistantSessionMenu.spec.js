import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import AssistantSessionMenu from '@/components/assistant/AssistantSessionMenu.vue'
import source from '@/components/assistant/AssistantSessionMenu.vue?raw'

const session = { sessionId: 101, title: '湖北创业研究', pinned: false }

describe('AssistantSessionMenu motion affordance', () => {
  it('enables the compact menu transition only for pointer activation', async () => {
    const wrapper = mount(AssistantSessionMenu, { props: { session, scope: 'active' } })
    const menu = wrapper.get('details')

    menu.element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    menu.element.open = true
    await menu.trigger('toggle')
    await nextTick()

    expect(menu.classes()).toContain('motion-enabled')
    expect(wrapper.get('.session-menu-popover').attributes('aria-hidden')).toBe('false')
    expect(wrapper.get('.session-menu-popover').attributes('inert')).toBeUndefined()
  })

  it('keeps keyboard activation immediate while retaining a real menu state', async () => {
    const wrapper = mount(AssistantSessionMenu, { props: { session, scope: 'active' } })
    const menu = wrapper.get('details')

    await menu.trigger('keydown', { key: 'Enter' })
    menu.element.open = true
    await menu.trigger('toggle')

    expect(menu.classes()).not.toContain('motion-enabled')
    expect(wrapper.get('.session-menu-popover').attributes('aria-hidden')).toBe('false')
  })

  it('keeps the animated popover tied to its trigger and reduced-motion safe', () => {
    expect(source).toMatch(/\.session-menu-popover\{[^}]*transform-origin:top right/)
    expect(source).toMatch(/\.session-menu\.motion-enabled \.session-menu-popover\{[^}]*transition:/)
    expect(source).toMatch(/@media\(prefers-reduced-motion:reduce\)/)
  })
})
