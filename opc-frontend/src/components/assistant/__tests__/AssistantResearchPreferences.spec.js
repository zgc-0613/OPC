import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  clear: vi.fn(),
  read: vi.fn(),
  update: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  clearResearchPreferences: api.clear,
  getResearchPreferences: api.read,
  updateResearchPreferences: api.update,
}))

import AssistantResearchPreferences from '@/components/assistant/AssistantResearchPreferences.vue'

const preference = {
  memoryEnabled: true,
  commonRegion: '湖北省',
  commonIndustry: '人工智能应用',
  technologyDirection: '企业服务',
  ventureStage: 'validation',
  budgetRange: 'under_100k',
  teamCapabilities: '产品与算法',
  existingResources: '原型与客户线索',
  policyFocus: '科技创新',
}

describe('AssistantResearchPreferences', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.read.mockResolvedValue(preference)
    api.update.mockImplementation(async (payload) => payload)
    api.clear.mockResolvedValue()
  })

  it('only reads and applies an explicit memory choice to a new research draft', async () => {
    const wrapper = mount(AssistantResearchPreferences, { props: { canApply: true } })

    expect(api.read).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply')).toBeUndefined()

    await wrapper.get('[data-testid="open-research-preferences"]').trigger('click')
    await flushPromises()

    expect(api.read).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('apply')).toBeUndefined()

    await wrapper.get('[data-testid="apply-research-preferences"]').trigger('click')

    expect(wrapper.emitted('apply')).toEqual([[preference]])
  })

  it('saves a deliberate memory change and requires confirmation before deletion', async () => {
    const wrapper = mount(AssistantResearchPreferences, { props: { canApply: false } })

    await wrapper.get('[data-testid="open-research-preferences"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('.preference-fields input')[1].setValue('企业服务')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.update).toHaveBeenCalledWith(expect.objectContaining({
      memoryEnabled: true,
      commonIndustry: '企业服务',
    }))
    expect(wrapper.find('[data-testid="apply-research-preferences"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前研究条件已固定')

    await wrapper.get('.delete-preferences').trigger('click')
    await wrapper.get('.delete-confirmation button').trigger('click')
    await flushPromises()

    expect(api.clear).toHaveBeenCalledTimes(1)
    expect(wrapper.get('input[type="checkbox"]').element.checked).toBe(false)
  })
})
