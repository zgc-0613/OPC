import request from './request'

export function getEvidenceReviewQueue(params = {}) {
  return request.get('/admin/evidence-reviews', { params })
}

export function updateEvidenceReview(itemType, itemId, payload) {
  return request.put(`/admin/evidence-reviews/${itemType}/${itemId}`, payload)
}
