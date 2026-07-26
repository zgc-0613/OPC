import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import AssistantConversation from '@/components/assistant/AssistantConversation.vue'

function setScrollGeometry(element, { scrollHeight = 1000, clientHeight = 400, scrollTop = 0 } = {}) {
  Object.defineProperty(element, 'scrollHeight', { configurable: true, value: scrollHeight })
  Object.defineProperty(element, 'clientHeight', { configurable: true, value: clientHeight })
  element.scrollTop = scrollTop
  element.scrollTo = vi.fn()
}

describe('AssistantConversation incoming scroll behavior', () => {
  it('links citations only when the message run owns the visible evidence panel', () => {
    const wrapper = mount(AssistantConversation, {
      props: {
        evidenceRunId: 301,
        evidenceItems: [{ itemType: 'source', itemId: 2, sourceId: 2, available: true }],
        messages: [
          { messageId: 1, role: 'assistant', runId: 301, content: '当前结论 [来源 2]', citations: [{ sourceId: 2 }] },
          { messageId: 2, role: 'assistant', runId: 300, content: '历史结论 [来源 2]', citations: [{ sourceId: 2 }] },
        ],
      },
    })

    const messages = wrapper.findAll('.assistant-markdown')
    expect(messages[0].html()).toContain('href="#evidence-301-2"')
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
