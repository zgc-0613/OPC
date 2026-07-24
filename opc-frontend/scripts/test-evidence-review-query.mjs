import assert from 'node:assert/strict'
import { buildEvidenceReviewQuery } from '../src/utils/evidenceReviewQuery.js'

assert.deepEqual(
  buildEvidenceReviewQuery({ itemType: '', evidenceStatus: '', page: 1, size: 20 }),
  { page: 1, size: 20 },
  'all filters must omit blank query parameters',
)

assert.deepEqual(
  buildEvidenceReviewQuery({ itemType: 'case', evidenceStatus: 'verified', page: 2, size: 20 }),
  { itemType: 'case', evidenceStatus: 'verified', page: 2, size: 20 },
  'selected filters must be preserved',
)

console.log('Evidence review query regression checks passed.')
