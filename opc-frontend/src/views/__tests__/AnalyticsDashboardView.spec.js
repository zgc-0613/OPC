import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  overview: vi.fn(),
  industries: vi.fn(),
  technologies: vi.fn(),
  revenue: vi.fn(),
  regions: vi.fn(),
  trends: vi.fn(),
  drilldown: vi.fn(),
  policies: vi.fn(),
  startAnalytics: vi.fn(),
}))
const router = vi.hoisted(() => ({ push: vi.fn() }))
const route = vi.hoisted(() => ({ query: {} }))
const auth = vi.hoisted(() => ({ profile: { userId: 42, username: 'researcher' } }))

vi.mock('@/api/analytics', () => ({
  getAnalyticsOverview: api.overview,
  getAnalyticsIndustries: api.industries,
  getAnalyticsTechnologies: api.technologies,
  getAnalyticsRevenue: api.revenue,
  getAnalyticsRegions: api.regions,
  getAnalyticsTrends: api.trends,
  getAnalyticsDrilldown: api.drilldown,
}))
vi.mock('@/api/ai', () => ({ startResearchFromAnalytics: api.startAnalytics }))
vi.mock('@/api/auth', () => ({ getUserProfile: () => auth.profile }))
vi.mock('@/api/policy', () => ({ getPolicies: api.policies }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))

import AnalyticsDashboardView from '@/views/AnalyticsDashboardView.vue'

