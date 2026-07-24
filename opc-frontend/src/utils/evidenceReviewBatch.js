import { buildEvidenceBatchPayload } from './evidenceWorkbench.js'

export function buildEvidenceReviewBatchPayload(items, selectedKeys, evidenceStatus, options = {}) {
  return buildEvidenceBatchPayload(items, selectedKeys, evidenceStatus, options)
}
