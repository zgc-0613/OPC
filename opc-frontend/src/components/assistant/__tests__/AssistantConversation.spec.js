import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import conversationSource from '@/components/assistant/AssistantConversation.vue?raw'

function setScrollGeometry(element, { scrollHeight = 1000, clientHeight = 400, scrollTop = 0 } = {}) {
  Object.defineProperty(element, 'scrollHeight', { configurable: true, value: scrollHeight })
  Object.defineProperty(element, 'clientHeight', { configurable: true, value: clientHeight })
  element.scrollTop = scrollTop
  element.scrollTo = vi.fn()
}

describe('AssistantConversation incoming scroll behavior', () => {
  it('emits the server intent paired with each starter prompt', async () => {
    const wrapper = mount(AssistantConversation, { props: { draftMode: true } })

    await wrapper.findAll('.starter-grid button')[1].trigger('click')

    expect(wrapper.emitted('prefill')).toHaveLength(1)
    expect(wrapper.emitted('prefill')[0][0]).toMatchObject({
      requestedIntent: 'case_comparison',
      prompt: expect.any(String),
    })
  })

  it('shows one compact materials summary and delegates the full panel to the drawer', async () => {
    const wrapper = mount(AssistantConversation, {
      props: {
        evidenceRunId: 301,
        evidenceSummary: {
          availableCount: 4,
          totalCount: 6,
          availableGroups: { case: 2, policy: 1, source: 1 },
        },
      },
    })

    expect(conversationSource).not.toContain('AssistantEvidencePanel')
    const summary = wrapper.get('.evidence-summary-command')
    expect(summary.text()).toContain('2')
    expect(summary.text()).toContain('1')
    expect(summary.text()).toContain('查看资料')
    await summary.trigger('click')
    expect(wrapper.emitted('evidence')).toHaveLength(1)
  })

  it('links citations only when the message run owns the visible evidence panel', () => {
    const wrapper = mount(AssistantConversation, {
      props: {
        evidenceRunId: 301,
        evidenceItems: [
          { itemType: 'source', itemId: 2, sourceId: 2, available: true, citationId: '301:2:1' },
          { itemType: 'source', itemId: 3, sourceId: 3, available: false, citationId: '301:3:2' },
        ],
        messages: [
          { messageId: 1, role: 'assistant', runId: 301, content: '当前结论 [来源 2、3]', citations: [{ sourceId: 2 }, { sourceId: 3 }] },
          { messageId: 2, role: 'assistant', runId: 300, content: '历史结论 [来源 2]', citations: [{ sourceId: 2 }] },
        ],
      },
    })

    const messages = wrapper.findAll('.assistant-markdown')
    expect(messages[0].html()).toContain('href="#evidence-301-2"')
    expect(messages[0].html()).not.toContain('href="#evidence-301-3"')
    expect(messages[1].html()).not.toContain('href="#evidence-301-2"')
  })

  it('follows incoming messages only when the reader was already near the bottom', async () => {
    const wrapper = mount(AssistantConversation, { props: { messages: [] } })
    const transcript = wrapper.get('.conversation').element
    setScrollGeometry(transcript, { scrollTop: 580 })

    const follow = wrapper.vm.isNearBottom()
    expect(follow).toBe(true)
    await wrapper.setProps({ messages: [{ messageId: 1, role: 'assistant', content: '新回答' }] })
    await wrapper.vm.applyIncoming(follow, 'smooth')

    expect(transcript.scrollTo).toHaveBeenCalledWith({ top: 1000, behavior: 'smooth' })
  })

  it('preserves an upward reading position and exposes the jump command', async () => {
    const wrapper = mount(AssistantConversation, { props: { messages: [] } })
    const transcript = wrapper.get('.conversation').element
    setScrollGeometry(transcript, { scrollTop: 100 })

    const follow = wrapper.vm.isNearBottom()
    expect(follow).toBe(false)
    await wrapper.setProps({ messages: [{ messageId: 1, role: 'assistant', content: '新回答' }] })
    await wrapper.vm.applyIncoming(follow, 'smooth')

    expect(transcript.scrollTo).not.toHaveBeenCalled()
    expect(wrapper.get('.jump-bottom').text()).toContain('回到底部')
  })
})