describe('AnalyticsDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    route.query = {}
    api.policies.mockResolvedValue([
      { id: 1, policyType: 'comprehensive', tags: '综合发展政策，投融资与金融服务' },
      { id: 2, policyType: 'governance_market', tags: '制度治理与市场环境' },
    ])
    api.overview.mockResolvedValue({
      dataVersion: 'analytics-v1:9d18c2',
      generatedAt: '2026-08-01T10:00:00',
      status: 'partial',
      cards: [
        { metricId: 'overview.verified_cases', label: '已核验案例', value: null, unit: '条', readiness: 'Red', caveat: '案例尚未具备独立 canonical_case_id，不能发布已核验案例总数。' },
        { metricId: 'overview.verified_policies', label: '已核验政策', value: 18, unit: '条', readiness: 'Green', caveat: null },
        { metricId: 'overview.covered_technologies', label: '覆盖技术数量', value: null, unit: '种', readiness: 'Red', caveat: '正式技术 taxonomy 尚未完成审核。' },
      ],
      materialCounts: [
        { code: 'formal_policy', label: '正式文件', value: 80 },
        { code: 'expired_formal_policy', label: '失效正式文件', value: 1 },
        { code: 'consultation_draft', label: '征求意见稿', value: 14 },
        { code: 'other_material', label: '其他资料', value: 9 },
      ],
    })
    api.industries.mockResolvedValue({
      dataVersion: 'analytics-v1:9d18c2',
      generatedAt: '2026-08-01T10:00:00+08:00',
      status: 'partial',
      metric: { metricId: 'industry.case_count', name: '行业案例数量', unit: '条', multiLabel: true, readiness: 'Yellow' },
      filters: { industryTagIds: [] },
      sampleSize: 5,
      missingCount: 1,
      totalEligible: 6,
      caveats: [{ code: 'CANONICAL_CASE_DEDUPLICATION_NOT_READY', message: '当前按已核验案例 ID 去重。' }],
      buckets: [
        { bucketId: 'industry:7', industryTagId: 7, label: '人工智能服务', value: 4, ratio: 0.666667, sampleSize: 4, readiness: 'Yellow' },
        { bucketId: 'industry:9', industryTagId: 9, label: '企业服务', value: 1, ratio: 0.166667, sampleSize: 1, readiness: 'Red' },
      ],
    })
    api.technologies.mockResolvedValue({
      available: false,
      unavailableReason: 'TECHNOLOGY_TAXONOMY_NOT_READY',
      rows: [],
      caveats: [{ code: 'TECHNOLOGY_TAXONOMY_NOT_READY', message: '正式技术 taxonomy 尚未完成审核。' }],
    })
    api.revenue.mockResolvedValue({
      available: false,
      unavailableReason: 'REVENUE_SCHEMA_NOT_READY',
      rows: [],
      caveats: [{ code: 'REVENUE_SCHEMA_NOT_READY', message: '收入字段尚未结构化。' }],
    })
    api.regions.mockImplementation(({ metricId }) => Promise.resolve(metricId === 'region.case_count' ? {
      available: true,
      unavailableReason: null,
      verifiedOnly: true,
      dataVersion: 'analytics-v1:9d18c2',
      coverage: { eligible: 5, covered: 4, missing: 1, ratio: 0.8 },
      filters: { regionRole: 'operation', regionLevel: 'province' },
      caveats: [{ code: 'CANONICAL_CASE_DEDUPLICATION_NOT_READY', message: '当前按已核验案例记录 ID 去重；与全部收录案例总量分开解释。' }],
      rows: [
        { bucketId: 'region:44', regionId: 44, label: '广东省', regionRole: 'operation', value: 3, readiness: 'Yellow' },
        { bucketId: 'region:33', regionId: 33, label: '浙江省', regionRole: 'operation', value: 1, readiness: 'Yellow' },
      ],
    } : {
      available: true,
      unavailableReason: null,
      verifiedOnly: true,
      dataVersion: 'analytics-v1:9d18c2',
      coverage: { eligible: 3, covered: 3, missing: 0, ratio: 1 },
      filters: { regionRole: 'policy_applicability', regionLevel: 'province' },
      caveats: [],
      rows: [
        { bucketId: 'region:33', regionId: 33, label: '浙江省', regionRole: 'policy_applicability', value: 2, readiness: 'Green' },
        { bucketId: 'region:32', regionId: 32, label: '江苏省', regionRole: 'policy_applicability', value: 1, readiness: 'Green' },
      ],
    }))
    api.trends.mockResolvedValue({
      available: true,
      unavailableReason: null,
      verifiedOnly: true,
      dataVersion: 'analytics-v1:9d18c2',
      coverage: { eligible: 3, covered: 2, missing: 1, ratio: 0.666667 },
      filters: { granularity: 'month', regionRole: 'policy_applicability' },
      series: [
        { bucketId: '2026-01', value: 2, sampleSize: 2, syntheticEmptyBucket: false },
        { bucketId: '2026-02', value: 0, sampleSize: 0, syntheticEmptyBucket: true },
      ],
    })
    api.drilldown.mockResolvedValue({
      available: true,
      dataVersion: 'analytics-v1:9d18c2',
      rows: [
        { id: 11, title: '浙江省创业扶持政策', publishDate: '2026-01-05', regionName: '浙江省', evidenceStatus: 'verified', detailHref: '/policies/11' },
      ],
    })
  })

  it('renders verified policy analytics and requests exact region drilldown on demand', async () => {
    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(api.technologies).toHaveBeenCalledWith()
    expect(api.revenue).toHaveBeenCalledWith({
      currency: 'CNY', revenuePeriod: 'annual', revenueType: 'revenue',
    })
    expect(api.regions).toHaveBeenCalledWith({
      metricId: 'region.case_count', regionRole: 'operation',
    })
    expect(api.regions).toHaveBeenCalledWith({
      metricId: 'region.policy_count', regionRole: 'policy_applicability',
    })
    expect(api.trends).toHaveBeenCalledWith({ metricId: 'trend.policy_publish_time' })
    expect(wrapper.get('[data-testid="policy-trend"]').text()).toContain('2026-01')
    expect(wrapper.get('[data-testid="policy-regions"]').text()).toContain('浙江省')
    expect(wrapper.get('[data-testid="case-regions"]').text()).toContain('广东省')
    expect(wrapper.get('[data-testid="case-regions"]').text()).toContain('当前按已核验案例记录 ID 去重')
    expect(wrapper.get('[data-testid="case-regions"]').text()).toContain('未归属 1 条')
    expect(wrapper.text()).toContain('正式技术 taxonomy 尚未完成审核')
    expect(wrapper.text()).toContain('收入字段尚未结构化')

    await wrapper.get('[data-testid="drilldown-region:33"]').trigger('click')
    await flushPromises()

    expect(api.drilldown).toHaveBeenCalledWith({
      metricId: 'region.policy_count',
      dataVersion: 'analytics-v1:9d18c2',
      entityType: 'policy',
      bucketId: 'region:33',
    })
    expect(wrapper.get('[data-testid="policy-drilldown"]').text()).toContain('浙江省创业扶持政策')
  })

  it('loads server-owned data-versioned metrics and hands off only an explicit research draft', async () => {
    const wrapper = mount(AnalyticsDashboardView)
    await flushPromises()

    expect(api.overview).toHaveBeenCalledTimes(1)
    expect(api.industries).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('analytics-v1:9d18c2')
    expect(wrapper.text()).toContain('18')
    expect(wrapper.get('[data-testid="policy-material-distribution"]').text()).toContain('正式文件')
    expect(wrapper.get('[data-testid="policy-material-distribution"]').text()).toContain('80')
    expect(wrapper.text()).toContain('案例尚未具备独立 canonical_case_id')
    expect(wrapper.text()).toContain('正式技术 taxonomy 尚未完成审核')
    expect(wrapper.get('[data-testid="research-from-overview.verified_policies"]').attributes('aria-label')).toBe('将已核验政策带入研究')

    await wrapper.get('[data-testid="research-from-overview.verified_policies"]').trigger('click')

    expect(api.startAnalytics).not.toHaveBeenCalled()
    expect(router.push).toHaveBeenCalledWith({ name: 'assistant', query: { handoff: 'analytics' } })
    expect(JSON.parse(sessionStorage.getItem('opc_analytics_research_draft:user:42'))).toEqual({
      version: 1,
      metricId: 'overview.verified_policies',
      metricLabel: '已核验政策',
      dataVersion: 'analytics-v1:9d18c2',
      filters: {},
      selectedBucketIds: [],
      userQuestion: '请基于“已核验政策”这一已核验数据指标，说明它对当前创业研究范围意味着什么；区分可确认事实、推断、风险和下一步行动。',
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

    await wrapper.get('[data-testid="research-from-overview.verified_policies"]').trigger('click')
    await flushPromises()

    expect(router.push).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('浏览器未能保存研究条件，请允许本地存储后重试。')
    write.mockRestore()
  })
})
