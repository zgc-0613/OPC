import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantResearchTask from '@/components/assistant/AssistantResearchTask.vue'

const baseTask = {
  taskType: '',
  caseIds: [],
  comparisonDimensions: [],
  sourceId: '',
  technologyTagId: '',
  technologyText: '',
  applicationScenario: '',
  teamCapabilities: '',
  timeline: '',
  existingResources: '',
  constraints: '',
  outputDepth: 'standard',
}

const cases = [
  { id: 11, title: '已核验案例 A', regionName: '湖北省', summary: '已发布并有合格来源' },
  { id: 12, title: '已核验案例 B', regionName: '湖北省', summary: '已发布并有合格来源' },
]

const sources = [
  { id: 71, title: '已核验来源', publisher: '公开发布机构', url: 'https://example.gov.cn/source' },
]

const technologies = [
  { id: 91, name: '检索增强生成' },
  { id: 92, name: '工作流自动化' },
]

describe('AssistantResearchTask', () => {
  it('shows all six controlled research tasks without creating a run', () => {
    const wrapper = mount(AssistantResearchTask, {
      props: { modelValue: baseTask, editable: true, caseOptions: cases, sourceOptions: sources },
    })

    expect(wrapper.findAll('[data-testid^="research-task-"]')).toHaveLength(6)
    expect(wrapper.get('.task-options').attributes('role')).toBe('group')
    expect(wrapper.get('[data-testid="research-task-case_analysis"]').attributes('aria-pressed')).toBe('false')
    expect(wrapper.text()).toContain('单案例分析')
    expect(wrapper.text()).toContain('多案例比较')
    expect(wrapper.text()).toContain('技术路线评估')
    expect(wrapper.text()).toContain('政策检索')
    expect(wrapper.text()).toContain('来源核验')
    expect(wrapper.text()).toContain('通用创业研究')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('emits only selected case IDs and controlled dimensions for a comparison', async () => {
    const wrapper = mount(AssistantResearchTask, {
      props: { modelValue: baseTask, editable: true, caseOptions: cases, sourceOptions: sources },
    })

    await wrapper.get('[data-testid="research-task-case_comparison"]').trigger('click')
    expect(wrapper.get('[data-testid="research-task-case_comparison"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({
      taskType: 'case_comparison',
      caseIds: [],
      comparisonDimensions: [],
    })

    await wrapper.get('[data-testid="task-case-selector"]').setValue(['11', '12'])
    await wrapper.get('input[value="businessModel"]').setValue(true)
    const value = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(value).toMatchObject({
      taskType: 'case_comparison',
      caseIds: [11, 12],
      comparisonDimensions: ['businessModel'],
    })
    expect(value.caseIds.every((id) => cases.some((item) => item.id === id))).toBe(true)
  })

  it('keeps source selection explicit and presents a compact read-only task summary', async () => {
    const wrapper = mount(AssistantResearchTask, {
      props: { modelValue: baseTask, editable: true, caseOptions: cases, sourceOptions: sources },
    })
    await wrapper.get('[data-testid="research-task-source_verification"]').trigger('click')
    await wrapper.get('[data-testid="task-source-selector"]').setValue('71')

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({
      taskType: 'source_verification', sourceId: 71,
    })

    await wrapper.setProps({
      editable: false,
      modelValue: { ...baseTask, taskType: 'case_comparison', caseIds: [11, 12], comparisonDimensions: ['businessModel'] },
    })
    expect(wrapper.text()).toContain('多案例比较')
    expect(wrapper.text()).toContain('2 个比较对象')
    expect(wrapper.find('[data-testid="task-case-selector"]').exists()).toBe(false)
  })

  it('captures the complete technology assessment boundary and restores it read-only', async () => {
    const wrapper = mount(AssistantResearchTask, {
      props: {
        modelValue: baseTask,
        editable: true,
        caseOptions: cases,
        sourceOptions: sources,
        technologyOptions: technologies,
      },
    })

    await wrapper.get('[data-testid="research-task-technology_assessment"]').trigger('click')
    await wrapper.get('[data-testid="task-technology-tag"]').setValue('91')
    await wrapper.get('[data-testid="task-technology-text"]').setValue('面向私有知识库的补充说明')
    await wrapper.get('[data-testid="task-application-scenario"]').setValue('为小微企业客服提供可追溯回答')
    await wrapper.get('[data-testid="task-team-capabilities"]').setValue('两名全栈工程师，具备向量检索经验')
    await wrapper.get('[data-testid="task-timeline"]').setValue('3_6_months')
    await wrapper.get('[data-testid="task-existing-resources"]').setValue('已有脱敏 FAQ 与试点客户')
    await wrapper.get('[data-testid="task-constraints"]').setValue('数据不得离开私有网络')
    await wrapper.get('[data-testid="task-output-depth"]').setValue('deep')

    const value = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(value).toMatchObject({
      taskType: 'technology_assessment',
      technologyTagId: 91,
      technologyText: '面向私有知识库的补充说明',
      applicationScenario: '为小微企业客服提供可追溯回答',
      teamCapabilities: '两名全栈工程师，具备向量检索经验',
      timeline: '3_6_months',
      existingResources: '已有脱敏 FAQ 与试点客户',
      constraints: '数据不得离开私有网络',
      outputDepth: 'deep',
    })

    await wrapper.setProps({ editable: false, modelValue: value })
    expect(wrapper.text()).toContain('检索增强生成')
    expect(wrapper.text()).toContain('为小微企业客服提供可追溯回答')
    expect(wrapper.text()).toContain('两名全栈工程师')
    expect(wrapper.text()).toContain('3-6 个月')
    expect(wrapper.text()).toContain('已有脱敏 FAQ')
    expect(wrapper.text()).toContain('数据不得离开私有网络')
  })
})
