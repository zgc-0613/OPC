import assert from 'node:assert/strict'

import {
  confirmIndustrySuggestion,
  createLatestRequestGate,
  decideIndustryResolution,
  industrySuggestionKey,
  readinessPresentation,
} from '../src/utils/assistantWorkflow.js'

const fuzzy = {
  tagId: 703,
  name: '人工智能应用',
  method: 'fuzzy',
  confidence: 0.9,
  requiresConfirmation: true,
}
const decision = decideIndustryResolution(fuzzy, '人工智能应用行业', '', '')
assert.equal(decision.action, 'confirm')
assert.equal(decision.selection, null)
assert.equal(decision.suggestion.originalText, '人工智能应用行业')

const confirmed = confirmIndustrySuggestion(decision.suggestion)
assert.deepEqual(confirmed, {
  industryTagId: '703',
  industry: '人工智能应用',
  query: '人工智能应用',
})

const rejectedKey = industrySuggestionKey(decision.suggestion)
const rejected = decideIndustryResolution(fuzzy, '人工智能应用行业', '', rejectedKey)
assert.equal(rejected.action, 'rejected')
assert.equal(rejected.suggestion, null)

const alias = decideIndustryResolution({
  ...fuzzy,
  method: 'alias',
  confidence: 0.98,
  requiresConfirmation: false,
}, 'AIGC', '', '')
assert.equal(alias.action, 'accept')
assert.equal(alias.selection.industryTagId, '703')

const gate = createLatestRequestGate()
const first = gate.begin()
const second = gate.begin()
assert.equal(gate.isCurrent(first), false)
assert.equal(gate.isCurrent(second), true)

assert.deepEqual(readinessPresentation('sufficient'), { canSubmit: true, warning: false })
assert.deepEqual(readinessPresentation('partial'), { canSubmit: true, warning: true })
assert.deepEqual(readinessPresentation('insufficient'), { canSubmit: false, warning: false })
assert.deepEqual(readinessPresentation(null, { loading: true }), { canSubmit: false, warning: false })
assert.deepEqual(readinessPresentation(null, { error: true }), { canSubmit: false, warning: false })

console.log('assistant industry confirmation and readiness workflow: ok')
