import request from './request'

export function getCases(params = {}) {
  return request.get('/public/cases', { params })
}

export function getCaseDetail(id) {
  return request.get(`/public/cases/${id}`)
}
