import { describe, expect, it } from 'vitest'

import { COMPARISON_DIMENSIONS, RESEARCH_TASK_TYPE_IDS } from '@/constants/researchTasks'

describe('Phase Three research task contract', () => {
  it('keeps the six API task type values centralized and stable', () => {
    expect(RESEARCH_TASK_TYPE_IDS).toEqual([
      'case_analysis',
      'case_comparison',
      'technology_assessment',
      'policy_lookup',
      'source_verification',
      'general_research',
    ])
    expect(COMPARISON_DIMENSIONS.map((item) => item.id)).toEqual([
      'businessModel', 'technicalPath', 'targetCustomer',
      'outcome', 'regionalContext', 'evidenceStrength',
    ])
  })
})
