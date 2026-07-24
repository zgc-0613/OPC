import request from './request'
import { buildEvidenceReviewQuery } from '@/utils/evidenceReviewQuery'

export function getEvidenceReviewQueue(params = {}) {
  return request.get('/admin/evidence-reviews', { params: buildEvidenceReviewQuery(params) })
}

export function getEvidenceReviewDetail(itemType, itemId) {
  return request.get(`/admin/evidence-reviews/${itemType}/${itemId}`)
}

export function preflightEvidenceReviews(payload) {
  return request.post('/admin/evidence-reviews/batch/preflight', payload)
}

export function updateEvidenceReview(itemType, itemId, payload) {
  return request.put(`/admin/evidence-reviews/${itemType}/${itemId}`, payload)
}

export function updateEvidenceReviews(payload) {
  return request.put('/admin/evidence-reviews/batch', payload)
}
