import request from './request'

export function getDashboardSummary() {
  return request.get('/public/dashboard/summary')
}
