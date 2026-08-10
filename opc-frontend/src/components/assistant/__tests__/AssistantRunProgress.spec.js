import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantRunProgress from '@/components/assistant/AssistantRunProgress.vue'
import source from '@/components/assistant/AssistantRunProgress.vue?raw'

describe('AssistantRunProgress retry state', () => {
  it('exposes the running stop action as a bordered danger command', async () => {
    const wrapper = mount(AssistantRunProgress, {
      props: { run: { status: 'running', currentStage: 'tool_running' } },
    })

    const stop = wrapper.get('button')
    expect(stop.classes()).toContain('danger-command')
    expect(stop.attributes('aria-label')).toBe('停止当前研究')
    expect(stop.text()).toContain('停止研究')
    await stop.trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it.each(['failed', 'expired', 'evidence_insufficient'])('offers %s retry only with server-linked content', (status) => {
    const withContent = mount(AssistantRunProgress, { props: { run: { status, retryContent: '关联问题' } } })
    expect(withContent.text()).toContain('安全重试')

    const purged = mount(AssistantRunProgress, { props: { run: { status, retryContent: '' } } })
    expect(purged.text()).not.toContain('安全重试')
  })

  it('offers a keyboard-operable result synchronization action before retry when terminal details are missing', async () => {
    const wrapper = mount(AssistantRunProgress, {
      props: {
        run: { status: 'failed', currentStage: 'failed', retryContent: '关联问题' },
        terminalSyncStatus: 'failed',
      },
    })

    expect(wrapper.text()).toContain('会话内容仍在同步')
    expect(wrapper.text()).not.toContain('安全重试')
    const sync = wrapper.get('button[aria-label="同步研究结果"]')
    expect(sync.text()).toContain('同步结果')
    expect(sync.attributes('disabled')).toBeUndefined()
    await sync.trigger('click')
    expect(wrapper.emitted('resume')).toHaveLength(1)
    expect(source).toMatch(/\.text-command,.danger-command\{min-height:44px\}/)
  })

  it('renders the server-owned research plan as a concise ordered task list', () => {
    const wrapper = mount(AssistantRunProgress, {
      props: {
        run: {
          status: 'running',
          currentStage: 'planning',
          researchPlan: ['理解研究目标与适用条件', '检索匹配政策', '核验政策来源与时效'],
        },
      },
    })

    const plan = wrapper.get('[data-testid="research-plan"]')
    expect(plan.findAll('li').map((item) => item.text())).toEqual([
      '理解研究目标与适用条件',
      '检索匹配政策',
      '核验政策来源与时效',
    ])
  })

  it('keeps progress copy to the controlled stage vocabulary and defers run metadata to the process inspector', () => {
    const wrapper = mount(AssistantRunProgress, {
      props: {
        run: {
          status: 'running',
          currentStage: 'tool_running',
          visibleProgress: '内部 lease lease_expires_at 诊断',
          provider: 'deepseek',
          model: 'deepseek-chat',
          tokenUsage: { totalTokens: 812 },
          latencyMs: 930,
          tools: [{ toolName: 'search_policies', evidenceCount: 3, latencyMs: 210 }],
        },
      },
    })

    expect(wrapper.text()).toContain('正在检索政策')
    expect(wrapper.text()).not.toContain('内部 lease')
    expect(wrapper.text()).not.toContain('deepseek')
    expect(wrapper.text()).not.toContain('812')
    expect(wrapper.text()).not.toContain('930 ms')
    expect(wrapper.find('.run-tools').exists()).toBe(false)
    expect(wrapper.find('footer').exists()).toBe(false)
  })

  it('uses a reduced-motion-safe opacity transition when the user-visible research stage changes', () => {
    expect(source).toMatch(/<Transition name="run-stage" mode="out-in">/)
    expect(source).toMatch(/\.run-stage-enter-active\{transition:opacity/)
    expect(source).toMatch(/@media\(prefers-reduced-motion:reduce\)/)
  })
})
