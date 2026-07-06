import request from './request'

export function getPolicies(params = {}) {
  return request.get('/public/policies', { params })
}

export function getPolicyDetail(id) {
  return request.get(`/public/policies/${id}`)
}

export function createPolicy(data) {
  return request.post('/admin/policies', data)
}

export function updatePolicy(id, data) {
  return request.put(`/admin/policies/${id}`, data)
}

export function deletePolicy(id) {
  return request.delete(`/admin/policies/${id}`)
}
