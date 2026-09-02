import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const route = vi.hoisted(() => ({ query: {} }))

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

import UniversityOpcView from '@/views/UniversityOpcView.vue'

const readPrismaStyles = () => readFileSync('src/styles/prisma.css', 'utf8')

const makeRecord = (index, type = 'communities') => ({
  id: `${type}-${String(index + 1).padStart(3, '0')}`,
  type,
  name: `${type} 记录 ${index + 1}`,
  institution: `高校 ${index + 1}`,
  province: index % 2 ? '浙江省' : '江苏省',
  city: '杭州市',
  status: 'verified',
  grade: 'A',
  summary: `真实记录摘要 ${index + 1}`,
  sourceTitle: `官方来源 ${index + 1}`,
  sourceUrl: `https://example.com/source/${index + 1}`,
})

describe('UniversityOpcView public data workspace', () => {
  beforeEach(() => {
    route.query = {}
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ data: [
        ...Array.from({ length: 55 }, (_, index) => makeRecord(index)),
        makeRecord(0, 'support'),
      ] }),
    }))
  })

  it('renders only the selected page size and supports 10, 20, and 50 rows', async () => {
    const wrapper = mount(UniversityOpcView)
    await flushPromises()

    expect(wrapper.findAll('.university-opc-record')).toHaveLength(10)
    const pageSize = wrapper.get('[data-testid="university-page-size"]')
    expect(pageSize.findAll('option').map((option) => option.element.value)).toEqual(['10', '20', '50'])

    await pageSize.setValue('20')
    expect(wrapper.findAll('.university-opc-record')).toHaveLength(20)

    await pageSize.setValue('50')
    expect(wrapper.findAll('.university-opc-record')).toHaveLength(50)
    expect(wrapper.get('[data-testid="university-pagination-summary"]').text()).toContain('1 / 2')
  })

  it('moves between pages without rendering the full filtered dataset', async () => {
    const wrapper = mount(UniversityOpcView)
    await flushPromises()

    await wrapper.get('[data-testid="university-next-page"]').trigger('click')
    expect(wrapper.findAll('.university-opc-record')).toHaveLength(10)
    expect(wrapper.get('.university-opc-record').text()).toContain('记录 11')
    expect(wrapper.get('[data-testid="university-pagination-summary"]').text()).toContain('2 / 6')
  })

  it('resets the page when a filter changes', async () => {
    const wrapper = mount(UniversityOpcView)
    await flushPromises()
    await wrapper.get('[data-testid="university-next-page"]').trigger('click')
    expect(wrapper.get('[data-testid="university-pagination-summary"]').text()).toContain('2 / 6')

    await wrapper.get('[data-testid="university-province-filter"]').setValue('浙江省')
    expect(wrapper.get('[data-testid="university-pagination-summary"]').text()).toMatch(/^1 \/ /)
    expect(wrapper.findAll('.university-opc-record').length).toBeGreaterThan(0)
  })

  it('keeps the 06 navigation group keyboard and pointer closable', async () => {
    const MainLayout = (await import('@/layouts/MainLayout.vue')).default
    const layout = mount(MainLayout, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: { template: '<div />' },
        },
      },
    })

    const trigger = layout.get('.nav-group-trigger')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    await trigger.trigger('click')
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(layout.get('#university-opc-subnav').isVisible()).toBe(true)
    await trigger.trigger('click')
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })

  it('keeps real source links and defines a high-density archive scale without overflow', async () => {
    const wrapper = mount(UniversityOpcView)
    await flushPromises()

    const source = wrapper.get('.record-source')
    expect(source.attributes('href')).toBe('https://example.com/source/1')
    const prismaStyles = readPrismaStyles()
    expect(prismaStyles).toMatch(/@media\s*\(min-width:\s*1440px\)[\s\S]*?route-university-opc[\s\S]*?university-opc-page/is)
    expect(prismaStyles).toMatch(/route-university-opc[\s\S]*?overflow-wrap\s*:\s*anywhere/is)
  })

  it('uses a responsive, touch-safe pagination control surface', async () => {
    const wrapper = mount(UniversityOpcView)
    await flushPromises()

    expect(wrapper.get('.university-opc-pagination').attributes('aria-label')).toBe('高校 OPC 分页')
    const prismaStyles = readPrismaStyles()
    expect(prismaStyles).toMatch(/\.route-university-opc \.university-opc-pagination[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) auto minmax\(0, 1fr\)/is)
    expect(prismaStyles).toMatch(/\.route-university-opc \.university-opc-page-controls button[\s\S]*?min-height:\s*44px/is)
    expect(prismaStyles).toMatch(/@media \(max-width:\s*640px\)[\s\S]*?\.route-university-opc \.university-opc-pagination[\s\S]*?grid-template-columns:\s*1fr/is)
  })
})
