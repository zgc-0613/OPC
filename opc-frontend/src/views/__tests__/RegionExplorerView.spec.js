import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ regions: vi.fn(), policies: vi.fn(), cases: vi.fn() }))
vi.mock('@/api/region', () => ({ getRegions: api.regions }))
vi.mock('@/api/policy', () => ({ getPolicies: api.policies }))
vi.mock('@/api/case', () => ({ getCases: api.cases }))

import RegionDirectoryView from '@/views/RegionDirectoryView.vue'

const routerLinkStub = { props: ['to'], template: '<a><slot /></a>' }

describe('region explorer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.regions.mockResolvedValue([
      { id: 1, name: '江苏省', level: 'province' },
      { id: 2, name: '浙江省', level: 'province' },
      { id: 3, name: '安徽省', level: 'province' },
    ])
    api.policies.mockResolvedValue([{ id: 11, regionId: 1, title: '江苏示例政策' }])
    api.cases.mockResolvedValue([{ id: 21, regionId: 2, title: '甲案例' }])
  })

  it('switches the preview to the selected region', async () => {
    const wrapper = mount(RegionDirectoryView, { global: { stubs: { RouterLink: routerLinkStub } } })
    await flushPromises()

    expect(wrapper.get('.atlas-region-preview-identity h2').text()).toBe('江苏省')
    await wrapper.findAll('.atlas-region-row')[1].trigger('click')
    expect(wrapper.get('.atlas-region-preview-identity h2').text()).toBe('浙江省')
    expect(wrapper.get('.atlas-region-preview-samples').text()).toContain('暂无收录')
    expect(wrapper.findAll('.atlas-region-preview-samples')[1].text()).toContain('甲案例')
  })

  it('searches related records and can hide uncovered regions', async () => {
    const wrapper = mount(RegionDirectoryView, { global: { stubs: { RouterLink: routerLinkStub } } })
    await flushPromises()

    await wrapper.get('.atlas-region-search input').setValue('甲案例')
    expect(wrapper.findAll('.atlas-region-row')).toHaveLength(1)
    expect(wrapper.get('.atlas-region-preview-identity h2').text()).toBe('浙江省')

    await wrapper.get('.atlas-region-search input').setValue('')
    await wrapper.get('.atlas-region-covered-toggle input').setValue(true)
    expect(wrapper.findAll('.atlas-region-row')).toHaveLength(2)
  })
})
