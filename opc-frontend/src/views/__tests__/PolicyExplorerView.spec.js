import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ policies: vi.fn(), regions: vi.fn(), rankings: vi.fn(), search: vi.fn() }))
vi.mock('@/api/policy', () => ({ getPolicies: api.policies }))
vi.mock('@/api/region', () => ({ getRegions: api.regions }))
vi.mock('@/api/visit', () => ({ getVisitRankings: api.rankings }))
vi.mock('@/api/searchLog', () => ({ recordSearchKeyword: api.search }))
vi.mock('@/api/export', () => ({ exportPolicies: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))

import PolicyListView from '@/views/PolicyListView.vue'

const policies = [
  { id: 1, title: '甲政策', regionId: 1, regionName: '江苏省', issuingBody: '甲单位', publishDate: '2024-04-12', summary: '甲政策摘要', originalUrl: 'https://example.com/a' },
  { id: 2, title: '乙政策', regionId: 2, regionName: '浙江省', issuingBody: '乙单位', publishDate: '2025-05-01', summary: '乙政策摘要', originalUrl: 'https://example.com/b' },
]

describe('policy explorer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.policies.mockResolvedValue(policies)
    api.regions.mockResolvedValue([{ id: 1, name: '江苏省' }, { id: 2, name: '浙江省' }])
    api.rankings.mockResolvedValue([])
    api.search.mockResolvedValue(undefined)
    window.matchMedia = vi.fn().mockReturnValue({ matches: true })
  })

  it('previews the selected real record and links to its full detail', async () => {
    const wrapper = mount(PolicyListView, { global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } } })
    await flushPromises()

    expect(wrapper.get('.policy-preview h2').text()).toBe('乙政策')
    await wrapper.findAll('.policy-explorer-table tbody button')[1].trigger('click')
    expect(wrapper.get('.policy-preview h2').text()).toBe('甲政策')
    expect(wrapper.get('.policy-preview-detail-link').attributes('href')).toBe('/policies/1')
    expect(wrapper.get('.policy-preview-section a').attributes('href')).toBe('https://example.com/a')
  })

  it('keeps the three requested filters and filters by region', async () => {
    const wrapper = mount(PolicyListView)
    await flushPromises()
    expect(wrapper.findAll('.policy-explorer-filters select')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('发布年份')
    expect(wrapper.text()).not.toContain('来源信息')

    await wrapper.findAll('.policy-explorer-filters select')[0].setValue('1')
    await flushPromises()
    expect(wrapper.findAll('.policy-explorer-table tbody tr')).toHaveLength(1)
    expect(wrapper.get('.policy-preview h2').text()).toBe('甲政策')
  })
})
