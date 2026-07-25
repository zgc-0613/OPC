import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn() }))

vi.mock('@/api/ai', () => ({
  getAdminAgentRuns: api.list,
  getAdminAgentRun: api.detail,
}))

import AgentRunAuditPanel from '@/components/admin/AgentRunAuditPanel.vue'

describe('AgentRunAuditPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.list.mockResolvedValue([{
      runId: 91,
      maskedUser: 'ow***r',
      sessionId: 31,
      status: 'completed',
      provider: 'deepseek',
      model: 'deepseek-chat',
      modelRounds: 2,
      toolCallCount: 1,
      promptTokens: 21,
      completionTokens: 9,
      totalTokens: 30,
      latencyMs: 38,
      finishReason: 'stop',
      diagnosticCode: null,
      requestId: 'req-safe-1',
      createdAt: '2026-07-25T10:00:00',
      completedAt: '2026-07-25T10:00:01',
    }])
    api.detail.mockResolvedValue({
      run: { runId: 91, requestId: 'req-safe-1', provider: 'deepseek', model: 'deepseek-chat' },
      tools: [{
        toolCallId: 71,
        stepNo: 1,
        toolName: 'search_policies',
        status: 'completed',
        evidenceCount: 3,
        latencyMs: 12,
        evidenceHash: 'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
      }],
    })
  })

  it('shows safe run metadata and expands a tool audit without private prompts', async () => {
    const wrapper = mount(AgentRunAuditPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('ow***r')
    expect(wrapper.text()).toContain('deepseek-chat')
    expect(wrapper.text()).toContain('30')

    await wrapper.get('[data-run-id="91"]').trigger('click')
    await flushPromises()

    expect(api.detail).toHaveBeenCalledWith(91)
    expect(wrapper.text()).toContain('search_policies')
    expect(wrapper.text()).toContain('3 条证据')
    expect(wrapper.text()).toContain('abcdef012345')
    expect(wrapper.text()).not.toContain('用户完整问题')
  })
})
