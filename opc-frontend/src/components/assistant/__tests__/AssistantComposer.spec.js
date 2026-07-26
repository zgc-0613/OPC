import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantComposer from '@/components/assistant/AssistantComposer.vue'

describe('AssistantComposer keyboard submission', () => {
  it('distinguishes the first research question from a follow-up', () => {
    const first = mount(AssistantComposer, {
      props: { modelValue: '', newResearch: true },
    })
    expect(first.text()).toContain('提出第一个问题')
    expect(first.get('textarea').attributes('placeholder')).toContain('描述本次创业研究问题')
    expect(first.get('button[type="submit"]').attributes('aria-label')).toBe('开始本次研究')

    const followUp = mount(AssistantComposer, { props: { modelValue: '' } })
    expect(followUp.text()).toContain('继续研究')
    expect(followUp.get('button[type="submit"]').attributes('aria-label')).toBe('发送研究问题')
  })

  it('does not submit Enter while an IME composition is active', async () => {
    const wrapper = mount(AssistantComposer, { props: { modelValue: '研究人工智能创业' } })
    const textarea = wrapper.get('textarea')

    await textarea.trigger('compositionstart')
    await textarea.trigger('keydown', { key: 'Enter', isComposing: true })
    expect(wrapper.emitted('send')).toBeUndefined()

    await textarea.trigger('compositionend')
    await textarea.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('send')).toHaveLength(1)
  })

  it('keeps Shift+Enter as text input without submitting', async () => {
    const wrapper = mount(AssistantComposer, { props: { modelValue: '第一行' } })
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('shows authoritative used, reserved, remaining, and daily quota fields', () => {
    const wrapper = mount(AssistantComposer, {
      props: {
        modelValue: '',
        usage: { usedTokens: 120, reservedTokens: 30, remainingTokens: 850, dailyLimit: 1000, resetAt: '2026-07-26T00:00:00' },
      },
    })
    expect(wrapper.text()).toContain('已用 120 · 预留 30 · 剩余 850 / 日上限 1000')

    const unlimited = mount(AssistantComposer, {
      props: { modelValue: '', usage: { usedTokens: 120, reservedTokens: 30, remainingTokens: 0, dailyLimit: 0, unlimited: true } },
    })
    expect(unlimited.text()).toContain('已用 120 · 不限额')
    expect(unlimited.text()).not.toContain('日上限')
  })
})
