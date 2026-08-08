import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ overview: vi.fn(), industries: vi.fn(), startAnalytics: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn() }))
const route = vi.hoisted(() => ({ query: {} }))
const auth = vi.hoisted(() => ({ profile: { userId: 42, username: 'researcher' } }))

vi.mock('@/api/analytics', () => ({
  getAnalyticsOverview: api.overview,
  getAnalyticsIndustries: api.industries,
}))
vi.mock('@/api/ai', () => ({ startResearchFromAnalytics: api.startAnalytics }))
vi.mock('@/api/auth', () => ({ getUserProfile: () => auth.profile }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))

import AnalyticsDashboardView from '@/views/AnalyticsDashboardView.vue'

describe('AnalyticsDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    route.query = {}
    api.overview.mockResolvedValue({
      dataVersion: 'analytics-v1:9d18c2',
      generatedAt: '2026-08-01T10:00:00',
      status: 'complete',
      cards: [
        { metricId: 'overview.verified_cases', label: '已核验案例', value: 18, unit: '条', readiness: 'green', caveat: null },
        { metricId: 'technology.coverage', label: '技术 taxonomy', value: null, unit: '', readiness: 'red', caveat: '正式技术 taxonomy 尚未完成审核' },
      ],
    })
    api.industries.mockResolvedValue({
      dataVersion: 'analytics-v1:9d18c2',
      generatedAt: '2026-08-01T10:00:00+08:00',
      status: 'partial',
      metric: { metricId: 'industry.case_count', name: '行业案例数量', unit: '条', multiLabel: true, readiness: 'yellow' },
      filters: { industryTagIds: [] },
      sampleSize: 5,
      missingCount: 1,
      totalEligible: 6,
      caveats: [{ code: 'CANONICAL_CASE_DEDUPLICATION_NOT_READY', message: '当前按已核验案例 ID 去重。' }],
      buckets: [
        { bucketId: 'industry:7', industryTagId: 7, label: '人工智能服务', value: 4, ratio: 0.666667, sampleSize: 4, readiness: 'yellow' },
        { bucketId: 'industry:9', industryTagId: 9, label: '企业服务', value: 1, ratio: 0.166667, sampleSize: 1, readiness: 'red' },
      ],
    })
  })

  it('loads server-owned data-versioned metrics and hands off only an explicit research draft', async () => {
    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(api.overview).toHaveBeenCalledTimes(1)
    expect(api.industries).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('analytics-v1:9d18c2')
    expect(wrapper.text()).toContain('18')
    expect(wrapper.text()).toContain('正式技术 taxonomy 尚未完成审核')
    expect(wrapper.get('[data-testid="research-from-overview.verified_cases"]').attributes('aria-label')).toBe('将已核验案例带入研究')

    await wrapper.get('[data-testid="research-from-overview.verified_cases"]').trigger('click')

    expect(api.startAnalytics).not.toHaveBeenCalled()
    expect(router.push).toHaveBeenCalledWith({ name: 'assistant', query: { handoff: 'analytics' } })
    expect(JSON.parse(sessionStorage.getItem('opc_analytics_research_draft:user:42'))).toEqual({
      version: 1,
      metricId: 'overview.verified_cases',
      metricLabel: '已核验案例',
      dataVersion: 'analytics-v1:9d18c2',
      filters: {},
      selectedBucketIds: [],
      userQuestion: '请基于“已核验案例”这一已核验数据指标，说明它对当前创业研究范围意味着什么；区分可确认事实、推断、风险和下一步行动。',
    })
  })

  it('renders an accessible industry comparison and hands off the selected server bucket', async () => {
    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(wrapper.get('[data-testid="industry-chart"]').attributes('aria-label')).toContain('人工智能服务 4 条')
    expect(wrapper.get('[data-testid="industry-table"]').text()).toContain('企业服务')
    expect(wrapper.get('.analytics-sample').text()).toContain('样本')
    expect(wrapper.get('.analytics-sample').text()).toContain('5')
    expect(wrapper.get('.analytics-sample').text()).toContain('缺失')
    expect(wrapper.get('.analytics-sample').text()).toContain('1')
    expect(wrapper.text()).toContain('当前按已核验案例 ID 去重。')
    expect(wrapper.get('[data-testid="industry-row-industry:9"]').text()).toContain('低样本')

    await wrapper.get('[data-testid="research-from-industry:7"]').trigger('click')

    expect(api.startAnalytics).not.toHaveBeenCalled()
    expect(JSON.parse(sessionStorage.getItem('opc_analytics_research_draft:user:42'))).toMatchObject({
      metricId: 'industry.case_count',
      dataVersion: 'analytics-v1:9d18c2',
      filters: { industryTagId: 7 },
      selectedBucketIds: ['industry:7'],
    })
    expect(router.push).toHaveBeenCalledWith({ name: 'assistant', query: { handoff: 'analytics' } })
  })

  it('opens the case workspace with the server-owned industry tag filter', async () => {
    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    await wrapper.get('[data-testid="cases-from-industry:7"]').trigger('click')

    expect(router.push).toHaveBeenCalledWith({ name: 'case-list', query: { industryTagId: '7' } })
    expect(router.push.mock.calls.at(-1)[0].query).not.toHaveProperty('keyword')
  })

  it('restores an exact industry metric filter from an analytics backlink', async () => {
    route.query = { metricId: 'industry.case_count', industryTagId: '9' }

    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(api.industries).toHaveBeenCalledWith([9])
    expect(wrapper.find('[data-testid="industry-row-industry:7"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="industry-row-industry:9"]').exists()).toBe(true)
  })

  it('does not fall back to unrelated buckets when an exact backlink has no matching sample', async () => {
    route.query = { metricId: 'industry.case_count', industryTagId: '11' }

    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(api.industries).toHaveBeenCalledWith([11])
    expect(wrapper.find('[data-testid^="industry-row-"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前筛选没有合格行业案例。')
  })

  it('explains when no audited metric can be brought into research', async () => {
    api.overview.mockResolvedValue({ dataVersion: 'analytics-v1:empty', generatedAt: '2026-08-01T10:00:00', status: 'complete', cards: [] })
    api.industries.mockResolvedValue({ dataVersion: 'analytics-v1:empty', status: 'unavailable', buckets: [], caveats: [] })

    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('当前没有可用于研究的已核验统计。')
    expect(wrapper.find('[data-testid^="research-from-"]').exists()).toBe(false)
  })

  it('explains when the browser cannot keep an explicit handoff draft', async () => {
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => {
      throw new Error('storage blocked')
    })
    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    await wrapper.get('[data-testid="research-from-overview.verified_cases"]').trigger('click')
    await flushPromises()

    expect(router.push).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('浏览器未能保存研究条件，请允许本地存储后重试。')
    write.mockRestore()
  })
})
