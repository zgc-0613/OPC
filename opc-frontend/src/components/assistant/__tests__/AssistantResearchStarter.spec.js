import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantResearchStarter from '@/components/assistant/AssistantResearchStarter.vue'

describe('AssistantResearchStarter', () => {
  const baseProps = {
    modelValue: '',
    profileSummary: ['湖北省', '人工智能应用', '需求验证'],
    taskSummary: '政策查询',
  }

  it('starts a new research only from its explicit launch form', async () => {
    const wrapper = mount(AssistantResearchStarter, { props: baseProps })

    await wrapper.get('textarea[aria-label="研究问题"]').setValue('核验湖北人工智能创业扶持政策')
    await wrapper.setProps({ modelValue: '核验湖北人工智能创业扶持政策' })
    expect(wrapper.emitted('start')).toBeUndefined()

    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('start')).toHaveLength(1)
  })

  it('keeps an empty first question from creating a research session', async () => {
    const wrapper = mount(AssistantResearchStarter, { props: baseProps })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('start')).toBeUndefined()
  })

  it('opens research conditions from a dedicated control', async () => {
    const wrapper = mount(AssistantResearchStarter, { props: baseProps })

    await wrapper.get('[data-testid="open-research-conditions"]').trigger('click')

    expect(wrapper.emitted('conditions')).toHaveLength(1)
  })

  it('keeps the first question editable while launch validation is pending', async () => {
    const wrapper = mount(AssistantResearchStarter, {
      props: { ...baseProps, launchDisabled: true, launchDisabledReason: '请先选择所在地区' },
    })

    expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[role="status"]').text()).toContain('请先选择所在地区')

    await wrapper.get('textarea[aria-label="研究问题"]').setValue('先保存我的问题')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('start')).toBeUndefined()
  })
})
