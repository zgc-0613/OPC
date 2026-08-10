import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantStructuredResult from '@/components/assistant/AssistantStructuredResult.vue'

const result = {
  schemaVersion: 'phase3-structured-result-v1',
  taskType: 'case_analysis',
  directAnswer: '该案例适合先验证面向小型企业的订阅式服务。',
  keyFindings: [{
    id: 'business-model', kind: 'fact', text: '案例以订阅收入为主。',
    sourceIds: [91], confidence: 'high', missingEvidence: false,
  }],
  recommendations: [{
    id: 'pilot', kind: 'inference', text: '先完成一个付费试点。',
    sourceIds: [91], confidence: 'medium', missingEvidence: false,
  }],
  risks: [{
    id: 'risk', kind: 'inference', text: '客户获取成本仍需验证。',
    sourceIds: [], confidence: 'low', missingEvidence: false,
  }],
  assumptions: [{
    id: 'assumption', kind: 'methodology', text: '本结论基于当前已核验来源。',
    sourceIds: [], confidence: 'medium', missingEvidence: false,
  }],
  uncertainties: [{
    id: 'unknown', kind: 'methodology', text: '缺少近期续费率证据。',
    sourceIds: [], confidence: 'low', missingEvidence: false,
  }],
  nextQuestions: ['确认首批客户的预算区间。'],
  citations: [{
    sourceId: 91, title: '案例原始来源', publisher: '公开发布者', url: 'https://example.com/source',
    evidenceRevision: 3, availability: 'current',
  }],
  evidenceCoverage: { factClaimCount: 1, citedFactClaimCount: 1, missingEvidenceFactCount: 0, ratio: 1 },
  confidence: 'medium',
  evidenceVersion: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  dataVersion: null,
  generatedAt: '2026-08-01T12:00:00+08:00',
  taskResult: {
    type: 'case_analysis', caseId: 22, evidenceStatus: 'sufficient',
    sections: {
      businessModel: {
        status: 'known', caveat: null,
        items: [{ id: 'model', kind: 'fact', text: '订阅模式已有来源支撑。', sourceIds: [91], confidence: 'high', missingEvidence: false }],
      },
    },
  },
}

