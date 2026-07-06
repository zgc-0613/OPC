import request from './request'

export function getCases(params = {}) {
  return request.get('/public/cases', { params })
}

export function getCaseDetail(id) {
  return request.get(`/public/cases/${id}`)
}

export function createCase(data) {
  return request.post('/admin/cases', data)
}

export function updateCase(id, data) {
  return request.put(`/admin/cases/${id}`, data)
}

export function deleteCase(id) {
  return request.delete(`/admin/cases/${id}`)
}
