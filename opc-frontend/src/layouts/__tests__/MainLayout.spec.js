import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({ name: 'policy-list', fullPath: '/policies' }))
vi.mock('vue-router', () => ({ useRoute: () => routeState }))
vi.mock('@/api/auth', () => ({ isUserAuthenticated: () => false }))

import MainLayout from '@/layouts/MainLayout.vue'

function renderLayout() {
  return mount(MainLayout, {
    global: { stubs: {
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      RouterView: { template: '<div data-test="public-view" />' },
    } },
  })
}

describe('public navigation', () => {
  it('shows the six public destinations with analysis after home', () => {
    const wrapper = renderLayout()
    expect(wrapper.findAll('.atlas-topnav a').map((item) => item.text())).toEqual([
      '首页概览', '资料分析', '政策索引', '案例索引', '平台集合', '高校 OPC',
    ])
  })

  it('shows six sidebar destinations with one merged analysis entry', () => {
    const wrapper = renderLayout()
    expect(wrapper.findAll('.atlas-sidebar nav a .atlas-side-copy strong').map((item) => item.text())).toEqual([
      '首页概览', '资料分析', '政策索引', '案例索引', '平台集合', '高校 OPC',
    ])
    expect(wrapper.find('.atlas-side-index').exists()).toBe(false)
    expect(wrapper.find('[data-test="public-view"]').exists()).toBe(true)
  })

  it('opens the navigation drawer from the mobile toggle', async () => {
    const wrapper = renderLayout()
    expect(wrapper.get('.atlas-menu-toggle').text()).toContain('菜单')
    await wrapper.get('.atlas-menu-toggle').trigger('click')
    expect(wrapper.get('.atlas-sidebar').classes()).toContain('is-open')
    expect(wrapper.get('.atlas-menu-toggle').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.atlas-menu-toggle').attributes('aria-label')).toBe('关闭导航菜单')
  })

  it('keeps the top links and icon rail visible at tablet widths', () => {
    const styles = readFileSync('src/styles/atlas.css', 'utf8')
    expect(styles).toMatch(/@media \(min-width: 721px\) and \(max-width: 1080px\)[\s\S]*?\.atlas-shell \.atlas-topnav\s*\{[\s\S]*?display: flex;/)
    expect(styles).toMatch(/@media \(min-width: 721px\) and \(max-width: 1080px\)[\s\S]*?\.atlas-shell \.atlas-sidebar\s*\{[\s\S]*?position: sticky;/)
  })

  it('collapses and restores the desktop sidebar while keeping destinations accessible', async () => {
    const wrapper = renderLayout()
    const toggle = wrapper.get('.atlas-sidebar-collapse')
    await toggle.trigger('click')
    expect(wrapper.classes()).toContain('atlas-sidebar-collapsed')
    expect(toggle.attributes('aria-label')).toBe('展开侧边导航')
    expect(wrapper.get('.atlas-sidebar nav a[aria-label="资料分析"]').exists()).toBe(true)
    await toggle.trigger('click')
    expect(wrapper.classes()).not.toContain('atlas-sidebar-collapsed')
    expect(toggle.attributes('aria-expanded')).toBe('true')
  })
})
