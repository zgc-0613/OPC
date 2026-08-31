import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  cases: vi.fn(),
  policies: vi.fn(),
  regions: vi.fn(),
  sources: vi.fn(),
  industries: vi.fn(),
  deleteCase: vi.fn(),
  deletePolicy: vi.fn(),
  evidenceDetail: vi.fn(),
  evidenceUpdate: vi.fn(),
}))

vi.mock('@/api/case', () => ({
  createCase: vi.fn(), deleteCase: api.deleteCase, getAdminCaseDetail: vi.fn(),
  getAdminCases: api.cases, updateCase: vi.fn(),
}))
vi.mock('@/api/policy', () => ({
  createPolicy: vi.fn(), deletePolicy: api.deletePolicy, getAdminPolicyDetail: vi.fn(),
  getAdminPolicies: api.policies, updatePolicy: vi.fn(), updatePolicyApplicabilityBatch: vi.fn(),
}))
vi.mock('@/api/evidenceReview', () => ({
  getEvidenceReviewDetail: api.evidenceDetail,
  updateEvidenceReview: api.evidenceUpdate,
}))
vi.mock('@/api/region', () => ({ getRegions: api.regions }))
vi.mock('@/api/source', () => ({
  getAdminSources: api.sources,
  resolveSourcePlaceholder: vi.fn(),
}))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.industries }))

import CaseAdminView from '@/views/admin/CaseAdminView.vue'
import PolicyAdminView from '@/views/admin/PolicyAdminView.vue'

const RouterLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a><slot /></a>',
}

describe('admin policy and case navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.regions.mockResolvedValue([
      { id: 1, name: '中国', level: 'country', parentId: null, sortOrder: 1 },
      { id: 33, name: '浙江省', level: 'province', parentId: 1, sortOrder: 33 },
      { id: 3301, name: '杭州市', level: 'city', parentId: 33, sortOrder: 1 },
      { id: 44, name: '广东省', level: 'province', parentId: 1, sortOrder: 44 },
    ])
    api.sources.mockResolvedValue([])
    api.industries.mockResolvedValue([])
    api.policies.mockResolvedValue([
      { id: 11, title: '浙江政策', regionId: 3301, regionName: '杭州市', status: 'published' },
      { id: 12, title: '广东政策', regionId: 44, regionName: '广东省', status: 'published' },
    ])
    api.cases.mockResolvedValue([
      { id: 21, title: '浙江案例', regionId: 33, regionName: '浙江省', status: 'published' },
      { id: 22, title: '广东案例', regionId: 44, regionName: '广东省', status: 'published' },
    ])
  })

  it('filters policies by parent province and exposes detail links', async () => {
    const wrapper = mount(PolicyAdminView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()

    await wrapper.get('[aria-label="政策地区筛选"] select').setValue('33')

    expect(wrapper.text()).toContain('当前显示 1 / 2 条政策')
    expect(wrapper.text()).toContain('浙江政策')
    expect(wrapper.text()).not.toContain('广东政策')
    expect(wrapper.get('[aria-label="查看政策详情：浙江政策"]').attributes('target')).toBe('_blank')
    expect(wrapper.text()).toContain('查看详情')
  })

  it('filters cases by province and exposes detail links', async () => {
    const wrapper = mount(CaseAdminView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()

    await wrapper.get('[aria-label="案例地区筛选"] select').setValue('44')

    expect(wrapper.text()).toContain('当前显示 1 / 2 条案例')
    expect(wrapper.text()).toContain('广东案例')
    expect(wrapper.text()).not.toContain('浙江案例')
    expect(wrapper.get('[aria-label="查看案例详情：广东案例"]').attributes('target')).toBe('_blank')
    expect(wrapper.text()).toContain('查看详情')
  })

  it('deletes a verified policy after confirmation and evidence downgrade', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.policies.mockResolvedValueOnce([{
      id: 66,
      title: '测试政策',
      regionId: 33,
      regionName: '浙江省',
      status: 'published',
      aiEvidenceStatus: 'verified',
      evidenceRevision: 4,
      updatedAt: '2026-08-17T20:00:00',
    }]).mockResolvedValueOnce([])
    api.evidenceDetail.mockResolvedValue({
      evidenceStatus: 'verified', version: 4, updatedAt: '2026-08-17T20:00:00',
    })
    api.evidenceUpdate.mockResolvedValue({
      evidenceStatus: 'legacy_unverified', version: 5, updatedAt: '2026-08-18T00:10:00',
    })

    const wrapper = mount(PolicyAdminView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()
    await wrapper.get('tbody tr button.danger').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('先自动移回待审'))
    expect(api.evidenceUpdate).toHaveBeenCalled()
    expect(api.deletePolicy).toHaveBeenCalledWith(66, {
      expectedEvidenceRevision: 5,
      expectedUpdatedAt: '2026-08-18T00:10:00',
    })
  })
})
