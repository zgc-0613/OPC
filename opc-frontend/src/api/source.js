import request from './request'

export function getSources() {
  return request.get('/public/sources')
}

export function createSource(data) {
  return request.post('/admin/sources', data)
}

export function updateSource(id, data) {
  return request.put(`/admin/sources/${id}`, data)
}

export function deleteSource(id) {
  return request.delete(`/admin/sources/${id}`)
}
