import request from './request'

export function getPolicies(params = {}) {
  return request.get('/public/policies', { params })
}

export function getPolicyDetail(id) {
  return request.get(`/public/policies/${id}`)
}
