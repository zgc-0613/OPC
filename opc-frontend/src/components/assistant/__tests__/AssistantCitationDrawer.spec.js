import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import AssistantCitationDrawer from '@/components/assistant/AssistantCitationDrawer.vue'

describe('AssistantCitationDrawer focus management', () => {
  it('traps keyboard focus and restores the opener when closed', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const wrapper = mount(AssistantCitationDrawer, {
      attachTo: document.body,
      props: {
        open: false,
        citations: [{ sourceId: 8, title: '政策原文', url: 'https://example.gov.cn/policy' }],
      },
    })

    await wrapper.setProps({ open: true })
    await nextTick()
    const close = document.querySelector('.citation-drawer header button')
    const drawer = document.querySelector('.citation-drawer')
    const link = document.querySelector('.citation-drawer a')
    expect(document.activeElement).toBe(close)
    expect(opener.hasAttribute('inert')).toBe(true)
    expect(document.querySelector('.drawer-backdrop').hasAttribute('inert')).toBe(false)

    link.focus()
    drawer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    await nextTick()
    expect(document.activeElement).toBe(close)
    close.focus()
    drawer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }))
    await nextTick()
    expect(document.activeElement).toBe(link)

    drawer.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.setProps({ open: false })
    expect(document.activeElement).toBe(opener)
    expect(opener.hasAttribute('inert')).toBe(false)
    wrapper.unmount()
    opener.remove()
  })

  it('does not restore a stale opener when the surrounding session changes', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const wrapper = mount(AssistantCitationDrawer, {
      attachTo: document.body,
      props: { open: false, restoreFocus: true },
    })

    await wrapper.setProps({ open: true })
    await nextTick()
    expect(document.activeElement).toBe(document.querySelector('.citation-drawer header button'))

    await wrapper.setProps({ restoreFocus: false, open: false })
    expect(document.activeElement).not.toBe(opener)
    wrapper.unmount()
    opener.remove()
  })

  it('rejects unsafe and credential-bearing citation URLs', async () => {
    const wrapper = mount(AssistantCitationDrawer, {
      attachTo: document.body,
      props: {
        open: true,
        citations: [
          { sourceId: 8, title: 'unsafe', url: 'javascript:alert(1)' },
          { sourceId: 9, title: 'credentials', url: 'https://user:pass@example.gov.cn/source' },
        ],
      },
    })
    await nextTick()

    expect(document.querySelectorAll('.citation-drawer a')).toHaveLength(0)
    wrapper.unmount()
  })
})
