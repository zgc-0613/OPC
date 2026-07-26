import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantEvidencePanel from '@/components/assistant/AssistantEvidencePanel.vue'

describe('AssistantEvidencePanel', () => {
  it('groups authorized research materials and hardens every external link', () => {
    const wrapper = mount(AssistantEvidencePanel, {
      props: {
        runId: 31,
        items: [
          {
            itemType: 'case', itemId: 11, sourceId: 2, title: '武汉 AI 工作室', brief: '案例摘要',
            regionName: '武汉市', geographicLevel: 'city', industry: 'software',
            matchReason: '匹配已核验行业标签', evidenceStatus: 'verified', publisher: '武汉市政府',
            sourceTitle: '案例原文', originalUrl: 'https://example.gov.cn/case/11', detailUrl: '/cases/11', available: true,
          },
          {
            itemType: 'policy', itemId: 21, sourceId: 1, title: '湖北创业支持', brief: '政策摘要',
            regionName: '湖北省', geographicLevel: 'province', industry: 'funding',
            matchReason: '地区通用创业政策', evidenceStatus: 'verified', publisher: '湖北省政府',
            sourceTitle: '政策原文', originalUrl: 'https://example.gov.cn/policy/21', detailUrl: '/policies/21', available: true,
          },
          {
            itemType: 'source', itemId: 3, sourceId: 3, title: '不安全来源', brief: '',
            matchReason: '已核验原始来源', evidenceStatus: 'verified', publisher: '未知',
            sourceTitle: '不安全来源', originalUrl: 'javascript:alert(1)', detailUrl: null, available: true,
          },
        ],
      },
    })

    expect(wrapper.text()).toContain('研究资料')
    expect(wrapper.text()).toContain('案例')
    expect(wrapper.text()).toContain('政策')
    expect(wrapper.text()).toContain('原始来源')
    expect(wrapper.text()).toContain('引用 [2]')
    expect(wrapper.get('a[href="/cases/11"]').text()).toContain('站内详情')
    const external = wrapper.get('a[href="https://example.gov.cn/case/11"]')
    expect(external.attributes('target')).toBe('_blank')
    expect(external.attributes('rel')).toBe('noopener noreferrer')
    expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false)
  })

  it('does not render stale content or links for unavailable evidence', () => {
    const wrapper = mount(AssistantEvidencePanel, {
      props: {
        runId: 31,
        items: [{
          itemType: 'case', itemId: 11, sourceId: 2, title: '资料当前不可用',
          brief: 'must not leak', matchReason: '状态已变化，请重新检索', evidenceStatus: 'unavailable',
          originalUrl: 'https://example.gov.cn/stale', detailUrl: '/cases/11', available: false,
        }],
      },
    })

    expect(wrapper.text()).toContain('资料当前不可用')
    expect(wrapper.text()).toContain('状态已变化，请重新检索')
    expect(wrapper.text()).not.toContain('must not leak')
    expect(wrapper.find('a').exists()).toBe(false)
  })

  it('distinguishes loading, empty, and error states', async () => {
    const wrapper = mount(AssistantEvidencePanel, { props: { runId: 31, loading: true } })
    expect(wrapper.get('[role="status"]').text()).toContain('正在整理研究资料')

    await wrapper.setProps({ loading: false, error: '资料读取失败' })
    expect(wrapper.get('[role="alert"]').text()).toContain('资料读取失败')

    await wrapper.setProps({ error: '', items: [] })
    expect(wrapper.get('[role="status"]').text()).toContain('暂无可展示的研究资料')
  })
})
