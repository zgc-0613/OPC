import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

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
  })
})
