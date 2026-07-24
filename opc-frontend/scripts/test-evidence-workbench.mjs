import assert from 'node:assert/strict'
import {
  buildEvidenceBatchPayload,
  buildEvidenceDecisionPayload,
  buildEvidenceEditPayload,
  clampEvidencePage,
  groupEvidenceBySource,
  hydrateEvidenceEditForm,
  isLatestEvidenceRequest,
  nextEvidenceItem,
  reconcileEvidenceSelection,
  readEvidenceQuery,
  resolveEvidencePane,
  writeEvidenceQuery,
} from '../src/utils/evidenceWorkbench.js'

const source = { itemType: 'source', itemId: 8, sourceId: 8, title: '省政府公报', evidenceStatus: 'verified', version: 4, updatedAt: '2026-07-25T01:00:00' }
const policy = { itemType: 'policy', itemId: 12, sourceId: 8, sourceTitle: '省政府公报', title: '创业政策', evidenceStatus: 'legacy_unverified', version: 2, updatedAt: '2026-07-25T01:01:00' }
const caseItem = { itemType: 'case', itemId: 11, sourceId: 8, sourceTitle: '省政府公报', title: '创业案例', evidenceStatus: 'legacy_unverified', version: 3, updatedAt: '2026-07-25T01:02:00' }

const groups = groupEvidenceBySource([policy, source, caseItem])
assert.equal(groups.length, 1)
assert.equal(groups[0].source.itemId, 8)
assert.deepEqual(groups[0].items.map((item) => item.itemType), ['policy', 'case'])

const query = readEvidenceQuery({ q: '人工智能', type: 'case', page: '3', selected: 'case:11' })
assert.equal(query.keyword, '人工智能')
assert.equal(query.page, 3)
assert.deepEqual(writeEvidenceQuery(query, 'case:11'), { q: '人工智能', type: 'case', status: 'legacy_unverified', page: 3, selected: 'case:11' })

const decision = buildEvidenceDecisionPayload(caseItem, 'verified', { notes: '已核对原文' })
assert.equal(decision.expectedEvidenceStatus, 'legacy_unverified')
assert.equal(decision.expectedUpdatedAt, caseItem.updatedAt)
assert.equal(decision.expectedVersion, 3)

const batch = buildEvidenceBatchPayload([source, policy, caseItem], new Set(['source:8', 'case:11']), 'excluded', { reason: '测试记录', cascade: true })
assert.deepEqual(batch.items.map((item) => `${item.itemType}:${item.itemId}`), ['source:8', 'case:11'])
assert.equal(batch.items[0].expectedEvidenceStatus, 'verified')
assert.equal(batch.items[0].expectedVersion, 4)
assert.equal(batch.cascade, true)

assert.equal(nextEvidenceItem([source, policy, caseItem], 'policy:12').itemId, 11)
assert.equal(nextEvidenceItem([source], 'source:8'), null)
assert.deepEqual([...reconcileEvidenceSelection([source, policy], new Set(['source:8', 'case:11']))], ['source:8'])
assert.equal(clampEvidencePage(2, 1, 20), 1)

const editPayload = buildEvidenceEditPayload('case', {
  id: 11,
  title: '新标题',
  regionId: '2',
  sourceId: '8',
  aiEvidenceStatus: 'verified',
  evidenceRevision: 7,
  updatedAt: '2026-07-25T01:02:00',
})
assert.equal(editPayload.aiEvidenceStatus, undefined)
assert.equal(editPayload.regionId, 2)
assert.equal(editPayload.expectedEvidenceRevision, 7)
assert.equal(editPayload.expectedUpdatedAt, '2026-07-25T01:02:00')

const proxiedContent = new Proxy({
  title: '武汉人工智能应用创业案例',
  regionId: 2,
  sourceId: 8,
  summary: '已有案例摘要',
  tags: ['人工智能', '一人公司'],
}, {})
assert.throws(() => structuredClone(proxiedContent), { name: 'DataCloneError' })
const hydratedContent = hydrateEvidenceEditForm(proxiedContent)
assert.deepEqual(hydratedContent, {
  title: '武汉人工智能应用创业案例',
  regionId: 2,
  sourceId: 8,
  summary: '已有案例摘要',
  tags: ['人工智能', '一人公司'],
})
proxiedContent.tags.push('数据分析')
assert.deepEqual(hydratedContent.tags, ['人工智能', '一人公司'])
assert.equal(resolveEvidencePane(true, 'case:11'), 'detail')
assert.equal(resolveEvidencePane(true, ''), 'queue')
assert.equal(isLatestEvidenceRequest(4, 5), false)
assert.equal(isLatestEvidenceRequest(5, 5), true)

console.log('evidence workbench tests passed')
