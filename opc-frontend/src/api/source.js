import request from './request'

export function getSources() {
  return request.get('/public/sources')
}
