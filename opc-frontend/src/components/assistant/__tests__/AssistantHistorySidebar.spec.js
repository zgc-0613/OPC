import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import AssistantHistorySidebar from '@/components/assistant/AssistantHistorySidebar.vue'

const row = { sessionId: 101, title: '湖北创业研究', status: 'active', pinned: false, lastMessageAt: '2026-07-25T10:00:00' }

describe('AssistantHistorySidebar', () => {
  it('supports keyboard rename and emits the new title', async () => {
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [row], scope: 'active' } })
    await wrapper.get('.session-menu summary').trigger('click')
    const rename = wrapper.findAll('.session-menu-popover>button').find((button) => button.text().includes('重命名'))
    await rename.trigger('click')
    const input = wrapper.get('.history-rename')
    await input.setValue('湖北 AI 创业研究')
    await input.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('rename')[0][0]).toEqual({ session: row, title: '湖北 AI 创业研究' })
  })

  it('exposes pin and archive as lightweight row actions', async () => {
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [row], scope: 'active' } })
    await wrapper.get('.session-menu summary').trigger('click')
    const buttons = wrapper.findAll('.session-menu-popover>button')
    await buttons.find((button) => button.text().includes('置顶')).trigger('click')
    expect(wrapper.emitted('pin')[0][0]).toEqual(row)

    await wrapper.get('.session-menu summary').trigger('click')
    await wrapper.findAll('.session-menu-popover>button').find((button) => button.text().includes('归档')).trigger('click')
    expect(wrapper.emitted('archive')[0][0]).toEqual(row)
  })

  it('forwards the same pin action when a pinned session is being unpinned', async () => {
    const pinned = { ...row, pinned: true }
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [pinned], scope: 'active' } })

    await wrapper.get('.session-menu summary').trigger('click')
    const unpin = wrapper.findAll('.session-menu-popover>button').find((button) => button.text().includes('取消置顶'))
    expect(unpin).toBeTruthy()
    await unpin.trigger('click')

    expect(wrapper.emitted('pin')[0][0]).toEqual(pinned)
  })

  it('requires an explicit second action before permanent deletion', async () => {
    const trashed = { ...row, deletedAt: '2026-07-25T11:00:00' }
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [trashed], scope: 'trash' } })
    await wrapper.get('.session-menu summary').trigger('click')
    const deleteButton = wrapper.findAll('.session-menu-popover>button').find((button) => button.text().includes('永久删除'))
    await deleteButton.trigger('click')
    expect(wrapper.emitted('purge')).toBeUndefined()
    await wrapper.get('.session-delete-confirm .danger').trigger('click')
    expect(wrapper.emitted('purge')[0][0]).toEqual(trashed)
  })

  it('keeps only centered commands when collapsed', () => {
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [row], collapsed: true } })
    expect(wrapper.find('.history-search').exists()).toBe(false)
    expect(wrapper.get('.new-research').attributes('type')).toBe('button')
    expect(wrapper.get('.sidebar-toggle').attributes('aria-label')).toBe('展开历史栏')
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('false')
  })

  it('emits pointer toggles immediately so the workspace can coordinate rail geometry and content', async () => {
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [row], collapsed: false } })
    const toggle = wrapper.get('.sidebar-toggle')

    toggle.element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))
    await nextTick()

    expect(wrapper.emitted('toggle')).toHaveLength(1)
    wrapper.unmount()
  })

  it('keeps extended history content mounted until an orchestrated collapse settles', async () => {
    const wrapper = mount(AssistantHistorySidebar, {
      props: { items: [row], collapsed: true, motionPhase: 'collapsing' },
    })

    expect(wrapper.find('.history-search').exists()).toBe(true)
    await wrapper.setProps({ motionPhase: '' })
    expect(wrapper.find('.history-search').exists()).toBe(false)
  })

  it('keeps collapsing history controls out of the accessibility tree until the rail settles', () => {
    const wrapper = mount(AssistantHistorySidebar, {
      props: { items: [row], collapsed: true, motionPhase: 'collapsing' },
    })
    const content = wrapper.get('.history-extended-content')

    expect(content.element.hasAttribute('inert')).toBe(true)
    expect(content.attributes('aria-hidden')).toBe('true')
  })

  it('forwards pointer selection context so a mobile drawer can close on its own transition path', async () => {
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [row] } })
    await wrapper.get('.history-row-main').trigger('click')

    expect(wrapper.emitted('select')[0][0]).toEqual(row)
    expect(wrapper.emitted('select')[0][1]).toBeInstanceOf(MouseEvent)
  })

  it('keeps keyboard-style sidebar toggles immediate', async () => {
    const wrapper = mount(AssistantHistorySidebar, { props: { items: [row], collapsed: false } })
    const toggle = wrapper.get('.sidebar-toggle')

    toggle.element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 0 }))
    await nextTick()

    expect(wrapper.emitted('toggle')).toHaveLength(1)
    expect(wrapper.get('.history-sidebar').classes()).not.toContain('is-motion-collapsing')
    wrapper.unmount()
  })

  it('renders the full drawer and marks the current session when mobile is open', () => {
    const wrapper = mount(AssistantHistorySidebar, {
      props: { items: [row], selectedId: row.sessionId, collapsed: true, mobileOpen: true },
    })

    expect(wrapper.get('.history-sidebar-head strong').text()).toBe('研究历史')
    expect(wrapper.get('.new-research span').text()).toBe('新建研究')
    expect(wrapper.find('.history-search').exists()).toBe(true)
    expect(wrapper.get('.history-row-main').attributes('aria-current')).toBe('page')
    expect(wrapper.get('.sidebar-toggle').attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.history-scopes button').attributes('aria-pressed')).toBe('true')
  })

  it('removes the closed mobile history drawer from the accessibility tree', async () => {
    const originalMatchMedia = window.matchMedia
    window.matchMedia = vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
    }))
    try {
      const wrapper = mount(AssistantHistorySidebar, {
        attachTo: document.body,
        props: { items: [row], mobileOpen: false },
      })
      await nextTick()
      expect(wrapper.get('.history-sidebar').element.hasAttribute('inert')).toBe(true)
      expect(wrapper.get('.history-sidebar').attributes('aria-hidden')).toBe('true')
      await wrapper.setProps({ mobileOpen: true })
      expect(wrapper.get('.history-sidebar').attributes('inert')).toBeUndefined()
      expect(wrapper.get('.history-sidebar').attributes('aria-hidden')).toBeUndefined()
      wrapper.unmount()
    } finally {
      window.matchMedia = originalMatchMedia
    }
  })

  it('traps focus inside the mobile history dialog', async () => {
    const outside = document.createElement('button')
    document.body.append(outside)
    const wrapper = mount(AssistantHistorySidebar, {
      attachTo: document.body,
      props: { items: [row], mobileOpen: false },
    })
    await wrapper.setProps({ mobileOpen: true })
    await nextTick()

    const drawer = wrapper.get('.history-sidebar')
    const first = drawer.get('.sidebar-toggle').element
    const focusable = drawer.element.querySelectorAll('button, input, summary')
    const last = focusable[focusable.length - 1]
    expect(drawer.attributes('role')).toBe('dialog')
    expect(document.activeElement).toBe(drawer.get('.sidebar-close').element)
    expect(outside.hasAttribute('inert')).toBe(true)
    expect(wrapper.get('.history-backdrop').attributes('inert')).toBeUndefined()

    last.focus()
    await drawer.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(first)
    first.focus()
    await drawer.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(last)
    await wrapper.setProps({ mobileOpen: false })
    expect(outside.hasAttribute('inert')).toBe(false)
    wrapper.unmount()
    outside.remove()
  })
})
