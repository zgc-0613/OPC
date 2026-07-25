import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  checkReadiness: vi.fn(),
  getCapabilities: vi.fn(),
  getAdvice: vi.fn(),
  resolveIndustry: vi.fn(),
  getRegions: vi.fn(),
  getIndustryTags: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  checkEntrepreneurshipReadiness: api.checkReadiness,
  getAiCapabilities: api.getCapabilities,
  getEntrepreneurshipAdvice: api.getAdvice,
  resolveIndustryWithAi: api.resolveIndustry,
}))
vi.mock('@/api/region', () => ({ getRegions: api.getRegions }))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.getIndustryTags }))

import AssistantView from '@/views/AssistantView.vue'

describe('AssistantView request feedback', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    sessionStorage.clear()
    api.getRegions.mockResolvedValue([{ id: 42, name: '湖北省' }])
    api.getCapabilities.mockResolvedValue({
      provider: { available: true, provider: 'DeepSeek', model: 'configured-model' },
      capabilities: [{ id: 'entrepreneurship-advisor', available: true }],
    })
    api.getIndustryTags.mockResolvedValue([{
      tagId: 7,
      name: '人工智能应用',
      caseUsageCount: 1,
      policyUsageCount: 2,
    }])
    api.checkReadiness.mockResolvedValue({
      readinessStatus: 'sufficient',
      evidenceAvailable: true,
      selectedEvidenceCount: 3,
      verifiedCaseCount: 1,
      verifiedPolicyCount: 2,
      verifiedPolicyCandidateCount: 4,
      directIndustryPolicyCount: 1,
      generalPolicyCount: 1,
      unclassifiedPolicyCount: 2,
      selectedPolicyCount: 2,
      verifiedSourceCount: 3,
      reasons: [],
    })
    api.getAdvice.mockRejectedValue(new Error('上游模型返回格式错误'))
  })

  async function mountReadyProfile() {
    const wrapper = mount(AssistantView, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    const selects = wrapper.findAll('.assistant-profile-form select')
    await selects[0].setValue('solo_company')
    await selects[1].setValue('42')
    await selects[3].setValue('100k_500k')
    await wrapper.get('#assistant-industry-input').setValue('人工智能应用')
    await wrapper.findAll('.assistant-profile-form textarea')[0].setValue('验证人工智能应用创业机会')
    await vi.advanceTimersByTimeAsync(421)
    await flushPromises()

    return wrapper
  }

  it('shows the first request error and retry action when there are no turns', async () => {
    const wrapper = await mountReadyProfile()

    await wrapper.get('.assistant-profile-form').trigger('submit')
    await flushPromises()

    expect(api.getAdvice).toHaveBeenCalledOnce()
    expect(wrapper.get('.assistant-request-error').text()).toContain('上游模型返回格式错误')
    expect(wrapper.get('.assistant-request-error button').text()).toContain('重试')
    expect(wrapper.text()).not.toContain('READY WHEN YOU ARE')

    await wrapper.get('.assistant-request-error button').trigger('click')
    await flushPromises()
    expect(api.getAdvice).toHaveBeenCalledTimes(2)
  })

  it('labels selected policies and shows direct, general, and unclassified composition', async () => {
    const wrapper = await mountReadyProfile()

    expect(wrapper.text()).toContain('选入政策 2')
    expect(wrapper.text()).toContain('直接行业政策 1')
    expect(wrapper.text()).toContain('通用政策 1')
    expect(wrapper.text()).toContain('地区参考政策 2 条，尚未标注适用行业')
  })

  it('shows the readiness failure instead of replacing it with a generic empty state', async () => {
    api.checkReadiness.mockRejectedValue(new Error('证据预检请求超时'))
    const wrapper = await mountReadyProfile()

    await wrapper.get('.assistant-profile-form').trigger('submit')
    await flushPromises()

    expect(api.getAdvice).not.toHaveBeenCalled()
    expect(wrapper.get('.assistant-request-error').text()).toContain('证据预检请求超时')
    expect(wrapper.text()).not.toContain('READY WHEN YOU ARE')
  })

  it.each([
    '资料在生成期间被修改，请重新提交',
    '智能体请求超时，请稍后重试',
    '上游模型返回格式错误',
  ])('keeps the advice error visible: %s', async (message) => {
    api.getAdvice.mockRejectedValue(new Error(message))
    const wrapper = await mountReadyProfile()

    await wrapper.get('.assistant-profile-form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.assistant-request-error').text()).toContain(message)
    expect(wrapper.get('.assistant-request-error button').exists()).toBe(true)
  })

  it.each([
    ['TRUNCATED_RESPONSE', '模型输出被截断'],
    ['UNKNOWN_SOURCE_ID', '模型引用未通过核验'],
    ['MISSING_CITATIONS', '模型引用未通过核验'],
    ['INVALID_JSON', '模型返回格式错误'],
    ['MISSING_FIELD', '模型返回格式错误'],
    ['INVALID_CONFIDENCE', '模型返回格式错误'],
  ])('shows a specific heading for %s', async (diagnosticCode, heading) => {
    const error = new Error('安全的模型响应诊断')
    error.diagnosticCode = diagnosticCode
    api.getAdvice.mockRejectedValue(error)
    const wrapper = await mountReadyProfile()

    await wrapper.get('.assistant-profile-form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.assistant-request-error strong').text()).toBe(heading)
    expect(wrapper.get('.assistant-request-error').attributes('data-diagnostic')).toBe(diagnosticCode)
    expect(wrapper.get('.assistant-request-error').text()).toContain('安全的模型响应诊断')
  })
})
