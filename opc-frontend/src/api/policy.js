import request from './request'

export function getPolicies(params = {}) {
  return request.get('/public/policies', { params })
}

export function getPolicyDetail(id) {
  return request.get(`/public/policies/${id}`)
}

export function getAdminPolicies(params = {}) {
  return request.get('/admin/policies', { params })
}

export function getAdminPolicyDetail(id) {
  return request.get(`/admin/policies/${id}`)
}

export function createPolicy(data) {
  return request.post('/admin/policies', data)
}

export function updatePolicy(id, data) {
  return request.put(`/admin/policies/${id}`, data)
}

export function updatePolicyApplicabilityBatch(data) {
  return request.put('/admin/policies/applicability/batch', data)
}

export function deletePolicy(id, snapshot) {
  return request.delete(`/admin/policies/${id}`, { params: snapshot })
}
