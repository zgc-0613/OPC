import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    name: 'policy-list',
    fullPath: '/policies',
    query: {},
  }),
}))

vi.mock('@/api/auth', () => ({
  isUserAuthenticated: () => true,
}))

import MainLayout from '@/layouts/MainLayout.vue'

describe('MainLayout public archive shell', () => {
  it('uses one typography contract for numbered links and the university trigger', () => {
    const prismaStyles = readFileSync('src/styles/prisma.css', 'utf8')
    const sharedTitleRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.archive-nav > a > span,\s*\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-group-title\s*\{([^}]*)\}/is,
    )
    const sharedSubtitleRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.archive-nav > a > small,\s*\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-group-trigger small\s*\{([^}]*)\}/is,
    )

    expect(sharedTitleRule?.[1]).toMatch(
      /font-family:\s*'Noto Serif SC',\s*'Songti SC',\s*'STSong',\s*'SimSun',\s*serif/is,
    )
    expect(sharedTitleRule?.[1]).toMatch(/font-size:\s*14px/is)
    expect(sharedTitleRule?.[1]).toMatch(/font-weight:\s*760/is)
    expect(sharedTitleRule?.[1]).toMatch(/line-height:\s*1\.5/is)
    expect(sharedSubtitleRule?.[1]).toMatch(
      /font-family:\s*'Noto Serif SC',\s*'Songti SC',\s*'STSong',\s*'SimSun',\s*serif/is,
    )
    expect(sharedSubtitleRule?.[1]).toMatch(/color:\s*var\(--prisma-quiet\)/is)
    expect(sharedSubtitleRule?.[1]).toMatch(/font-size:\s*11px/is)
    expect(sharedSubtitleRule?.[1]).toMatch(/line-height:\s*1\.2/is)
  })

  it('keeps the university trigger and expanded rows in the archive type scale', () => {
    const prismaStyles = readFileSync('src/styles/prisma.css', 'utf8')

    expect(prismaStyles).toMatch(/university-nav-group \.nav-group-trigger[\s\S]*?grid-template-rows:\s*auto auto/is)
    expect(prismaStyles).toMatch(/university-nav-group \.nav-group-trigger[\s\S]*?color:\s*#555b56/is)
    expect(prismaStyles).toMatch(/nav-group-title[\s\S]*?font-size:\s*14px/is)
    expect(prismaStyles).toMatch(/nav-submenu-copy[\s\S]*?align-content:\s*center/is)
    expect(prismaStyles).toMatch(/nav-submenu-title[\s\S]*?font-size:\s*14px/is)
    expect(prismaStyles).toMatch(/nav-submenu a small[\s\S]*?color:\s*#515752/is)
    const activeSubmenuRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-submenu a\.nav-active\s*\{([^}]*)\}/is,
    )
    expect(activeSubmenuRule?.[1]).toMatch(/background:\s*#181a18/is)
    expect(activeSubmenuRule?.[1]).toMatch(/color:\s*#fbfbf8/is)
    expect(prismaStyles).toMatch(/@media \(max-width:\s*900px\)[\s\S]*?sidebar-collapsed \.university-nav-group \.nav-group-trigger[\s\S]*?grid-template-columns:\s*34px minmax\(0, 1fr\) auto/is)
  })

  it('keeps bilingual submenu labels full width and centered on mobile drawers', () => {
    const prismaStyles = readFileSync('src/styles/prisma.css', 'utf8')
    const submenuLinkRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-submenu a\s*\{([^}]*)\}/is,
    )
    const submenuBeforeRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-submenu a::before\s*\{([^}]*)\}/is,
    )
    const submenuCopyRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-submenu-copy\s*\{([^}]*)\}/is,
    )
    const submenuTitleRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-submenu-title\s*\{([^}]*)\}/is,
    )
    const submenuSubtitleRule = prismaStyles.match(
      /\.archive-shell:not\(\.home-shell\) \.university-nav-group \.nav-submenu a small\s*\{([^}]*)\}/is,
    )

    expect(submenuLinkRule?.[1]).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)/is)
    expect(submenuLinkRule?.[1]).toMatch(/width:\s*100%/is)
    expect(submenuLinkRule?.[1]).toMatch(/align-content:\s*center/is)
    expect(submenuLinkRule?.[1]).toMatch(/justify-items:\s*stretch/is)
    expect(submenuBeforeRule?.[1]).toMatch(/display:\s*none\s*!important/is)
    expect(submenuCopyRule?.[1]).toMatch(/align-content:\s*center/is)
    expect(submenuTitleRule?.[1]).toMatch(/overflow-wrap:\s*normal\s*!important/is)
    expect(submenuTitleRule?.[1]).toMatch(/word-break:\s*keep-all\s*!important/is)
    expect(submenuSubtitleRule?.[1]).toMatch(/overflow-wrap:\s*normal\s*!important/is)
    expect(submenuSubtitleRule?.[1]).toMatch(/word-break:\s*normal\s*!important/is)
    expect(submenuSubtitleRule?.[1]).toMatch(/white-space:\s*nowrap\s*!important/is)
  })

  it('renders the real university sections as bilingual archive navigation rows', async () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: { template: '<div data-test="public-view" />' },
        },
      },
    })

    await wrapper.get('.nav-group-trigger').trigger('click')

    expect(wrapper.findAll('.nav-submenu a')).toHaveLength(4)
    expect(
      wrapper.findAll('.nav-submenu a').map((item) => ({
        title: item.get('.nav-submenu-title').text(),
        subtitle: item.get('small').text(),
      })),
    ).toEqual([
      { title: 'OPC 社区', subtitle: 'OPC communities' },
      { title: '支持措施', subtitle: 'Support measures' },
      { title: '竞赛活动', subtitle: 'Competition activities' },
      { title: '高校创业案例', subtitle: 'University venture cases' },
    ])
  })

  it('keeps the public archive navigation without Assistant-only branches', async () => {
    const wrapper = mount(MainLayout, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: { template: '<div data-test="public-view" />' },
        },
      },
    })

    expect(wrapper.find('.public-page-heading').exists()).toBe(true)
    expect(wrapper.find('.archive-sidebar').exists()).toBe(true)
    expect(wrapper.find('.content-shell').exists()).toBe(true)
    expect(wrapper.find('.assistant-content-shell').exists()).toBe(false)
    expect(wrapper.classes()).not.toContain('assistant-route-shell')
    expect(wrapper.find('[data-test="public-view"]').exists()).toBe(true)

    await wrapper.get('.sidebar-toggle').trigger('click')
    expect(wrapper.classes()).toContain('sidebar-collapsed')

    await wrapper.get('.nav-group-trigger').trigger('click')
    expect(wrapper.get('.nav-group-trigger').attributes('aria-expanded')).toBe('true')
    expect(wrapper.findAll('.nav-submenu a')).toHaveLength(4)

    await wrapper.get('.nav-group-trigger').trigger('keydown', { key: 'Escape' })
    expect(wrapper.get('.nav-group-trigger').attributes('aria-expanded')).toBe('false')
  })

})
