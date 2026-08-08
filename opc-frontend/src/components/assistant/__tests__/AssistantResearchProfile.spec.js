import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import AssistantResearchProfile from '@/components/assistant/AssistantResearchProfile.vue'
import profileSource from '@/components/assistant/AssistantResearchProfile.vue?raw'

const modelValue = {
  ventureType: 'solo_company', regionId: '42', industryTagId: '', industry: '',
  stage: 'validation', budgetRange: 'under_100k', goal: '', existingResources: '',
}
const industries = [
  { tagId: 7, name: '人工智能应用' },
  { tagId: 8, name: '智能制造' },
]

describe('AssistantResearchProfile industry combobox', () => {
  it('presents the read-only fork action as a discoverable secondary command', async () => {
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue, editable: false, industries },
    })

    const fork = wrapper.get('button')
    expect(fork.classes()).toContain('secondary-command')
    expect(fork.attributes('aria-label')).toBe('基于当前研究条件新建研究')
    await fork.trigger('click')
    expect(wrapper.emitted('fork')).toHaveLength(1)
  })

  it('keeps semantic field groups for wide tablet and phone container layouts', () => {
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue, editable: true, industries },
    })
    const fields = wrapper.findAll('.profile-fields > label')

    expect(fields).toHaveLength(7)
    expect(fields.map((field) => field.classes())).toEqual([
      expect.arrayContaining(['field-venture']),
      expect.arrayContaining(['field-region']),
      expect.arrayContaining(['field-industry']),
      expect.arrayContaining(['field-stage']),
      expect.arrayContaining(['field-budget']),
      expect.arrayContaining(['field-goal']),
      expect.arrayContaining(['field-resources']),
    ])
    expect(profileSource).toMatch(/@container research-profile \(min-width:521px\) and \(max-width:680px\)/)
    expect(profileSource).toMatch(/\.field-industry\s*\{\s*grid-column:\s*span 2/)
    expect(profileSource).toMatch(/@container research-profile \(max-width:520px\)/)
  })

  it('selects a standard industry with combobox keyboard semantics', async () => {
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue, editable: true, industries },
    })
    const input = wrapper.get('[role="combobox"]')

    expect(input.attributes('aria-controls')).toBe('assistant-industry-listbox')
    expect(input.attributes('aria-expanded')).toBe('false')
    await input.trigger('click')
    expect(input.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="listbox"]').exists()).toBe(true)

    await input.setValue('人工')
    await input.trigger('keydown', { key: 'ArrowDown' })
    expect(input.attributes('aria-activedescendant')).toBe('assistant-industry-option-7')
    await input.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual({
      ...modelValue,
      industryTagId: '7',
      industry: '人工智能应用',
    })
    expect(input.attributes('aria-expanded')).toBe('false')
  })

  it('opens from the whole control, closes with Escape, and reports no local match', async () => {
    const wrapper = mount(AssistantResearchProfile, {
      attachTo: document.body,
      props: { modelValue, editable: true, industries },
    })
    const control = wrapper.get('.industry-combobox-control')
    await control.trigger('click')
    expect(document.activeElement).toBe(wrapper.get('[role="combobox"]').element)

    await wrapper.get('[role="combobox"]').setValue('农业智能决策')
    expect(wrapper.get('.industry-empty').text()).toContain('没有本地匹配')
    await wrapper.get('[role="combobox"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('uses a Lucide chevron and bounded reduced-motion mobile styles', () => {
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue, editable: true, industries },
    })
    expect(wrapper.get('.industry-combobox-toggle').find('svg').exists()).toBe(true)
    expect(wrapper.get('.industry-combobox-toggle').attributes('aria-label')).toBe('展开行业选项')
    expect(wrapper.get('.industry-combobox-toggle').text()).toBe('')
    expect(profileSource).toMatch(/--assistant-field-focus:/)
    expect(profileSource).toMatch(/\.industry-combobox-control:focus-within/)
    expect(profileSource).toMatch(/box-shadow:\s*0 6px 18px/)
    expect(profileSource).toMatch(/text-overflow:\s*ellipsis/)
    expect(profileSource).toMatch(/\.industry-listbox>\[role=option\]\{min-height:44px/)
    expect(profileSource).toMatch(/@media\(prefers-reduced-motion:reduce\)/)
    expect(profileSource).toMatch(/max-width:\s*100%/)
  })

  it('announces whether the industry list will expand or collapse', async () => {
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue, editable: true, industries },
    })
    const toggle = wrapper.get('.industry-combobox-toggle')

    expect(toggle.attributes('aria-label')).toBe('展开行业选项')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-label')).toBe('收起行业选项')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-label')).toBe('展开行业选项')
  })

  it('keeps DOM focus on the combobox while the visible toggle remains keyboard reachable', async () => {
    const wrapper = mount(AssistantResearchProfile, {
      attachTo: document.body,
      props: { modelValue, editable: true, industries },
    })
    const input = wrapper.get('[role="combobox"]')
    const toggle = wrapper.get('.industry-combobox-toggle')

    expect(toggle.attributes('tabindex')).not.toBe('-1')
    toggle.element.focus()
    expect(document.activeElement).toBe(toggle.element)
    await input.trigger('click')
    expect(wrapper.findAll('[role="option"]')).toHaveLength(2)
    expect(wrapper.findAll('[role="option"]').every((option) => option.attributes('tabindex') === '-1')).toBe(true)

    await input.element.focus()
    await input.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(input.element)
    expect(input.attributes('aria-activedescendant')).toBe('assistant-industry-option-7')
    wrapper.unmount()
  })

  it('distinguishes the selected standard tag from the keyboard active option', async () => {
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue: { ...modelValue, industryTagId: '7', industry: '人工智能应用' }, editable: true, industries },
    })
    const input = wrapper.get('[role="combobox"]')
    await input.trigger('click')
    await input.setValue('')
    await input.trigger('keydown', { key: 'ArrowDown' })
    await input.trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.get('#assistant-industry-option-7').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('#assistant-industry-option-7').classes()).not.toContain('active')
    expect(wrapper.get('#assistant-industry-option-8').attributes('aria-selected')).toBe('false')
    expect(wrapper.get('#assistant-industry-option-8').classes()).toContain('active')
  })

  it('keeps the keyboard-active option visible inside a long listbox', async () => {
    const scrollIntoView = vi.fn()
    Object.defineProperty(Element.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    })
    const longIndustries = Array.from({ length: 20 }, (_, index) => ({
      tagId: index + 1,
      name: `行业 ${String(index + 1).padStart(2, '0')}`,
    }))
    const wrapper = mount(AssistantResearchProfile, {
      attachTo: document.body,
      props: { modelValue, editable: true, industries: longIndustries },
    })
    const input = wrapper.get('[role="combobox"]')
    await input.trigger('click')
    for (let index = 0; index < 12; index += 1) {
      await input.trigger('keydown', { key: 'ArrowDown' })
    }

    expect(input.attributes('aria-activedescendant')).toBe('assistant-industry-option-12')
    expect(scrollIntoView).toHaveBeenLastCalledWith({ block: 'nearest' })
    wrapper.unmount()
  })

  it('labels an AI suggestion explicitly and keeps rejection visible', async () => {
    const suggestion = { tagId: 7, name: '人工智能应用', originalText: 'AI 行业', method: 'fuzzy' }
    const wrapper = mount(AssistantResearchProfile, {
      props: { modelValue, editable: true, industries, industrySuggestion: suggestion },
    })
    expect(wrapper.text()).toContain('建议匹配“人工智能应用”')
    await wrapper.findAll('.industry-resolution-actions button')[1].trigger('click')
    expect(wrapper.emitted('reject-industry')).toHaveLength(1)

    await wrapper.setProps({ industrySuggestion: null, industryResolutionRejected: '已保留“AI 行业”' })
    expect(wrapper.get('.industry-resolution-rejected').text()).toContain('已保留“AI 行业”')

    await wrapper.setProps({ industryResolutionRejected: '', industryResolutionError: '行业解析暂时失败' })
    expect(wrapper.get('.industry-resolution').attributes('role')).toBe('alert')
    expect(wrapper.get('.industry-resolution').text()).toContain('行业解析暂时失败')
  })
})
