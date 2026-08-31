import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  detail: vi.fn(),
  update: vi.fn(),
}))

vi.mock('@/api/evidenceReview', () => ({
  getEvidenceReviewDetail: api.detail,
  updateEvidenceReview: api.update,
}))

import { deletionConfirmation, prepareEvidenceItemDeletion } from '@/utils/adminEvidenceDeletion'

describe('admin evidence deletion', () => {
  beforeEach(() => vi.clearAllMocks())

  it('moves a verified policy back to review before returning its fresh deletion snapshot', async () => {
    api.detail.mockResolvedValue({
      evidenceStatus: 'verified', version: 4, updatedAt: '2026-08-17T20:00:00',
    })
    api.update.mockResolvedValue({
      evidenceStatus: 'legacy_unverified', version: 5, updatedAt: '2026-08-18T00:10:00',
    })

    const snapshot = await prepareEvidenceItemDeletion('policy', { id: 66, title: '测试政策' })

    expect(api.update).toHaveBeenCalledWith('policy', 66, expect.objectContaining({
      evidenceStatus: 'legacy_unverified',
      expectedEvidenceStatus: 'verified',
      expectedVersion: 4,
      cascade: false,
    }))
    expect(snapshot).toEqual({
      expectedEvidenceRevision: 5,
      expectedUpdatedAt: '2026-08-18T00:10:00',
    })
  })

  it('uses the current server snapshot without changing an unverified case', async () => {
    api.detail.mockResolvedValue({
      evidenceStatus: 'legacy_unverified', version: 2, updatedAt: '2026-08-18T00:20:00',
    })

    const snapshot = await prepareEvidenceItemDeletion('case', { id: 9, title: '测试案例' })

    expect(api.update).not.toHaveBeenCalled()
    expect(snapshot.expectedEvidenceRevision).toBe(2)
  })

  it('warns clearly when deleting a verified record', () => {
    expect(deletionConfirmation('case', {
      title: '测试案例', aiEvidenceStatus: 'verified',
    })).toContain('先自动移回待审')
  })
})
