import { getEvidenceReviewDetail, updateEvidenceReview } from '@/api/evidenceReview'

export function deletionConfirmation(itemType, item) {
  const label = itemType === 'case' ? '案例' : '政策'
  if (item.aiEvidenceStatus === 'verified') {
    return `该${label}已经核验。继续后会先自动移回待审，再永久删除「${item.title}」，且无法恢复。确认继续吗？`
  }
  return `确认永久删除${label}「${item.title}」吗？此操作无法恢复。`
}

export async function prepareEvidenceItemDeletion(itemType, item) {
  const label = itemType === 'case' ? '案例' : '政策'
  let current = await getEvidenceReviewDetail(itemType, item.id)

  if (current.evidenceStatus === 'verified') {
    current = await updateEvidenceReview(itemType, item.id, {
      evidenceStatus: 'legacy_unverified',
      expectedEvidenceStatus: current.evidenceStatus,
      expectedUpdatedAt: current.updatedAt,
      expectedVersion: Number(current.version ?? 0),
      reason: `${label}管理页永久删除前自动移回待审`,
      notes: '管理员已在删除确认框中确认永久删除。',
      cascade: false,
    })
  }

  return {
    expectedEvidenceRevision: Number(current.version ?? 0),
    expectedUpdatedAt: current.updatedAt,
  }
}
