import { flushPromises, mount } from '@vue/test-utils'
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
  it('renders a user message exactly once without the assistant Markdown branch', () => {
    const wrapper = mount(AssistantConversation, {
      props: { messages: [{ messageId: 10, role: 'user', content: 'USER_ONLY_CONTENT' }] },
    })

    expect(wrapper.findAll('.message.is-user > p')).toHaveLength(1)
    expect(wrapper.find('.message.is-user .assistant-markdown').exists()).toBe(false)
    expect(wrapper.text().match(/USER_ONLY_CONTENT/g)).toHaveLength(1)
  })

  it('renders a supported structured result and keeps unknown result versions on the safe Markdown path', async () => {
    const structuredResult = {
      schemaVersion: 'phase3-structured-result-v1', taskType: 'policy_lookup', directAnswer: '当前政策适用于早期创业团队。',
      keyFindings: [], recommendations: [], risks: [], assumptions: [], uncertainties: [], nextQuestions: [],
      citations: [{ sourceId: 9, title: '政策原文', publisher: '公开发布者', url: 'https://example.gov.cn/policy', evidenceRevision: 1, availability: 'current' }],
      evidenceCoverage: { factClaimCount: 1, citedFactClaimCount: 1, missingEvidenceFactCount: 0, ratio: 1 },
      confidence: 'high', evidenceVersion: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', dataVersion: null,
      generatedAt: '2026-08-01T12:00:00+08:00', taskResult: { type: 'policy_lookup' },
    }
    const wrapper = mount(AssistantConversation, {
      props: { messages: [{ messageId: 1, role: 'assistant', content: '旧版兼容正文', structuredResult }] },
    })

    expect(wrapper.get('[data-testid="structured-result"]').text()).toContain('当前政策适用于早期创业团队。')
    await wrapper.get('[data-testid="structured-result-citations"]').trigger('click')
    expect(wrapper.emitted('citations')[0][0].citations).toEqual(structuredResult.citations)

    await wrapper.setProps({ messages: [{ messageId: 2, role: 'assistant', content: '**保留的 Markdown 正文**', structuredResult: { ...structuredResult, schemaVersion: 'future-result-v2' } }] })
    expect(wrapper.find('[data-testid="structured-result"]').exists()).toBe(false)
    expect(wrapper.get('.assistant-markdown').text()).toContain('保留的 Markdown 正文')
  })

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

  it('opens only an authorized inline evidence link through the citations event', async () => {
    const message = {
      messageId: 11,
      role: 'assistant',
      runId: 301,
      content: '[授权来源](#evidence-301-2) [外部来源](https://example.com) [不可用来源](#evidence-301-3) [不可信来源](#evidence-999-2)',
      citations: [{ sourceId: 2 }],
    }
    const wrapper = mount(AssistantConversation, {
      props: {
        evidenceRunId: 301,
        evidenceItems: [
          { itemType: 'source', itemId: 2, sourceId: 2, available: true, citationId: '301:2:1' },
          { itemType: 'source', itemId: 3, sourceId: 3, available: false, citationId: '301:3:2' },
        ],
        messages: [message],
      },
    })

    const links = wrapper.findAll('.assistant-markdown a')
    const authorized = links.find((link) => link.attributes('href') === '#evidence-301-2')
    const external = links.find((link) => link.attributes('href') === 'https://example.com')
    const unavailable = links.find((link) => link.text() === '不可用来源')
    const untrusted = links.find((link) => link.text() === '不可信来源')
    expect(authorized).toBeTruthy()
    expect(external).toBeTruthy()
    expect(unavailable?.attributes('href')).toBeUndefined()
    expect(untrusted?.attributes('href')).toBeUndefined()

    await authorized.trigger('click')
    expect(wrapper.emitted('citations')).toEqual([[message, 2]])

    await external.trigger('click')
    await unavailable.trigger('click')
    await untrusted.trigger('click')
    expect(wrapper.emitted('citations')).toHaveLength(1)
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

  it('announces clipboard failures instead of claiming a successful copy', async () => {
    const originalClipboard = navigator.clipboard
    const writeText = vi.fn().mockRejectedValue(new Error('clipboard denied'))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    try {
      const wrapper = mount(AssistantConversation, {
        props: { messages: [{ messageId: 7, role: 'assistant', content: '需要复制的回答' }] },
      })
      await wrapper.get('.message footer button').trigger('click')
      await flushPromises()
      expect(writeText).toHaveBeenCalledWith('需要复制的回答')
      expect(wrapper.get('[role="alert"]').text()).toContain('复制失败')
      expect(wrapper.get('.message footer button').text()).toContain('复制回答')
    } finally {
      Object.defineProperty(navigator, 'clipboard', { configurable: true, value: originalClipboard })
    }
  })
})
