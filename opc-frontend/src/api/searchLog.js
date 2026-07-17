import request from './request'

export function recordSearchKeyword(payload) {
  return request.post('/public/search-logs', payload)
}

export function getHotSearchKeywords(params = {}) {
  return request.get('/public/search-logs/hot', { params })
}
