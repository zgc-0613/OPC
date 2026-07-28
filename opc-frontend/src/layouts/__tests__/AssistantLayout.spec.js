import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantLayout from '@/layouts/AssistantLayout.vue'

describe('AssistantLayout', () => {
  it('renders a single bounded workbench shell with explicit exits and no public archive sidebar', () => {
    const wrapper = mount(AssistantLayout, {
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
          RouterView: { template: '<div data-test="assistant-view" />' },
        },
      },
    })

    expect(wrapper.find('.assistant-shell').exists()).toBe(true)
    expect(wrapper.find('.archive-sidebar').exists()).toBe(false)
    expect(wrapper.find('.assistant-layout-content').exists()).toBe(true)
    expect(wrapper.get('a[href="/"]').attributes('aria-label')).toBe('返回公开索引')
    expect(wrapper.get('a[href="/account"]').attributes('aria-label')).toBe('进入个人主页')
    const materials = wrapper.get('button[aria-label="打开研究资料"]')
    expect(materials.attributes('type')).toBe('button')
    expect(wrapper.find('a[href="#assistant-evidence-title"]').exists()).toBe(false)
  })
})
