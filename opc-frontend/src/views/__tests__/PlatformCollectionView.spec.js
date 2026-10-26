import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ list: vi.fn() }))
vi.mock('@/api/platformSite', () => ({ getPlatformSites: api.list }))

import PlatformCollectionView from '@/views/PlatformCollectionView.vue'

describe('platform directory', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps the supplied grouped-card layout and filters live database records', async () => {
    api.list.mockResolvedValue([
      { id: 1, name: '甲平台', url: 'https://example.com/', logoUrl: 'https://example.com/favicon.ico', category: '全国平台', owner: '甲主体', services: '算力服务', summary: '甲简介', domain: 'example.com' },
      { id: 2, name: '乙平台', url: 'https://example.org/', category: '地方平台', owner: '乙主体', services: '创业服务', summary: '乙简介', domain: 'example.org' },
      { id: 3, name: 'WeOPC', url: 'javascript:alert(1)', category: '地方平台', owner: '丙主体', services: '资料服务', summary: '丙简介', domain: 'example.net' },
    ])
    const wrapper = mount(PlatformCollectionView)
    await flushPromises()

    expect(wrapper.findAll('.platform-directory-group')).toHaveLength(2)
    expect(wrapper.findAll('.platform-directory-card')).toHaveLength(3)
    expect(wrapper.findAll('.platform-directory-visit[href]')).toHaveLength(2)
    expect(wrapper.get('.platform-directory-card .platform-directory-logo img').attributes('src')).toBe('https://example.com/favicon.ico')
    await wrapper.get('.platform-directory-card .platform-directory-logo img').trigger('error')
    expect(wrapper.get('.platform-directory-card .platform-directory-logo span').text()).toBe('甲平台')
    expect(wrapper.findAll('.platform-directory-card')[1].get('.platform-directory-logo span').text()).toBe('乙平台')
    expect(wrapper.findAll('.platform-directory-card')[2].get('.platform-directory-logo span').text()).toBe('WEO')
    await wrapper.get('.platform-directory-search input').setValue('算力')
    expect(wrapper.findAll('.platform-directory-card')).toHaveLength(1)
    expect(wrapper.get('.platform-directory-card h4').text()).toBe('甲平台')
    await wrapper.get('.platform-directory-search input').setValue('')
    await wrapper.get('.platform-directory-select select').setValue('地方平台')
    expect(wrapper.findAll('.platform-directory-card')).toHaveLength(2)
  })

  it('shows an error without invented platform records when the database API is unavailable', async () => {
    api.list.mockRejectedValue(new Error('offline'))
    const wrapper = mount(PlatformCollectionView)
    await flushPromises()

    expect(wrapper.get('.platform-directory-notice').text()).toContain('平台资料暂时无法读取')
    expect(wrapper.findAll('.platform-directory-card')).toHaveLength(0)
    expect(wrapper.findAll('.platform-directory-group')).toHaveLength(0)
  })
})
