import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ quality: vi.fn() }))

vi.mock('@/api/ai', () => ({
  getAdminResearchQuality: api.quality,
}))

import AdminAgentQualityView from '@/views/admin/AdminAgentQualityView.vue'
import qualitySource from '@/views/admin/AdminAgentQualityView.vue?raw'

const aggregate = {
  sampleSize: 10,
  completedCount: 7,
  failedCount: 1,
  cancelledCount: 1,
  timeoutCount: 1,
  evidenceInsufficientCount: 2,
  helpfulCount: 5,
  notHelpfulCount: 2,
  helpfulRate: 5 / 7,
  reasonCounts: { good_evidence: 5, missing_evidence: 2 },
  evidenceInsufficientReasons: { NO_POLICY: 2 },
  failureReasons: { PROVIDER_TIMEOUT: 1 },
  taskBreakdown: [{
    key: 'case_analysis',
    sampleSize: 10,
    completedCount: 7,
    failedCount: 1,
    evidenceInsufficientCount: 2,
  }],
  modelBreakdown: [{
    key: 'deepseek-v4',
    sampleSize: 10,
    completedCount: 7,
    failedCount: 1,
    evidenceInsufficientCount: 2,
  }],
  latencySummary: { total: 2000, average: 200 },
  tokenSummary: { total: 3000, average: 300 },
  toolCallSummary: { total: 22, average: 2 },
  generatedAt: '2026-08-01T10:00:00',
  rawPrompt: 'private user message must never be displayed',
  apiSecret: 'secret-token-must-never-be-displayed',
}

describe('AdminAgentQualityView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.quality.mockResolvedValue(aggregate)
  })

  it('shows aggregate quality outcomes and applies date, task, and model filters without exposing run content', async () => {
    const wrapper = mount(AdminAgentQualityView)
    await flushPromises()

    expect(wrapper.get('section[aria-labelledby="agent-quality-title"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Aggregate quality overview')
    expect(wrapper.text()).toContain('70.0%')
    expect(wrapper.text()).toContain('2,000 ms')
    expect(wrapper.text()).toContain('3,000')
    expect(wrapper.text()).toContain('NO_POLICY')
    expect(wrapper.text()).not.toContain('private user message must never be displayed')
    expect(wrapper.text()).not.toContain('secret-token-must-never-be-displayed')

    await wrapper.get('[name="date-from"]').setValue('2026-07-01')
    await wrapper.get('[name="date-to"]').setValue('2026-07-31')
    await wrapper.get('[name="task-type"]').setValue('case_analysis')
    await wrapper.get('[name="model"]').setValue('deepseek-v4')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(api.quality).toHaveBeenLastCalledWith({
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      taskType: 'case_analysis',
      model: 'deepseek-v4',
      granularity: 'day',
    })
  })

  it('renders persisted tool-call telemetry and aggregate failure reasons', async () => {
    const wrapper = mount(AdminAgentQualityView)
    await flushPromises()

    const resources = wrapper.get('[aria-labelledby="quality-resource-title"]').text()
    expect(resources).toContain('Average tool calls')
    expect(resources).toContain('22 total tool calls')
    expect(resources).not.toContain('Not reported')

    const failures = wrapper.get('[aria-labelledby="quality-failure-title"]').text()
    expect(failures).toContain('PROVIDER_TIMEOUT')
    expect(failures).toContain('1')
  })

  it('shows an explicit empty state when the selected aggregate has no sampled runs', async () => {
    api.quality.mockResolvedValue({ ...aggregate, sampleSize: 0 })
    const wrapper = mount(AdminAgentQualityView)
    await flushPromises()

    expect(wrapper.get('[aria-labelledby="quality-empty-title"]').text()).toContain('No aggregate runs match these filters.')
    expect(wrapper.find('[aria-labelledby="quality-overview-title"]').exists()).toBe(false)
  })

  it('announces the initial loading state until the aggregate response arrives', async () => {
    let resolveRequest
    api.quality.mockImplementation(() => new Promise((resolve) => { resolveRequest = resolve }))
    const wrapper = mount(AdminAgentQualityView)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('[role="status"]').text()).toContain('Loading aggregate quality data...')
    resolveRequest(aggregate)
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.get('[aria-labelledby="quality-overview-title"]').exists()).toBe(true)
  })

  it('shows a retryable error when the aggregate endpoint cannot be read', async () => {
    api.quality.mockRejectedValueOnce(new Error('Quality service unavailable')).mockResolvedValueOnce(aggregate)
    const wrapper = mount(AdminAgentQualityView)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Quality service unavailable')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(api.quality).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[aria-labelledby="quality-overview-title"]').exists()).toBe(true)
  })

  it('keeps filter and recovery controls at a 44px touch target', () => {
    expect(qualitySource).toMatch(/\.quality-filters :is\(input, select\)[^{]*\{[^}]*min-height:\s*44px/)
    expect(qualitySource).toMatch(/\.quality-filters__actions \.button[^{]*\{[^}]*min-height:\s*44px/)
    expect(qualitySource).toMatch(/\.quality-state--error button[^{]*\{[^}]*min-height:\s*44px/)
  })
})
