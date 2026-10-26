import request from './request'

export function getPlatformSites() {
  return request.get('/public/platform-sites')
}
