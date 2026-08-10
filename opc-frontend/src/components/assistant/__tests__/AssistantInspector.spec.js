import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AssistantInspector from '@/components/assistant/AssistantInspector.vue'

describe('AssistantInspector', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    window.matchMedia = vi.fn((query) => ({ matches: query.includes('max-width: 1023px'), addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('moves focus into the mobile drawer, closes with Escape, and restores its trigger', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()

    const wrapper = mount(AssistantInspector, {
      attachTo: document.body,
      props: { open: true, title: '研究条件', caption: 'RESEARCH CONDITIONS' },
      slots: { default: '<p>研究条件内容</p>' },
    })
    await nextTick()

    const drawer = document.querySelector('.assistant-inspector')
    expect(drawer.getAttribute('role')).toBe('dialog')
    expect(document.activeElement).toBe(document.querySelector('.assistant-inspector-close'))

    drawer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(1)

    await wrapper.setProps({ open: false })
    await nextTick()
    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
  })

  it('keeps the desktop inspector as a complementary region instead of a modal', async () => {
    window.matchMedia = vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    const wrapper = mount(AssistantInspector, {
      props: { open: true, title: '引用依据', caption: 'VERIFIED SOURCES' },
      slots: { default: '<p>引用内容</p>' },
    })
    await nextTick()

    expect(wrapper.get('.assistant-inspector').attributes('role')).toBe('complementary')
    expect(wrapper.get('.assistant-inspector').attributes('aria-modal')).toBeUndefined()
  })

  it('reconfigures an open inspector when the viewport switches between desktop and tablet', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    let changeListener = null
    const mediaQuery = {
      matches: false,
      addEventListener: vi.fn((type, listener) => { if (type === 'change') changeListener = listener }),
      removeEventListener: vi.fn(),
    }
    window.matchMedia = vi.fn(() => mediaQuery)
    const wrapper = mount(AssistantInspector, {
      attachTo: document.body,
      props: { open: true, title: '研究条件', caption: 'RESEARCH CONDITIONS' },
      slots: { default: '<p>研究条件内容</p>' },
    })
    await nextTick()

    mediaQuery.matches = true
    changeListener({ matches: true })
    await nextTick()
    await nextTick()
    expect(document.querySelector('.assistant-inspector').getAttribute('role')).toBe('dialog')
    expect(document.body.style.overflow).toBe('hidden')
    expect(trigger.hasAttribute('inert')).toBe(true)
    expect(document.activeElement).toBe(document.querySelector('.assistant-inspector-close'))

    mediaQuery.matches = false
    changeListener({ matches: false })
    await nextTick()
    expect(document.querySelector('.assistant-inspector').getAttribute('role')).toBe('complementary')
    expect(document.body.style.overflow).toBe('')
    expect(trigger.hasAttribute('inert')).toBe(false)

    await wrapper.setProps({ open: false })
    await nextTick()
    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
  })
})
