import request from './request'

export function getTags(tagType) {
  return request.get('/public/tags', {
    params: tagType ? { tagType } : {},
  })
}
