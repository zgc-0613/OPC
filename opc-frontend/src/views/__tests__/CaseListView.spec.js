import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  cases: vi.fn(),
  regions: vi.fn(),
  rankings: vi.fn(),
  industries: vi.fn(),
  recordSearch: vi.fn(),
}))
const route = vi.hoisted(() => ({ query: {} }))

vi.mock('@/api/case', () => ({ getCases: api.cases }))
vi.mock('@/api/region', () => ({ getRegions: api.regions }))
vi.mock('@/api/visit', () => ({ getVisitRankings: api.rankings }))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.industries }))
vi.mock('@/api/searchLog', () => ({ recordSearchKeyword: api.recordSearch }))
vi.mock('vue-router', () => ({ useRoute: () => route }))

import CaseListView from '@/views/CaseListView.vue'

describe('CaseListView analytics drill-down', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    api.cases.mockResolvedValue([
      {
        id: 11,
        title: '可核验 AI 服务案例',
        regionId: 1,
        regionName: '杭州',
        category: '企业服务',
        status: 'published',
      },
    ])
    api.regions.mockResolvedValue([{ id: 1, name: '杭州' }])
    api.rankings.mockResolvedValue([])
    api.industries.mockResolvedValue([{ tagId: 7, name: '人工智能服务' }])
    api.recordSearch.mockResolvedValue(undefined)
    window.matchMedia = vi.fn().mockReturnValue({ matches: true })
  })

  it('requests and displays the exact server-owned industry filter from the route', async () => {
    route.query = { industryTagId: '7' }

    const wrapper = mount(CaseListView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(api.cases).toHaveBeenCalledWith({ industryTagId: 7 })
    expect(wrapper.get('[data-testid="active-industry-filter"]').text()).toContain('人工智能服务')
    expect(wrapper.text()).toContain('行业：人工智能服务')
  })

  it.each(['7.5', '-7', 'not-a-tag', '9007199254740992'])(
    'ignores invalid industryTagId %s without degrading it to a keyword',
    async (industryTagId) => {
      route.query = { industryTagId }

      const wrapper = mount(CaseListView, {
        global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
      })
      await flushPromises()

      expect(api.cases).toHaveBeenCalledWith({})
      expect(api.cases.mock.calls.some(([params]) => params?.keyword === industryTagId)).toBe(false)
      expect(wrapper.find('[data-testid="active-industry-filter"]').exists()).toBe(false)
    },
  )
})
