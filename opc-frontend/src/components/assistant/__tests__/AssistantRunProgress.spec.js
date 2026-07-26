import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantRunProgress from '@/components/assistant/AssistantRunProgress.vue'

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
})
