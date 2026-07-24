import assert from 'node:assert/strict'
import { buildEvidenceReviewBatchPayload } from '../src/utils/evidenceReviewBatch.js'

const pageItems = [
  { itemType: 'case', itemId: 11, title: 'Case 11', evidenceStatus: 'legacy_unverified', version: 2, updatedAt: '2026-07-25T01:00:00' },
  { itemType: 'policy', itemId: 11, title: 'Policy 11', evidenceStatus: 'legacy_unverified', version: 2, updatedAt: '2026-07-25T01:00:00' },
  { itemType: 'source', itemId: 8, title: 'Source 8', evidenceStatus: 'verified', version: 5, updatedAt: '2026-07-25T01:00:00' },
]

assert.deepEqual(
  buildEvidenceReviewBatchPayload(
    pageItems,
    new Set(['case:11', 'source:8']),
    'verified',
  ),
  {
    items: [
      { itemType: 'case', itemId: 11, expectedEvidenceStatus: 'legacy_unverified', expectedUpdatedAt: '2026-07-25T01:00:00', expectedVersion: 2 },
      { itemType: 'source', itemId: 8, expectedEvidenceStatus: 'verified', expectedUpdatedAt: '2026-07-25T01:00:00', expectedVersion: 5 },
    ],
    evidenceStatus: 'verified',
    reason: undefined,
    notes: undefined,
    cascade: false,
  },
  'batch payload must contain only explicitly selected composite evidence keys',
)

console.log('Evidence review batch selection checks passed.')
