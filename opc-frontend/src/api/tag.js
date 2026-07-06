import request from './request'

export function getTags(tagType) {
  return request.get('/public/tags', {
    params: tagType ? { tagType } : {},
  })
}

export function createTag(data) {
  return request.post('/admin/tags', data)
}

export function updateTag(id, data) {
  return request.put(`/admin/tags/${id}`, data)
}

export function deleteTag(id) {
  return request.delete(`/admin/tags/${id}`)
}
