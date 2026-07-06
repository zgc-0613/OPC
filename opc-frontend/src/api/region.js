import request from './request'

export function getRegions() {
  return request.get('/public/regions')
}
