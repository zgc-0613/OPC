export const RESEARCH_TASKS = Object.freeze([
  { id: 'case_analysis', label: '单案例分析', detail: '围绕一个已核验案例拆解路径' },
  { id: 'case_comparison', label: '多案例比较', detail: '用统一维度比较 2-3 个案例' },
  { id: 'technology_assessment', label: '技术路线评估', detail: '评估可行性、成本和风险' },
  { id: 'policy_lookup', label: '政策检索', detail: '核验地区与行业支持条件' },
  { id: 'source_verification', label: '来源核验', detail: '确认来源能支撑哪些结论' },
  { id: 'general_research', label: '通用创业研究', detail: '综合机会、约束和行动顺序' },
])

export const RESEARCH_TASK_TYPE_IDS = Object.freeze(RESEARCH_TASKS.map((task) => task.id))
export const RESEARCH_TASK_TYPE_SET = new Set(RESEARCH_TASK_TYPE_IDS)

export const COMPARISON_DIMENSIONS = Object.freeze([
  { id: 'businessModel', label: '商业模式' },
  { id: 'technicalPath', label: '技术路线' },
  { id: 'targetCustomer', label: '目标客户' },
  { id: 'outcome', label: '结果与收入' },
  { id: 'regionalContext', label: '地区条件' },
  { id: 'evidenceStrength', label: '证据强度' },
])

export const TECHNOLOGY_TIMELINES = Object.freeze([
  { id: '', label: '暂未确定' },
  { id: 'under_1_month', label: '1 个月内' },
  { id: '1_3_months', label: '1-3 个月' },
  { id: '3_6_months', label: '3-6 个月' },
  { id: '6_12_months', label: '6-12 个月' },
  { id: 'over_12_months', label: '12 个月以上' },
])

export function researchTaskLabel(taskType) {
  return RESEARCH_TASKS.find((task) => task.id === taskType)?.label || '研究结果'
}

export function technologyTimelineLabel(timeline) {
  return TECHNOLOGY_TIMELINES.find((item) => item.id === timeline)?.label || timeline || '未填写'
}