describe('AssistantStructuredResult', () => {
  it('presents a verifiable structured research result and delegates citations to the existing drawer', async () => {
    const wrapper = mount(AssistantStructuredResult, { props: { result } })

    expect(wrapper.get('[data-testid="structured-result"]').text()).toContain('研究摘要')
    expect(wrapper.text()).toContain('该案例适合先验证面向小型企业的订阅式服务。')
    expect(wrapper.text()).toContain('事实')
    expect(wrapper.text()).toContain('建议')
    expect(wrapper.text()).toContain('研究方法')
    expect(wrapper.text()).toContain('已核验且充分')
    expect(wrapper.text()).toContain('案例原始来源')

    await wrapper.get('[data-testid="structured-result-citations"]').trigger('click')

    expect(wrapper.emitted('citations')).toEqual([[[91]]])
  })

  it('scopes heading IDs to the owning message', () => {
    const first = mount(AssistantStructuredResult, { props: { result, messageId: 101 } })
    const second = mount(AssistantStructuredResult, { props: { result, messageId: 102 } })

    const firstSummaryId = first.get('.result-summary h3').attributes('id')
    const secondSummaryId = second.get('.result-summary h3').attributes('id')
    expect(firstSummaryId).not.toBe(secondSummaryId)
    expect(first.get('.result-summary').attributes('aria-labelledby')).toBe(firstSummaryId)
    expect(second.get('.result-summary').attributes('aria-labelledby')).toBe(secondSummaryId)
  })

  it('renders the compatible server result shape without treating recommendations or limitations as raw markdown', () => {
    const serverResult = {
      schemaVersion: 'phase3-structured-result-v1',
      taskType: 'policy_lookup',
      summaryKind: 'inference',
      directAnswer: '先核对申请条件，再安排申报准备。',
      keyFindings: [{ text: '该政策面向已发布地区的创业项目。', evidenceType: 'fact', sourceIds: [17] }],
      recommendations: [{ priority: 'high', reason: '条件存在时间限制。', nextAction: '在本周核对资格材料。', sourceIds: [17] }],
      risks: ['申请窗口可能已经变化。'],
      assumptions: ['以当前核验版本为准。'],
      uncertainties: ['缺少具体项目资格信息。'],
      nextQuestions: [],
      citations: [{ sourceId: 17, claim: '政策支持创业项目。' }],
      evidenceVersion: 'sha256:server-derived',
      evidenceCoverage: { factClaimCount: 1, citedFactClaimCount: 1, missingEvidenceFactCount: 0, ratio: 1 },
    }

    const wrapper = mount(AssistantStructuredResult, { props: { result: serverResult } })

    expect(wrapper.text()).toContain('推断')
    expect(wrapper.text()).toContain('在本周核对资格材料。')
    expect(wrapper.text()).toContain('申请窗口可能已经变化。')
    expect(wrapper.text()).toContain('以当前核验版本为准。')
    expect(wrapper.text()).toContain('缺少具体项目资格信息。')
  })

  it('shows the server-owned analytics snapshot provenance without inventing aggregate values', () => {
    const wrapper = mount(AssistantStructuredResult, {
      props: {
        result: {
          ...result,
          dataVersion: 'analytics:20260801:verified',
          analyticsSnapshot: {
            analyticsSnapshotId: 77,
            metricId: 'industry.case_count',
            dataVersion: 'analytics:20260801:verified',
            filters: { regionId: 4, industryTagId: 9 },
            snapshot: { metricId: 'industry.case_count', dataVersion: 'analytics:20260801:verified', sampleSize: 12, missingCount: 2 },
          },
        },
      },
    })

    const provenance = wrapper.get('[data-testid="analytics-snapshot-provenance"]')
    expect(provenance.text()).toContain('数据快照')
    expect(provenance.text()).toContain('industry.case_count')
    expect(provenance.text()).toContain('analytics:20260801:verified')
    expect(provenance.text()).toContain('样本 12')
    expect(provenance.text()).toContain('缺失 2')
    expect(provenance.text()).not.toContain('未经服务端提供的聚合')
    const backlink = provenance.get('[data-testid="analytics-snapshot-backlink"]')
    const target = new URL(backlink.attributes('href'), 'https://opc.test')
    expect(target.pathname).toBe('/analytics')
    expect(Object.fromEntries(target.searchParams)).toEqual({
      metricId: 'industry.case_count',
      regionId: '4',
      industryTagId: '9',
    })
  })

  it.each([
    ['supports', '支持'],
    ['partially_supports', '部分支持'],
    ['does_not_support', '不支持'],
    ['conflicting', '存在冲突'],
    ['insufficient', '证据不足'],
  ])('renders the server-derived source verification verdict %s', (verdict, label) => {
    const explanation = `服务端核验说明：${label}`
    const wrapper = mount(AssistantStructuredResult, {
      props: {
        result: {
          ...result,
          taskType: 'source_verification',
          taskResult: {
            type: 'source_verification', verdict, verdictExplanation: explanation,
            evidenceStatus: verdict === 'insufficient' ? 'insufficient' : 'partial',
          },
        },
      },
    })

    const status = wrapper.get('.evidence-coverage')
    expect(status.text()).toContain(label)
    expect(status.text()).toContain(explanation)
  })

  it('renders server-owned publisher assessment metadata and unknown publisher caveat', () => {
    const knownWrapper = mount(AssistantStructuredResult, {
      props: {
        result: {
          ...result,
          taskType: 'source_verification',
          taskResult: {
            type: 'source_verification',
            verdict: 'supports',
            verdictExplanation: '已支持',
            evidenceStatus: 'sufficient',
            publisherAssessment: {
              status: 'known',
              items: [{ id: 'publisher_91', kind: 'fact', text: '服务端发布者', sourceIds: [91], confidence: 'high', missingEvidence: false }],
              caveat: null,
            },
          },
        },
      },
    })
    expect(knownWrapper.text()).toContain('服务端发布者')

    const unknownWrapper = mount(AssistantStructuredResult, {
      props: {
        result: {
          ...result,
          taskType: 'source_verification',
          taskResult: {
            type: 'source_verification',
            verdict: 'insufficient',
            verdictExplanation: '证据不足',
            evidenceStatus: 'insufficient',
            publisherAssessment: {
              status: 'unknown',
              items: [],
              caveat: '来源记录未提供发布者信息，无法核验发布者。',
            },
          },
        },
      },
    })
    expect(unknownWrapper.text()).toContain('来源记录未提供发布者信息，无法核验发布者。')
  })

  it('defensively suppresses legacy facts and citations from an insufficient source-verification payload', () => {
    const wrapper = mount(AssistantStructuredResult, {
      props: {
        result: {
          ...result,
          taskType: 'source_verification',
          directAnswer: 'LEGACY_UNVERIFIED_FACT must not be shown.',
          keyFindings: [{ id: 'legacy-fact', kind: 'fact', text: 'Legacy fact', sourceIds: [91] }],
          recommendations: [{ id: 'legacy-advice', kind: 'recommendation', text: 'Legacy advice', sourceIds: [91] }],
          citations: [{ sourceId: 91, title: 'Legacy source', publisher: 'Legacy publisher', availability: 'current' }],
          taskResult: {
            type: 'source_verification',
            verdict: 'insufficient',
            verdictExplanation: '当前主张尚未形成可核验的证据链。',
            evidenceStatus: 'insufficient',
            publisherAssessment: {
              status: 'known',
              items: [{ id: 'legacy-publisher', kind: 'fact', text: 'Legacy publisher', sourceIds: [91] }],
              caveat: null,
            },
            supportedClaims: {
              status: 'known',
              items: [{ id: 'legacy-supported', kind: 'fact', text: 'Legacy supported claim', sourceIds: [91] }],
              caveat: null,
            },
            invalidityReasons: {
              status: 'known',
              items: [{ id: 'unresolved', kind: 'methodology', text: '主张仍待核验。', sourceIds: [] }],
              caveat: null,
            },
          },
        },
      },
    })

    expect(wrapper.get('.result-summary').text()).toContain('当前没有足够的授权证据完成来源核验结论。')
    expect(wrapper.text()).toContain('证据不足')
    expect(wrapper.text()).toContain('当前主张尚未形成可核验的证据链。')
    expect(wrapper.text()).toContain('主张仍待核验。')
    expect(wrapper.text()).not.toContain('LEGACY_UNVERIFIED_FACT')
    expect(wrapper.text()).not.toContain('Legacy fact')
    expect(wrapper.text()).not.toContain('Legacy advice')
    expect(wrapper.text()).not.toContain('Legacy source')
    expect(wrapper.text()).not.toContain('Legacy publisher')
    expect(wrapper.find('[data-testid="structured-result-citations"]').exists()).toBe(false)
  })
})
